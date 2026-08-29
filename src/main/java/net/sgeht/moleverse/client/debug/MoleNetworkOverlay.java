package net.sgeht.moleverse.client.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowRoute;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Draws the mound network the way the client can see it: every mound around the
 * player, every link between two of them, and the path of any mole currently
 * travelling underground.
 *
 * <p>It sends no packets and asks the server nothing. The mounds are blocks the
 * client already has, so the picture is rebuilt here with the same chaining rule
 * the server uses - two mounds are linked up to
 * {@link BurrowConstants#NETWORK_LINK_MAX} apart, and the chain carries on from
 * each of them.</p>
 *
 * <p><strong>It is approximate, on purpose.</strong> The scan reaches at most the
 * render distance, and {@link BurrowConstants#NETWORK_SCAN_MAX} is larger than
 * that on most settings, so the server can legitimately reach a mound this
 * overlay never draws. The route line is the same kind of half-truth: the client
 * is never told where a mole is going, only where it is, so what is drawn is the
 * ground it has already covered, sampled once per rebuild. Following the logic
 * approximately still beats guessing at it - the same reasoning that produced the
 * slider panel.</p>
 *
 * <p>The scan is a flat block sweep rather than a point-of-interest query, which
 * the server would never do. It can be, because it happens once every ten ticks
 * on one position instead of per mole per decision with a chained search on top,
 * and because {@code PoiManager} is server-side anyway.</p>
 */
public final class MoleNetworkOverlay {

    /** Ticks between two rebuilds. The scan is what costs; drawing the result is not. */
    private static final int REBUILD_INTERVAL = 10;

    /** Beyond this many samples a trail is old news and the oldest end is dropped. */
    private static final int TRAIL_LIMIT = 128;

    private static final int CLOSED_COLOUR = 0xFFC8873C;
    private static final int OPEN_COLOUR = 0xFFFFD24A;
    private static final int LINK_COLOUR = 0xC04AC8FF;
    private static final int TRAIL_COLOUR = 0xFFFF5A4A;

    private static final float MOUND_STROKE = 2.0F;
    private static final float LINK_WIDTH = 2.0F;
    private static final float TRAIL_WIDTH = 3.0F;

    private static boolean enabled;
    private static int ticksSinceRebuild;

    private static List<Mound> mounds = List.of();
    private static List<Link> links = List.of();

    /** Where each travelling mole has been, keyed by entity id. */
    private static Map<Integer, List<Vec3>> trails = Map.of();

    private MoleNetworkOverlay() {
    }

    /** A mound as the client sees it. {@code open} is the shaft a mole is down. */
    private record Mound(BlockPos pos, boolean open) {
    }

    /**
     * A tunnel between two mounds, as a chain of points rather than a straight
     * line. Built on the client with {@link BurrowRoute#between}, the same call
     * the server makes: the route is derived from the heightmap and the
     * heightmap is the same on both sides, so this is where the mole really
     * travels, at the depth it really travels at - not a line drawn between two
     * holes.
     */
    private record Link(List<Vec3> path) {
    }

    /**
     * Turns the overlay on or off.
     *
     * @return a line for the chat, naming the reach so the approximation is
     *         visible rather than implied
     */
    public static String toggle(boolean on) {
        enabled = on;
        if (!on) {
            forget();
            return "Mound overlay off.";
        }

        ticksSinceRebuild = REBUILD_INTERVAL;
        return "Mound overlay on, reaching " + scanRadius() + " blocks and rebuilt every "
                + REBUILD_INTERVAL + " ticks. Client-side and approximate: the server chains out to "
                + BurrowConstants.NETWORK_SCAN_MAX + " blocks, and the route line is where a mole has been,"
                + " not where it is going.";
    }

    /**
     * Called once per client tick. The picture is rebuilt on a timer and emitted
     * from the cache every tick, because a gizmo added in a tick lives only until
     * the next one is drained.
     */
    public static void tick() {
        if (!enabled) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            // Left the world. Entity ids mean nothing in the next one.
            forget();
            return;
        }

        if (++ticksSinceRebuild >= REBUILD_INTERVAL) {
            ticksSinceRebuild = 0;
            rebuild(client.level, client.player.position());
        }
        draw();
    }

    private static void forget() {
        mounds = List.of();
        links = List.of();
        trails = Map.of();
        ticksSinceRebuild = 0;
    }

    /** Capped at the render distance: past it the client has no blocks to read. */
    private static int scanRadius() {
        return Math.min(BurrowConstants.NETWORK_SCAN_MAX,
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
    }

    // --- building the picture -------------------------------------------------

    private static void rebuild(ClientLevel level, Vec3 around) {
        int radius = scanRadius();
        mounds = findMounds(level, BlockPos.containing(around), radius);
        links = linkMounds(level, mounds);
        trails = extendTrails(level, around, radius);
    }

    /**
     * Sweeps the columns around the player and looks at the one block a mound
     * could occupy in each.
     *
     * <p>That block is exactly what {@code MOTION_BLOCKING_NO_LEAVES} reports,
     * because the mound has no collision and therefore does not raise the
     * heightmap itself - the same identity the server's {@code MoundNetwork}
     * relies on when it places one.</p>
     */
    private static List<Mound> findMounds(ClientLevel level, BlockPos centre, int radius) {
        List<Mound> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radiusSqr = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                cursor.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

                BlockState state = level.getBlockState(cursor);
                if (state.is(ModBlocks.MOLE_MOUND.get())) {
                    found.add(new Mound(cursor.immutable(), state.getValue(MoleMound.OPEN)));
                }
            }
        }
        return found;
    }

    /**
     * Every pair inside {@link BurrowConstants#NETWORK_LINK_MAX}. Drawing the
     * links rather than the chain is deliberate: the chain is what you get by
     * following them, and seeing which pairs are joined is what explains a mole
     * surfacing sixty blocks away from a network it reached in four hops.
     */
    private static List<Link> linkMounds(ClientLevel level, List<Mound> found) {
        List<Link> drawn = new ArrayList<>();
        int maxSqr = BurrowConstants.NETWORK_LINK_MAX * BurrowConstants.NETWORK_LINK_MAX;

        for (int i = 0; i < found.size(); i++) {
            BlockPos a = found.get(i).pos();
            for (int j = i + 1; j < found.size(); j++) {
                BlockPos b = found.get(j).pos();
                if (a.distSqr(b) <= maxSqr) {
                    drawn.add(new Link(BurrowRoute.between(level, a, b).waypoints()));
                }
            }
        }
        return drawn;
    }

    /**
     * One more breadcrumb per mole that is still on a trip, and nothing at all
     * for the ones that have finished - which is what clears the map again.
     */
    private static Map<Integer, List<Vec3>> extendTrails(ClientLevel level, Vec3 around, int radius) {
        AABB box = AABB.ofSize(around, radius * 2.0, radius * 2.0, radius * 2.0);
        Map<Integer, List<Vec3>> extended = new HashMap<>();

        for (Mole mole : level.getEntitiesOfClass(Mole.class, box)) {
            if (!mole.getBurrowState().isBusy()) {
                continue;
            }

            List<Vec3> trail = trails.get(mole.getId());
            if (trail == null) {
                trail = new ArrayList<>();
            }
            trail.add(mole.position());
            if (trail.size() > TRAIL_LIMIT) {
                trail.remove(0);
            }
            extended.put(mole.getId(), trail);
        }
        return extended;
    }

    // --- drawing --------------------------------------------------------------

    private static void draw() {
        for (Mound mound : mounds) {
            // Always on top: a mound behind a hill is exactly the one worth
            // knowing about when a mole vanishes towards it.
            Gizmos.cuboid(mound.pos(), GizmoStyle.stroke(mound.open() ? OPEN_COLOUR : CLOSED_COLOUR, MOUND_STROKE))
                    .setAlwaysOnTop();
        }

        for (Link link : links) {
            List<Vec3> path = link.path();
            for (int i = 1; i < path.size(); i++) {
                Gizmos.line(path.get(i - 1), path.get(i), LINK_COLOUR, LINK_WIDTH).setAlwaysOnTop();
            }
        }

        for (List<Vec3> trail : trails.values()) {
            for (int i = 1; i < trail.size(); i++) {
                Gizmos.line(trail.get(i - 1), trail.get(i), TRAIL_COLOUR, TRAIL_WIDTH).setAlwaysOnTop();
            }
        }
    }
}

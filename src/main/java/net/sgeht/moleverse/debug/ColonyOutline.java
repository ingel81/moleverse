package net.sgeht.moleverse.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.burrow.MoundNetwork;

/**
 * Draws the ground of every colony while it is switched on.
 *
 * <p>Particles rather than the network overlay. That one is client side and
 * finds mounds by scanning blocks; colonies live in server data, and putting a
 * box on the client would mean a payload, a codec and a cache to keep it fresh
 * as colonies come and go. {@code sendParticles} is server side, reaches every
 * player who can see the spot, and needs none of it.</p>
 *
 * <p>The cost is that particles have to be re-sent, so this is a repeating draw
 * rather than a state the client holds. {@link #INTERVAL} is what keeps that
 * affordable, and only the stretch of border near a player is drawn at all:
 * a colony is 128 blocks across and most of its edge is out of sight of anybody.</p>
 */
public final class ColonyOutline {

    /** Ticks between two draws. Short enough to look continuous, long enough not to matter. */
    private static final int INTERVAL = 10;

    /**
     * Blocks between two particles along a border. One, because anything sparser
     * reads as a scatter of dots rather than an edge - at four the border of a
     * 128 block box was barely visible at all.
     */
    private static final int MARK_SPACING = 1;

    /**
     * Magenta. Deliberately a colour nothing else in the world uses: the mounds
     * draw orange, the tunnels blue, the core white, and a border has to be
     * telling apart from all three at a glance.
     */
    private static final DustParticleOptions BORDER_COLOUR = new DustParticleOptions(0xFF00FF, 1.5F);

    /**
     * How far from a player a border mark is still drawn.
     *
     * <p>Also the guard against loading chunks: a mark asks the heightmap for
     * its height, which would generate terrain if the column were not there yet.
     * Nothing this close to a player is unloaded.</p>
     */
    private static final int DRAW_RANGE = 80;

    private static boolean enabled;
    private static int ticks;

    private ColonyOutline() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        ticks = 0;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Called from the level tick. Does nothing at all while switched off. */
    public static void tick(ServerLevel level) {
        if (!enabled || level.players().isEmpty()) {
            return;
        }
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;

        for (Colony colony : ColonyStore.get(level).all()) {
            for (ServerPlayer player : level.players()) {
                drawNear(level, colony, player.blockPosition());
            }
        }
    }

    /**
     * The part of one colony's border that lies within {@link #DRAW_RANGE} of
     * this position, plus the core when that is in reach.
     */
    private static void drawNear(ServerLevel level, Colony colony, BlockPos near) {
        // Snapped to the spacing so the marks stay in the same places as the
        // player walks, rather than crawling along the border with them.
        int fromX = Math.max(colony.minX(), snap(near.getX() - DRAW_RANGE));
        int toX = Math.min(colony.maxX(), near.getX() + DRAW_RANGE);
        int fromZ = Math.max(colony.minZ(), snap(near.getZ() - DRAW_RANGE));
        int toZ = Math.min(colony.maxZ(), near.getZ() + DRAW_RANGE);

        boolean nearMinZ = Math.abs(colony.minZ() - near.getZ()) <= DRAW_RANGE;
        boolean nearMaxZ = Math.abs(colony.maxZ() - near.getZ()) <= DRAW_RANGE;
        boolean nearMinX = Math.abs(colony.minX() - near.getX()) <= DRAW_RANGE;
        boolean nearMaxX = Math.abs(colony.maxX() - near.getX()) <= DRAW_RANGE;

        for (int x = fromX; x <= toX; x += MARK_SPACING) {
            if (nearMinZ) {
                mark(level, x, colony.minZ());
            }
            if (nearMaxZ) {
                mark(level, x, colony.maxZ());
            }
        }
        for (int z = fromZ; z <= toZ; z += MARK_SPACING) {
            if (nearMinX) {
                mark(level, colony.minX(), z);
            }
            if (nearMaxX) {
                mark(level, colony.maxX(), z);
            }
        }

        if (Math.abs(colony.core().getX() - near.getX()) <= DRAW_RANGE
                && Math.abs(colony.core().getZ() - near.getZ()) <= DRAW_RANGE) {
            BlockPos core = MoundNetwork.surfaceAt(level, colony.core().getX(), colony.core().getZ());
            level.sendParticles(ParticleTypes.END_ROD,
                    core.getX() + 0.5, core.getY() + 1.0, core.getZ() + 0.5, 3, 0.15, 0.4, 0.15, 0.0);
        }
    }

    private static int snap(int coordinate) {
        return Math.floorDiv(coordinate, MARK_SPACING) * MARK_SPACING;
    }

    private static void mark(ServerLevel level, int x, int z) {
        BlockPos surface = MoundNetwork.surfaceAt(level, x, z);
        level.sendParticles(BORDER_COLOUR,
                x + 0.5, surface.getY() + 0.3, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}

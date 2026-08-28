package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.registry.ModPoi;

/**
 * Which mounds are around, which of them are linked, and where another one could
 * go.
 *
 * <p>Everything here goes through {@link PoiManager}. The alternative - scanning
 * blocks - means a 16-radius search around <em>every</em> node of the chain, so
 * hundreds of thousands of {@code getBlockState} calls for a single decision, per
 * mole, repeated after every refusal. The mounds in the world are still the data;
 * the index only answers "where are they" quickly.</p>
 *
 * <p>The index is not force-loaded ({@code PoiManager.ensureLoadedAndValid}
 * pulls chunks in, and only the nether portal search in vanilla does that).
 * That does <em>not</em> mean the answers are limited to loaded chunks: the
 * lookup reads the point-of-interest file straight from disk when a section is
 * cold, so it happily reports mounds in terrain nobody is standing in. Exits are
 * therefore filtered by {@link #canTravelTo} - travelling to one of those would
 * stop dead at the first waypoint outside the ticking area.</p>
 */
public final class MoundNetwork {

    private static final Predicate<Holder<PoiType>> IS_MOUND = type -> type.is(ModPoi.MOLE_MOUND.getKey());

    private MoundNetwork() {
    }

    /**
     * The mounds around a point, nearest first, and whether that point has room
     * for another one.
     */
    public record Scan(List<BlockPos> mounds, boolean densityCapReached) {

        public @Nullable BlockPos nearest() {
            return this.mounds.isEmpty() ? null : this.mounds.get(0);
        }
    }

    /**
     * A chained neighbourhood of mounds, taken to be connected underground.
     *
     * @param mounds every member including the entry, in the order they were found
     * @param chainDepth how many links deep the search had to go
     * @param farthest distance from the entry to the most distant member
     */
    public record Members(List<BlockPos> mounds, int chainDepth, double farthest) {
    }

    public static Scan scan(ServerLevel level, BlockPos origin) {
        List<BlockPos> mounds = moundsWithin(level, origin, BurrowConstants.SEARCH_RADIUS);
        mounds.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(origin)));
        return new Scan(mounds, mounds.size() >= BurrowConstants.MAX_MOUNDS_IN_RADIUS);
    }

    /**
     * The density cap, asked at the site where a new mound would actually go
     * rather than where the mole happens to stand. That is the only place it can
     * do any work: reusing an existing mound adds nothing to the count.
     *
     * <p>Two moles can pass this in the same tick and overshoot the cap by one.
     * That is left alone - chasing exactness across entities costs more than the
     * fifth mound does.</p>
     */
    public static boolean hasRoomForMound(ServerLevel level, BlockPos site) {
        return level.getPoiManager().getCountInRange(
                IS_MOUND, site, BurrowConstants.SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                < BurrowConstants.MAX_MOUNDS_IN_RADIUS;
    }

    /**
     * Follows the chain out from the entry: every mound within
     * {@link BurrowConstants#NETWORK_LINK_MAX} of a known member joins, and the
     * search continues from there, out to {@link BurrowConstants#NETWORK_SCAN_MAX}
     * from the entry.
     */
    public static Members build(ServerLevel level, BlockPos entry) {
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();

        members.add(entry);
        seen.add(entry);
        frontier.add(entry);

        int scanLimitSqr = BurrowConstants.NETWORK_SCAN_MAX * BurrowConstants.NETWORK_SCAN_MAX;
        int depth = 0;
        double farthest = 0.0;

        // Breadth first, one full ring per round, so chainDepth means what it says.
        while (!frontier.isEmpty() && members.size() < BurrowConstants.NETWORK_MAX_MEMBERS) {
            int ring = frontier.size();
            depth++;
            boolean grew = false;

            for (int i = 0; i < ring && members.size() < BurrowConstants.NETWORK_MAX_MEMBERS; i++) {
                BlockPos node = frontier.poll();
                // Each node costs a point-of-interest query over 5x5 chunks, so
                // polling the rest of the ring once the network is full is the
                // most expensive way available of learning nothing.
                for (BlockPos neighbour : moundsWithin(level, node, BurrowConstants.NETWORK_LINK_MAX)) {
                    if (members.size() >= BurrowConstants.NETWORK_MAX_MEMBERS) {
                        break;
                    }
                    if (neighbour.distSqr(entry) > scanLimitSqr || !seen.add(neighbour)) {
                        continue;
                    }
                    members.add(neighbour);
                    frontier.add(neighbour);
                    farthest = Math.max(farthest, Math.sqrt(neighbour.distSqr(entry)));
                    grew = true;
                }
            }

            if (!grew) {
                // The last round only re-found what was already known; it was not
                // a real link and must not show up as one in the log.
                depth--;
                break;
            }
        }

        return new Members(members, depth, farthest);
    }

    /**
     * Picks the mound the mole comes back out of.
     *
     * <p>Two rules, both with a failure mode behind them. Never the entry and
     * never closer to it than {@link BurrowConstants#MIN_EXIT_DISTANCE}, because
     * uniform choice otherwise picks the mound he just dived into and he pops
     * straight back out. And when he is fleeing, weight the choice by distance
     * from the threat - a random member of the network is as likely to surface
     * him next to the wolf as away from it, which turns the signature escape into
     * a suicide.</p>
     *
     * @param threat where the danger is, or {@code null} when he is merely bored
     * @return an existing mound to surface at, or {@code null} when the network
     *         holds none that is far enough away
     */
    /**
     * Whether a mound is somewhere a mole could actually arrive.
     *
     * <p>The index answers from disk, so it names mounds in terrain that is not
     * being ticked. A trip to one of those ends at the first waypoint outside
     * the ticking area, and the mole surfaces there instead - halfway to
     * nowhere, with a mound to show for it.</p>
     */
    private static boolean canTravelTo(ServerLevel level, BlockPos mound) {
        return level.isPositionEntityTicking(mound);
    }

    public static @Nullable BlockPos chooseExit(ServerLevel level, RandomSource random, Members network,
            BlockPos entry, @Nullable Vec3 threat) {
        int minSqr = BurrowConstants.MIN_EXIT_DISTANCE * BurrowConstants.MIN_EXIT_DISTANCE;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos mound : network.mounds()) {
            if (!mound.equals(entry) && mound.distSqr(entry) >= minSqr && canTravelTo(level, mound)) {
                candidates.add(mound);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (threat == null) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        // Weight by squared distance from the threat: still random, but a mound
        // twice as far away is four times as likely. A flat "take the farthest"
        // would make every escape from the same spot identical.
        double total = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = Math.max(1.0, candidates.get(i).getCenter().distanceToSqr(threat));
            total += weights[i];
        }

        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0.0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * A surface point {@link BurrowConstants#NEW_TRAVEL_MIN} to
     * {@link BurrowConstants#NEW_TRAVEL_MAX} blocks away that could take a new
     * mound. Used when the entry has no network to travel through.
     *
     * @return the position the mound would occupy, or {@code null} after
     *         {@link BurrowConstants#FRESH_SITE_ATTEMPTS} unusable tries
     */
    public static @Nullable BlockPos findFreshSite(ServerLevel level, RandomSource random, BlockPos entry) {
        for (int attempt = 0; attempt < BurrowConstants.FRESH_SITE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Mth.nextInt(random, BurrowConstants.NEW_TRAVEL_MIN, BurrowConstants.NEW_TRAVEL_MAX);
            int x = entry.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = entry.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos site = surfaceAt(level, x, z);
            if (hasRoomForMound(level, site) && MoleMound.canPlaceAt(level, site)) {
                return site;
            }
        }
        return null;
    }

    /**
     * The block a mound would sit in at these coordinates: the first free spot
     * above the ground.
     *
     * <p>{@code MOTION_BLOCKING_NO_LEAVES} rather than {@code WORLD_SURFACE},
     * because short grass, ferns and flowers do not block motion and are exactly
     * what a mound replaces. Asking for the first non-air block would put the
     * mound on top of the grass instead of in it.</p>
     */
    public static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static List<BlockPos> moundsWithin(ServerLevel level, BlockPos centre, int radius) {
        return level.getPoiManager()
                .getInRange(IS_MOUND, centre, radius, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}

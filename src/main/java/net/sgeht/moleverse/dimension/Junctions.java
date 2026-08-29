package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * What happens where two runs of one level cross.
 *
 * <p>Without this they simply overlap. Two corridors carved at the same height
 * through the same block of earth clear each other's ground and leave a plus
 * shape of corridor - four ways out, all of them exactly as wide and as tall as
 * the tube you walked in through, and nothing at all to say that anything
 * happened here. That is the difference between a network and a maze: a maze is
 * a set of identical passages, and a network is passages plus the places they
 * meet. A player who cannot see that they have arrived somewhere cannot remember
 * having been there, and a burrow you cannot remember is one you navigate by
 * trial.</p>
 *
 * <h2>What a junction is</h2>
 *
 * <p>A widening with a domed ceiling and a patch of light in the crown of it.
 * Nothing else - no new block, no fitting, no state. The walls step back two
 * blocks, the ceiling lifts, and the light overhead is visible from down the
 * corridor before you get there, which is the whole job: from inside a run you
 * can see that a choice is coming.</p>
 *
 * <p><strong>The widening is round and it is cleared, not built.</strong> Same
 * disc as a corridor and a chamber, through {@link CorridorCarver#clear}, so the
 * one rule about what may be replaced has exactly one copy. That also makes a
 * junction idempotent for free: a second visit finds air and clears nothing.</p>
 *
 * <p><strong>Eleven blocks across is not a taste setting.</strong> It is one more
 * than the widest slice {@code TunnelDecorator} still treats as a corridor, so
 * the decoration pass stops at a junction's edge instead of walking into it and
 * dressing half a room as though it were a tunnel. A hanging root across the
 * middle of a crossroads would undo the widening on its own.</p>
 *
 * <h2>Why crossings are counted rather than waypoints</h2>
 *
 * <p>Both runs are straight lines in plan view - {@code BurrowRoute} moves the
 * waypoints only in the vertical - so two runs cross at one point or at none,
 * and that point comes out of a single determinant. A colony therefore gets one
 * junction per pair of runs that genuinely cross, not one per waypoint that
 * happens to land near another run, and thirty runs give a couple of dozen
 * crossings rather than a continuous cavern.</p>
 *
 * <p>The same arithmetic is in {@link LevelShafts#connect}, which cannot be
 * called for this: a shaft exists only where the two runs are at
 * <em>different</em> levels and there is a rise to climb, and its finder rejects
 * the same-level pair on its first line. A junction is the other half of the same
 * question. If the two are ever to agree on where a crossing is, the plan-view
 * intersection and the height lookup want lifting out of both of them rather than
 * copying a third time.</p>
 */
public final class Junctions {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * How far the widening reaches past the corridor that arrives in it.
     *
     * <p>Two blocks either side. Enough that the wall visibly steps back, little
     * enough that the junction is still something you cross in a stride or two -
     * a place to choose at, not a place to be in.</p>
     */
    private static final int WIDENING = 2;

    /**
     * Smallest a junction may be, as a radius. Eleven blocks across.
     *
     * <p>One more than {@code TunnelDecorator}'s widest corridor slice, for the
     * reason in the class javadoc. Every junction today lands exactly on this
     * floor; the term above it is what keeps a future wider run from arriving at
     * a junction narrower than the corridor it came out of.</p>
     */
    private static final int MIN_RADIUS = 5;

    /** The largest a junction can come out, which is what the clearances are measured with. */
    private static final int MAX_RADIUS = Math.max(CorridorProfile.WIDEST_RADIUS + WIDENING, MIN_RADIUS);

    /**
     * How much higher the ceiling goes than the corridors that meet under it.
     *
     * <p>Two, and it has to be at least two: at one block the lift is inside the
     * noise of a corridor that is following sloping ground, and nobody would read
     * it as deliberate.</p>
     */
    private static final int RAISE = 2;

    /**
     * How many of the topmost layers curve inwards.
     *
     * <p>The chamber's argument, at a smaller size: without it the extra height
     * is a lid over the whole widening and the junction reads as a box somebody
     * cut. Three layers is enough for the ceiling to visibly come down to meet
     * the corridors it lets out.</p>
     */
    private static final int DOME = 3;

    /**
     * Radius of the patch of light in the crown.
     *
     * <p>The same size as one of {@code TunnelDecorator}'s pools, but always
     * present and always dead centre over the crossing - which is what makes it a
     * landmark rather than another lit stretch. It replaces ceiling earth the way
     * the decorator's threads do, so it costs no headroom and reads as growth on
     * the dome rather than as a lamp somebody hung.</p>
     */
    private static final int CROWN_RADIUS = 2;

    /**
     * Shallowest crossing that still counts as one, as a sine.
     *
     * <p>{@link LevelShafts}'s figure and its reasoning: two runs meeting at a
     * glancing angle do not cross, they run alongside each other and their
     * corridors merge over a long stretch. Widening that merge would produce a
     * long cavern rather than a junction, and the intersection point of two nearly
     * parallel lines is arithmetic nobody should trust anyway.</p>
     */
    private static final double MIN_CROSS_SIN = 0.5;

    /**
     * How far apart the two walking surfaces may be and still be one place.
     *
     * <p>One block, and it is an idempotency figure rather than a comfort one.
     * The junction's topmost layer is what {@link #standsAlready} reads to decide
     * whether it has been cut before, and that answer is only worth anything while
     * no corridor can reach that layer by itself. A corridor is at most
     * {@code RAISE} short of the junction's height, so one block of floor step
     * still leaves the crown out of its reach and two does not. Two runs whose
     * floors are further apart than that get no junction - they cross on a slope
     * and the crossing is a ramp, which is a different problem.</p>
     */
    private static final int MAX_FLOOR_STEP = 1;

    /**
     * How far a junction has to stay clear of any chamber in the colony.
     *
     * <p>Far enough that the two can never touch: a chamber's own radius plus the
     * widest a junction comes out. A chamber already answers the question a
     * junction exists to ask - it is where several runs meet and it has galleries
     * to join them - so a widening cut into its wall would be a second, worse
     * answer laid over the first.</p>
     *
     * <p>Every mound of the colony is checked, not only the two runs that cross.
     * A crossing eight blocks from a third mound's chamber is at that chamber
     * whether or not either run ends there.</p>
     */
    private static final int CHAMBER_CLEARANCE = BurrowGeometry.CHAMBER_RADIUS + MAX_RADIUS;

    /**
     * How far apart two junctions have to be.
     *
     * <p>The shortest run a mole will ever dig, in burrow blocks - the same figure
     * {@link LevelShafts} spaces its shafts by, and here it does more work than it
     * does there. A run is between this and four times this long, so at this
     * spacing no single corridor can ever carry two junctions. That is what bounds
     * a colony: the number of junctions cannot exceed the number of corridors, so
     * a colony of thirty runs is a network with a couple of dozen decision points
     * in it and not one continuous room.</p>
     */
    private static final int MIN_SEPARATION = BurrowConstants.MIN_EXIT_DISTANCE * BurrowGeometry.SCALE;

    /**
     * New junctions one call will cut.
     *
     * <p>A bound on the work rather than on the colony, exactly as with the
     * shafts: what already stands is recognised and costs nothing, so a colony
     * with more crossings than this finishes over the next few visits. Lower than
     * the shafts' figure because a junction clears a room's worth of earth where a
     * shaft clears a well.</p>
     */
    private static final int MAX_NEW_PER_CALL = 6;

    /**
     * Clients yes, neighbours no. {@link CorridorCarver}'s reasoning, and the
     * threads in a crown must not ask the six blocks around them whether they
     * still like their shape.
     */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private Junctions() {
    }

    /**
     * Where a junction goes, in burrow space.
     *
     * @param x      centre of the widening, on both corridors' centre lines
     * @param z      the same
     * @param walkY  the lower of the two walking surfaces, and the junction's own
     *               floor. The block below it is never touched, exactly as in a
     *               corridor
     * @param step   how far the other corridor's surface sits above that, zero or
     *               one
     * @param radius how far the widening reaches from the centre
     * @param height layers of air above {@code walkY}
     */
    private record Crossing(int x, int z, int walkY, int step, int radius, int height) {

        /** The topmost layer the widening clears. No corridor can reach it - see {@code MAX_FLOOR_STEP}. */
        int crest() {
            return this.walkY + this.height - 1;
        }
    }

    /**
     * Widens every crossing of one colony that deserves it.
     *
     * <p>Hand it <em>all</em> the runs of the colony. Two runs that leave the same
     * mound meet at that mound and nowhere else, so the runs of a single mound
     * produce nothing here - and what they do produce would be rejected for
     * standing in a chamber.</p>
     *
     * <p>Nothing is dug where the two corridors are not already open. A junction
     * is a widening of something, and widening earth the carver has not reached
     * yet would leave a sealed room beside a corridor that has not arrived. The
     * world is what answers that, and an unloaded crossing answers "not yet" - the
     * next visit asks again, which is the bargain {@link CorridorCarver} and
     * {@link LevelShafts} both make.</p>
     *
     * @return how many junctions this call cut. Junctions that already stood are
     *         not counted and are not dug again
     */
    public static int cut(ServerLevel burrow, List<BurrowLink> colonyRuns) {
        List<BlockPos> chambers = chambersOf(colonyRuns);
        List<Crossing> candidates = new ArrayList<>();
        for (int i = 0; i < colonyRuns.size(); i++) {
            for (int j = i + 1; j < colonyRuns.size(); j++) {
                Crossing crossing = crossingOf(colonyRuns.get(i), colonyRuns.get(j), chambers);
                if (crossing != null) {
                    candidates.add(crossing);
                }
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }

        // Sorted by position rather than left in the order the runs happened to be
        // stored in, so which crossing wins a contested spot is a property of the
        // colony's shape and not of how the store was last written.
        candidates.sort(Comparator.<Crossing>comparingInt(Crossing::x)
                .thenComparingInt(Crossing::z)
                .thenComparingInt(Crossing::walkY));

        // Two passes, for the shafts' reason: what already stands is a fact and
        // claims its ground first. The other way round a fresh crossing could win
        // a spot beside a junction that already exists, and the colony would end
        // up with two widenings twenty blocks apart.
        List<Crossing> standing = new ArrayList<>();
        List<Crossing> fresh = new ArrayList<>();
        for (Crossing crossing : candidates) {
            if (!bothCorridorsOpen(burrow, crossing)) {
                continue;
            }
            (standsAlready(burrow, crossing) ? standing : fresh).add(crossing);
        }

        int cut = 0;
        for (Crossing crossing : fresh) {
            if (cut >= MAX_NEW_PER_CALL) {
                break;
            }
            if (crowded(crossing, standing)) {
                continue;
            }
            dig(burrow, crossing);
            standing.add(crossing);
            cut++;
            LOG.debug("junction at {} {} {}, {} across and {} tall", crossing.x(), crossing.walkY(), crossing.z(),
                    crossing.radius() * 2 + 1, crossing.height());
        }
        return cut;
    }

    // --- finding the crossings ------------------------------------------------

    /**
     * Where these two runs cross, or null where they do not cross usefully.
     *
     * <p>Plan view first, because that part is exact: both runs are straight lines
     * between their mounds, so one determinant says whether they meet at a decent
     * angle and where. Heights come afterwards, out of the stored depth profile at
     * the crossing's own parameter along each run - the same reconstruction the
     * carve used, so a junction can never be cut a block beside the corridors it
     * is supposed to widen.</p>
     *
     * <p>Runs of different levels are not a junction. They pass over one another
     * rather than meeting, and what belongs there is a way up: see
     * {@link LevelShafts}.</p>
     */
    private static @Nullable Crossing crossingOf(BurrowLink one, BurrowLink other, List<BlockPos> chambers) {
        if (one.level() != other.level()) {
            return null;
        }
        if (one.pointCount() < 2 || other.pointCount() < 2) {
            return null;
        }

        Vec3 from = one.a().getCenter();
        Vec3 to = one.b().getCenter();
        Vec3 otherFrom = other.a().getCenter();
        Vec3 otherTo = other.b().getCenter();

        double rx = to.x - from.x;
        double rz = to.z - from.z;
        double vx = otherTo.x - otherFrom.x;
        double vz = otherTo.z - otherFrom.z;

        // The determinant is |r| * |v| * sin(angle), so dividing it by the two
        // lengths gives the sine outright - one test that rejects the parallel
        // case and the glancing case together, and never divides by a zero it has
        // not already looked at.
        double lengths = Math.sqrt((rx * rx + rz * rz) * (vx * vx + vz * vz));
        double determinant = rx * vz - rz * vx;
        if (lengths <= 0.0 || Math.abs(determinant) < MIN_CROSS_SIN * lengths) {
            return null;
        }

        double gapX = otherFrom.x - from.x;
        double gapZ = otherFrom.z - from.z;
        double alongOne = (gapX * vz - gapZ * vx) / determinant;
        double alongOther = (gapX * rz - gapZ * rx) / determinant;
        if (alongOne < 0.0 || alongOne > 1.0 || alongOther < 0.0 || alongOther > 1.0) {
            return null;
        }

        Vec3 here = burrowAt(one, alongOne * (one.pointCount() - 1));
        Vec3 there = burrowAt(other, alongOther * (other.pointCount() - 1));

        int x = (int) Math.round((here.x + there.x) / 2.0);
        int z = (int) Math.round((here.z + there.z) / 2.0);
        int oneY = (int) Math.round(here.y);
        int otherY = (int) Math.round(there.y);
        int step = Math.abs(oneY - otherY);
        if (step > MAX_FLOOR_STEP) {
            return null;
        }
        if (nearAChamber(x, z, chambers)) {
            return null;
        }

        // Both runs are at the same level, so there is one profile to size the
        // widening against. The floor step is added to the height rather than
        // averaged away: the junction has to clear the upper corridor's ceiling as
        // well as the lower one's, or the dome opens into the side of a tunnel.
        CorridorProfile profile = CorridorProfile.of(one.level());
        int radius = Math.max(profile.radius() + WIDENING, MIN_RADIUS);
        int height = Math.min(BurrowGeometry.CHAMBER_HEIGHT, profile.height() + RAISE + step);
        return new Crossing(x, z, Math.min(oneY, otherY), step, radius, height);
    }

    /**
     * Where a run is at a fractional waypoint, in burrow space.
     *
     * <p>Interpolated between the two neighbouring waypoints rather than snapped
     * to the nearest one: waypoints are eight blocks apart down here, and rounding
     * to one of them would put the junction further from the corridor than the
     * corridor is wide. The two ends come from {@link CorridorCarver#burrowPoint}
     * so this reads the run at exactly the places the carve wrote it.</p>
     */
    private static Vec3 burrowAt(BurrowLink link, double index) {
        int last = link.pointCount() - 1;
        double clamped = Math.clamp(index, 0.0, (double) last);
        int lower = Math.min((int) clamped, last - 1);
        double fraction = clamped - lower;

        BlockPos from = CorridorCarver.burrowPoint(link, lower);
        BlockPos to = CorridorCarver.burrowPoint(link, lower + 1);
        return new Vec3(
                from.getX() + (to.getX() - from.getX()) * fraction,
                from.getY() + (to.getY() - from.getY()) * fraction,
                from.getZ() + (to.getZ() - from.getZ()) * fraction);
    }

    /**
     * Every place this colony has a chamber, in burrow space.
     *
     * <p>Both ends of every run, deduplicated - a mound with four runs is one
     * chamber, and asking about it four times per crossing is work for nothing.
     * Only x and z are ever read from these: a chamber is nine blocks tall and
     * sits at the depth of its own runs, so its height has no bearing on whether a
     * junction would land inside it.</p>
     */
    private static List<BlockPos> chambersOf(List<BurrowLink> colonyRuns) {
        Set<BlockPos> mounds = new HashSet<>();
        for (BurrowLink run : colonyRuns) {
            if (run.pointCount() < 1) {
                continue;
            }
            mounds.add(CorridorCarver.burrowPoint(run, 0));
            mounds.add(CorridorCarver.burrowPoint(run, run.pointCount() - 1));
        }
        return List.copyOf(mounds);
    }

    private static boolean nearAChamber(int x, int z, List<BlockPos> chambers) {
        for (BlockPos chamber : chambers) {
            int dx = x - chamber.getX();
            int dz = z - chamber.getZ();
            if (dx * dx + dz * dz < CHAMBER_CLEARANCE * CHAMBER_CLEARANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean crowded(Crossing crossing, List<Crossing> taken) {
        for (Crossing other : taken) {
            int dx = crossing.x() - other.x();
            int dz = crossing.z() - other.z();
            if (dx * dx + dz * dz < MIN_SEPARATION * MIN_SEPARATION) {
                return true;
            }
        }
        return false;
    }

    // --- asking the world -----------------------------------------------------

    /**
     * Whether both runs have really been carved here.
     *
     * <p>One block each, on the axis at each run's own walking surface, which is
     * corridor air in both directions and stays so after the junction is cut.
     * Unloaded reads false, the way {@link CorridorCarver#alreadyCarved} does: not
     * knowing and not being there are the same answer from here, and the cheaper
     * mistake is to try again next time.</p>
     */
    private static boolean bothCorridorsOpen(ServerLevel burrow, Crossing crossing) {
        return isAir(burrow, crossing.x(), crossing.walkY(), crossing.z())
                && isAir(burrow, crossing.x(), crossing.walkY() + crossing.step(), crossing.z());
    }

    /**
     * Whether this junction has already been cut.
     *
     * <p>One block: the crest, the topmost layer of the widening. That layer is
     * the point of {@code MAX_FLOOR_STEP} - a corridor arriving here reaches at
     * most one block short of it, so nothing but this class can have opened it,
     * and a junction that was cut on an earlier visit is recognised without
     * reading a hundred blocks to find out.</p>
     *
     * <p>Two things can still make it answer yes wrongly: a run of another level
     * passing directly overhead, or a third same-level run whose own floor sits a
     * block higher. Both cost a widening that is not cut and neither can cause one
     * to be cut in the wrong place, which is the right way round for a mistake
     * here to fall.</p>
     */
    private static boolean standsAlready(ServerLevel burrow, Crossing crossing) {
        return isAir(burrow, crossing.x(), crossing.crest(), crossing.z());
    }

    private static boolean isAir(ServerLevel burrow, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return burrow.isInsideBuildHeight(y) && burrow.isLoaded(pos) && burrow.getBlockState(pos).isAir();
    }

    // --- cutting one ----------------------------------------------------------

    /**
     * The widening, and then the light in it.
     *
     * <p>In that order, and only that way round: the crown grows on the block
     * above the crest, which is ceiling only once the crest itself has been
     * cleared.</p>
     */
    private static void dig(ServerLevel burrow, Crossing crossing) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int layer = 0; layer < crossing.height(); layer++) {
            int radius = radiusAt(layer, crossing.height(), crossing.radius());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!withinDisc(dx, dz, radius)) {
                        continue;
                    }
                    cursor.set(crossing.x() + dx, crossing.walkY() + layer, crossing.z() + dz);
                    CorridorCarver.clear(burrow, cursor);
                }
            }
        }

        lightCrown(burrow, crossing, cursor);
    }

    /**
     * Radius of the widening at layer {@code layer}, counted up from the floor.
     *
     * <p>A quarter ellipse over the top {@link #DOME} layers, the chamber's own
     * curve at a smaller size. The parameter stops short of 1 so the crown keeps a
     * usable radius rather than closing to a single column - and it has to stay
     * wider than the corridors that meet underneath, or the dome would pinch in
     * over a corridor's own ceiling and leave a lip across it.</p>
     */
    private static int radiusAt(int layer, int height, int radius) {
        int flat = height - DOME;
        if (layer < flat) {
            return radius;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (DOME + 1.0), 0.0, 1.0);
        return (int) Math.round(radius * Math.sqrt(1.0 - t * t));
    }

    /**
     * Grows the patch of light in the crown of the dome.
     *
     * <p>Ceiling earth becomes threads, so the light sits in the ceiling plane and
     * costs no headroom - {@code TunnelDecorator} lights a corridor the same way
     * and for the same reason. Deep earth only, which makes this idempotent
     * without a second thought: threads are not deep earth, so a second visit
     * finds its work done, and a block a player has put up there is left
     * alone.</p>
     */
    private static void lightCrown(ServerLevel burrow, Crossing crossing, BlockPos.MutableBlockPos cursor) {
        BlockState threads = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        int y = crossing.crest() + 1;

        for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
            for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
                if (!withinDisc(dx, dz, CROWN_RADIUS)) {
                    continue;
                }
                cursor.set(crossing.x() + dx, y, crossing.z() + dz);
                grow(burrow, cursor, threads);
            }
        }
    }

    /**
     * Turns one block of ceiling into something else.
     *
     * <p>The same rule the whole dimension is built on, stated for a placement
     * rather than for a clearing: deep earth and nothing else. A chunk that is not
     * loaded is left alone rather than read - an unguarded read here does not fail,
     * it generates.</p>
     */
    private static void grow(ServerLevel burrow, BlockPos pos, BlockState state) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return;
        }
        if (!burrow.getBlockState(pos).is(ModBlocks.DEEP_EARTH.get())) {
            return;
        }
        burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /** The same integer disc a corridor is cut with, for the same reason. */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }
}

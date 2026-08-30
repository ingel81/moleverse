package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The way from one level of a burrow to the one above it.
 *
 * <p>A colony digs at two depths - feeding runs two blocks under the turf, main
 * runs four - and {@link BurrowGeometry#VERTICAL_SCALE} turns those two blocks
 * into four down here. Without this class the burrow reads as one network and is
 * two: a player walking a feeding run drops into the main run wherever the pair
 * cross, and cannot get back up. Four blocks is a safe fall and an impossible
 * climb, which is the whole of the problem.</p>
 *
 * <h2>Where the runs actually cross</h2>
 *
 * <p>In plan view a run is a straight line. {@code BurrowRoute} samples the line
 * between the two mounds and only ever moves the waypoints in the vertical, and
 * {@link BurrowLink#pointAt} reconstructs x and z from the two ends - so the
 * question "do these two runs cross" is a segment intersection between two
 * straight lines, not a search over waypoint pairs. The waypoints are still what
 * answers <em>at what height</em>: the depth profile is the one thing the link
 * really stores, and the heights of the two runs at the crossing come out of it
 * through the same mapping {@link CorridorCarver} carves with, so the shaft can
 * never land a block beside the corridor it is supposed to open into.</p>
 *
 * <h2>What a shaft is</h2>
 *
 * <p>A well the width of a corridor with a helix of {@link ModBlocks#ROOT_BEAM}
 * steps in it, and a deck of the same root at the top that closes the hole the
 * crossing had torn in the upper corridor's floor.</p>
 *
 * <p><strong>Steps rather than a ladder</strong>, because root beam is a full
 * block and nothing in this mod makes it climbable: a column of it is a wall.
 * Steps need no tag, no new block and no state - one block of rise per step is
 * something a player crosses without thinking about it.</p>
 *
 * <p><strong>Placed rather than left standing.</strong> A chamber's galleries are
 * earth the carver never cut, which is the right answer there and the wrong one
 * here: a shaft sits inside two corridors, and {@link CorridorCarver#carve} runs
 * again on every visit and would eat any earth left inside a cross-section. Root
 * beam survives, because carving only ever replaces deep earth - the same rule
 * that lets {@code TunnelDecorator} hang a root across a run and keep it.</p>
 *
 * <p><strong>The deck is not decoration.</strong> Where the two corridors cross,
 * the lower one has already cleared the upper one's floor - the upper corridor is
 * cut by a five block pit before this class does anything. A stair that ended in
 * mid-air over that pit would connect nothing, so the deck goes in first and the
 * stairwell is the hole left in it. It is laid with the same fill-air rule as the
 * steps, so it shapes itself to the pit and stops at the point where the upper
 * corridor's own floor takes over.</p>
 *
 * <h2>Finding and building are two halves</h2>
 *
 * <p>{@link #crossingsOf} is arithmetic on the colony's links and touches no
 * level at all, so a shaft's place is known before any of the ground exists;
 * {@link #connect(ServerLevel, Crossing, BoundingBox)} builds one of them,
 * writing only inside whatever box the caller owns. The old whole-colony
 * {@link #connect(ServerLevel, List)} is the two of them in a loop.</p>
 *
 * <p>Spacing moved into the finder with the rest of the arithmetic, so which
 * crossing wins a contested spot no longer depends on which shafts happened to be
 * standing when somebody asked - see {@link Junctions}, which made the same trade
 * for the same reason and states it at length.</p>
 *
 * <h2>The height contract, kept here by the shape rather than by a constant</h2>
 *
 * <p>{@link Junctions} states it in full: a place that is not a corridor has to
 * be clear to at least {@code CORRIDOR_HEIGHT + 2} on its axis, because that is
 * how the decoration pass tells the two apart - it probes for a ceiling and a
 * corridor is capped one block below that reach. A shaft mouth keeps the same
 * promise, and keeps it without a constant of its own:</p>
 *
 * <p>The column at the middle of a well is open from the lower corridor's walking
 * surface, up the well, through the stairwell the deck leaves in itself, and on
 * into the upper corridor's own air. That is {@link #MIN_RISE} plus the upper
 * corridor's height at the very least, which is nine against a bound of eight -
 * and a shaft with less rise than that is not built at all. Standing at the
 * bottom of a shaft and looking up, the probe finds nothing within its reach,
 * which is the correct answer: there is no ceiling there, there is another
 * storey.</p>
 *
 * <p>What that turns on is the deck being {@link ModBlocks#ROOT_BEAM}, which the
 * decorator counts as corridor space rather than as a ceiling. If the deck ever
 * becomes something solid to a probe, this promise breaks and a shaft starts
 * reading as a corridor with a very low roof.</p>
 */
public final class LevelShafts {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * The well is exactly as wide as a corridor.
     *
     * <p>Same number, same disc, same feel underfoot - a shaft is a corridor
     * stood on its end and there is no reason for it to measure differently.</p>
     */
    private static final int WELL_RADIUS = (BurrowGeometry.CORRIDOR_WIDTH - 1) / 2;

    /**
     * How far along the upper run the deck reaches.
     *
     * <p>It has to outlast the pit. The pit is the lower corridor's own width
     * seen from the upper one, so it stretches {@code CORRIDOR_WIDTH / sin(angle)}
     * along it - at {@link #MIN_CROSS_SIN} just under ten blocks end to end. Six
     * either side covers that with room to spare, and costs nothing where the
     * floor is already solid: the deck only ever fills air.</p>
     */
    private static final int DECK_REACH = 6;

    /**
     * Shallowest crossing that still counts as one, as a sine.
     *
     * <p>Two runs meeting at a glancing angle are not a crossroads; they run
     * alongside one another, their corridors merge over a long stretch, and the
     * intersection point of two nearly parallel lines is arithmetic nobody should
     * trust. A half is thirty degrees, which is generous and still keeps the pit
     * inside {@link #DECK_REACH}.</p>
     */
    private static final double MIN_CROSS_SIN = 0.5;

    /** Blocks of air a player needs over a step. Two, because that is how tall they are. */
    private static final int HEADROOM = 2;

    /**
     * How far the topmost layer of the well is drawn in, so the shaft opens rather
     * than ends.
     *
     * <p>One block. A well cut to one radius from end to end meets its own lid in
     * a right angle, and that ring is the most machined thing in the dimension
     * because you are looking straight up at it while you climb. Only the very top
     * layer, and only by a block: the layer below it is where the top step's
     * headroom is, and the helix itself sits on the rim of the well, so anything
     * more would take a tread's column away and leave a staircase with a step
     * missing.</p>
     */
    private static final int MOUTH_TAPER = 1;

    /**
     * Smallest gap this joins.
     *
     * <p>It is a headroom figure, not a taste one. The deck lies one block under
     * the upper walking surface, so it leaves the lower corridor {@code rise - 1}
     * blocks of air beneath it - and at a rise of two that is one block, which is
     * a root ceiling laid across the lower run at knee height. A crossing that
     * tight is two corridors sharing two thirds of their height anyway, which is
     * nearer to one room than to two levels.</p>
     */
    private static final int MIN_RISE = HEADROOM + 1;

    /**
     * Largest gap this joins: the deepest run level against the shallowest, in
     * burrow blocks.
     *
     * <p>Anything more than that is not two levels of one burrow meeting, it is
     * two unrelated corridors that happen to be stacked, and driving a stair
     * between them would be inventing a connection the colony never dug.</p>
     */
    private static final int MAX_RISE =
            (BurrowConstants.DEPTH_CHAMBER - BurrowConstants.DEPTH_FEEDING) * BurrowGeometry.VERTICAL_SCALE;

    /**
     * How far a crossing has to stay clear of either run's mound.
     *
     * <p>Two runs that leave the same mound meet at that mound and nowhere else,
     * and a chamber already joins its runs with galleries - a shaft there would
     * be a second staircase cut through the first. The chamber's radius plus the
     * deck's reach is the distance at which the two cannot touch.</p>
     */
    private static final int MOUND_CLEARANCE = BurrowGeometry.CHAMBER_RADIUS + DECK_REACH;

    /**
     * How far apart two shafts have to be.
     *
     * <p>The shortest run a mole will ever dig, in burrow blocks. It is the
     * smallest distance that has any meaning down here: below it two shafts would
     * be closer together than the shortest corridor either of them opens into,
     * which is what turns a colony of thirty runs into a lift shaft every ten
     * blocks.</p>
     */
    private static final int MIN_SEPARATION = BurrowConstants.MIN_EXIT_DISTANCE * BurrowGeometry.SCALE;

    /**
     * New shafts one call will cut.
     *
     * <p>A bound on the work, not on the colony: shafts that already stand are
     * recognised and cost nothing, so a colony with more crossings than this
     * finishes over the next few visits, exactly the way a run whose far end was
     * not loaded finishes over the next few visits.</p>
     *
     * <p>It bounds {@link #connect(ServerLevel, List)} alone. A caller working a
     * chunk at a time is already spending a chunk's worth of effort and has no
     * whole colony to be surprised by.</p>
     */
    private static final int MAX_NEW_PER_CALL = 8;

    /**
     * The ring the helix climbs, in angular order.
     *
     * <p>Every offset is two blocks out, so the whole helix sits inside the well,
     * and every neighbour is one block or one diagonal away - which is what makes
     * the next step a stride rather than a leap. Twelve of them is one turn, and
     * that is more than {@link #MAX_RISE}, so a helix can never climb into its own
     * bottom step.</p>
     */
    private static final int[][] RING = {
            {2, 0}, {2, 1}, {1, 2}, {0, 2}, {-1, 2}, {-2, 1},
            {-2, 0}, {-2, -1}, {-1, -2}, {0, -2}, {1, -2}, {2, -1}
    };

    /**
     * Clients yes, neighbours no - the reasoning is
     * {@link CorridorCarver}'s and {@code TunnelDecorator}'s alike. A step must
     * not ask the six blocks around it whether they still like their shape.
     */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private LevelShafts() {
    }

    /**
     * Where a shaft goes, in burrow space.
     *
     * @param one      one of the two runs that pass over one another here. Kept so
     *                 that whoever holds a crossing can say which pair produced it
     *                 without finding it a second time - the plan layer names its
     *                 features after exactly that pair
     * @param other    the other run
     * @param x        centre of the well, on both corridors' centre lines
     * @param z        the same
     * @param low      walking surface of the lower run at the crossing
     * @param rise     blocks from that surface up to the upper run's
     * @param alongX   plan direction of the <em>upper</em> run, normalised. The
     *                 deck follows it, because the pit it closes is a hole in that
     *                 corridor's floor and nowhere else
     * @param alongZ   the same
     */
    public record Crossing(BurrowLink one, BurrowLink other, int x, int z, int low, int rise,
            double alongX, double alongZ) {

        public int top() {
            return this.low + this.rise;
        }

        /**
         * Every block building this shaft can reach.
         *
         * <p>The deck outreaches the well, so the horizontal is its business; the
         * vertical belongs to the well, which goes a player's height past the top
         * step. A single box for both is honest here because the two overlap
         * almost completely and the surplus is a corner of earth nobody writes
         * into.</p>
         *
         * <p>The well's own reach now includes the soil lining around it, in all
         * six directions - a chunk is only asked about a feature whose bounds
         * reach into it, and a box drawn to the cut alone would have the lining
         * stop at a chunk border with deep earth showing beside it.</p>
         */
        public BoundingBox bounds() {
            int reach = Math.max(WELL_RADIUS + CorridorCarver.SHELL_MAX, DECK_REACH);
            return new BoundingBox(
                    this.x - reach, this.low - CorridorCarver.SHELL_MAX, this.z - reach,
                    this.x + reach, this.top() + HEADROOM - 1 + CorridorCarver.SHELL_MAX, this.z + reach);
        }

        /**
         * Where the helix starts on the ring.
         *
         * <p>Derived from the position and nothing else, so the answer is the same
         * on every visit - which is what {@link LevelShafts#standsAlready} depends
         * on, and what keeps two neighbouring shafts from facing the same way.</p>
         */
        private int ringStart() {
            return Math.floorMod(this.x * 31 + this.z, RING.length);
        }

        /** Ring offset of step {@code k}, counted up from the lower corridor. */
        private int[] step(int k) {
            return RING[(ringStart() + k) % RING.length];
        }
    }

    /**
     * Where one colony's shafts belong, spacing already settled.
     *
     * <p>Pure arithmetic on the links - no level, no block reads, no world at all.
     * The answer therefore exists before any of the ground does, which is what
     * lets the plan layer hand a chunk the shafts that pass through it rather than
     * have the chunk work them out.</p>
     *
     * <p>Hand it <em>all</em> the runs of the colony. A crossing is between two
     * different runs, and the runs that meet at one mound meet only there - so the
     * runs of a single mound produce nothing.</p>
     *
     * <p>The list comes out sorted by position and thinned by
     * {@link #MIN_SEPARATION}, greedily and in that order: a crossing is kept
     * unless one already kept is too close. Sorting first is what makes the
     * thinning a property of the colony's shape rather than of the order the store
     * happened to be written in.</p>
     */
    public static List<Crossing> crossingsOf(List<BurrowLink> colonyRuns) {
        List<Crossing> candidates = new ArrayList<>();
        for (int i = 0; i < colonyRuns.size(); i++) {
            for (int j = i + 1; j < colonyRuns.size(); j++) {
                BurrowLink one = colonyRuns.get(i);
                BurrowLink other = colonyRuns.get(j);
                if (one.level() == other.level()) {
                    continue;
                }
                Crossing crossing = crossingOf(one, other);
                if (crossing != null) {
                    candidates.add(crossing);
                }
            }
        }

        candidates.sort(Comparator.<Crossing>comparingInt(Crossing::x)
                .thenComparingInt(Crossing::z)
                .thenComparingInt(Crossing::low));

        List<Crossing> spaced = new ArrayList<>();
        for (Crossing crossing : candidates) {
            if (!crowded(crossing, spaced)) {
                spaced.add(crossing);
            }
        }
        return List.copyOf(spaced);
    }

    /**
     * Builds one shaft, writing only inside {@code clamp}.
     *
     * <p>A null clamp is the unbounded case and builds the whole thing. Anything
     * else is a caller that owns a box - a chunk, in practice - and every write
     * outside it is dropped silently: the block belongs to a chunk that will ask
     * for its own part of this shaft when its turn comes. Building the same shaft
     * from several chunks costs reads and nothing else, because the well only
     * clears deep earth and the deck and the steps only fill air.</p>
     *
     * <p>Nothing is dug where the two corridors are not already open. A shaft is a
     * connection between two things, and driving one into ground the carver has not
     * reached yet would leave a stair in a sealed pocket. The world is what answers
     * that, and an unloaded crossing answers "not yet" - the next visit asks
     * again, which is the same bargain {@link CorridorCarver} makes.</p>
     *
     * <p><strong>It does not ask whether the shaft is already standing.</strong>
     * {@link #standsAlready} reads the helix, which a clamp is free to split
     * across two chunks - so a shaft raised from one chunk would tell the next
     * chunk its share was already done, and the steps on that side would never be
     * placed. Building again is cheap and idempotent; being told a lie about it is
     * not.</p>
     *
     * @return whether the crossing was open and was therefore built, as far as the
     *         clamp let it
     */
    public static boolean connect(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp) {
        if (!bothCorridorsOpen(burrow, crossing)) {
            return false;
        }

        dig(burrow, crossing, clamp);
        LOG.debug("shaft at {} {} {}, {} blocks of rise", crossing.x(), crossing.low(), crossing.z(),
                crossing.rise());
        return true;
    }

    /**
     * Cuts a way between the levels of one colony wherever two runs pass over one
     * another, unclamped.
     *
     * <p>The finder and the builder in a loop. {@link #standsAlready} is asked here
     * and only here: this path builds whole shafts, so the helix it reads is one
     * this call would otherwise raise itself, and recognising the ones that already
     * stand is what keeps {@link #MAX_NEW_PER_CALL} spending its budget on shafts
     * that do not yet exist.</p>
     *
     * @return how many shafts this call cut. Shafts that already stood are not
     *         counted and are not dug again
     */
    public static int connect(ServerLevel burrow, List<BurrowLink> colonyRuns) {
        int cut = 0;
        for (Crossing crossing : crossingsOf(colonyRuns)) {
            if (cut >= MAX_NEW_PER_CALL) {
                break;
            }
            if (standsAlready(burrow, crossing)) {
                continue;
            }
            if (connect(burrow, crossing, null)) {
                cut++;
            }
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
     * the crossing's own parameter along each run.</p>
     */
    private static @Nullable Crossing crossingOf(BurrowLink one, BurrowLink other) {
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
        int rise = Math.abs(oneY - otherY);
        if (rise < MIN_RISE || rise > MAX_RISE) {
            return null;
        }
        if (nearAMound(x, z, one) || nearAMound(x, z, other)) {
            return null;
        }

        // The deck belongs to whichever run is on top: it closes the hole in that
        // corridor's floor, and the hole runs the length of that corridor.
        boolean oneIsUpper = oneY > otherY;
        double upperX = oneIsUpper ? rx : vx;
        double upperZ = oneIsUpper ? rz : vz;
        double upperLength = Math.sqrt(upperX * upperX + upperZ * upperZ);

        return new Crossing(one, other, x, z, Math.min(oneY, otherY), rise,
                upperX / upperLength, upperZ / upperLength);
    }

    /**
     * Where a run is at a fractional waypoint, in burrow space.
     *
     * <p>Interpolated between the two neighbouring waypoints rather than snapped to
     * the nearest one: waypoints are two overworld blocks apart, which is eight
     * down here, and rounding to one of them would put the shaft further from the
     * corridor than the corridor is wide. The two ends come from
     * {@link CorridorCarver#burrowPoint} so that this reads the run at exactly the
     * places the carve wrote it - a second copy of that mapping would be a second
     * thing to keep in step.</p>
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

    /** Whether this spot is close enough to either end of the run to be chamber business. */
    private static boolean nearAMound(int x, int z, BurrowLink link) {
        return within(x, z, CorridorCarver.burrowPoint(link, 0))
                || within(x, z, CorridorCarver.burrowPoint(link, link.pointCount() - 1));
    }

    private static boolean within(int x, int z, BlockPos mound) {
        int dx = x - mound.getX();
        int dz = z - mound.getZ();
        return dx * dx + dz * dz < MOUND_CLEARANCE * MOUND_CLEARANCE;
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
     * corridor air in both directions and stays so after the shaft is built - the
     * helix is out on the ring and the deck is a layer below the upper surface.
     * Unloaded reads false, the way {@link CorridorCarver#alreadyCarved} does: not
     * knowing and not being there are the same answer from here, and the cheaper
     * mistake is to try again next time.</p>
     */
    private static boolean bothCorridorsOpen(ServerLevel burrow, Crossing crossing) {
        return isAir(burrow, crossing.x(), crossing.low(), crossing.z())
                && isAir(burrow, crossing.x(), crossing.top(), crossing.z());
    }

    /**
     * Whether a shaft is already standing here.
     *
     * <p>Every step is asked, not just one. A single root beam proves nothing -
     * {@code TunnelDecorator} stands roots in corridors too, and one of them can
     * land on one of these columns by chance. It cannot land on all of them: the
     * helix is a different column at every height.</p>
     *
     * <p>Only {@link #connect(ServerLevel, List)} may ask this, and the reason is
     * in that method's own javadoc: the helix cannot speak for a shaft that is
     * being raised a clamped piece at a time.</p>
     */
    private static boolean standsAlready(ServerLevel burrow, Crossing crossing) {
        for (int k = 0; k < crossing.rise(); k++) {
            int[] offset = crossing.step(k);
            BlockPos pos = new BlockPos(crossing.x() + offset[0], crossing.low() + k, crossing.z() + offset[1]);
            if (!burrow.isLoaded(pos) || !burrow.getBlockState(pos).is(ModBlocks.ROOT_BEAM.get())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAir(ServerLevel burrow, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return burrow.isInsideBuildHeight(y) && burrow.isLoaded(pos) && burrow.getBlockState(pos).isAir();
    }

    // --- cutting one --------------------------------------------------------

    /**
     * The well, the deck and the steps, in that order.
     *
     * <p>The well goes first, and that is the one ordering that matters: it opens
     * the stairwell columns and the headroom over the top step, and both of the
     * others only ever fill air. Between the deck and the steps the order is free
     * - the top step lies in the deck's own layer and is the same block either
     * way, which is cheaper than working out which of the two owns it.</p>
     */
    private static void dig(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();

        sinkWell(burrow, crossing, clamp, cursor);
        layDeck(burrow, crossing, beam, clamp, cursor);

        for (int k = 0; k < crossing.rise(); k++) {
            int[] offset = crossing.step(k);
            cursor.set(crossing.x() + offset[0], crossing.low() + k, crossing.z() + offset[1]);
            if (within(clamp, cursor)) {
                place(burrow, cursor, beam);
            }
        }
    }

    /**
     * Opens the well itself.
     *
     * <p>From the lower walking surface to one block above the upper one - the two
     * blocks of air a player standing on the top step occupies, out at the ring
     * where neither corridor's own cross-section reaches. Where the two
     * runs are only a corridor's height apart this clears almost nothing - both
     * corridors are already there. It earns its keep on the wider gaps, where the
     * middle of the shaft is solid earth.</p>
     *
     * <p>The floor of the lower corridor is not touched: the well starts at the
     * walking surface, one above it, exactly as a corridor does.</p>
     *
     * <p>Lined as it is sunk, through {@code CorridorCarver.discAndShell} - the
     * same pass a corridor and a junction use, so the wall of a shaft is the soil
     * every other wall down there is and the rule about what may be cleared has
     * one copy. On the long rises that matters most: the middle of a tall shaft is
     * the one place in this dimension where a player is surrounded by earth on all
     * four sides at once.</p>
     */
    private static void sinkWell(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp,
            BlockPos.MutableBlockPos cursor) {
        int lid = crossing.top() + HEADROOM - 1;

        for (int y = crossing.low() - CorridorCarver.SHELL_MAX;
                y <= lid + CorridorCarver.SHELL_MAX; y++) {
            int away = y < crossing.low() ? crossing.low() - y : Math.max(0, y - lid);
            // The mouth: the top layer of the well comes in, so the shaft opens
            // into the upper corridor on a curve instead of through a ring. The
            // soil above it wraps that same layer rather than the full bore, so
            // the skin closes over the mouth on the same curve.
            int nearest = Math.clamp(y, crossing.low(), lid);
            int core = nearest == lid ? WELL_RADIUS - MOUTH_TAPER : WELL_RADIUS;
            CorridorCarver.discAndShell(burrow, crossing.x(), y, crossing.z(), core, away, cursor, clamp);
        }
    }

    /**
     * Closes the hole the crossing tore in the upper corridor's floor, and leaves
     * the stairwell open in it.
     *
     * <p>The strip is the upper corridor's own width, along the upper corridor's
     * own direction - the pit is a hole in that floor and nothing else, and a deck
     * laid to a plain radius would also roof over the lower corridor for a dozen
     * blocks either side of the crossing.</p>
     *
     * <p>Air only. That is what makes the deck fit the pit without measuring it:
     * where the upper corridor still has its floor the block is earth and nothing
     * happens, and where the lower corridor cut that floor away the block is air
     * and gets a root across it. It is also what keeps this off anything a player
     * has built or the decorator has already put down.</p>
     */
    private static void layDeck(ServerLevel burrow, Crossing crossing, BlockState beam,
            @Nullable BoundingBox clamp, BlockPos.MutableBlockPos cursor) {
        int deckY = crossing.top() - 1;

        for (int dx = -DECK_REACH; dx <= DECK_REACH; dx++) {
            for (int dz = -DECK_REACH; dz <= DECK_REACH; dz++) {
                if (isStairwell(crossing, dx, dz)) {
                    continue;
                }
                double along = dx * crossing.alongX() + dz * crossing.alongZ();
                double across = dx * crossing.alongZ() - dz * crossing.alongX();
                if (Math.abs(along) > DECK_REACH || Math.abs(across) > WELL_RADIUS + 0.5) {
                    continue;
                }
                cursor.set(crossing.x() + dx, deckY, crossing.z() + dz);
                if (within(clamp, cursor)) {
                    place(burrow, cursor, beam);
                }
            }
        }
    }

    /**
     * Whether this column is the stairwell, and so has to stay open in the deck.
     *
     * <p>Every step but the top one. The top step lies in the deck's own layer and
     * is part of it - stand on it and you are standing on the deck. The ones below
     * need their column clear right up to the deck, or the climb ends with a
     * player's head in a root.</p>
     */
    private static boolean isStairwell(Crossing crossing, int dx, int dz) {
        for (int k = 0; k < crossing.rise() - 1; k++) {
            int[] offset = crossing.step(k);
            if (offset[0] == dx && offset[1] == dz) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts one block of root into open space.
     *
     * <p>Air and nothing else, so a second call finds its own work done, a player's
     * cellar wall survives and a step that somebody mined out grows back - which it
     * has to, because a staircase with a tread missing is worse than no staircase
     * at all.</p>
     */
    private static void place(ServerLevel burrow, BlockPos pos, BlockState state) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return;
        }
        if (!burrow.getBlockState(pos).isAir()) {
            return;
        }
        burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /**
     * Whether the caller's box lets us write here. Null is the unbounded case.
     *
     * <p>A copy rather than a shared helper: it is one line, {@link Junctions} is
     * the only other caller, and neither class has any other business with the
     * other. The clamp is the <em>normal</em> bound on a write now; the
     * loaded-chunk checks in {@link #place} and {@link CorridorCarver#clear} still
     * sit underneath it as the last line of defence.</p>
     */
    private static boolean within(@Nullable BoundingBox clamp, BlockPos pos) {
        return clamp == null || clamp.isInside(pos);
    }
}

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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * <h2>The height contract</h2>
 *
 * <p><strong>A junction is told apart from a corridor by how tall it is, not by
 * how wide.</strong> Width is exhausted as a discriminator and the arithmetic
 * says so outright: a backbone is seven wide, {@code CorridorProfile.RADIUS_SWING}
 * lets it swell to a radius of four, and nine across is therefore a legitimate
 * corridor slice. There is no width a junction can take that a corridor cannot
 * also reach, short of making junctions larger again - which is the thing the
 * first playtest asked us to undo.</p>
 *
 * <p>So this is the promise, and it is load bearing rather than descriptive:</p>
 *
 * <blockquote>Every column on the axis of a junction bell is cut clear to at
 * least {@link #MIN_CLEAR_HEIGHT} - {@link BurrowGeometry#CORRIDOR_HEIGHT} plus
 * two - above its walking surface, whatever its radius, whatever its level, and
 * whatever the dome and the roughness do to the rest of the bell.</blockquote>
 *
 * <p>A corridor cannot reach that. {@code CorridorProfile.MAX_LIT_HEIGHT} caps
 * every section at {@code CORRIDOR_HEIGHT + 1}, and it is capped there for the
 * same reason this is two: that is exactly how far {@code TunnelDecorator}'s
 * ceiling probe searches. So a ceiling probe that succeeds is standing in a
 * corridor and a ceiling probe that fails is standing in a junction, exactly, and
 * the decoration pass gates itself on that rather than on a span.</p>
 *
 * <p>Two things hold the promise up. {@link #MIN_CLEAR_HEIGHT} is applied as a
 * floor on the height in {@link #crossingOf}, after every other term, so no
 * retune of {@link #RAISE} or of a level's section can quietly drop below it. And
 * the axis is open at every layer by construction: {@link #radiusAt} never
 * returns a negative radius, and the integer disc of radius zero still contains
 * its own centre, so neither the dome nor the bite it takes out of the dome can
 * close the column the probe reads.</p>
 *
 * <p>It is a promise about the axis and not about the rim, and the difference is
 * worth knowing: out where the dome has come down, the last two rings of a bell
 * are inside the probe's reach and get a corridor's floor laid in them. That is
 * neither new nor wrong - the old eleven-wide junction lost the same two rings to
 * the span test, and the mouth of a junction is where the corridor ends
 * anyway.</p>
 *
 * <p>The widening used to be eleven blocks across on the strength of the same
 * argument made about the width instead, and it was too large: the first person
 * to walk a colony said so of the places where several runs meet. Nine across
 * with a rounder bell is the same statement about the same crossing, made with
 * about a third fewer blocks - and a hanging root across the middle of a
 * crossroads, which is what all of this exists to prevent, is still impossible
 * because a root needs a ceiling to hang from.</p>
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
 * <p>The same arithmetic is in {@link LevelShafts#crossingsOf}, which cannot be
 * called for this: a shaft exists only where the two runs are at
 * <em>different</em> levels and there is a rise to climb, and its finder rejects
 * the same-level pair on its first line. A junction is the other half of the same
 * question. If the two are ever to agree on where a crossing is, the plan-view
 * intersection and the height lookup want lifting out of both of them rather than
 * copying a third time.</p>
 *
 * <h2>Finding and cutting are two halves</h2>
 *
 * <p>{@link #crossingsOf} is arithmetic on the colony's links and touches no
 * level at all, so the plan layer can ask a colony where its junctions belong
 * before any of the ground exists; {@link #cut(ServerLevel, Crossing, BoundingBox)}
 * carves one of them, clamped to whatever box the caller is allowed to write in.
 * The old whole-colony {@link #cut(ServerLevel, List)} is now the two of them in
 * a loop and nothing else.</p>
 *
 * <p>The split cost the world-driven half of the spacing rule, and that is an
 * improvement rather than a sacrifice. Which crossing won a contested spot used
 * to depend on which junctions happened to be standing when somebody asked;
 * spacing is now resolved in the finder, from the colony's shape alone, so the
 * same colony gets the same junctions whoever asks and whether or not any ground
 * is loaded. What it buys back is the same bargain the corridors make: a colony
 * that grows a run can re-decide a contested spot, and the widening that lost it
 * stays where it was cut, as an abandoned one.</p>
 */
public final class Junctions {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * How far the widening reaches past the corridor that arrives in it.
     *
     * <p>One block either side. It used to be two, and the first person to walk a
     * colony said the crossings were far too large - a junction at eleven across
     * and nine tall is a chamber with corridors in it, and a burrow whose crossings
     * are rooms has no rooms. One block is still a wall that visibly steps back,
     * and what makes the place read as a junction is the ceiling lifting and the
     * light in the crown of it, not the floor being wide enough to lose somebody
     * on.</p>
     */
    private static final int WIDENING = 1;

    /**
     * Smallest a junction may be, as a radius. Nine blocks across.
     *
     * <p>Nine is also the widest a swollen backbone gets, and that is fine now:
     * nothing is asked to tell the two apart by width. See the class javadoc for
     * the height contract that does the telling, and for why width could not.</p>
     *
     * <p>Free, therefore, to be a size rather than a signal - and the size is what
     * the playtest asked for. Every junction today lands exactly on this floor;
     * the term above it is what keeps a future wider run from arriving at a
     * junction narrower than the corridor it came out of.</p>
     */
    private static final int MIN_RADIUS = 4;

    /**
     * The clear height a junction promises on its axis, whatever else it does.
     *
     * <p>{@link BurrowGeometry#CORRIDOR_HEIGHT} plus two, and both the base and
     * the two are somebody else's numbers rather than taste. The base is what
     * {@code TunnelDecorator}'s ceiling probe searches from the walking surface;
     * {@code CorridorProfile.MAX_LIT_HEIGHT} caps every corridor at one more than
     * it, because a corridor whose ceiling is out of that reach goes dark. Two
     * therefore clears the tallest corridor that can exist by exactly one block,
     * which is the least that makes a failed probe mean "junction" rather than
     * "unlucky corridor".</p>
     *
     * <p>Applied as a floor in {@link #crossingOf}, last, so that it survives
     * every other term. It does not bite today - the shortest section is six and
     * {@link #RAISE} is two - and that is the point of writing it down: it is a
     * guard against a retune, not a shape anything aims for. If it ever does bite,
     * it wins over the chamber height cap above it, because a junction that is a
     * block taller than intended is a cosmetic surprise and a junction the
     * decoration pass mistakes for a corridor is a root hanging across a
     * crossroads.</p>
     */
    private static final int MIN_CLEAR_HEIGHT = BurrowGeometry.CORRIDOR_HEIGHT + 2;

    /** The largest a junction can come out, which is what the clearances are measured with. */
    private static final int MAX_RADIUS = Math.max(CorridorProfile.WIDEST_RADIUS + WIDENING, MIN_RADIUS);

    /**
     * How much higher the ceiling goes than the corridors that meet under it.
     *
     * <p>Two, and it has to be at least two twice over. At one block the lift is
     * inside the noise of a corridor that is following sloping ground and nobody
     * would read it as deliberate; and at one block a junction over the shortest
     * section would land exactly on {@link #MIN_CLEAR_HEIGHT} rather than above
     * it, so the guard would be doing all the work and the shape none of it.</p>
     */
    private static final int RAISE = 2;

    /**
     * How many of the topmost layers curve inwards.
     *
     * <p>The chamber's argument, at a smaller size: without it the extra height
     * is a lid over the whole widening and the junction reads as a box somebody
     * cut. Four layers rather than three now that the widening is narrower - the
     * bell has to come down to meet the corridors over the same few blocks, so the
     * curve has to start lower, and the volume the change takes out is volume that
     * was reading as a hall.</p>
     */
    private static final int DOME = 4;

    /**
     * How coarse the roughness on a junction's bell is: one decision per this many
     * blocks in every direction.
     *
     * <p>{@code CorridorCarver}'s figure and its argument. A cell rather than a
     * block, because this is a shape and not a texture.</p>
     */
    private static final int BELL_ROUGH_CELL = 2;

    /**
     * Share of the bell left standing a block proud of the curve it was cut from.
     *
     * <p>Inwards only, and only on the layers the dome has already pulled back
     * from the wall - the chamber's rule, for the chamber's reason: the wall
     * itself is what the corridors arrive through and what the crown is measured
     * against, and roughening it would put the widening's edge somewhere nobody
     * predicted. On the bell it is what stops the ceiling reading as a turned rim
     * over a cylinder.</p>
     */
    private static final float BELL_ROUGH_CHANCE = 0.3F;

    /** Salt for that roughness, distinct from every other decision in the dimension. */
    private static final long SALT_BELL_ROUGH = 0x1CE0_0000L;

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
     *
     * <p>It bounds {@link #cut(ServerLevel, List)} alone. A caller working a chunk
     * at a time is already spending a chunk's worth of effort and has no whole
     * colony to be surprised by.</p>
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
     * @param one    one of the two runs that meet here. Kept so that whoever holds
     *               a crossing can say which pair of runs produced it without
     *               finding it a second time - the plan layer names its features
     *               after exactly that pair
     * @param other  the other run
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
    public record Crossing(BurrowLink one, BurrowLink other, int x, int z, int walkY, int step,
            int radius, int height) {

        /** The topmost layer the widening clears. No corridor can reach it - see {@code MAX_FLOOR_STEP}. */
        public int crest() {
            return this.walkY + this.height - 1;
        }

        /**
         * Every block cutting this junction can reach: the widening itself, the
         * one layer above its crest that the crown of threads grows into, and the
         * soil lining that wraps all of it.
         *
         * <p>{@code CorridorCarver.SHELL_MAX} in all six directions, the floor
         * included. A chunk is only asked about a feature whose bounds reach into
         * it, so a box drawn to the cut alone would leave the lining stopping at a
         * chunk border with deep earth showing on the other side.</p>
         */
        public BoundingBox bounds() {
            int reach = this.radius + CorridorCarver.SHELL_MAX;
            return new BoundingBox(
                    this.x - reach, this.walkY - CorridorCarver.SHELL_MAX, this.z - reach,
                    this.x + reach, this.crest() + 1 + CorridorCarver.SHELL_MAX, this.z + reach);
        }
    }

    /**
     * Where one colony's junctions belong, spacing already settled.
     *
     * <p>Pure arithmetic on the links - no level, no block reads, no world at all.
     * The answer therefore exists before any of the ground does, which is what
     * lets the plan layer hand a chunk the list of junctions that pass through it
     * rather than have the chunk work them out.</p>
     *
     * <p>Hand it <em>all</em> the runs of the colony. Two runs that leave the same
     * mound meet at that mound and nowhere else, so the runs of a single mound
     * produce nothing here - and what they do produce would be rejected for
     * standing in a chamber.</p>
     *
     * <p>The list comes out sorted by position and thinned by
     * {@link #MIN_SEPARATION}, greedily and in that order: a crossing is kept
     * unless one already kept is too close. Sorting first is what makes the
     * thinning a property of the colony's shape rather than of the order the store
     * happened to be written in.</p>
     */
    public static List<Crossing> crossingsOf(List<BurrowLink> colonyRuns) {
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

        candidates.sort(Comparator.<Crossing>comparingInt(Crossing::x)
                .thenComparingInt(Crossing::z)
                .thenComparingInt(Crossing::walkY));

        List<Crossing> spaced = new ArrayList<>();
        for (Crossing crossing : candidates) {
            if (!crowded(crossing, spaced)) {
                spaced.add(crossing);
            }
        }
        return List.copyOf(spaced);
    }

    /**
     * Widens one crossing, writing only inside {@code clamp}.
     *
     * <p>A null clamp is the unbounded case and cuts the whole junction. Anything
     * else is a caller that owns a box - a chunk, in practice - and every write
     * outside it is dropped silently: the block belongs to a chunk that will ask
     * for its own quarter of this junction when its turn comes. Cutting the same
     * junction from four chunks costs reads and nothing else, because clearing
     * only ever replaces deep earth.</p>
     *
     * <p>Nothing is dug where the two corridors are not already open. A junction
     * is a widening of something, and widening earth the carver has not reached
     * yet would leave a sealed room beside a corridor that has not arrived. The
     * world is what answers that, and an unloaded crossing answers "not yet" - the
     * next visit asks again, which is the bargain {@link CorridorCarver} and
     * {@link LevelShafts} both make.</p>
     *
     * <p><strong>It does not ask whether it has been cut before.</strong>
     * {@link #standsAlready} reads one block at the crest, and under a clamp that
     * block is as likely as not to sit in a neighbouring chunk - so a junction cut
     * from one chunk would tell the next chunk its quarter was already done, and
     * that quarter would stay earth for good. Re-cutting is cheap and idempotent;
     * being told a lie about it is not.</p>
     *
     * @return whether the crossing was open and was therefore cut, as far as the
     *         clamp let it
     */
    public static boolean cut(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp) {
        if (!bothCorridorsOpen(burrow, crossing)) {
            return false;
        }

        dig(burrow, crossing, clamp);
        LOG.debug("junction at {} {} {}, {} across and {} tall", crossing.x(), crossing.walkY(), crossing.z(),
                crossing.radius() * 2 + 1, crossing.height());
        return true;
    }

    /**
     * Widens every crossing of one colony that deserves it, unclamped.
     *
     * <p>The finder and the cutter in a loop. {@link #standsAlready} is asked here
     * and only here: this path carves whole junctions, so the crest it reads is
     * one this call would otherwise cut itself, and recognising the ones that
     * already stand is what keeps {@link #MAX_NEW_PER_CALL} spending its budget on
     * junctions that do not yet exist.</p>
     *
     * @return how many junctions this call cut. Junctions that already stood are
     *         not counted and are not dug again
     */
    public static int cut(ServerLevel burrow, List<BurrowLink> colonyRuns) {
        int cut = 0;
        for (Crossing crossing : crossingsOf(colonyRuns)) {
            if (cut >= MAX_NEW_PER_CALL) {
                break;
            }
            if (standsAlready(burrow, crossing)) {
                continue;
            }
            if (cut(burrow, crossing, null)) {
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

        // MIN_CLEAR_HEIGHT last, so it survives both the lift and the chamber cap.
        // This is where the class javadoc's contract is actually kept: everything
        // above it is a shape, and this is the line that has to be true for a
        // junction to still be recognisable as one from inside it.
        int height = Math.max(MIN_CLEAR_HEIGHT,
                Math.min(BurrowGeometry.CHAMBER_HEIGHT, profile.height() + RAISE + step));
        return new Crossing(one, other, x, z, Math.min(oneY, otherY), step, radius, height);
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
     *
     * <p>Only {@link #cut(ServerLevel, List)} may ask this, and the reason is in
     * that method's own javadoc: one block cannot speak for a junction that is
     * being cut a clamped quarter at a time.</p>
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
     * cleared. Under a clamp that stops being guaranteed - the crest may lie in
     * one chunk and the crown in the next - and it does not need to be: the crown
     * replaces deep earth, so a chunk that gets the crown before its crest simply
     * grows threads into the ceiling that is still there, which is what the crown
     * is anyway.</p>
     */
    private static void dig(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int height = crossing.height();

        // Clearing and lining in one sweep, through the carver's own pass: a
        // junction is a round volume like everything else down here, and the rule
        // about what may be cleared and what may be lined has exactly one copy.
        // The layers either side of the widening cut nothing and line only, so the
        // bell has soil over its crown and under its floor rather than a rim.
        for (int layer = -CorridorCarver.SHELL_MAX; layer < height + CorridorCarver.SHELL_MAX; layer++) {
            int away = layer < 0 ? -layer : Math.max(0, layer - (height - 1));
            // The curve is read at the nearest layer the bell actually has, not at
            // the layer being written: the soil over the crown has to wrap the
            // shape that was cut, and a bite sampled a block higher would be a
            // different shape.
            int nearest = Math.clamp(layer, 0, height - 1);
            int nearestY = crossing.walkY() + nearest;
            CorridorCarver.discAndShell(burrow, crossing.x(), crossing.walkY() + layer, crossing.z(),
                    radiusAt(nearest, height, crossing.radius(), crossing.x(), nearestY, crossing.z()),
                    away, cursor, clamp);
        }

        lightCrown(burrow, crossing, clamp, cursor);
    }

    /**
     * Radius of the widening at layer {@code layer}, counted up from the floor.
     *
     * <p>A quarter ellipse over the top {@link #DOME} layers, the chamber's own
     * curve at a smaller size. The parameter stops short of 1 so the crown keeps a
     * usable radius rather than closing to a single column - and it has to stay
     * wider than the corridors that meet underneath, or the dome would pinch in
     * over a corridor's own ceiling and leave a lip across it.</p>
     *
     * <p>On the domed layers the curve is bitten into, inwards only, so the bell
     * comes down in a profile that steps in and out rather than in a turned one.
     * The bite is decided per two blocks of height at the junction's own column,
     * so it varies up the bell and not around it - a whole layer moves in or it
     * does not. That is the right grain here: a nine block bell has no room for a
     * rough surface, and what reads as machined about a small dome is the
     * regularity of its profile rather than the smoothness of its face.</p>
     *
     * <p>The floor and the wall below the dome are left exactly round, and so is
     * the crest. All three are load bearing rather than tidy. The corridors arrive
     * through the wall. The crest carries the crown of threads, and
     * {@link #CROWN_RADIUS} is measured against the curve rather than against
     * whatever a bite left of it - take a block off the top layer and the outer
     * ring of the landmark light is grown inside the ceiling where nobody sees it.
     * And the crest is where the height contract has least to spare: a block off
     * it costs a block of clear height two columns out from the axis, which is
     * exactly the ground the decoration pass is being kept off.</p>
     *
     * <p><strong>The axis is never closed, and that is the class javadoc's
     * contract rather than a convenience.</strong> The return is floored at zero,
     * and the integer disc of radius zero still contains its own centre, so every
     * layer of the bell clears the column the crossing stands on. The clear height
     * on that column is therefore the height, exactly, whatever the dome does and
     * whatever the bite takes - which is what lets a failed ceiling probe mean
     * "junction" and nothing else.</p>
     */
    private static double radiusAt(int layer, int height, int radius, int x, int y, int z) {
        int flat = height - DOME;
        if (layer < flat) {
            return radius;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (DOME + 1.0), 0.0, 1.0);
        double curve = radius * Math.sqrt(1.0 - t * t);
        if (layer == height - 1) {
            return curve;
        }
        return Math.max(0.0, curve - bellBite(x, y, z));
    }

    /**
     * Whether the bell keeps this block, one block proud of the curve it was cut
     * from.
     *
     * <p>Hashed from where the block is in the world rather than from where it is
     * in the junction, the discipline the whole dimension works to: a lump is then
     * a property of the place and comes back identical however often, and in
     * whatever order, the crossing is cut.</p>
     */
    private static int bellBite(int x, int y, int z) {
        return CorridorCarver.noise(SALT_BELL_ROUGH,
                Math.floorDiv(x, BELL_ROUGH_CELL),
                Math.floorDiv(y, BELL_ROUGH_CELL),
                Math.floorDiv(z, BELL_ROUGH_CELL)) < BELL_ROUGH_CHANCE ? 1 : 0;
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
    private static void lightCrown(ServerLevel burrow, Crossing crossing, @Nullable BoundingBox clamp,
            BlockPos.MutableBlockPos cursor) {
        BlockState threads = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        int y = crossing.crest() + 1;

        for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
            for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
                if (!withinDisc(dx, dz, CROWN_RADIUS)) {
                    continue;
                }
                cursor.set(crossing.x() + dx, y, crossing.z() + dz);
                if (within(clamp, cursor)) {
                    grow(burrow, cursor, threads);
                }
            }
        }
    }

    /**
     * Turns one block of ceiling into something else.
     *
     * <p>The same rule the whole dimension is built on, stated for a placement
     * rather than for a clearing: raw ground and nothing else. Raw ground is now
     * two blocks rather than one - the bell is lined with loose soil the moment it
     * is cut, so by the time the crown is grown the ceiling over a crossing is
     * soil and never deep earth. Anything else up there is a decoration that made
     * its choice or a block somebody put down, and neither wants overwriting.</p>
     *
     * <p>A chunk that is not loaded is left alone rather than read - an unguarded
     * read here does not fail, it generates.</p>
     */
    private static void grow(ServerLevel burrow, BlockPos pos, BlockState state) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!existing.is(ModBlocks.DEEP_EARTH.get()) && !existing.is(ModBlocks.LOOSE_SOIL.get())) {
            return;
        }
        burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /** The same integer disc a corridor is cut with, for the same reason. */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    /**
     * Whether the caller's box lets us write here. Null is the unbounded case.
     *
     * <p>A copy rather than a shared helper, for {@link #withinDisc}'s reason: it
     * is one line, {@link LevelShafts} is the only other caller, and neither class
     * has any other business with the other. The clamp is the <em>normal</em>
     * bound on a write now; {@link CorridorCarver#clear}'s own loaded-chunk check
     * still sits underneath it as the last line of defence.</p>
     */
    private static boolean within(@Nullable BoundingBox clamp, BlockPos pos) {
        return clamp == null || clamp.isInside(pos);
    }
}

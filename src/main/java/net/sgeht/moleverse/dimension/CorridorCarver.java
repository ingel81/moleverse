package net.sgeht.moleverse.dimension;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Turns a run a mole dug in the overworld into a corridor you can walk down in
 * the burrow.
 *
 * <p>The burrow is solid {@link ModBlocks#DEEP_EARTH} until something clears it,
 * so carving is the whole of world generation down there: the shape of the place
 * is the history of the colony above it, not a noise function. A
 * {@link BurrowLink} is the unit, because a link is what gets walked again and
 * again - see the reasoning in that record.</p>
 *
 * <p><strong>Interpolation, not stamping.</strong> A link stores a waypoint every
 * {@code WAYPOINT_SPACING} overworld blocks, which
 * {@link BurrowGeometry#SCALE} turns into eight blocks down here. Carving one box
 * per waypoint would give a string of disconnected rooms, so every segment is
 * walked in single-block steps and a cross-section is cleared at each.</p>
 *
 * <p><strong>The cross-section is a disc, not a square.</strong> A square stamp
 * swept along a diagonal comes out half again as wide as the same stamp swept
 * along an axis; a disc is the same width in every direction, which is what a
 * corridor's width is supposed to mean. It also matches the chambers, and a
 * round tunnel reads as dug rather than as mined.</p>
 *
 * <p><strong>And that disc breathes.</strong> One disc of one size swept along a
 * straight line is a pipe, however round it is, and a burrow of pipes is
 * plumbing. So the radius and the height swing a block either way along the run,
 * the centre line wanders a block off the straight, and the topmost layer is
 * pulled in so the roof is an arch rather than a lid. All of it is
 * {@linkplain #undulation low frequency} - a swell over about nine blocks, not
 * a value per block, because per-block noise is gravel rather than digging.</p>
 *
 * <p>Every one of those swings is a pure function of the link's seed and how far
 * along the run the slice is. Not of the {@code RandomSource}, not of the order
 * the slices are carved in, not of which chunks happen to be loaded: the same
 * run carved in four clamped quarters has to come out as the one run it would
 * have been, and a modulation that drew from a stream would come out as four
 * mismatched stubs instead.</p>
 *
 * <p>Two things survive the modulation untouched. The centre line is cleared to
 * {@link CorridorProfile#LOWEST_SECTION} whatever the dice say, so a run is
 * walkable end to end by construction rather than on average; and the swings
 * fade to nothing over the last {@link #MOUTH_TAPER} blocks at each end, so a
 * mouth is always cut to its section - the chamber places its galleries against
 * that width and a doorway a block off would have ledge standing in it.</p>
 *
 * <p><strong>The clamp is how a chunk carves its own share.</strong> Every entry
 * point takes a nullable {@link BoundingBox}: a write outside it is skipped and
 * nothing else changes. That is what lets the same feature be carved once per
 * chunk it crosses, each pass complete in itself, and come out identical to one
 * unbounded carve - which is only true because nothing here reads the world to
 * decide what to cut. Reads are deliberately <em>not</em> clamped; only writes
 * are. Null means unbounded, and the {@code isLoaded} check in {@link #clear}
 * stays underneath as a last-line guard.</p>
 *
 * <h2>Cutting and dressing are two passes</h2>
 *
 * <p>{@link #carve} cuts and nothing else; {@link #decorateRun} dresses and
 * cuts nothing. The split exists because the two have opposite relationships
 * with the clamp. Carving decides everything from arithmetic, so a chunk can cut
 * its own share in isolation and be right. Decoration <em>measures</em>: it
 * probes for the walls, the floor and the ceiling, and a corridor that is only
 * carved as far as a chunk border measures as a corridor that ends there. Dress
 * it then and the pools of light are placed against a centre line that is about
 * to move - and because nothing here ever removes a block, the later, correct
 * pass cannot take the misplaced one back.</p>
 *
 * <p>So the caller gets to choose the moment. A reconciler that carves a chunk
 * on load can hold the dressing back until the whole neighbourhood is cut, and
 * dress a corridor that is already finished in both directions. The unclamped
 * {@link #carve(ServerLevel, BurrowLink)} keeps doing both in order, which is
 * the same choice made the only way it can be made when the whole run is carved
 * in one call.</p>
 *
 * <p>Both passes walk the run through {@link #walkSegments}, which is the only
 * statement of where a link's segments are. Two copies of that loop would be two
 * things to keep in step, and a decoration pass that drifted by one waypoint
 * would dress a corridor that is nearly, but not quite, the one that was
 * cut.</p>
 *
 * <p><strong>How wide that disc is depends on the run.</strong> A colony's
 * backbone is not the same thing as a feeding run and should not come out the
 * same size; {@link CorridorProfile} holds the section per {@code RunLevel} and
 * the reasoning behind each number. Everything else here is written against the
 * profile rather than against a constant, so the shape of a run follows from
 * what the colony dug it as.</p>
 *
 * <p><strong>Nothing but deep earth and air is ever replaced.</strong> Corridors
 * get furniture - roots, mycelium, whatever a later feature puts there - and a
 * second mole travelling the same run must not eat it. That also makes carving
 * idempotent, so {@link #alreadyCarved} is an optimisation rather than a
 * correctness requirement.</p>
 *
 * <h2>The soil shell</h2>
 *
 * <p>Every air volume this class opens is wrapped in a lining of
 * {@link ModBlocks#LOOSE_SOIL} - walls, ceiling and floor alike, two blocks
 * thick with a third wherever the ground says so. The first person down there
 * put it plainly: a burrow should be dug through earth, and deep earth is
 * unbreakable, so a corridor whose walls are deep earth is a corridor cut into
 * bedrock. Loose soil is the block a mole digs in up top, it can be mined, and
 * it is what makes the burrow read as soil rather than as tunnel-shaped stone.
 * Deep earth is still there - it starts two or three blocks out, which is far
 * enough that you meet it by widening a passage rather than by looking at
 * one.</p>
 *
 * <p>The rule that keeps it safe is narrow on purpose: {@link #line} turns
 * <em>deep earth</em> into lining and touches nothing else. Not air, not a
 * decoration, not water, not a block a player laid. So the lining pass can run
 * as often as it likes, from as many chunks as it likes, in any order, and reach
 * the same conclusion - which is the same bargain the clearing makes.</p>
 *
 * <p><strong>A small share of that lining is worth digging for.</strong> One
 * block in a few hundred comes out as {@link ModBlocks#ROOT_NODULE} instead of
 * soil - see {@link #liningAt} and {@link #POCKET_CHANCE}. It is the only reason
 * to put a spade into a wall that is not in the way, and it is what makes the
 * shell gameplay rather than scenery: the pockets are inside it, most of them
 * behind the surface, and the deep earth two or three blocks out ends the dig
 * before it can become mining.</p>
 *
 * <p>The other half of the bargain is that {@link #clear} now takes soil as well
 * as earth. It has to, for two reasons. A run cut later across a corridor that
 * has already been lined must go through the lining rather than stop at it; and
 * within a single carve the lining of one slice can land where the next slice
 * wants air, so the two orders - line then clear, clear then line - have to end
 * in the same place. They do: clearing takes soil away, and lining refuses to
 * put it into air.</p>
 *
 * <p>Cost is one sweep, not two. A cross-section walks the square that covers
 * the widest it could line, and every position it visits is either inside the
 * cut and cleared or outside it and lined - see {@link #discAndShell}, which is
 * the one statement of that and is what the junctions and the shafts line
 * themselves with.</p>
 *
 * <p><strong>A ledge is earth that was never carved.</strong> The galleries and
 * staircases in a chamber are not built; they are what is left when the room is
 * cut around them. That is the only form a fitting can take here that survives
 * the rule above - anything written into the air would be a block a later carve
 * is forbidden to touch, and so would hang in the middle of the next, deeper
 * chamber.</p>
 */
public final class CorridorCarver {

    // --- The soil shell -------------------------------------------------------

    /**
     * How deep the lining of loose soil goes before the ground's own variation is
     * added.
     *
     * <p>Two, and the number is doing one job: it is the distance a player has to
     * dig before they meet deep earth. One block would show the bedrock the moment
     * anybody widened a corner or knocked a boulder out of a wall, which is what
     * the burrow looked like on its first playtest. Three everywhere would be a
     * lot of blocks for a difference nobody can see, because the third one is only
     * ever met by somebody who is already digging.</p>
     */
    private static final int SHELL_THICKNESS = 2;

    /**
     * The thickest the lining can come out, once the variation has had its say.
     *
     * <p>Package visible because it is a reach rather than a taste: {@link
     * Junctions} and {@link LevelShafts} line their own volumes with the same
     * pass, and the bounding boxes their crossings publish have to cover it or a
     * chunk at the rim is never asked and the lining stops at a chunk border.</p>
     */
    static final int SHELL_MAX = SHELL_THICKNESS + 1;

    /**
     * Edge of one cell of ground that is lined to the same depth.
     *
     * <p>Cells and not blocks, for {@link #DOME_ROUGH_CELL}'s reason: a depth
     * rolled per block gives a boundary that frays one block at a time, which
     * nobody can see and which costs a hash per position anyway. Three is about
     * the size of a spadeful, so the extra block of soil arrives in patches you
     * could dig out in one go.</p>
     */
    private static final int SHELL_CELL = 3;

    /**
     * Share of those cells that are lined a block deeper.
     *
     * <p>Just under a half, so neither depth reads as the exception. The whole
     * point of the variation is that the boundary between soil and deep earth is
     * not a surface anybody can predict - a player who has dug two blocks and
     * found earth once should not conclude that two is the number.</p>
     */
    private static final float SHELL_DEEPER_CHANCE = 0.45F;

    /**
     * Share of the lining that comes out as {@link ModBlocks#ROOT_NODULE} rather
     * than as soil.
     *
     * <p>Rolled per block, unlike {@link #SHELL_CELL}'s depth: a nodule is one
     * find and not a patch, and a cell of them would read as an ore seam in a mod
     * that has no ore.</p>
     *
     * <p>Six in a thousand, which is a rarer number than it sounds because the
     * lining is enormous. A feeding run shows something like twenty blocks of wall,
     * floor and ceiling per block of corridor, so this puts a nodule where a player
     * can see one about every eight blocks of run - often enough to be a thing that
     * happens, rare enough that most stretches of wall have none. The roll runs
     * through the whole two or three blocks of lining rather than over its surface,
     * so about two thirds of them are behind what shows: a wall with one nodule in
     * it has more, which is the entire argument for digging into one rather than
     * walking past it.</p>
     */
    private static final float POCKET_CHANCE = 0.006F;

    /**
     * How many of a chamber's topmost layers curve inwards.
     *
     * <p>Without it a chamber is a drilled silo: full radius right up to a flat
     * lid. The dome costs three lines and is the difference between a room and a
     * shaft.</p>
     */
    private static final int CHAMBER_DOME = 4;

    /**
     * How far the bottom layer of a chamber is pulled in, so the wall meets the
     * floor on a curve instead of at a right angle.
     *
     * <p>One block, which at a radius of six is the whole of what is available:
     * the room cannot get any wider - {@code ChamberFurnisher} cuts its larders at
     * a fixed distance from the middle and measures that distance against
     * {@link BurrowGeometry#CHAMBER_RADIUS} - so a fillet here can only be earth
     * left standing at the foot of the wall. It reads as the floor curving up into
     * the wall, which is what a hole in the ground does and what a silo does
     * not.</p>
     *
     * <p>It leaves a sill across the mouth of a larder, and the larder cuts
     * through it: see {@code ChamberFurnisher.cutLarder}. That is the one thing
     * this constant costs, and it is worth knowing before it is raised to two.</p>
     */
    private static final int CHAMBER_FILLET = 1;

    /**
     * Innermost radius the ledge may occupy. Everything closer to the axis stays
     * clear.
     *
     * <p>The way home is on that axis: the transit post stands on the chamber's
     * centre block and the deepest run leaves through the same column. Measured
     * against {@link CorridorProfile#WIDEST_RADIUS} rather than against one
     * level's width, so the claim holds for every run that can arrive here - no
     * ledge block ever lies inside a mouth, and there is therefore no step left
     * standing in a doorway. One block of clearance, which is all that claim
     * needs and all a chamber of this radius can afford.</p>
     */
    private static final int LEDGE_INNER_RADIUS = CorridorProfile.WIDEST_RADIUS + 1;

    /**
     * Blocks of air a gallery needs above its floor.
     *
     * <p>Two, because that is how tall a player is. It is not a taste setting: a
     * gallery with one block of headroom is a crawlspace you cannot walk along,
     * which defeats the entire point of cutting it.</p>
     */
    private static final int GALLERY_HEADROOM = 2;

    /**
     * Where the ramps start. Inside this the ledge is gallery and nothing else.
     *
     * <p>The lane this reserves is what makes a gallery a complete circle. A ramp
     * climbing away from a gallery has to stand on the layers above it, and a ramp
     * wide enough to cover the whole ledge would bury the ring it just left - which
     * is exactly what the first attempt did, leaving the middle gallery walkable on
     * sixteen bearings out of seventy-two. Ramps are kept outboard so the ring
     * behind them always survives.</p>
     */
    private static final int RAMP_INNER_RADIUS = LEDGE_INNER_RADIUS + 1;

    /**
     * How many ramps join one gallery to the next.
     *
     * <p>Two, opposite each other. A run leaving the chamber carves through
     * whatever stands in its way, so a single ramp can be cut in half by a
     * corridor that happens to point at it; two cannot both be lost to one run.
     * More than two is worse rather than better - every ramp eats a slice of the
     * gallery it climbs from, and at four the rings are more hole than ring.</p>
     */
    private static final int RAMPS_PER_GAP = 2;

    /**
     * Blocks of arc per step, measured at {@link #RAMP_MID_RADIUS}.
     *
     * <p>Just under one, so that on a block grid every step of the ramp gets a
     * column of its own and none is skipped into a two block riser nobody can
     * climb.</p>
     */
    private static final double RAMP_STEP_ARC = 0.8;

    /** The radius a ramp's arc is measured at - the middle of the ledge. */
    private static final double RAMP_MID_RADIUS = (LEDGE_INNER_RADIUS + BurrowGeometry.CHAMBER_RADIUS) / 2.0;

    private static final double TAU = Math.PI * 2.0;

    // --- The shape of a run ---------------------------------------------------

    /**
     * Blocks of run between two nodes of the shape noise.
     *
     * <p>What makes the modulation read as digging rather than as damage. A value
     * per block is speckle - the walls would come out crumbly at arm's length and
     * straight from twenty blocks away, which is backwards. Twelve is about a
     * block for every step of a swell that is a whole block deep, so the wall
     * moves out over half a dozen paces rather than over two; at nine it was a
     * bulge you could see the ends of from inside it, which is the thing that
     * reads as damage rather than as ground.</p>
     */
    private static final int SHAPE_WAVELENGTH = 12;

    /**
     * How many of the topmost layers of a corridor are pulled in.
     *
     * <p>The single cheapest line in this file for the money. A tube of constant
     * radius meets its ceiling in a right angle all the way along, and that corner
     * is what reads as machined; pulling the last layers in turns the same
     * cross-section into an arch.</p>
     *
     * <p>Two layers rather than one, which is only affordable because the inset is
     * a curve rather than a block per layer. A block per layer on a five wide run
     * gives a three wide roof over a three wide apex - a flat topped slot, which
     * is what the first version of this refused to do and was right to. The
     * quarter ellipse takes the corners off the second layer and the sides off the
     * first, so a feeding run comes out five, five with the corners gone, three,
     * and a backbone seven, seven, five. That is a spring, not a step.</p>
     */
    private static final int CORRIDOR_ARCH = 2;

    /**
     * How far up the quarter ellipse the crown of the arch sits.
     *
     * <p>Short of one, exactly as in {@link #chamberDomeAt}: at the pole the
     * radius goes to nothing and the apex closes to a single column, which is a
     * gothic vault and not a hole an animal made. Four fifths leaves the top layer
     * about half the width of the section, which is a roof you can see across.</p>
     */
    private static final double ARCH_CROWN = 0.8;

    /**
     * The width at which a corridor starts rounding its bottom corners, and the
     * block it gives up doing so.
     *
     * <p>A five wide run has nothing to spare - take a block off each side of its
     * floor and the walking surface is three, which is the width the centre line
     * is promised and nothing more. A backbone at seven does: its floor comes out
     * five and the wall stands a block outside it, so the wall meets the floor on
     * a curve instead of at a right angle. The fillet fades in with the width
     * rather than switching on at a threshold, because a run that swells past the
     * threshold and back would otherwise cut a notch in its own floor at the two
     * places it crossed.</p>
     */
    private static final double FOOT_ROUND_FROM = 2.5;

    private static final double FOOT_ROUND_DEPTH = 1.0;

    /**
     * Blocks at each end of a run over which the swings fade in from nothing.
     *
     * <p>Both ends of a run are mouths in a chamber wall, and a mouth has to be
     * exactly the section its level says. {@link CorridorProfile#WIDEST_RADIUS}
     * is what {@link #LEDGE_INNER_RADIUS} is measured against, so a doorway cut a
     * block wider - or shifted a block sideways - would leave gallery standing
     * inside it, which is the one thing the ledge rules exist to prevent.</p>
     *
     * <p>Reaching past {@link BurrowGeometry#CHAMBER_RADIUS} means the swings are
     * already at nothing before the run enters the room; the blocks beyond that
     * are what make the fade a fade rather than a step.</p>
     */
    private static final int MOUTH_TAPER = BurrowGeometry.CHAMBER_RADIUS + 4;

    /**
     * How far from a segment centre {@link TunnelDecorator} may write.
     *
     * <p>Its own reach is private and measured along the run; this is a box
     * around the same number, generous enough to cover the width and height of
     * the slices it dresses as well. It decides one thing only: whether a clamped
     * carve still bothers to call the decorator for a segment whose middle lies
     * outside the clamp. Erring large costs a pass that writes nothing, because
     * decoration is position-hashed and idempotent; erring small leaves a stretch
     * of corridor undressed with no chunk left to dress it.</p>
     */
    private static final int DECORATION_REACH = 8;

    /**
     * How coarse the roughness on a chamber's dome is: one decision per this many
     * blocks in every direction.
     *
     * <p>Cells rather than blocks for the same reason the corridor swings have a
     * wavelength - a per-block decision is a texture and this wants to be a
     * shape. Two is small enough to break the sphere and large enough that what
     * is left standing reads as a lump of earth.</p>
     */
    private static final int DOME_ROUGH_CELL = 2;

    /**
     * Share of the dome left standing a block proud of the sphere it was cut
     * from.
     *
     * <p>Inwards only, never outwards, and only on the layers the dome has
     * already pulled back from the wall. A chamber's wall is the one surface in
     * this dimension that is not probed but assumed - {@code ChamberFurnisher}
     * cuts its larders at a fixed distance from the middle - so roughening it
     * would put alcoves inside the earth. The dome answers to nobody's
     * arithmetic, so that is where the roughness is allowed to live.</p>
     */
    private static final float DOME_ROUGH_CHANCE = 0.35F;

    // Salts. Distinct so that two swings never agree by accident and turn into one.
    private static final long SALT_RADIUS = 0x0D16_0000L;
    private static final long SALT_HEIGHT = 0x0D16_0001L;
    private static final long SALT_WANDER = 0x0D16_0002L;
    private static final long SALT_DOME_ROUGH = 0x0D16_0003L;
    private static final long SALT_SHELL_DEPTH = 0x0D16_0004L;
    private static final long SALT_POCKET = 0x0D16_0005L;

    /** A chamber nobody has told about its runs. Carves the bare room. */
    private static final int[] NO_MOUTHS = new int[0];

    /**
     * Clients yes, neighbour updates no.
     *
     * <p>{@code UPDATE_ALL} is {@code UPDATE_NEIGHBORS | UPDATE_CLIENTS}, and the
     * neighbour half is the expensive one: a run is on the order of a thousand
     * block changes, and each would fire a redstone-style update into six
     * neighbours that are themselves deep earth or air and have nothing to say.
     * Dropping it costs nothing, because the burrow has no block whose state
     * depends on what is next to it.</p>
     *
     * <p>{@code UPDATE_CLIENTS} stays because a corridor may be carved while a
     * player is standing in the burrow. It is affordable at this volume:
     * {@code ChunkHolder.blockChanged} collects the positions into one set per
     * 16-block section and the server flushes them as a single section-update
     * packet at the end of the tick, so a thousand changes are a handful of
     * packets, not a thousand.</p>
     *
     * <p>{@code UPDATE_KNOWN_SHAPE} suppresses the shape updates that
     * {@code Level.markAndNotifyBlock} otherwise runs against all six neighbours
     * of every changed block - six per carved block, none of which can change
     * anything here. It is also the safer flag next to decorated corridors: a
     * fitting hanging in a corridor we carve past is not asked whether it still
     * has support, so re-carving cannot knock it down.</p>
     */
    private static final int CARVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private CorridorCarver() {
    }

    /**
     * Carves the whole run and dresses it.
     *
     * <p>Both passes, in the only order they can go, for a caller that has the
     * whole run in hand and no reason to separate them. Anything working chunk by
     * chunk wants {@link #carve(ServerLevel, BurrowLink, BoundingBox)} and
     * {@link #decorateRun} instead, and wants to think about when to run the
     * second - see the class javadoc.</p>
     *
     * <p>The two ends are corridor, not chamber - {@link #carveChamber} is a
     * separate call because several links meet at one mound and the chamber there
     * belongs to the mound, not to any one of them.</p>
     *
     * <p>Positions in unloaded chunks are skipped, never forced. Reading or
     * writing one would make {@code Level.getChunkAt} load and if necessary
     * generate that chunk on the spot, which is how carving a long run turns into
     * a server freeze. A partly carved run is not a problem: carving is
     * idempotent, so the next visit finishes what this one could not reach.</p>
     *
     * @return how many blocks were actually cleared - positions that were already
     *         air do not count
     */
    public static int carve(ServerLevel burrow, BurrowLink link) {
        int cleared = carve(burrow, link, null);
        decorateRun(burrow, link, null);
        return cleared;
    }

    /**
     * Carves the part of the run that falls inside {@code clamp}, and dresses
     * nothing.
     *
     * <p>Null means the whole run. Anything else is one chunk asking for its own
     * share: the walk over the run is the same walk, every slice is decided the
     * same way, and only the writes outside the box are dropped. Carve a run once
     * per chunk it crosses and the result is the run - see the class javadoc for
     * why that holds.</p>
     *
     * <p>The decoration is {@link #decorateRun}'s, and the split is the whole
     * point of it: see the class javadoc under the two passes.</p>
     *
     * @return how many blocks this call cleared, which for a clamped carve is its
     *         share and not the run's total
     */
    public static int carve(ServerLevel burrow, BurrowLink link, @Nullable BoundingBox clamp) {
        if (link.pointCount() < 2) {
            return 0;
        }

        long seed = seedOf(link);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // The level the run was dug at decides its section. A colony's backbone
        // is carved wider and taller than a feeding run so that it reads as the
        // backbone from inside it - see CorridorProfile for the two caps that
        // decide how much wider and taller it may be.
        CorridorProfile profile = CorridorProfile.of(link.level());

        // Walked before anything is cut, because the taper at one end has to
        // know where the other end is. Arithmetic on the waypoints only - no
        // level access - so it costs nothing and, more to the point, it answers
        // the same for every clamped share of the same run.
        int totalSteps = stepCount(link);

        // The first waypoint is a mouth, so its slice is unmodulated whatever
        // direction the run leaves in - which is why it can be cut before there
        // is a segment to take a bearing from.
        BlockPos start = burrowPoint(link, 0);
        int cleared = section(burrow, start.getX(), start.getY(), start.getZ(), 0.0, 0.0,
                profile, seed, 0, totalSteps, cursor, clamp);

        return cleared + walkSegments(link, (from, to, steps, stepsBefore) -> carveSegment(
                burrow, from, to, steps, profile, seed, stepsBefore, totalSteps, cursor, clamp));
    }

    /**
     * Dresses the run, and carves nothing.
     *
     * <p>The second half of the pair. It walks the same segments {@link #carve}
     * does and hands each midpoint to {@link TunnelDecorator}, for every centre
     * whose reach meets {@code clamp} - null meaning the whole run.</p>
     *
     * <p>Idempotent and order-independent, because the decorator is: every roll
     * it makes comes from the block position, so calling this twice, or from four
     * chunks at once, or after somebody re-carved the corridor, reaches the same
     * conclusions and finds its own work already done. Nothing here can remove a
     * block; a corridor is only ever dressed, never re-cut.</p>
     *
     * @param random handed straight through and deliberately unused by the
     *               decorator - see its class javadoc
     */
    public static void decorateRun(ServerLevel burrow, BurrowLink link, @Nullable BoundingBox clamp) {
        // Seeded from the link rather than from the level, so a run that is
        // dressed again after its corridor was somehow lost gets the same
        // decoration back. The burrow is a place people learn their way around;
        // furniture that moves between visits would undo that.
        RandomSource random = RandomSource.create(seedOf(link));

        walkSegments(link, (from, to, steps, stepsBefore) -> {
            BlockPos centre = midpoint(from, to);
            if (dressedFrom(clamp, centre) && burrow.isLoaded(centre)) {
                TunnelDecorator.decorate(burrow, centre, random);
            }
            return 0;
        });
    }

    /**
     * Carves a bare chamber, with no way up to anything above its floor.
     *
     * <p>Only correct where every run leaving the mound was dug at the same
     * level. Prefer {@link #carveChamber(ServerLevel, BlockPos, int[])} and hand
     * it the heights - a chamber whose runs sit at different levels needs
     * galleries, or the shallower runs are doorways nobody can reach.</p>
     */
    public static int carveChamber(ServerLevel burrow, BlockPos burrowCentre) {
        return carveChamber(burrow, burrowCentre, NO_MOUTHS);
    }

    /**
     * Carves a chamber where a mound maps to, with a gallery at every height a
     * run leaves from.
     *
     * <p>{@code burrowCentre} is already in burrow space and names the
     * <em>walking surface</em> at the middle of the chamber, the same convention
     * a corridor centre follows: the block below it is floor and is left alone.
     * The caller decides where that is, because the height a mound maps to and
     * the height the runs leaving it were dug at are not the same number.</p>
     *
     * <p><strong>Why the heights have to be passed in.</strong> A run leaves the
     * chamber through the centre column at its own height and exits the wall in
     * whatever direction the far mound lies. Inside the chamber that mouth has no
     * floor under it - the room already cleared it - so it can only be entered
     * from the wall, at that height, <em>in that direction</em>. A spiral ramp is
     * at one height per direction and so misses almost every mouth; a terrace at
     * every height would need the radius to be at least the height, and six is
     * not nine. A full ring has no direction at all, which is why the galleries
     * are rings. Rings need to know which heights matter, and the carver cannot
     * see the runs from here.</p>
     *
     * <p>Each ring doubles as the corridor's own floor: a gallery at height
     * {@code L} is solid at layer {@code L - 1}, which is exactly the layer a
     * corridor at {@code L} never carves. The two meet without either knowing
     * about the other. Rings also survive being cut - a corridor crossing one
     * takes a five block bite out of it and you walk round the other way, where
     * the same bite would have severed a spiral.</p>
     *
     * <p>No decoration is applied. Corridor furniture in a chamber would fight
     * with whatever the mound's own way out puts there.</p>
     *
     * @param mouthLayers heights above the chamber floor, in burrow blocks, at
     *                    which runs leave - {@code burrowY(runEnd) -
     *                    burrowY(deepestRunEnd)} for each run touching the mound.
     *                    The floor itself is implied; values outside the chamber
     *                    and repeats are ignored, so an unsorted array with a zero
     *                    in it is fine.
     * @return how many blocks were actually cleared
     */
    public static int carveChamber(ServerLevel burrow, BlockPos burrowCentre, int[] mouthLayers) {
        return carveChamber(burrow, burrowCentre, mouthLayers, null);
    }

    /**
     * Carves the part of that chamber which falls inside {@code clamp}.
     *
     * <p>Null means the whole room. The room is decided before the box is
     * consulted - which layers get a gallery, where the ramps climb, how far the
     * dome has pulled back - so a chamber cut chunk by chunk is the same chamber
     * as one cut in a single call, with the same rings and the same earth left
     * standing.</p>
     */
    public static int carveChamber(ServerLevel burrow, BlockPos burrowCentre, int[] mouthLayers,
            @Nullable BoundingBox clamp) {
        int[] levels = galleryLevels(mouthLayers);
        int highest = levels[levels.length - 1];

        // How high the ledge zone is cut. A mouth at the top of the chamber puts
        // its gallery against the ceiling, so the room is opened out at the ledge
        // past the dome's own top - where the dome had already pulled back. A
        // gallery you cannot stand up in is not a gallery, and neither is the top
        // step of a ramp.
        int ledgeTop = highest + GALLERY_HEADROOM;
        int top = Math.max(BurrowGeometry.CHAMBER_HEIGHT, ledgeTop) - 1;

        // One box around everything the sweep can reach: the room's own radius
        // sideways and the lining past it, the floor and its lining at the bottom,
        // and at the top whichever of the dome and the highest gallery's headroom
        // stands taller.
        int reach = BurrowGeometry.CHAMBER_RADIUS + SHELL_MAX;
        if (clamp != null && misses(clamp,
                burrowCentre.getX() - reach, burrowCentre.getY() - SHELL_MAX,
                burrowCentre.getZ() - reach,
                burrowCentre.getX() + reach, burrowCentre.getY() + top + SHELL_MAX,
                burrowCentre.getZ() + reach)) {
            return 0;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;

        // One sweep, not two. The room and the ledge zone used to be cut in
        // separate passes over the same layers, and the union of the two is what
        // a chamber is; asking both questions of one position is the same answer
        // for half the walking, and it is the only way the lining can be decided
        // once rather than by whichever pass happened to reach a block first.
        //
        // Everything the ledge keeps is simply never carved, so the galleries and
        // their staircases are earth left standing rather than blocks written into
        // the air - which is what lets a later, deeper chamber absorb an older
        // ledge instead of leaving it hanging.
        for (int layer = -SHELL_MAX; layer <= top + SHELL_MAX; layer++) {
            cleared += chamberLayer(burrow, burrowCentre, layer, ledgeTop, levels, highest, cursor, clamp);
        }

        return cleared;
    }

    /**
     * True when this run already exists down there, so a second visit digs
     * nothing.
     *
     * <p>One block is read: the walking surface at the middle waypoint. Cheap on
     * purpose - this is asked before every trip, whereas carving happens once.
     * The midpoint is the honest sample because both ends of a run touch a
     * chamber and would read as carved as soon as the mound had one.</p>
     *
     * <p>An unloaded midpoint answers false. "Nothing there" and "nothing loaded
     * there" are indistinguishable from here, and {@link #carve} skips unloaded
     * positions anyway, so guessing wrong costs a walk over blocks that are
     * already air.</p>
     */
    public static boolean alreadyCarved(ServerLevel burrow, BurrowLink link) {
        if (link.pointCount() < 1) {
            return false;
        }

        BlockPos centre = burrowPoint(link, link.pointCount() / 2);
        return burrow.isLoaded(centre) && burrow.getBlockState(centre).isAir();
    }

    // --- carving --------------------------------------------------------------

    /**
     * Clears the line between two waypoints.
     *
     * <p>Steps of one block: the cross-section is several blocks wide, so a
     * coarser step would still join up, but only for as long as no level's
     * profile is narrow. A unit step is gapless whatever the width, and the cost
     * is reads on blocks that are already air rather than extra writes.</p>
     *
     * <p>Starts at step 1. Step 0 is the waypoint the caller has already cleared,
     * either as the start of the run or as the end of the previous segment.</p>
     *
     * <p>{@code stepsBefore} is how many steps of this run lie behind
     * {@code from}. It is what the swings are indexed by, so it has to be the
     * count for the whole run rather than for this segment - which is also why a
     * segment the clamp misses still advances it before it is skipped.</p>
     */
    private static int carveSegment(ServerLevel burrow, BlockPos from, BlockPos to, int steps,
            CorridorProfile profile, long seed, int stepsBefore, int totalSteps,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        if (clamp != null && misses(clamp, from, to, profile)) {
            return 0;
        }

        // The bearing the wander is measured across: a horizontal unit vector at
        // right angles to the run. A run that goes straight up - which the
        // vertical scale makes possible on a steep hillside - has no such
        // bearing, and gets no wander rather than an arbitrary one.
        double flat = Math.sqrt(dx * dx + dz * dz);
        double perpX = flat == 0.0 ? 0.0 : -dz / flat;
        double perpZ = flat == 0.0 ? 0.0 : dx / flat;

        int cleared = 0;
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            cleared += section(burrow,
                    from.getX() + (int) Math.round(dx * t),
                    from.getY() + (int) Math.round(dy * t),
                    from.getZ() + (int) Math.round(dz * t),
                    perpX, perpZ, profile, seed, stepsBefore + step, totalSteps, cursor, clamp);
        }
        return cleared;
    }

    /**
     * One cross-section of corridor, cut to what the swings say about this point
     * of this run.
     *
     * <p>{@code y} is the walking surface and nothing below it is ever
     * <em>cleared</em>. That is the floor, and leaving it standing rather than
     * cutting through it is deliberate: two runs one level apart lie four blocks
     * apart down here against a corridor height of six, so their corridors
     * genuinely overlap, and clearing a floor would be opening a hole in the
     * middle of somebody else's corridor.</p>
     *
     * <p>The layers below it are <em>lined</em>, though, which is a different
     * thing: they stay solid and become soil. The floor a player stands on is the
     * block they could dig, and the deep earth is two or three blocks under their
     * feet - the same relationship the walls have.</p>
     *
     * <p><strong>The wander is sideways only.</strong> Nothing here moves the
     * walking surface off the height the link put it at, and that is a hard rule
     * rather than a simplification: the floor is the layer below whatever is
     * carved, so a slice cut a block low leaves a step in the floor of a corridor
     * that is otherwise smooth. One block is a hop, and a hop every wavelength
     * along a sixty block run is not character, it is a staircase nobody asked
     * for. The roof carries the vertical variation instead, where a block up or
     * down costs a duck at worst.</p>
     */
    private static int section(ServerLevel burrow, int x, int y, int z, double perpX, double perpZ,
            CorridorProfile profile, long seed, int step, int totalSteps,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        float taper = taperAt(step, totalSteps);
        double radius = profile.radiusSwungBy(swing(seed, SALT_RADIUS, step, taper, CorridorProfile.RADIUS_SWING));
        int height = profile.heightSwungBy(
                Math.round(swing(seed, SALT_HEIGHT, step, taper, CorridorProfile.HEIGHT_SWING)));
        double wander = undulation(seed, SALT_WANDER, step) * taper * CorridorProfile.WANDER;

        // The wander is carried as a real number rather than rounded to a block.
        // A rounded centre line jumps sideways in one step every wavelength, and
        // the jump is a seam across the floor and the ceiling both; a disc cut
        // around a fractional centre drifts across, which is what a run that was
        // dug rather than surveyed does.
        double centreX = x + perpX * wander;
        double centreZ = z + perpZ * wander;

        int cleared = 0;
        for (int layer = -SHELL_MAX; layer < height + SHELL_MAX; layer++) {
            int layerY = y + layer;
            if (clamp != null && (layerY < clamp.minY() || layerY > clamp.maxY())) {
                continue;
            }
            // Below the floor and above the roof nothing is cut; the lining goes
            // on wrapping the tube, pulled in by a block for every layer it is
            // away from it, so the skin is round at the top and the bottom as
            // well as at the sides.
            int away = layer < 0 ? -layer : Math.max(0, layer - (height - 1));
            int nearest = Math.clamp(layer, 0, height - 1);
            cleared += discAndShell(burrow, centreX, layerY, centreZ,
                    sectionRadiusAt(nearest, height, radius), away, cursor, clamp);
        }

        return cleared + walkway(burrow, x, y, z, cursor, clamp);
    }

    /**
     * How wide the cut is at one layer of a cross-section.
     *
     * <p>Two things pull it in from the section's own radius, at opposite ends.
     * The arch takes the top {@link #CORRIDOR_ARCH} layers in along a quarter
     * ellipse, and the fillet takes a block off the bottom one where the run is
     * wide enough to give it - see the two constants for what each buys. Both are
     * insets rather than radii, so whichever asks for more wins and neither has to
     * know about the other.</p>
     */
    private static double sectionRadiusAt(int layer, int height, double radius) {
        double inset = 0.0;

        int fromTop = height - 1 - layer;
        if (fromTop < CORRIDOR_ARCH) {
            double t = ARCH_CROWN * (CORRIDOR_ARCH - fromTop) / CORRIDOR_ARCH;
            inset = radius * (1.0 - Math.sqrt(1.0 - t * t));
        }

        if (layer == 0) {
            inset = Math.max(inset,
                    Mth.clamp(radius - FOOT_ROUND_FROM, 0.0, FOOT_ROUND_DEPTH));
        }

        return Math.max(CorridorProfile.NARROWEST_RADIUS, radius - inset);
    }

    /**
     * The one promise the swings cannot break: the centre line of the run, open
     * to head height and a block over it.
     *
     * <p>Cut at the <em>unwandered</em> centre, which is the line a player walks
     * and the line {@code TunnelDecorator} probes from. The arithmetic already
     * says the disc covers it - a wander of one block against a radius of at
     * least one - but "already says" is the wrong footing for the claim that the
     * burrow can be walked out of. Three blocks that are almost always air
     * anyway is a cheap price for not having to reason about it.</p>
     *
     * <p>Last, after the cross-section and its lining. That order is what makes it
     * a promise rather than a hope: a slice whose wander put the centre column at
     * the very rim of the cut could have had it lined instead of cleared, and
     * {@link #clear} takes soil as readily as earth, so the walkway takes it
     * back.</p>
     */
    private static int walkway(ServerLevel burrow, int x, int y, int z,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        int cleared = 0;
        for (int layer = 0; layer < CorridorProfile.LOWEST_SECTION; layer++) {
            if (writes(clamp, x, y + layer, z) && clear(burrow, cursor.set(x, y + layer, z))) {
                cleared++;
            }
        }
        return cleared;
    }

    /**
     * One horizontal layer of a round volume: cleared out to {@code core}, lined
     * out past that, and nothing beyond.
     *
     * <p>The one sweep the shell costs. Every position of the enlarged square is
     * visited once and does one thing - it is inside the cut and is cleared, or it
     * is outside and is lined, or it is out of reach and is skipped on two
     * multiplications. A second pass over the volume to line what the first pass
     * had just cut would cost twice this and would have to be told where the first
     * pass had been.</p>
     *
     * <p>Package visible, because {@link Junctions} and {@link LevelShafts} open
     * round volumes too and their linings must be this one rather than a second
     * copy of it - the same argument that keeps {@link #clear} in one place.</p>
     *
     * @param core how far this layer is cleared. Negative clears nothing, which is
     *             what a layer above the roof or below the floor asks for
     * @param away how many layers this one is outside the volume being lined. The
     *             lining loses a block of reach for each, so the skin closes over
     *             the top and under the bottom instead of ending in a rim
     * @return how many blocks were cleared - lining is not clearing and is not
     *         counted, so a second carve of the same ground still answers zero
     */
    static int discAndShell(ServerLevel burrow, double centreX, int y, double centreZ,
            double core, int away, BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        double widest = core + SHELL_MAX - away;
        if (widest < 0.0) {
            return 0;
        }

        int reach = Mth.ceil(widest);
        int minX = Mth.floor(centreX) - reach;
        int maxX = Mth.ceil(centreX) + reach;
        int minZ = Mth.floor(centreZ) - reach;
        int maxZ = Mth.ceil(centreZ) + reach;
        if (clamp != null && !clamp.intersects(minX, minZ, maxX, maxZ)) {
            return 0;
        }

        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double distanceSqr = square(x - centreX) + square(z - centreZ);

                if (away == 0 && within(distanceSqr, core)) {
                    if (writes(clamp, x, y, z) && clear(burrow, cursor.set(x, y, z))) {
                        cleared++;
                    }
                    continue;
                }
                // The cheap bound first: most of the square is past even the
                // deepest lining, and settling that costs no block read and no
                // hash.
                if (!within(distanceSqr, widest) || !writes(clamp, x, y, z)) {
                    continue;
                }
                int depth = shellDepthAt(x, y, z);
                if (away <= depth && within(distanceSqr, core + depth - away)) {
                    line(burrow, cursor.set(x, y, z));
                }
            }
        }
        return cleared;
    }

    /**
     * The disc, with a real radius: {@code radius * radius + radius} rather than
     * {@code radius * radius} is the squared radius of a circle drawn half a block
     * outside the ring, which is what keeps the diagonals from being cut back to a
     * plus sign.
     */
    private static boolean within(double distanceSqr, double radius) {
        return radius >= 0.0 && distanceSqr <= radius * radius + radius;
    }

    /**
     * The same for a whole number of blocks, where the chamber still counts in
     * them. A negative radius is nothing at all rather than a single column - the
     * arithmetic would otherwise let {@code -1} through at the very centre.
     */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return radius >= 0 && dx * dx + dz * dz <= radius * radius + radius;
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * How deep the lining goes at this block: {@link #SHELL_THICKNESS}, or one
     * more where the ground says so.
     *
     * <p>Hashed from where the block is in the world and from nothing else, which
     * is the discipline the whole dimension works to - the depth is then a
     * property of the place and comes out the same however many chunks line it and
     * in whatever order.</p>
     */
    private static int shellDepthAt(int x, int y, int z) {
        boolean deeper = noise(SALT_SHELL_DEPTH,
                Math.floorDiv(x, SHELL_CELL),
                Math.floorDiv(y, SHELL_CELL),
                Math.floorDiv(z, SHELL_CELL)) < SHELL_DEEPER_CHANCE;
        return deeper ? SHELL_THICKNESS + 1 : SHELL_THICKNESS;
    }

    /**
     * Turns one block of deep earth into lining.
     *
     * <p>Deep earth and nothing else. Not air - a hole is either a corridor
     * somebody else cut or one a player dug, and dropping soil into it would undo
     * their work either way. Not soil that is already there, not a decoration, not
     * water, not a block anybody built with. That single rule is what makes the
     * lining pass idempotent and order-independent, and it is why the pass may be
     * run from every chunk a feature touches without anybody counting.</p>
     *
     * <p>Package visible for {@link Junctions}, which grows the crown of a
     * junction into a ceiling this may have lined first.</p>
     */
    static boolean line(ServerLevel burrow, BlockPos.MutableBlockPos pos) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return false;
        }
        if (!burrow.getBlockState(pos).is(ModBlocks.DEEP_EARTH.get())) {
            return false;
        }
        return burrow.setBlock(pos, liningAt(pos.getX(), pos.getY(), pos.getZ()), CARVE_FLAGS);
    }

    /**
     * What one block of lining is made of: soil, or now and then a root nodule.
     *
     * <p>A pure function of where the block is and of nothing else, which is what
     * keeps {@link #line} the idempotent pass the rest of this class relies on.
     * Deep earth converted twice converts to the <em>same</em> thing both times,
     * however many chunks line it and in whatever order - a roll taken from a
     * stream, or one that looked at what was already there, would give a wall that
     * changed between visits.</p>
     *
     * <p>A nodule is only ever written into deep earth, never over soil, so the
     * question is asked once per block of ground for the life of the world. What
     * the pocket is worth is {@code ModBlockLootProvider}'s business; all that is
     * decided here is where the ground keeps one.</p>
     */
    private static BlockState liningAt(int x, int y, int z) {
        return noise(SALT_POCKET, x, y, z) < POCKET_CHANCE
                ? ModBlocks.ROOT_NODULE.get().defaultBlockState()
                : ModBlocks.LOOSE_SOIL.get().defaultBlockState();
    }

    /**
     * Clears one block, if this is a block we are allowed to clear.
     *
     * <p>The cursor is reused across the whole run and handed straight to
     * {@code setBlock}, which is safe because NeoForge patches {@code Level}
     * to call {@code immutable()} on the position before storing it anywhere.</p>
     *
     * <p>Package visible because {@link LevelShafts} sinks its wells with it. The
     * rule about what may be replaced is the safety model of the whole dimension
     * and there must only ever be one copy of it.</p>
     *
     * <p><strong>Soil counts as ground.</strong> Deep earth is what the dimension
     * is filled with and loose soil is what this class lines its own cuts with, so
     * a run crossing a corridor that has already been lined has to go through the
     * lining rather than stop at it - and the two orders a single carve can put
     * the same block in, lined then cleared and cleared then lined, have to agree.
     * The price is that a block of soil a player laid down here is ground as far
     * as a later carve is concerned. It is the same grey area {@code
     * TunnelDecorator} names in its own palette, and it is the cost of building
     * the burrow out of a block that is worth carrying home.</p>
     *
     * <p><strong>And so does a root nodule</strong>, for exactly the same reason
     * and with more force: a nodule <em>is</em> lining - see {@link #liningAt} -
     * and a run that stopped at one would be a run with a knot of roots standing
     * in the middle of it. Nothing is dropped when a carve eats one; the pocket
     * was never dug, it was passed through.</p>
     *
     * @return true when something was actually turned into air
     */
    static boolean clear(ServerLevel burrow, BlockPos.MutableBlockPos pos) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return false;
        }

        BlockState state = burrow.getBlockState(pos);
        if (!state.is(ModBlocks.DEEP_EARTH.get()) && !state.is(ModBlocks.LOOSE_SOIL.get())
                && !state.is(ModBlocks.ROOT_NODULE.get())) {
            return false;
        }

        return burrow.setBlock(pos, Blocks.AIR.defaultBlockState(), CARVE_FLAGS);
    }

    // --- The clamp ------------------------------------------------------------

    /**
     * Whether a write at this position is this call's to make.
     *
     * <p>No clamp means every position is. A clamped call silently drops the
     * rest, which is not a failure but the whole mechanism: the chunk that owns
     * those blocks will carve them from the same arithmetic when its turn
     * comes.</p>
     */
    private static boolean writes(@Nullable BoundingBox clamp, int x, int y, int z) {
        return clamp == null || clamp.isInside(x, y, z);
    }

    /**
     * Whether a box misses the clamp entirely, and so is not worth walking.
     *
     * <p>Written out rather than built as a {@link BoundingBox} and handed to
     * {@code intersects}: a chunk asks it twice for every segment of every run
     * that comes anywhere near it, and six comparisons beat an allocation.</p>
     */
    private static boolean misses(BoundingBox clamp, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        return clamp.maxX() < minX || clamp.minX() > maxX
                || clamp.maxY() < minY || clamp.minY() > maxY
                || clamp.maxZ() < minZ || clamp.minZ() > maxZ;
    }

    /**
     * The same question for one segment, envelope included.
     *
     * <p>Measured against {@link CorridorProfile#outerRadius} and
     * {@link CorridorProfile#outerHeight} rather than the section's own, because
     * a segment that only just misses the box with its nominal disc can still
     * reach into it with a bulge. Skipping a segment that had something to carve
     * would leave a hole no other chunk is going to fill.</p>
     *
     * <p>And {@link #SHELL_MAX} on top of that, in all six directions. The lining
     * reaches past the cut in every one of them, the floor included, and a segment
     * skipped because only its lining fell inside the box would leave a stripe of
     * deep earth showing at a chunk border - which is exactly the thing the lining
     * exists to prevent.</p>
     */
    private static boolean misses(BoundingBox clamp, BlockPos from, BlockPos to, CorridorProfile profile) {
        int reach = profile.outerRadius() + SHELL_MAX;
        return misses(clamp,
                Math.min(from.getX(), to.getX()) - reach,
                Math.min(from.getY(), to.getY()) - SHELL_MAX,
                Math.min(from.getZ(), to.getZ()) - reach,
                Math.max(from.getX(), to.getX()) + reach,
                Math.max(from.getY(), to.getY()) + profile.outerHeight() - 1 + SHELL_MAX,
                Math.max(from.getZ(), to.getZ()) + reach);
    }

    /**
     * Whether a clamped carve should still run the decoration pass for this
     * segment centre.
     *
     * <p>The decorator is not clamped and does not need to be - every roll it
     * makes comes from the block position, so the same corridor comes out the
     * same however many times and from whichever chunk it is dressed. All this
     * has to get right is calling it <em>often enough</em>: any chunk whose box
     * the pass could reach into asks for it, and the overlap is free.</p>
     */
    private static boolean dressedFrom(@Nullable BoundingBox clamp, BlockPos centre) {
        return clamp == null || !misses(clamp,
                centre.getX() - DECORATION_REACH, centre.getY() - DECORATION_REACH,
                centre.getZ() - DECORATION_REACH, centre.getX() + DECORATION_REACH,
                centre.getY() + DECORATION_REACH, centre.getZ() + DECORATION_REACH);
    }

    // --- The shape of a run ---------------------------------------------------

    /**
     * What one pass makes of one segment of a run.
     *
     * <p>{@code stepsBefore} is how many steps of the whole run lie behind
     * {@code from}, and the return is whatever the pass counts - blocks cleared,
     * or nothing at all.</p>
     */
    @FunctionalInterface
    private interface SegmentPass {
        int apply(BlockPos from, BlockPos to, int steps, int stepsBefore);
    }

    /**
     * Walks the run segment by segment and hands each one to {@code pass}.
     *
     * <p>The single statement of how a link becomes a sequence of segments, and
     * it is single on purpose: {@link #carve} and {@link #decorateRun} are two
     * passes over the same run and have to agree about where its segments are.
     * Two copies of this loop would be two things to keep in step, and the
     * failure would be silent - a decoration pass that drifted by one waypoint
     * would dress a corridor that is nearly, but not quite, the one that was
     * cut.</p>
     *
     * <p>Waypoints only, no level access, so it is safe to walk a run this call
     * has no intention of touching.</p>
     *
     * @return the sum of what the pass returned for each segment
     */
    private static int walkSegments(BurrowLink link, SegmentPass pass) {
        int points = link.pointCount();
        if (points < 2) {
            return 0;
        }

        int total = 0;
        int stepsBefore = 0;
        BlockPos previous = burrowPoint(link, 0);

        for (int i = 1; i < points; i++) {
            BlockPos next = burrowPoint(link, i);
            int steps = stepsBetween(previous, next);
            total += pass.apply(previous, next, steps, stepsBefore);
            stepsBefore += steps;
            previous = next;
        }

        return total;
    }

    /**
     * How many one block steps the whole run is walked in.
     *
     * <p>It exists so that the taper at the far end of a run can be known at the
     * near end of it, and it has to be the whole run's count even when a single
     * chunk is being carved - a taper that counted from the edge of a chunk would
     * put a mouth in the middle of a corridor.</p>
     */
    private static int stepCount(BurrowLink link) {
        return walkSegments(link, (from, to, steps, stepsBefore) -> steps);
    }

    private static int stepsBetween(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        return Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }

    /**
     * How much of a swing this point of the run is allowed, from nothing at
     * either mouth to all of it {@link #MOUTH_TAPER} blocks in.
     */
    private static float taperAt(int step, int totalSteps) {
        int fromEnd = Math.min(step, totalSteps - step);
        return Math.min(1.0F, Math.max(0.0F, fromEnd / (float) MOUTH_TAPER));
    }

    /**
     * One swing, in blocks and fractions of one: the undulation, tapered and
     * scaled.
     *
     * <p>Deliberately not rounded here. The radius wants the fraction - see
     * {@link CorridorProfile#radiusSwungBy} for what a whole block of step costs a
     * wall - and the height, which can only be whole layers, rounds it at the one
     * place that has to.</p>
     */
    private static float swing(long seed, long salt, int step, float taper, int blocks) {
        return undulation(seed, salt, step) * taper * blocks;
    }

    /**
     * A smooth value in {@code [-1, 1)} along the run, one swell per
     * {@link #SHAPE_WAVELENGTH} blocks.
     *
     * <p>Value noise: a hashed number every wavelength, smoothstepped between.
     * The interpolation is the point - hashing the step directly would give a
     * different radius every block, and a corridor whose wall changes every block
     * is not rough, it is broken. Two salts on the same seed give two swells that
     * do not move together, which is what keeps a wide stretch from also being
     * the tall stretch and the whole run from breathing in unison.</p>
     */
    private static float undulation(long seed, long salt, int step) {
        int node = Math.floorDiv(step, SHAPE_WAVELENGTH);
        float t = (step - node * SHAPE_WAVELENGTH) / (float) SHAPE_WAVELENGTH;
        float from = noise(seed ^ salt, node, 0, 0) * 2.0F - 1.0F;
        float to = noise(seed ^ salt, node + 1, 0, 0) * 2.0F - 1.0F;
        return from + (to - from) * t * t * (3.0F - 2.0F * t);
    }

    /**
     * A stable value in {@code [0, 1)} for one purpose at one place.
     *
     * <p>The third copy of the hash {@link TunnelDecorator} and
     * {@link ChamberFurnisher} each carry, and the one that makes hoisting it
     * worth doing - the note in {@code ChamberFurnisher} left that decision to
     * whoever wrote this one. Left copied for now because the alternative is a
     * new shared class in the middle of a wave where three people are editing
     * this package, and the three copies are byte for byte the same function.</p>
     *
     * <p>Package visible so that {@link Junctions} can roughen its bell without
     * making a fourth copy. That is as far as the sharing goes on purpose: a
     * caller in this package borrows the arithmetic, and the day somebody wants it
     * from outside is the day it earns a class of its own.</p>
     */
    static float noise(long salt, int a, int b, int c) {
        long h = salt;
        h = h * 0x9E3779B97F4A7C15L + a;
        h = h * 0x9E3779B97F4A7C15L + b;
        h = h * 0x9E3779B97F4A7C15L + c;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) * 0x1.0p-24F;
    }

    // --- geometry -------------------------------------------------------------

    /**
     * One layer of a chamber: what the room clears, what the ledge keeps, and what
     * the lining wraps around both.
     *
     * <p>Two shapes overlap at every layer and the cut is their union. The
     * <em>room</em> is the domed volume, cleared from the axis out to
     * {@link #chamberRoomAt}. The <em>ledge zone</em> is the ring from
     * {@link #LEDGE_INNER_RADIUS} to the wall, cleared as high as the galleries
     * need it: past the top of the dome that is a ring of air cut into the roof,
     * and inside the dome it is the same blocks the room has already taken. Only
     * the room is domed, which is why the two cannot be collapsed into one
     * radius.</p>
     *
     * <p>A layer the dome has pulled back from the wall is also roughened, by
     * leaving the odd cell of it standing a block proud of the sphere. Only
     * inwards, and only there - see {@link #DOME_ROUGH_CHANCE} for why the wall
     * itself is left perfectly round. The lining is measured from the sphere and
     * not from the bite, so a lump left standing is lined like everything else and
     * the roughness reads as soil rather than as a black knuckle.</p>
     *
     * <p><strong>Every ledge block is lined.</strong> A gallery ring is one block
     * thick with air above it and air below it, so all of it is a surface; a ramp
     * is a wedge and only its top and its flanks are, but the inside of a ramp is
     * earth nobody can see without digging the ramp away, and telling the two
     * apart would cost a probe per block to hide a difference that has no
     * viewer.</p>
     *
     * @param ledgeTop one past the topmost layer the ledge zone is opened at
     */
    private static int chamberLayer(ServerLevel burrow, BlockPos centre, int layer, int ledgeTop,
            int[] levels, int highestLevel, BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        int y = centre.getY() + layer;
        if (clamp != null && (y < clamp.minY() || y > clamp.maxY())) {
            return 0;
        }

        // How far this layer is outside each shape, and which layer of that shape
        // it is nearest. Zero means the shape is cut here; anything more is lining
        // only, pulled in by a block for every layer of distance.
        int roomAway = away(layer, BurrowGeometry.CHAMBER_HEIGHT - 1);
        int ledgeAway = away(layer, ledgeTop - 1);
        int roomOuter = chamberRoomAt(Math.clamp(layer, 0, BurrowGeometry.CHAMBER_HEIGHT - 1));
        int ledgeOuter = chamberSpanAt(Math.clamp(layer, 0, Math.max(0, ledgeTop - 1)));
        boolean rough = roomAway == 0 && roomOuter < chamberSpanAt(layer);

        int reach = Math.max(roomOuter - roomAway, ledgeOuter - ledgeAway) + SHELL_MAX;
        if (reach < 0) {
            return 0;
        }

        int cleared = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;

                // Only inside the room can anything be ledge, and asking outside
                // it is not merely wrong but expensive: the ramp test takes a
                // bearing, and the square this sweeps is now three blocks wider
                // than the room in every direction to make room for the lining.
                boolean ledge = withinDisc(dx, dz, BurrowGeometry.CHAMBER_RADIUS)
                        && isLedge(layer, dx, dz, levels, highestLevel);
                if (!ledge) {
                    int bite = rough ? domeBite(x, y, z) : 0;
                    boolean cut = (roomAway == 0 && withinDisc(dx, dz, roomOuter - bite))
                            || (ledgeAway == 0 && distanceSqr >= LEDGE_INNER_RADIUS * LEDGE_INNER_RADIUS
                                    && withinDisc(dx, dz, ledgeOuter));
                    if (cut) {
                        if (writes(clamp, x, y, z) && clear(burrow, cursor.set(x, y, z))) {
                            cleared++;
                        }
                        continue;
                    }
                }

                if (!writes(clamp, x, y, z)) {
                    continue;
                }
                int depth = shellDepthAt(x, y, z);
                boolean lined = ledge
                        || (roomAway <= depth && withinDisc(dx, dz, roomOuter + depth - roomAway))
                        || (ledgeAway <= depth && withinDisc(dx, dz, ledgeOuter + depth - ledgeAway));
                if (lined) {
                    line(burrow, cursor.set(x, y, z));
                }
            }
        }
        return cleared;
    }

    /** How many layers {@code layer} lies outside the band from zero to {@code top}. */
    private static int away(int layer, int top) {
        return layer < 0 ? -layer : Math.max(0, layer - Math.max(0, top));
    }

    /**
     * Whether the dome keeps this block, one block proud of the sphere it was cut
     * from.
     *
     * <p>Hashed from where the block is in the world rather than from where it is
     * in the room, which is the same discipline {@code TunnelDecorator} works to:
     * a lump is then a property of the place and comes back identical however
     * often, and in whatever order, the chamber is carved.</p>
     */
    private static int domeBite(int x, int y, int z) {
        return noise(SALT_DOME_ROUGH,
                Math.floorDiv(x, DOME_ROUGH_CELL),
                Math.floorDiv(y, DOME_ROUGH_CELL),
                Math.floorDiv(z, DOME_ROUGH_CELL)) < DOME_ROUGH_CHANCE ? 1 : 0;
    }

    /**
     * Whether this block of the chamber is ledge, and so is left standing.
     *
     * <p>Two zones. Out from {@link #RAMP_INNER_RADIUS} the ramps run, and where
     * a ramp climbs it replaces the ring of the gallery it is climbing <em>to</em>
     * - only that one, because a ramp that suppressed every ring it passed would
     * dismantle the galleries it exists to connect. Inside that, the lane is ring
     * and nothing else, so every gallery is a complete circle whatever the ramps
     * are doing.</p>
     */
    private static boolean isLedge(int layer, int dx, int dz, int[] levels, int highestLevel) {
        int distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < LEDGE_INNER_RADIUS * LEDGE_INNER_RADIUS) {
            return false;
        }

        if (distanceSqr >= RAMP_INNER_RADIUS * RAMP_INNER_RADIUS && highestLevel > 0) {
            double angle = Math.atan2(dz, dx);
            boolean replaced = false;

            for (int gap = 0; gap + 1 < levels.length; gap++) {
                int top = rampTop(gap, levels[gap], levels[gap + 1], angle);
                if (top < 0) {
                    continue;
                }
                if (layer >= Math.max(0, levels[gap] - 1) && layer <= top) {
                    return true;
                }
                if (layer == levels[gap + 1] - 1) {
                    replaced = true;
                }
            }

            if (replaced) {
                return false;
            }
        }

        for (int level : levels) {
            if (level > 0 && layer == level - 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * The topmost layer a ramp keeps solid at this bearing, or -1 where this gap
     * has no ramp here.
     *
     * <p>A ramp is a wedge of earth growing out of the gallery below it, one
     * block taller every step round, until its top step lands level with the
     * gallery above. Wedge rather than a run of floating slabs, so a corridor
     * cutting through takes a notch out of it rather than leaving a step hanging
     * in mid-air.</p>
     *
     * <p>Consecutive gaps are offset by half a spacing so that one run cannot
     * line up with a ramp of every gap at once and cut the whole climb.</p>
     */
    private static int rampTop(int gap, int from, int to, double angle) {
        int rise = to - from;
        double width = rise * RAMP_STEP_ARC / RAMP_MID_RADIUS;
        int ramps = Math.max(1, Math.min(RAMPS_PER_GAP, (int) (TAU / width)));

        for (int ramp = 0; ramp < ramps; ramp++) {
            double base = ramp * (TAU / ramps) + gap * (Math.PI / ramps);
            double offset = Mth.positiveModulo(angle - base, TAU);
            if (offset < width) {
                int step = Math.min(rise, (int) (offset / (width / rise)) + 1);
                return from + step - 1;
            }
        }
        return -1;
    }

    /**
     * The heights that get a gallery: the floor, plus every mouth height inside
     * the chamber, sorted and without repeats.
     *
     * <p>The floor is always in the list so the staircase has something to start
     * from, and so the array is never empty for the caller that asks for its
     * highest entry.</p>
     */
    private static int[] galleryLevels(int[] mouthLayers) {
        return IntStream.concat(IntStream.of(0), Arrays.stream(mouthLayers))
                .filter(level -> level >= 0 && level < BurrowGeometry.CHAMBER_HEIGHT)
                .distinct()
                .sorted()
                .toArray();
    }

    /**
     * How wide the chamber is at this layer before the dome has its say: the
     * room's radius, less the fillet at the foot.
     *
     * <p>This is the wall, and the ledge zone is cut to it - a gallery has to
     * reach the wall or it is a shelf with a gap behind it. The dome is a separate
     * question and only the room answers it.</p>
     */
    private static int chamberSpanAt(int layer) {
        return BurrowGeometry.CHAMBER_RADIUS - Math.max(0, CHAMBER_FILLET - layer);
    }

    /**
     * Radius of the room at chamber layer {@code layer}, counted up from the
     * floor: the wall, pulled in by the dome where the dome has reached it.
     */
    private static int chamberRoomAt(int layer) {
        return Math.min(chamberSpanAt(layer), chamberDomeAt(layer));
    }

    /**
     * What the dome allows at this layer.
     *
     * <p>A quarter ellipse over the top {@link #CHAMBER_DOME} layers. The
     * parameter stops short of 1 so the apex keeps a usable radius instead of
     * closing to a single column - a dome, not a spire.</p>
     */
    private static int chamberDomeAt(int layer) {
        int flat = BurrowGeometry.CHAMBER_HEIGHT - CHAMBER_DOME;
        if (layer < flat) {
            return BurrowGeometry.CHAMBER_RADIUS;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (CHAMBER_DOME + 1.0), 0.0, 1.0);
        return (int) Math.round(BurrowGeometry.CHAMBER_RADIUS * Math.sqrt(1.0 - t * t));
    }

    /**
     * Waypoint {@code index} of the run, in burrow space.
     *
     * <p>Package visible so {@link LevelShafts} can find a crossing at the exact
     * places this carve wrote the run. A second copy of the mapping would be a
     * second thing to keep in step, and a shaft a block beside its corridor opens
     * into nothing.</p>
     */
    static BlockPos burrowPoint(BurrowLink link, int index) {
        Vec3 overworld = link.pointAt(index);
        return BurrowGeometry.toBurrow(BlockPos.containing(overworld));
    }

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.floorDiv(a.getX() + b.getX(), 2),
                Math.floorDiv(a.getY() + b.getY(), 2),
                Math.floorDiv(a.getZ() + b.getZ(), 2));
    }

    /**
     * A seed that depends on the run and on nothing else.
     *
     * <p>Sorted, so it does not matter which end was dug first. A link has no
     * direction of its own - {@code BurrowLink.joins} makes the same point - and a
     * seed that flipped with the storage order would decorate the same corridor
     * differently after a reshape.</p>
     */
    private static long seedOf(BurrowLink link) {
        long first = Math.min(link.a().asLong(), link.b().asLong());
        long second = Math.max(link.a().asLong(), link.b().asLong());
        return first * 31L + second;
    }
}

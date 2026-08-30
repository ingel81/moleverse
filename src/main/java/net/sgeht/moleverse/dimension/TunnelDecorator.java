package net.sgeht.moleverse.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * What turns a carved corridor into somewhere worth walking.
 *
 * <p>The whole dimension rests on one idea: at this scale the small life of the
 * soil is the large life of the burrow. A root is a beam you duck under, gravel
 * is a boulder field you step over, the fungal threads on the ceiling are where
 * the light comes from, and a seep is a ford. Nothing here invents a new kind of
 * thing - it only takes what is already in a spadeful of earth and gives it the
 * size a mole would grant it.</p>
 *
 * <p>Five surfaces get worked and then a sixth thing that is not a surface at all:
 * the angles between them. A floor, a ceiling and two walls dressed on their own
 * meet at a hard line, and the line is exactly where damp collects, where moss
 * takes hold and where a root cut through by the digging hangs its ends. That is
 * what {@link #dressFringe} is for, and it is the part of a corridor a player
 * walks nearest to.</p>
 *
 * <h2>Why every decision comes from the position</h2>
 *
 * <p>Nothing below draws from the {@code random} handed in. Every roll is a hash
 * of the block position, so a stretch of corridor keeps its character no matter
 * how often it is walked, re-carved or reloaded - a corridor that grows a new
 * root each visit is not a place, it is a screensaver. It also makes the whole
 * class idempotent: {@link #decorate} may be called for overlapping segments, in
 * any order, and the corridor comes out the same. That is what lets
 * {@link #REACH} be generous without the decoration piling up. The hash itself
 * and the patch shapes built on it live in {@link TunnelNoise}.</p>
 *
 * <p>The {@code random} parameter is kept because the carver has one to hand and
 * the day may come when something genuinely wants to differ per visit. Today it
 * is deliberately unused, and that is the reason.</p>
 *
 * <h2>Why a stretch has a character</h2>
 *
 * <p>Every family below asks {@link TunnelGrain} what the ground is like before it
 * rolls anything, and the grain is fixed per eight block cell. That single
 * dependency is what separates decoration from confetti: gravel gathers where the
 * ground is stony, roots cross where it is rooty, and a bare stretch stays bare
 * long enough to be the thing the others are noticed against. Correlation within
 * a family works the same way - moss grows under a pool of light because both ask
 * the same pool arithmetic, not because one of them looked at what the other had
 * already placed.</p>
 *
 * <h2>Why it probes instead of being told</h2>
 *
 * <p>A segment centre is all this gets, so the run direction, the floor, the
 * ceiling and the two walls are read out of the world. That costs a few hundred
 * block lookups per call and buys independence from how the carver works: a
 * sloping corridor, a bend or a hand-widened stretch all decorate correctly, and
 * a room is recognised and left alone. Recognised by its height, and only
 * seconded by its width - a junction is reliably taller than a corridor and only
 * usually broader, so the slice that cuts the narrow part of one used to be
 * dressed as a run. {@link #sweep} has the test and the coupling it rests on.</p>
 *
 * <h2>The rules that are not chances</h2>
 *
 * <p>The centre line of the run stays open at head height, always. Every other
 * placement is a dice roll, and a corridor that is walkable only on average is
 * one a player eventually has to dig through. It doubles as the probe line: the
 * column this class never touches is the column it can measure the corridor
 * from, visit after visit, without its own decoration moving the answer.</p>
 *
 * <p><b>A dressed slice, dressed again, measures identically.</b> That is not a
 * consequence of the rolls being stable - they can be perfectly stable and the
 * slice still drift, because the second pass measures a corridor the first pass
 * has already put things into. So every probe reads decoration as the corridor
 * space it stands in rather than as the wall it resembles: {@link #openRun} and
 * {@link #openEdge} across the width through {@link #isOpen}, {@link #ceilingOf}
 * overhead through {@link #isCeiling}, and {@link #walkLevel} through
 * {@link #isOpen} again. Whenever something new is placed into corridor space or
 * grown on the shell, the matching predicate has to learn about it in the same
 * edit. The failure is quiet and it compounds: the measurement moves a block, the
 * next pass dresses a slice that is one block off, and past
 * {@link #SLOPE_SEARCH} the sweep simply stops and leaves the rest of the run
 * bare.</p>
 *
 * <p>The middle block of every pool of light is lit without a roll. Darkness down
 * here is atmosphere and long unlit stretches are wanted, but "wanted" has to
 * have a ceiling on it: with the pool anchors held to the middle half of their
 * cells, one guaranteed lit block per cell puts a hard bound of
 * {@link #GLOW_SPACING} plus half of it between one light and the next. Without
 * it a small pool at a low density can roll itself out of existence and leave two
 * cells - forty blocks of corridor - with nothing to walk by.</p>
 *
 * <h2>Which numbers are dials and which are structure</h2>
 *
 * <p>Every spacing, radius, density and chance in the six families below is a
 * mutable static rather than a constant, so that {@code /moleverse burrow panel}
 * can move it with the corridor in view - see
 * {@code client.debug.BurrowTunePanel}. <b>The value written here is the shipped
 * one.</b> Nothing writes back into this file; a number settled at the slider is
 * baked in by editing it.</p>
 *
 * <p>The reach constants above them are not dials and stay final:
 * {@link #REACH}, {@link #MAX_SPAN}, {@link #MIN_SPAN}, {@link #SLOPE_SEARCH} and
 * {@link #PROBE_REACH} are how this class finds a corridor rather than how it
 * dresses one, and two of them are tied to numbers on the carver's side. So are
 * the salts, which decide nothing but keep two families from agreeing by
 * accident.</p>
 *
 * <p>Moving a dial changes nothing that has already been dressed. This class only
 * ever adds, so the panel's re-dress button hands the same corridor back with the
 * new roll's work laid on top of the old roll's - which is honest for anything
 * that grows denser and useless for anything that should have grown sparser. A
 * fresh stretch of corridor is the only place a lowered density can be read.</p>
 */
public final class TunnelDecorator {

    // --- Reach ---------------------------------------------------------------

    /** Blocks either side of the given centre that one call dresses. Overlap costs time and changes nothing, a gap leaves bare corridor - so err large. */
    private static final int REACH = 4;

    /**
     * Widest slice still treated as a corridor.
     *
     * <p>The second of the two tests a slice has to pass, and the weaker one: a
     * junction is only usually broader than a corridor, so this catches the middle
     * of one and misses the edges. The height test in {@link #sweep} is what
     * actually separates a run from a room. This is kept because it costs nothing
     * and it is the one that catches a chamber a player has widened by hand, which
     * is as tall as the corridor it grew out of.</p>
     */
    private static final int MAX_SPAN = BurrowGeometry.CORRIDOR_WIDTH + 4;

    /** Narrowest slice worth dressing. Below this there is no room beside the walkway to put anything into. */
    private static final int MIN_SPAN = 3;

    /** How far the floor may have moved between two neighbouring slices. Runs follow the ground, and ground slopes. */
    private static final int SLOPE_SEARCH = 2;

    /** How far the first probe looks along each axis to decide which way the run goes. Long enough that a corridor beats its own width. */
    private static final int PROBE_REACH = MAX_SPAN + 2;

    /** Clients see the change and nothing else reacts. A decoration must not pop its neighbours or set water walking. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // --- Light: the group that decides whether the burrow is navigable at all -

    /** One pool of light per this many blocks of run. Raise it for longer dark stretches, lower it for a lit corridor. */
    public static int GLOW_SPACING = 18;

    /** Smallest pool. One reads as a single lamp of threads, which is the loneliest thing down there and worth keeping. */
    public static int GLOW_RADIUS_MIN = 1;

    /** Largest ordinary pool. */
    public static int GLOW_RADIUS_MAX = 3;

    /** Chance a pool is a grand one instead: a ceiling of light you walk out under. Rare, because it only works as an arrival. */
    public static float GLOW_GRAND_CHANCE = 0.12F;

    /** Radius of a grand pool. Wide enough to span a corridor and keep going along it. */
    public static int GLOW_GRAND_RADIUS = 5;

    /** Thinnest a pool may be at its middle. Under about a half the patch stops reading as one thing. */
    public static float GLOW_DENSITY_MIN = 0.55F;

    /** Thickest an ordinary pool may be. Under one the patch keeps a ragged edge instead of a stamped disc. */
    public static float GLOW_DENSITY_MAX = 0.95F;

    /** Density of a grand pool. Near solid on purpose - a grand pool that is patchy is just a big untidy one. */
    public static float GLOW_GRAND_DENSITY = 0.9F;

    /** Chance a lit ceiling block trails vanilla roots beneath it. Threads should hang, not lie flat. */
    public static float GLOW_FRINGE_CHANCE = 0.45F;

    /** How far down the wall the light from a pool bleeds. Two rows: enough to round the corner of the ceiling, not enough to light the floor. */
    public static int GLOW_WALL_BLEED_DEPTH = 2;

    /** Chance a wall block under a pool carries threads too, scaled by how strongly the pool reaches it. */
    public static float GLOW_WALL_BLEED_CHANCE = 0.5F;

    /** Chance the floor under a pool is moss rather than whatever the stretch is made of. Light gathers growth; this is the cheapest way to say so. */
    public static float GLOW_UNDERFOOT_MOSS_CHANCE = 0.70F;

    // --- Roots: the landmarks you navigate by between the pools --------------

    /** One root crosses the run per this many blocks. Rarer than light on purpose, or they stop being landmarks. */
    public static int ROOT_SPACING = 9;

    /** Share of roots that stand floor to ceiling. The rest only hang, so most of them are ducked under rather than walked around. */
    public static float ROOT_STANDING_CHANCE = 0.4F;

    /** How far a hanging root reaches down from the ceiling. It always stops clear of head height regardless. */
    public static int ROOT_HANG_DEPTH = 2;

    /** Half width of a hanging root across the run. One gives a three block beam, more gives a curtain. */
    public static int ROOT_HANG_HALF_WIDTH = 1;

    /** Chance a root still crosses a stretch of bare ground. Not zero: bareness is a tendency, and a rule you can rely on is a rule you stop looking for. */
    public static float ROOT_IN_BARE_CHANCE = 0.35F;

    // --- Floor: stretches of character, never confetti -----------------------

    /** A stretch of one floor material per this many blocks. Short, because bare floor for twenty blocks reads as unfinished. */
    public static int FLOOR_SPACING = 6;

    /** Radius of a floor stretch, along the run and across it. */
    public static int FLOOR_RADIUS = 2;

    /** Chance at the middle of a stretch. The falloff to the edge is what keeps it from looking stencilled. */
    public static float FLOOR_DENSITY = 0.88F;

    /** Chance a gravel square also stands proud of the floor. At this scale one gravel block is a boulder to step over. */
    public static float BOULDER_CHANCE = 0.25F;

    /** Chance a moss square grows a carpet on top. Flat, so it never costs headroom. */
    public static float MOSS_CARPET_CHANCE = 0.7F;

    // --- The trodden line: what a run that gets used looks like --------------

    /** Length of one trodden stretch. Long enough that a path is a path rather than a rash of squares. */
    public static int PATH_RUN = 6;

    /** Share of stretches that read as trodden at all. Under a half, so a worn run still means something. */
    public static float PATH_CHANCE = 0.4F;

    /** How solidly a trodden stretch is packed. Short of one, so the path frays at its edges like a real one. */
    public static float PATH_DENSITY = 0.72F;

    /**
     * How far either side of the centre line the packing reaches.
     *
     * <p>The centre column itself is left to whatever the stretch gives it - it is
     * the probe line, and the one column this class promises never to make a
     * special case of. Two pressed flanks either side of loose middle is also what
     * a run actually looks like: the earth gets shouldered aside, not stamped
     * down.</p>
     */
    public static int PATH_HALF_WIDTH = 1;

    // --- Water: a ford, once in a long while ---------------------------------

    /** A seep per this many blocks of run. Rare enough that meeting one is an event rather than wet feet. */
    public static int PUDDLE_SPACING = 60;

    /** Radius of a seep, measured in city blocks. One gives a puddle of up to five squares - never a pool. */
    public static int PUDDLE_RADIUS = 1;

    /** How far past the water the soaked ground reaches. This is what you see before you see the water, and it is what makes a seep an approach rather than a surprise. */
    public static int SEEP_BANK_REACH = 2;

    /** Chance a square within that reach is soaked. High, because a bank with holes in it reads as an accident. */
    public static float SEEP_BANK_CHANCE = 0.75F;

    // --- Walls: texture up close, nothing you would walk towards -------------

    /**
     * Length of one band of wall that decides together.
     *
     * <p>Single blocks rolled on their own gave speckle you had to look for. A band
     * runs along the wall for four blocks at one height, which at mole scale is a
     * seam of root or a course of stone - the thing the eye actually follows when
     * it runs along a wall.</p>
     */
    public static int WALL_VEIN_RUN = 4;

    /** How solidly a band that fired is filled. Short of one so the seam thins out and picks up again. */
    public static float WALL_VEIN_DENSITY = 0.85F;

    /** Chance a wall block outside any band is something other than earth anyway. The old speckle, kept low, so the bands are not the only thing there. */
    public static float WALL_STRAY_CHANCE = 0.10F;

    /** How far a root stub may jut out of a wall. Two is a thing to squeeze past; more would block a narrow slice. */
    public static int WALL_STUB_REACH = 2;

    /** Chance a wall block in stony ground puts a mineral bud into the air beside it. Rare enough to stay a find rather than a gem cave. */
    public static float WALL_MINERAL_CHANCE = 0.06F;

    // --- Ceiling: what is overhead between the pools -------------------------

    /** Edge length of a ceiling cell that decides together. Same argument as the wall bands: pockets, not pepper. */
    public static int CEILING_POCKET_CELL = 4;

    /** How solidly a ceiling pocket is filled once its cell has fired. */
    public static float CEILING_GRIT_DENSITY = 0.65F;

    /** Chance an unlit rooty ceiling block trails roots instead. The dark stretches need something overhead too. */
    public static float CEILING_ROOT_CHANCE = 0.2F;

    // --- The fringe: what grows in the angles of a corridor ------------------

    /**
     * How many columns in from each wall count as the fringe.
     *
     * <p>One, so the fringe is the angle itself. Two would be a border down both
     * sides of every corridor, which is a carpet with a hole in it rather than
     * something growing where the damp collects.</p>
     */
    public static int FRINGE_WIDTH = 1;

    /**
     * Share of a fringe that is moss rather than fungus.
     *
     * <p>{@link TunnelGrain#fringeChance} gives one number for how much lives in
     * the angles here, and this splits it: below this share of it the corner grows
     * a carpet, above it a mushroom, past it nothing. One roll for both, so a
     * corner is never both and the two can never disagree about how lush the
     * stretch is. High, because a mushroom is a thing you notice and moss is a
     * thing you walk over - equal numbers of them would read as a mushroom farm.</p>
     */
    public static float FRINGE_MOSS_SHARE = 0.82F;

    /** Share of the mushrooms that are red rather than brown. Low: brown is the burrow's own colour, red is a find. */
    public static float FRINGE_RED_SHARE = 0.18F;

    /**
     * How much of a fringe reaches the ceiling angle as well as the floor one.
     *
     * <p>Root ends trail out of the crown of a wall for the same reason moss
     * gathers at its foot, so they read off the same dial - but less of it. A
     * corridor whose every edge drips is a jungle, and the burrow is soil.</p>
     */
    public static float FRINGE_CEILING_SHARE = 0.55F;

    // Salts. Distinct so that two decorations never agree by accident and land on
    // the same block. Grouped by family; TunnelGrain keeps its own prefix for the
    // material tables, so nothing here can collide with a choice of material.
    private static final long SALT_GLOW_ALONG = 0x51A9_C0DEL;
    private static final long SALT_GLOW_ACROSS = 0x51A9_C0DFL;
    private static final long SALT_GLOW_FILL = 0x51A9_C0E0L;
    private static final long SALT_GLOW_FRINGE = 0x51A9_C0E1L;
    private static final long SALT_GLOW_GRAND = 0x51A9_C0E2L;
    private static final long SALT_GLOW_SIZE = 0x51A9_C0E3L;
    private static final long SALT_GLOW_SPREAD = 0x51A9_C0E4L;
    private static final long SALT_GLOW_BLEED = 0x51A9_C0E5L;
    private static final long SALT_GLOW_MOSS = 0x51A9_C0E6L;
    private static final long SALT_ROOT_ALONG = 0x2007_1EAFL;
    private static final long SALT_ROOT_KIND = 0x2007_1EB0L;
    private static final long SALT_ROOT_SIDE = 0x2007_1EB1L;
    private static final long SALT_ROOT_BARE = 0x2007_1EB2L;
    private static final long SALT_FLOOR_ALONG = 0x0F10_0B00L;
    private static final long SALT_FLOOR_ACROSS = 0x0F10_0B01L;
    private static final long SALT_FLOOR_FILL = 0x0F10_0B03L;
    private static final long SALT_BOULDER = 0x0F10_0B04L;
    private static final long SALT_MOSS_CARPET = 0x0F10_0B05L;
    private static final long SALT_PATH_RUN = 0x0F10_0B06L;
    private static final long SALT_PATH_FILL = 0x0F10_0B07L;
    private static final long SALT_PUDDLE_ALONG = 0x5EE9_0000L;
    private static final long SALT_PUDDLE_ACROSS = 0x5EE9_0001L;
    private static final long SALT_SEEP_BANK = 0x5EE9_0002L;
    private static final long SALT_WALL = 0x3A11_0000L;
    private static final long SALT_MINERAL = 0x3A11_0002L;
    private static final long SALT_WALL_VEIN = 0x3A11_0003L;
    private static final long SALT_WALL_STUB = 0x3A11_0004L;
    private static final long SALT_WALL_STUB_REACH = 0x3A11_0005L;
    private static final long SALT_CEILING_POCKET = 0x0CE1_0000L;
    private static final long SALT_CEILING_GRIT = 0x0CE1_0001L;
    private static final long SALT_CEILING_FRINGE = 0x0CE1_0002L;
    private static final long SALT_FRINGE_FOOT = 0x0F81_0000L;
    private static final long SALT_FRINGE_KIND = 0x0F81_0001L;
    private static final long SALT_FRINGE_CROWN = 0x0F81_0002L;

    /** Returned by every probe that failed to find corridor where it expected some. */
    private static final int NO_LEVEL = Integer.MIN_VALUE;

    private TunnelDecorator() {
    }

    /**
     * Dresses the run through {@code corridorCentre} for {@link #REACH} blocks
     * either side of it.
     *
     * <p>Safe to call for overlapping segments and safe to call twice: every roll
     * is derived from the block position, so a second pass reaches the same
     * conclusions and finds its own work already done. It is also safe to call on
     * a position that turns out not to be corridor - it probes first and returns
     * without touching anything.</p>
     *
     * <p>The {@code random} parameter is not used; see the class javadoc for why
     * that is a decision rather than an oversight.</p>
     */
    public static void decorate(ServerLevel burrow, BlockPos corridorCentre, RandomSource random) {
        if (!burrow.isLoaded(corridorCentre)) {
            return;
        }
        int walkY = walkLevel(burrow, corridorCentre.getX(), corridorCentre.getY(), corridorCentre.getZ(),
                BurrowGeometry.CORRIDOR_HEIGHT);
        if (walkY == NO_LEVEL) {
            return;
        }
        Direction.Axis axis = runAxis(burrow, corridorCentre.getX(), walkY, corridorCentre.getZ());
        int t = axis == Direction.Axis.X ? corridorCentre.getX() : corridorCentre.getZ();
        int perp = axis == Direction.Axis.X ? corridorCentre.getZ() : corridorCentre.getX();

        // Outwards from the middle in both directions, so the slope and the centre
        // line are carried from a slice that is known good to its neighbour.
        sweep(burrow, axis, t, perp, walkY, 1);
        sweep(burrow, axis, t, perp, walkY, -1);
    }

    /**
     * Walks one direction along the run, dressing slice by slice.
     *
     * <p>Stops at the first slice that is not corridor rather than skipping it:
     * past a bend or a dead end the carried centre line and floor height mean
     * nothing, and guessing on with them is how decoration ends up inside a wall.
     * The segment on the far side of the bend gets its own call.</p>
     *
     * <p>A slice has to pass two tests to be a corridor, and the height one is the
     * reliable half. Width was the only test for a long time and it leaks: a
     * junction ring or the foot of a shaft bell is a room, but the slice a sweep
     * happens to cut through one is often no wider than a corridor, so it passed
     * {@link #MAX_SPAN} and got dressed. Height does not leak, because the carver
     * cuts every junction and every bell taller than a corridor is ever cut - see
     * {@code CorridorProfile.MAX_LIT_HEIGHT} - and {@link #ceilingOf} looks exactly
     * that far and no further. So a centre column with no ceiling in reach is a
     * room, and the whole slice is left to whoever furnishes rooms.</p>
     *
     * <p>The gate has to be here and not inside the families. Four of the six
     * already give a column up when {@link #ceilingOf} fails, so the visible half
     * of the bug was the two that do not: {@link #dressFloor} and {@link #dressSeep}
     * never look up, and a junction floor was being laid with corridor materials,
     * worn with a corridor's trodden line and occasionally flooded, all of it
     * fighting the floor the junction had laid for itself.</p>
     *
     * <p>It is a {@code return} rather than a {@code continue} for the same reason
     * the two tests above it are: the slice that is not a corridor is also the
     * slice whose centre line and floor height stop meaning anything, and carrying
     * them across a junction into the run on the far side is how the sweep ends up
     * dressing a wall.</p>
     */
    private static void sweep(ServerLevel burrow, Direction.Axis axis, int startT, int startPerp, int startY, int sign) {
        int perp = startPerp;
        int walkY = startY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = sign > 0 ? 0 : 1; i <= REACH; i++) {
            int t = startT + sign * i;
            if (!burrow.isLoaded(cursor.set(worldX(axis, t, perp), walkY, worldZ(axis, t, perp)))) {
                return;
            }
            int y = walkLevel(burrow, worldX(axis, t, perp), walkY, worldZ(axis, t, perp),
                    i == 0 ? 0 : SLOPE_SEARCH);
            if (y == NO_LEVEL) {
                return;
            }
            walkY = y;

            int min = openEdge(burrow, axis, t, perp, walkY, -1);
            int max = openEdge(burrow, axis, t, perp, walkY, 1);
            int span = max - min + 1;
            if (span < MIN_SPAN || span > MAX_SPAN) {
                return;
            }
            int centreLine = Math.floorDiv(min + max, 2);
            if (ceilingOf(burrow, worldX(axis, t, centreLine), walkY, worldZ(axis, t, centreLine)) == NO_LEVEL) {
                return;
            }

            dressFloor(burrow, axis, t, min, max, centreLine, walkY);
            dressSeep(burrow, axis, t, min, max, centreLine, walkY);
            dressCeiling(burrow, axis, t, min, max, centreLine, walkY);
            dressRoot(burrow, axis, t, min, max, centreLine, walkY);
            dressWalls(burrow, axis, t, min, max, centreLine, walkY);
            dressFringe(burrow, axis, t, min, max, centreLine, walkY);

            perp = centreLine;
        }
    }

    // --- The dressings -------------------------------------------------------

    /**
     * Lays the floor of one slice.
     *
     * <p>Three things want the same square and they are asked in the order they
     * would win an argument. The bank of a seep beats everything, because water
     * decides what the ground next to it is like. The trodden flanks of the walking
     * line beat the stretch, because a path is what happens to a floor rather than
     * a kind of floor. Whatever is left gets the material its stretch is made of.
     * All three are pure arithmetic on the position, so the order they are asked in
     * is fixed and a second pass repeats it exactly.</p>
     */
    private static void dressFloor(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int axisId = axisId(axis);
        int cell = Math.floorDiv(t, FLOOR_SPACING);
        int anchorT = TunnelNoise.anchorAlong(SALT_FLOOR_ALONG, cell, FLOOR_SPACING, axisId);
        int anchorPerp = centreLine + TunnelNoise.jitter(SALT_FLOOR_ACROSS, cell, axisId, 1);
        int floorY = walkY - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int perp = min; perp <= max; perp++) {
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            TunnelGrain grain = TunnelGrain.at(x, z);
            BlockState laid = null;

            if (seepDistance(axis, t, perp, centreLine) <= PUDDLE_RADIUS + SEEP_BANK_REACH
                    && TunnelNoise.at(SALT_SEEP_BANK, x, floorY, z) < SEEP_BANK_CHANCE) {
                laid = TunnelGrain.bankOf(x, floorY, z);
            }

            if (laid == null && isPathFlank(perp, centreLine) && trodden(x, floorY, z)) {
                laid = grain.pathOf(x, floorY, z);
            }

            if (laid == null) {
                float chance = TunnelNoise.patchChance(FLOOR_DENSITY, FLOOR_RADIUS, t - anchorT, perp - anchorPerp);
                if (chance > 0.0F && TunnelNoise.at(SALT_FLOOR_FILL, x, floorY, z) < chance) {
                    laid = glowChance(axis, t, perp, centreLine) > 0.0F
                            && TunnelNoise.at(SALT_GLOW_MOSS, x, floorY, z) < GLOW_UNDERFOOT_MOSS_CHANCE
                            ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : grain.floorOf(cell, axisId);
                }
            }

            // Nothing decided means nothing read: the whole point of settling this
            // with arithmetic first is that a slice of untouched floor costs no
            // block lookups at all.
            if (laid == null) {
                continue;
            }

            // A seep that is already sunk here stays. Paving it over and letting
            // dressSeep dig it out again would make the result depend on the order
            // the two ran in, which is the one thing all of this is built to avoid.
            // Unloaded counts as "leave it alone" rather than as "not water".
            // Reading a block across a chunk border would load - and in this
            // dimension generate - the chunk, which is how dressing a long run
            // turns into a freeze.
            if (!level.isLoaded(cursor.set(x, floorY, z))
                    || level.getBlockState(cursor).is(Blocks.WATER)) {
                continue;
            }
            if (!replaceShell(level, cursor, laid)) {
                continue;
            }

            // Both of these stand in corridor space, so both are refused the
            // centre column. The carpet costs no headroom and looks harmless
            // there, and it is not: the centre column at walking height is what
            // walkLevel measures the corridor from, and a flat block is just as
            // good at moving that measurement as a boulder is.
            if (blocksTheWay(perp, centreLine, walkY, walkY)) {
                continue;
            }
            if (laid.is(Blocks.GRAVEL)) {
                if (TunnelNoise.at(SALT_BOULDER, x, walkY, z) < BOULDER_CHANCE) {
                    fillAir(level, cursor.set(x, walkY, z), Blocks.GRAVEL.defaultBlockState());
                }
            } else if (laid.is(Blocks.MOSS_BLOCK) && TunnelNoise.at(SALT_MOSS_CARPET, x, walkY, z) < MOSS_CARPET_CHANCE) {
                fillAir(level, cursor.set(x, walkY, z), Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
    }

    /**
     * Sinks a seep into the floor.
     *
     * <p>The water replaces the floor square rather than sitting on it, so it is a
     * dip you wade through instead of a slab you climb. Every square is checked
     * for a solid rim first: a source that finds one way out floods the run behind
     * it, and there is no drainage down there to fix it afterwards.</p>
     *
     * <p>Only the water is placed here. The soaked ground around it is laid by
     * {@link #dressFloor} off the same anchor arithmetic, so the bank exists even
     * where the rim check refuses the water - which is right, because a seep that
     * has dried out should still look like one.</p>
     */
    private static void dressSeep(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int floorY = walkY - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // A dry lip against both walls: it keeps the rim solid and it keeps the
        // seep readable as a dip in the middle of the run rather than a wet wall.
        for (int perp = min + 1; perp <= max - 1; perp++) {
            if (seepDistance(axis, t, perp, centreLine) > PUDDLE_RADIUS) {
                continue;
            }
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            if (!holdsSeep(level, cursor, x, floorY, z)) {
                continue;
            }
            replaceShell(level, cursor.set(x, floorY, z), Blocks.WATER.defaultBlockState());
        }
    }

    /**
     * Grows the pools of light, and gives the ceiling between them something to be.
     *
     * <p>The threads replace the ceiling block instead of hanging in the air below
     * it: the light then sits in the ceiling plane, costs no headroom, and reads
     * as growth on a surface rather than as a lamp somebody hung.</p>
     *
     * <p>Pools differ in size and in how solidly they are filled, decided per cell,
     * with a rare grand one that spans the corridor. That is the whole of the
     * light staging: identical pools at a fixed interval turn a burrow into a
     * corridor of street lamps, and the walk between two of them stops being worth
     * anything. The middle block is exempt from the roll - see the class javadoc
     * for the bound that buys.</p>
     */
    private static void dressCeiling(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int axisId = axisId(axis);
        int cell = Math.floorDiv(t, GLOW_SPACING);
        int anchorT = TunnelNoise.anchorAlong(SALT_GLOW_ALONG, cell, GLOW_SPACING, axisId);
        int anchorPerp = glowAnchorPerp(cell, axisId, centreLine);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int perp = min; perp <= max; perp++) {
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            int ceilingY = ceilingOf(level, x, walkY, z);
            if (ceilingY == NO_LEVEL) {
                continue;
            }

            float chance = glowChance(axis, t, perp, centreLine);
            boolean heart = t == anchorT && perp == anchorPerp;
            if (heart || (chance > 0.0F && TunnelNoise.at(SALT_GLOW_FILL, x, ceilingY, z) < chance)) {
                if (!replaceShell(level, cursor.set(x, ceilingY, z), ModBlocks.GLOW_MYCELIUM.get().defaultBlockState())) {
                    continue;
                }
                int fringeY = ceilingY - 1;
                if (fringeY >= walkY + 2 && TunnelNoise.at(SALT_GLOW_FRINGE, x, fringeY, z) < GLOW_FRINGE_CHANCE) {
                    fillAir(level, cursor.set(x, fringeY, z), Blocks.HANGING_ROOTS.defaultBlockState());
                }
                continue;
            }

            // Between the pools. A dark stretch is atmosphere, but a dark stretch of
            // nothing is an unfinished one: the ceiling still has to have a texture
            // for the light at either end to graze.
            TunnelGrain grain = TunnelGrain.at(x, z);
            boolean pocket = TunnelNoise.at(SALT_CEILING_POCKET,
                    Math.floorDiv(x, CEILING_POCKET_CELL), ceilingY, Math.floorDiv(z, CEILING_POCKET_CELL))
                    < grain.ceilingGritChance();
            if (pocket && TunnelNoise.at(SALT_CEILING_GRIT, x, ceilingY, z) < CEILING_GRIT_DENSITY) {
                replaceShell(level, cursor.set(x, ceilingY, z), grain.ceilingGritOf(x, ceilingY, z, axis));
                continue;
            }
            int fringeY = ceilingY - 1;
            if (grain == TunnelGrain.ROOTY && fringeY >= walkY + 2
                    && TunnelNoise.at(SALT_CEILING_FRINGE, x, fringeY, z) < CEILING_ROOT_CHANCE) {
                fillAir(level, cursor.set(x, fringeY, z), Blocks.HANGING_ROOTS.defaultBlockState());
            }
        }
    }

    /**
     * Puts a root across the run.
     *
     * <p>Two kinds, because they do different jobs. A standing root is a single
     * column floor to ceiling that you walk around - it gives a corridor a near
     * side and a far side, which is what makes a straight run readable. A hanging
     * root is a beam across the width that stops well above head height and only
     * has to be ducked under with the camera.</p>
     *
     * <p>Bare ground mostly refuses one. That is the grain earning its keep: the
     * crossings gather where the ground is full of roots, and the stretches
     * between them are the ones where a corridor is just a corridor.</p>
     *
     * <p>One slice thick along the run, so this fires only on the exact anchor
     * block. That is fine while segments are dense enough that every block of a
     * run is inside somebody's {@link #REACH}; if the carver ever spaces its
     * segments further apart than that, roots start going missing before anything
     * else does.</p>
     */
    private static void dressRoot(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int cell = Math.floorDiv(t, ROOT_SPACING);
        int axisId = axisId(axis);
        if (t != TunnelNoise.anchorAlong(SALT_ROOT_ALONG, cell, ROOT_SPACING, axisId)) {
            return;
        }
        if (TunnelGrain.at(worldX(axis, t, centreLine), worldZ(axis, t, centreLine)) == TunnelGrain.BARE
                && TunnelNoise.at(SALT_ROOT_BARE, cell, axisId, 0) >= ROOT_IN_BARE_CHANCE) {
            return;
        }
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        if (TunnelNoise.at(SALT_ROOT_KIND, cell, axisId, 0) < ROOT_STANDING_CHANCE) {
            int perp = standingRootColumn(SALT_ROOT_SIDE, cell, axisId, min, max, centreLine);
            if (perp == NO_LEVEL) {
                return;
            }
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            int ceilingY = ceilingOf(level, x, walkY, z);
            if (ceilingY == NO_LEVEL) {
                return;
            }
            for (int y = walkY; y < ceilingY; y++) {
                fillAir(level, cursor.set(x, y, z), beam);
            }
            return;
        }

        for (int perp = centreLine - ROOT_HANG_HALF_WIDTH; perp <= centreLine + ROOT_HANG_HALF_WIDTH; perp++) {
            if (perp < min || perp > max) {
                continue;
            }
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            int ceilingY = ceilingOf(level, x, walkY, z);
            if (ceilingY == NO_LEVEL) {
                continue;
            }
            int lowest = Math.max(ceilingY - ROOT_HANG_DEPTH, walkY + 2);
            for (int y = ceilingY - 1; y >= lowest; y--) {
                fillAir(level, cursor.set(x, y, z), beam);
            }
            if (lowest - 1 >= walkY + 2) {
                fillAir(level, cursor.set(x, lowest - 1, z), Blocks.HANGING_ROOTS.defaultBlockState());
            }
        }
    }

    /**
     * Works up both walls of a slice.
     *
     * <p>Four things can happen to one wall block and they are asked in order.
     * Under a pool of light the threads carry on down the wall, which is what
     * rounds the corner between ceiling and wall and stops a pool looking like a
     * decal. Otherwise the block may belong to a band of something else - a seam of
     * root, a course of stone - and bands are why this reads at a glance where the
     * old single block speckle did not. Independently of both, a rooty wall may
     * push a root stub out into the corridor, and a stony one may grow a mineral
     * bud.</p>
     *
     * <p>Only raw earth has its material changed. Anything else in a wall is either
     * a decoration that already made its choice or a block somebody put there, and
     * neither wants overwriting. The two things that reach into corridor space are
     * asked anyway, because they only ever fill air and finding their own work
     * already done is how a second pass is supposed to end.</p>
     */
    private static void dressWalls(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        Direction.Axis across = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos open = new BlockPos.MutableBlockPos();

        for (int side = -1; side <= 1; side += 2) {
            int openPerp = side < 0 ? min : max;
            int wallPerp = openPerp + side;
            int wallX = worldX(axis, t, wallPerp);
            int wallZ = worldZ(axis, t, wallPerp);
            int ceilingY = ceilingOf(level, worldX(axis, t, openPerp), walkY, worldZ(axis, t, openPerp));
            if (ceilingY == NO_LEVEL) {
                continue;
            }
            // A bud points away from what holds it, so out of the wall into the run.
            Direction outwards = Direction.fromAxisAndDirection(across,
                    side < 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            TunnelGrain grain = TunnelGrain.at(wallX, wallZ);
            float glow = glowChance(axis, t, openPerp, centreLine);
            int veinX = Math.floorDiv(wallX, WALL_VEIN_RUN);
            int veinZ = Math.floorDiv(wallZ, WALL_VEIN_RUN);

            for (int y = walkY - 1; y < ceilingY; y++) {
                if (!level.isLoaded(wall.set(wallX, y, wallZ))) {
                    continue;
                }
                BlockState existing = level.getBlockState(wall);
                if (existing.isAir()) {
                    continue;
                }
                // Both linings count as untouched wall. The carver wraps every
                // cut surface in loose soil, so deep earth only begins a couple
                // of blocks further out and a wall this pass has never seen is
                // normally soil rather than earth.
                boolean raw = existing.is(ModBlocks.DEEP_EARTH.get()) || existing.is(ModBlocks.LOOSE_SOIL.get());

                if (raw && glow > 0.0F && y >= ceilingY - GLOW_WALL_BLEED_DEPTH
                        && TunnelNoise.at(SALT_GLOW_BLEED, wallX, y, wallZ) < glow * GLOW_WALL_BLEED_CHANCE) {
                    level.setBlock(wall, ModBlocks.GLOW_MYCELIUM.get().defaultBlockState(), PLACE_FLAGS);
                    continue;
                }

                if (raw) {
                    float chance = TunnelNoise.at(SALT_WALL_VEIN, veinX, y, veinZ) < grain.wallVeinChance()
                            ? WALL_VEIN_DENSITY
                            : WALL_STRAY_CHANCE;
                    if (TunnelNoise.at(SALT_WALL, wallX, y, wallZ) < chance) {
                        level.setBlock(wall, grain.wallOf(wallX, y, wallZ, axis), PLACE_FLAGS);
                    }
                }

                // From walking height upwards only. A stub level with the floor is
                // buried in it, and the roll would be spent on nothing.
                if (y >= walkY && TunnelNoise.at(SALT_WALL_STUB, wallX, y, wallZ) < grain.rootStubChance()) {
                    stub(level, open, axis, t, openPerp, side, min, max, centreLine, y, walkY,
                            TunnelNoise.intBetween(SALT_WALL_STUB_REACH, wallX, y, wallZ, 1, WALL_STUB_REACH));
                }

                if (grain == TunnelGrain.STONY
                        && TunnelNoise.at(SALT_MINERAL, wallX, y, wallZ) < WALL_MINERAL_CHANCE
                        && !blocksTheWay(openPerp, centreLine, y, walkY)) {
                    fillAir(level, open.set(worldX(axis, t, openPerp), y, worldZ(axis, t, openPerp)),
                            Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                                    .setValue(AmethystClusterBlock.FACING, outwards));
                }
            }
        }
    }

    /**
     * Pushes a root stub inwards from one wall at one height.
     *
     * <p>Stops at the first column it may not have rather than skipping it: a stub
     * with a gap in it is two stubs, and the second one is floating.</p>
     */
    private static void stub(ServerLevel level, BlockPos.MutableBlockPos cursor, Direction.Axis axis, int t,
                             int openPerp, int side, int min, int max, int centreLine, int y, int walkY, int reach) {
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        for (int i = 0; i < reach; i++) {
            int perp = openPerp - side * i;
            if (perp < min || perp > max || blocksTheWay(perp, centreLine, y, walkY)) {
                return;
            }
            fillAir(level, cursor.set(worldX(axis, t, perp), y, worldZ(axis, t, perp)), beam);
        }
    }

    /**
     * Grows the angles of a slice: where a wall meets the floor, and where it
     * meets the ceiling.
     *
     * <p>The rest of the class works a surface at a time - a floor, a ceiling, two
     * walls - and a corridor dressed only that way comes out as three flat planes
     * meeting at a hard line. The angles are where earth actually holds damp and
     * where a root that has been cut through hangs its ends, so they are the one
     * part of a corridor worth putting growth into, and they are also the part a
     * player walks closest to.</p>
     *
     * <p>Runs last of the six, and that is deliberate rather than incidental.
     * Everything it grows stands in corridor space, and a root stub or a mineral
     * bud from {@link #dressWalls} has the better claim on that block: a stub is
     * structure and moss is a surface. It fills air only, so the fringe takes what
     * is left and a second pass finds it already taken. The order the six run in is
     * fixed, so that outcome repeats exactly.</p>
     *
     * <p>The mushrooms are the one thing down here that keeps growing after this
     * class has finished with it - vanilla spreads them on a random tick, thinly,
     * and stops at five in a nine block box. That is allowed to happen and does not
     * break the measurement, because a mushroom is corridor space by
     * {@link #isOpen} wherever it ends up, including the centre line. It is also
     * the only kind of change the burrow makes on its own, which is worth having.</p>
     */
    private static void dressFringe(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int side = -1; side <= 1; side += 2) {
            for (int step = 0; step < FRINGE_WIDTH; step++) {
                int perp = side < 0 ? min + step : max - step;
                if (perp < min || perp > max) {
                    continue;
                }
                int x = worldX(axis, t, perp);
                int z = worldZ(axis, t, perp);
                float lush = TunnelGrain.at(x, z).fringeChance();

                if (!blocksTheWay(perp, centreLine, walkY, walkY)) {
                    // One roll, split: moss below the share, fungus above it,
                    // nothing past the dial. Two rolls would let a stretch come out
                    // mossy and mushroomless, or the other way about.
                    float foot = TunnelNoise.at(SALT_FRINGE_FOOT, x, walkY, z);
                    if (foot < lush * FRINGE_MOSS_SHARE) {
                        fillAir(level, cursor.set(x, walkY, z), Blocks.MOSS_CARPET.defaultBlockState());
                    } else if (foot < lush) {
                        fillAir(level, cursor.set(x, walkY, z), fungusAt(x, walkY, z));
                    }
                }

                int ceilingY = ceilingOf(level, x, walkY, z);
                if (ceilingY == NO_LEVEL) {
                    continue;
                }
                int crownY = ceilingY - 1;
                if (crownY >= walkY + 2
                        && TunnelNoise.at(SALT_FRINGE_CROWN, x, crownY, z) < lush * FRINGE_CEILING_SHARE) {
                    fillAir(level, cursor.set(x, crownY, z), Blocks.HANGING_ROOTS.defaultBlockState());
                }
            }
        }
    }

    /**
     * Which mushroom stands in this corner.
     *
     * <p>Both survive here without any help: vanilla asks for a solid block under
     * them and less than light level thirteen above, and the burrow's brightest
     * block is the mycelium at nine.</p>
     */
    private static BlockState fungusAt(int x, int y, int z) {
        return TunnelNoise.at(SALT_FRINGE_KIND, x, y, z) < FRINGE_RED_SHARE
                ? Blocks.RED_MUSHROOM.defaultBlockState()
                : Blocks.BROWN_MUSHROOM.defaultBlockState();
    }

    // --- Where the families agree with each other ----------------------------

    /**
     * How strongly the pool of light in this cell reaches this column.
     *
     * <p>Public to the rest of the class rather than private to
     * {@link #dressCeiling}, because three families want the same number: the
     * ceiling lights by it, the wall bleeds by it, and the floor grows moss by it.
     * Asking the same arithmetic is what lets them correlate without any of them
     * reading what another has already placed - which would make the result depend
     * on the order they ran in.</p>
     */
    private static float glowChance(Direction.Axis axis, int t, int perp, int centreLine) {
        int axisId = axisId(axis);
        int cell = Math.floorDiv(t, GLOW_SPACING);
        return TunnelNoise.patchChance(glowDensity(cell, axisId), glowRadius(cell, axisId),
                t - TunnelNoise.anchorAlong(SALT_GLOW_ALONG, cell, GLOW_SPACING, axisId),
                perp - glowAnchorPerp(cell, axisId, centreLine));
    }

    /** Which column the pool in this cell is centred on. */
    private static int glowAnchorPerp(int cell, int axisId, int centreLine) {
        return centreLine + TunnelNoise.jitter(SALT_GLOW_ACROSS, cell, axisId, 1);
    }

    /** Whether this cell holds one of the rare wide pools. */
    private static boolean glowIsGrand(int cell, int axisId) {
        return TunnelNoise.at(SALT_GLOW_GRAND, cell, axisId, 0) < GLOW_GRAND_CHANCE;
    }

    /** How far the pool in this cell reaches. */
    private static int glowRadius(int cell, int axisId) {
        return glowIsGrand(cell, axisId)
                ? GLOW_GRAND_RADIUS
                : TunnelNoise.intBetween(SALT_GLOW_SIZE, cell, axisId, 0, GLOW_RADIUS_MIN, GLOW_RADIUS_MAX);
    }

    /** How solidly the pool in this cell is filled. */
    private static float glowDensity(int cell, int axisId) {
        return glowIsGrand(cell, axisId)
                ? GLOW_GRAND_DENSITY
                : TunnelNoise.floatBetween(SALT_GLOW_SPREAD, cell, axisId, 0, GLOW_DENSITY_MIN, GLOW_DENSITY_MAX);
    }

    /**
     * How far this column is from the middle of the seep its cell holds, in city
     * blocks. The water and the soaked ground around it both measure from here, so
     * a bank is never left without its seep or a seep without its bank.
     */
    private static int seepDistance(Direction.Axis axis, int t, int perp, int centreLine) {
        int axisId = axisId(axis);
        int cell = Math.floorDiv(t, PUDDLE_SPACING);
        int anchorT = TunnelNoise.anchorAlong(SALT_PUDDLE_ALONG, cell, PUDDLE_SPACING, axisId);
        int anchorPerp = centreLine + TunnelNoise.jitter(SALT_PUDDLE_ACROSS, cell, axisId, 1);
        return Math.abs(t - anchorT) + Math.abs(perp - anchorPerp);
    }

    /** The columns either side of the walking line, and never the line itself. */
    private static boolean isPathFlank(int perp, int centreLine) {
        int offset = Math.abs(perp - centreLine);
        return offset >= 1 && offset <= PATH_HALF_WIDTH;
    }

    /**
     * Whether the walking line is worn here.
     *
     * <p>Keyed on cells of world coordinates rather than on distance along the run,
     * so a path stays where it is when a run bends and two runs that share a corner
     * are worn in the same place.</p>
     */
    private static boolean trodden(int x, int y, int z) {
        return TunnelNoise.at(SALT_PATH_RUN, Math.floorDiv(x, PATH_RUN), Math.floorDiv(z, PATH_RUN), 0) < PATH_CHANCE
                && TunnelNoise.at(SALT_PATH_FILL, x, y, z) < PATH_DENSITY;
    }

    // --- Probes --------------------------------------------------------------

    /**
     * Which way the run goes here, decided by which way it stays open longer.
     *
     * <p>A junction answers either way and either answer is right: both arms get
     * decorated, from whichever call is standing in them.</p>
     */
    private static Direction.Axis runAxis(ServerLevel level, int x, int walkY, int z) {
        int alongX = openRun(level, Direction.Axis.X, x, walkY, z);
        int alongZ = openRun(level, Direction.Axis.Z, x, walkY, z);
        return alongZ > alongX ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** How many blocks of corridor space this axis holds through the given position. */
    private static int openRun(ServerLevel level, Direction.Axis axis, int x, int walkY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int open = 1;
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int i = 1; i <= PROBE_REACH; i++) {
                int step = sign * i;
                cursor.set(x + (axis == Direction.Axis.X ? step : 0), walkY, z + (axis == Direction.Axis.Z ? step : 0));
                if (!level.isLoaded(cursor) || !isOpen(level.getBlockState(cursor))) {
                    break;
                }
                open++;
            }
        }
        return open;
    }

    /**
     * The block a walker's feet sit in at this column: corridor space with
     * something under it, searched outward from {@code aroundY} and downward
     * first.
     *
     * <p>Downward first because the given centre may be anywhere in the height of a
     * corridor, and the floor is the only surface all the measurements hang off.</p>
     *
     * <p>Corridor space rather than air, and that is the whole of the difference
     * between a corridor that can be dressed twice and one that climbs. A slice
     * whose walking height holds anything this class puts there - a moss carpet, a
     * boulder, a root, a bud - reads as solid to a bare air test, so the probe
     * walks up a block, the slice below it is measured as the floor, and the second
     * dressing lays a new floor over the first one's trimmings. Two or three passes
     * of that and the drift exceeds {@link #SLOPE_SEARCH}, at which point
     * {@link #sweep} gives up and the rest of the run is left bare with nothing in
     * the log to say why. With per-chunk dressing a corridor that crosses a chunk
     * border is dressed several times as a matter of course, so this is the normal
     * case and not an edge one.</p>
     *
     * <p>The block <em>underneath</em> is still tested for air and not for corridor
     * space, because a seep's floor is water: water is corridor space by
     * {@link #isOpen}, and asking for solid ground there would stop a ford reading
     * as a floor at all. That leaves the test relying on the promise that nothing
     * ever stands at walking height on this column - which is the centre line rule
     * from the class javadoc, and the reason the rule is worth its inflexibility.</p>
     */
    private static int walkLevel(ServerLevel level, int x, int aroundY, int z, int search) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= search; d++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int y = aroundY + d * sign;
                // Both reads guarded: this probe walks up and down past the
                // corridor and is the one place that reaches into a chunk the
                // carver never touched. An unguarded read there loads it.
                if (!level.isLoaded(cursor.set(x, y, z))) {
                    continue;
                }
                boolean open = isOpen(level.getBlockState(cursor));
                if (open && level.isLoaded(cursor.set(x, y - 1, z))
                        && !level.getBlockState(cursor).isAir()) {
                    return y;
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return NO_LEVEL;
    }

    /** The outermost column on one side that is still corridor space at walking height. */
    private static int openEdge(ServerLevel level, Direction.Axis axis, int t, int perpCentre, int walkY, int sign) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int edge = perpCentre;
        for (int i = 1; i <= MAX_SPAN; i++) {
            int perp = perpCentre + sign * i;
            cursor.set(worldX(axis, t, perp), walkY, worldZ(axis, t, perp));
            if (!level.isLoaded(cursor) || !isOpen(level.getBlockState(cursor))) {
                break;
            }
            edge = perp;
        }
        return edge;
    }

    /**
     * The block that caps this column.
     *
     * <p>It looks for a lining the carver left or for something this class grew on
     * one, rather than for the first thing that is not air. Otherwise a ceiling
     * that has been decorated once measures a block higher the next time and the
     * decoration creeps upward with every visit. Anything else overhead means
     * somebody has built up there, and then this column gets nothing.</p>
     *
     * <p><b>The reach is a shared constant with the carver, not a local choice.</b>
     * It searches {@link BurrowGeometry#CORRIDOR_HEIGHT} + 1 above the walking
     * surface, and {@code CorridorProfile.MAX_LIT_HEIGHT} is the same number on the
     * other side: the carver caps every corridor at it and cuts every junction and
     * shaft bell above it. That is what makes "no ceiling in reach" mean "this is a
     * room" in {@link #sweep} rather than merely "this is tall". The two have to
     * move together. Raise this one alone and the sweep starts dressing junction
     * floors again; raise the carver's alone and whole corridors go dark, roots and
     * wall texture with them, and nothing in the log says why.</p>
     */
    private static int ceilingOf(ServerLevel level, int x, int walkY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = walkY + 1; y <= walkY + BurrowGeometry.CORRIDOR_HEIGHT + 1; y++) {
            if (!level.isLoaded(cursor.set(x, y, z))) {
                return NO_LEVEL;
            }
            BlockState state = level.getBlockState(cursor);
            if (isCeiling(state)) {
                return y;
            }
            if (!isOpen(state)) {
                return NO_LEVEL;
            }
        }
        return NO_LEVEL;
    }

    /**
     * Whether a seep may go here: solid underneath and on all four sides.
     *
     * <p>Water already placed counts as a side, so the squares of one puddle hold
     * each other in. The rim beyond them is untouched floor either way.</p>
     */
    private static boolean holdsSeep(ServerLevel level, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        if (!level.isLoaded(cursor.set(x, y - 1, z)) || level.getBlockState(cursor).isAir()) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            cursor.set(x + side.getStepX(), y, z + side.getStepZ());
            if (!level.isLoaded(cursor) || level.getBlockState(cursor).isAir()) {
                return false;
            }
        }
        return true;
    }

    // --- Placement -----------------------------------------------------------

    /**
     * Replaces a block of the corridor shell - floor, ceiling or wall.
     *
     * <p>Never air: a hole in the shell is one a player dug, and dropping soil back
     * into it would undo their work. Returns whether it went in, so a caller can
     * skip the trimming that only makes sense on top of it.</p>
     */
    private static boolean replaceShell(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || !ours(existing)) {
            return false;
        }
        if (existing != state) {
            level.setBlock(pos, state, PLACE_FLAGS);
        }
        return true;
    }

    /**
     * Fills corridor space.
     *
     * <p>Air only, so a second visit never stacks a decoration onto the one it put
     * there the first time.</p>
     */
    private static void fillAir(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, PLACE_FLAGS);
        }
    }

    /**
     * The rule that is not a chance: the centre column of the run stays clear at
     * foot and head height, so a corridor is walkable end to end no matter how the
     * dice fell.
     */
    private static boolean blocksTheWay(int perp, int centreLine, int y, int walkY) {
        return perp == centreLine && y >= walkY && y <= walkY + 1;
    }

    /**
     * Corridor space: air, or something of ours that stands in it without closing
     * it off.
     *
     * <p>Decorations have to count as open or the corridor measures narrower every
     * time it is dressed, and it would shrink itself out of existence. The same
     * list decides where the floor is, not only where the walls are: to
     * {@link #walkLevel} a block that is open is a block a walker stands in, so
     * anything missing here is read as ground and lifts the whole slice by one.</p>
     *
     * <p>Nothing laid into the shell belongs on this list - mud, packed mud, tuff,
     * andesite and the rest are floor, wall and ceiling materials, and calling them
     * open would widen every corridor they line.</p>
     *
     * <p>The two mushrooms are the only entries that can appear where this class
     * did not put them: vanilla spreads them a block or two on a random tick. That
     * is the whole reason they have to be here rather than only in the fringe's own
     * head - a spread mushroom landing on the centre line at walking height would
     * otherwise read as ground, lift the slice by one, and start the drift the
     * class javadoc describes, days after anybody last dressed the corridor.</p>
     *
     * <p>{@code ROOT_BEAM} being open - and not a ceiling - is load-bearing for
     * the shafts: the deck a level shaft cuts through is spanned by beams, and the
     * junction gate in {@link #sweep} reads a shaft as a junction only because the
     * ceiling probe passes straight through them. Move the beam into
     * {@link #isCeiling} and every shaft measures as a corridor with a very low
     * roof - and gets dressed as one.</p>
     */
    private static boolean isOpen(BlockState state) {
        return state.isAir()
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.WATER)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(ModBlocks.ROOT_BEAM.get());
    }

    /**
     * What counts as the roof of a corridor.
     *
     * <p>Both linings the carver leaves - loose soil over deep earth - plus
     * everything the ceiling can be dressed with. Every material
     * {@code TunnelGrain.ceilingGritOf} can return has to appear here, or a
     * decorated ceiling stops being recognised on the next visit and the whole
     * measurement climbs a block.</p>
     *
     * <p>The soil is the load-bearing entry and it fails in the worst direction.
     * A ceiling this list does not know is not a ceiling that measures a block
     * off - it is no ceiling at all, and {@link #ceilingOf} answers
     * {@link #NO_LEVEL} for every column of every corridor. Light, roots, ceiling
     * grit and both walls are gated on that answer, so leaving it out puts the
     * entire burrow in the dark with nothing in the log to say why.</p>
     *
     * <p>The nodule is the same failure at a hundredth of the rate, which made it
     * worse rather than better. It is lining like the soil is - the carver writes
     * one into about one block of shell in a hundred and fifty - so a corridor gets
     * one in its ceiling every so often, and until it was listed here that column
     * answered "no ceiling". Once the junction gate in {@link #sweep} started
     * reading that answer as "this is a room", a single nodule over the centre line
     * ended the sweep and left the rest of the run bare. Which runs went bare
     * depended on which chunk was dressed first, which is what the equality test
     * saw. Anything the carver can leave in a cut surface belongs on this list, and
     * the rarer it is the longer it hides.</p>
     */
    private static boolean isCeiling(BlockState state) {
        return state.is(ModBlocks.DEEP_EARTH.get())
                || state.is(ModBlocks.LOOSE_SOIL.get())
                || state.is(ModBlocks.ROOT_NODULE.get())
                || state.is(ModBlocks.GLOW_MYCELIUM.get())
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE);
    }

    /**
     * Whether this block is one nothing was built out of.
     *
     * <p>Raw earth, air, and the palette below - a player will build down here, and
     * none of this may write over what they left. Loose soil is the one grey area,
     * and it is a wide one now that the carver lines every cut surface with it: it
     * is what the whole burrow is made of and a block a player can carry, so
     * anything they lay in soil can be re-dressed. That is the price of the lining
     * being the mod's own material, and every vanilla block added to the palette
     * widens it a little further - which is the reason to keep the palette to
     * things nobody would choose to build with down here.</p>
     *
     * <p><b>The root nodule is deliberately absent, and it is the one entry whose
     * absence is a feature.</b> It is lining, so {@link #isCeiling} has to know it;
     * it is loot, so this list must not. Every write into the shell goes through
     * {@link #replaceShell}, which refuses anything this method does not claim, so
     * leaving the nodule off is what stops a floor stretch, a trodden path, a seep
     * bank or a ceiling pocket from quietly paving a pocket a player was meant to
     * find. Adding it here would destroy loot at a rate nobody would ever trace
     * back to a decoration pass.</p>
     *
     * <p>The one thing that costs: a nodule sitting exactly where a pool of light
     * has its heart refuses the mycelium, and that pool loses the one lit block the
     * class javadoc promises without a roll. The rest of the pool still rolls, and
     * a nodule lands on a given block about once in a hundred and fifty, so the
     * hard bound on a dark stretch becomes a very nearly hard one. That is the
     * right way round: a lamp is recoverable and a destroyed pocket is not.</p>
     */
    private static boolean ours(BlockState state) {
        return state.isAir()
                || state.is(ModBlocks.DEEP_EARTH.get())
                || state.is(ModBlocks.LOOSE_SOIL.get())
                || state.is(ModBlocks.ROOT_BEAM.get())
                || state.is(ModBlocks.GLOW_MYCELIUM.get())
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.WATER)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.PACKED_MUD)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.SMALL_AMETHYST_BUD);
    }

    // --- Patches -------------------------------------------------------------

    /**
     * Which column a standing root takes: one or two off the centre line, on the
     * side that has the room. Never the centre line itself, and {@link #NO_LEVEL}
     * when the slice is too narrow to give up a column at all.
     */
    private static int standingRootColumn(long salt, int cell, int axisId, int min, int max, int centreLine) {
        int side = TunnelNoise.at(salt, cell, axisId, 0) < 0.5F ? -1 : 1;
        int offset = 1 + (int) (TunnelNoise.at(salt, cell, axisId, 2) * 2.0F);
        int perp = centreLine + side * offset;
        if (perp < min || perp > max) {
            perp = centreLine - side * offset;
        }
        if (perp < min || perp > max || perp == centreLine) {
            return NO_LEVEL;
        }
        return perp;
    }

    // --- Run relative coordinates --------------------------------------------

    /** {@code t} runs along the corridor, {@code perp} across it. This puts the pair back into the world. */
    private static int worldX(Direction.Axis axis, int t, int perp) {
        return axis == Direction.Axis.X ? t : perp;
    }

    private static int worldZ(Direction.Axis axis, int t, int perp) {
        return axis == Direction.Axis.X ? perp : t;
    }

    /** Part of every anchor seed, so the two axes do not put their patches at matching coordinates. */
    private static int axisId(Direction.Axis axis) {
        return axis == Direction.Axis.X ? 0 : 1;
    }
}

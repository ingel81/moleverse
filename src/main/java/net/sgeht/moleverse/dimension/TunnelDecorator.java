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
 * <h2>Why every decision comes from the position</h2>
 *
 * <p>Nothing below draws from the {@code random} handed in. Every roll is a hash
 * of the block position, so a stretch of corridor keeps its character no matter
 * how often it is walked, re-carved or reloaded - a corridor that grows a new
 * root each visit is not a place, it is a screensaver. It also makes the whole
 * class idempotent: {@link #decorate} may be called for overlapping segments, in
 * any order, and the corridor comes out the same. That is what lets
 * {@link #REACH} be generous without the decoration piling up.</p>
 *
 * <p>The {@code random} parameter is kept because the carver has one to hand and
 * the day may come when something genuinely wants to differ per visit. Today it
 * is deliberately unused, and that is the reason.</p>
 *
 * <h2>Why it probes instead of being told</h2>
 *
 * <p>A segment centre is all this gets, so the run direction, the floor, the
 * ceiling and the two walls are read out of the world. That costs a few hundred
 * block lookups per call and buys independence from how the carver works: a
 * sloping corridor, a bend or a hand-widened stretch all decorate correctly, and
 * a chamber is recognised by its width and left alone.</p>
 *
 * <h2>The one rule that is not a chance</h2>
 *
 * <p>The centre line of the run stays open at head height, always. Every other
 * placement is a dice roll, and a corridor that is walkable only on average is
 * one a player eventually has to dig through. It doubles as the probe line: the
 * column this class never touches is the column it can measure the corridor
 * from, visit after visit, without its own decoration moving the answer.</p>
 */
public final class TunnelDecorator {

    // --- Reach ---------------------------------------------------------------

    /** Blocks either side of the given centre that one call dresses. Overlap costs time and changes nothing, a gap leaves bare corridor - so err large. */
    private static final int REACH = 4;

    /** Widest slice still treated as a corridor. Broader than this is a chamber or a junction, and those are somebody else's decoration. */
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
    private static final int GLOW_SPACING = 18;

    /** Radius of a pool on the ceiling. Two reads as a patch of threads, one reads as a lamp. */
    private static final int GLOW_RADIUS = 2;

    /** Chance at the middle of a pool. Under one the patch gets a ragged edge instead of a stamped disc. */
    private static final float GLOW_DENSITY = 0.85F;

    /** Chance a lit ceiling block trails vanilla roots beneath it. Threads should hang, not lie flat. */
    private static final float GLOW_FRINGE_CHANCE = 0.3F;

    // --- Roots: the landmarks you navigate by between the pools --------------

    /** One root crosses the run per this many blocks. Rarer than light on purpose, or they stop being landmarks. */
    private static final int ROOT_SPACING = 13;

    /** Share of roots that stand floor to ceiling. The rest only hang, so most of them are ducked under rather than walked around. */
    private static final float ROOT_STANDING_CHANCE = 0.4F;

    /** How far a hanging root reaches down from the ceiling. It always stops clear of head height regardless. */
    private static final int ROOT_HANG_DEPTH = 2;

    /** Half width of a hanging root across the run. One gives a three block beam, more gives a curtain. */
    private static final int ROOT_HANG_HALF_WIDTH = 1;

    // --- Floor: stretches of character, never confetti -----------------------

    /** A stretch of one floor material per this many blocks. Short, because bare floor for twenty blocks reads as unfinished. */
    private static final int FLOOR_SPACING = 7;

    /** Radius of a floor stretch, along the run and across it. */
    private static final int FLOOR_RADIUS = 2;

    /** Chance at the middle of a stretch. The falloff to the edge is what keeps it from looking stencilled. */
    private static final float FLOOR_DENSITY = 0.8F;

    /** Chance a gravel square also stands proud of the floor. At this scale one gravel block is a boulder to step over. */
    private static final float BOULDER_CHANCE = 0.25F;

    /** Chance a moss square grows a carpet on top. Flat, so it never costs headroom. */
    private static final float MOSS_CARPET_CHANCE = 0.5F;

    // --- Water: a ford, once in a long while ---------------------------------

    /** A seep per this many blocks of run. Rare enough that meeting one is an event rather than wet feet. */
    private static final int PUDDLE_SPACING = 60;

    /** Radius of a seep, measured in city blocks. One gives a puddle of up to five squares - never a pool. */
    private static final int PUDDLE_RADIUS = 1;

    // --- Walls: texture up close, nothing you would walk towards -------------

    /** Chance a wall block is something other than plain earth. Speckle, not pattern. */
    private static final float WALL_SPECKLE_CHANCE = 0.09F;

    /** Chance a speckle also puts a mineral bud into the air beside it. Rare enough to stay a find rather than a gem cave. */
    private static final float WALL_MINERAL_CHANCE = 0.05F;

    // Salts. Distinct so that two decorations never agree by accident and land on the same block.
    private static final long SALT_GLOW_ALONG = 0x51A9_C0DEL;
    private static final long SALT_GLOW_ACROSS = 0x51A9_C0DFL;
    private static final long SALT_GLOW_FILL = 0x51A9_C0E0L;
    private static final long SALT_GLOW_FRINGE = 0x51A9_C0E1L;
    private static final long SALT_ROOT_ALONG = 0x2007_1EAFL;
    private static final long SALT_ROOT_KIND = 0x2007_1EB0L;
    private static final long SALT_ROOT_SIDE = 0x2007_1EB1L;
    private static final long SALT_FLOOR_ALONG = 0x0F10_0B00L;
    private static final long SALT_FLOOR_ACROSS = 0x0F10_0B01L;
    private static final long SALT_FLOOR_KIND = 0x0F10_0B02L;
    private static final long SALT_FLOOR_FILL = 0x0F10_0B03L;
    private static final long SALT_BOULDER = 0x0F10_0B04L;
    private static final long SALT_MOSS_CARPET = 0x0F10_0B05L;
    private static final long SALT_PUDDLE_ALONG = 0x5EE9_0000L;
    private static final long SALT_PUDDLE_ACROSS = 0x5EE9_0001L;
    private static final long SALT_WALL = 0x3A11_0000L;
    private static final long SALT_WALL_KIND = 0x3A11_0001L;
    private static final long SALT_MINERAL = 0x3A11_0002L;

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

            dressFloor(burrow, axis, t, min, max, centreLine, walkY);
            dressSeep(burrow, axis, t, min, max, centreLine, walkY);
            dressCeiling(burrow, axis, t, min, max, centreLine, walkY);
            dressRoot(burrow, axis, t, min, max, centreLine, walkY);
            dressWalls(burrow, axis, t, min, max, centreLine, walkY);

            perp = centreLine;
        }
    }

    // --- The five dressings --------------------------------------------------

    /**
     * Lays a stretch of one material over the floor.
     *
     * <p>One material per cell rather than per block: a length of corridor that is
     * gravel and then coarse dirt and then soil reads as a place that changes,
     * whereas the same blocks shuffled per square read as noise.</p>
     */
    private static void dressFloor(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int cell = Math.floorDiv(t, FLOOR_SPACING);
        int axisId = axisId(axis);
        int anchorT = anchorAlong(SALT_FLOOR_ALONG, cell, FLOOR_SPACING, axisId);
        int anchorPerp = centreLine + jitter(SALT_FLOOR_ACROSS, cell, axisId, 1);
        Block material = floorMaterial(cell, axisId);
        int floorY = walkY - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int perp = min; perp <= max; perp++) {
            float chance = patchChance(FLOOR_DENSITY, FLOOR_RADIUS, t - anchorT, perp - anchorPerp);
            if (chance <= 0.0F) {
                continue;
            }
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            if (noise(SALT_FLOOR_FILL, x, floorY, z) >= chance) {
                continue;
            }
            // A seep that is already sunk here stays. Paving it over and letting
            // dressSeep dig it out again would make the result depend on the order
            // the two ran in, which is the one thing all of this is built to avoid.
            if (level.getBlockState(cursor.set(x, floorY, z)).is(Blocks.WATER)) {
                continue;
            }
            if (!replaceShell(level, cursor, material.defaultBlockState())) {
                continue;
            }
            if (material == Blocks.GRAVEL) {
                if (!blocksTheWay(perp, centreLine, walkY, walkY) && noise(SALT_BOULDER, x, walkY, z) < BOULDER_CHANCE) {
                    fillAir(level, cursor.set(x, walkY, z), Blocks.GRAVEL.defaultBlockState());
                }
            } else if (material == Blocks.MOSS_BLOCK && noise(SALT_MOSS_CARPET, x, walkY, z) < MOSS_CARPET_CHANCE) {
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
     */
    private static void dressSeep(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int cell = Math.floorDiv(t, PUDDLE_SPACING);
        int axisId = axisId(axis);
        int anchorT = anchorAlong(SALT_PUDDLE_ALONG, cell, PUDDLE_SPACING, axisId);
        int alongOffset = Math.abs(t - anchorT);
        if (alongOffset > PUDDLE_RADIUS) {
            return;
        }
        int anchorPerp = centreLine + jitter(SALT_PUDDLE_ACROSS, cell, axisId, 1);
        int floorY = walkY - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // A dry lip against both walls: it keeps the rim solid and it keeps the
        // seep readable as a dip in the middle of the run rather than a wet wall.
        for (int perp = min + 1; perp <= max - 1; perp++) {
            if (alongOffset + Math.abs(perp - anchorPerp) > PUDDLE_RADIUS) {
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
     * Grows the pools of light.
     *
     * <p>The threads replace the ceiling block instead of hanging in the air below
     * it: the light then sits in the ceiling plane, costs no headroom, and reads
     * as growth on a surface rather than as a lamp somebody hung.</p>
     */
    private static void dressCeiling(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int cell = Math.floorDiv(t, GLOW_SPACING);
        int axisId = axisId(axis);
        int anchorT = anchorAlong(SALT_GLOW_ALONG, cell, GLOW_SPACING, axisId);
        int anchorPerp = centreLine + jitter(SALT_GLOW_ACROSS, cell, axisId, 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int perp = min; perp <= max; perp++) {
            float chance = patchChance(GLOW_DENSITY, GLOW_RADIUS, t - anchorT, perp - anchorPerp);
            if (chance <= 0.0F) {
                continue;
            }
            int x = worldX(axis, t, perp);
            int z = worldZ(axis, t, perp);
            int ceilingY = ceilingOf(level, x, walkY, z);
            if (ceilingY == NO_LEVEL) {
                continue;
            }
            if (noise(SALT_GLOW_FILL, x, ceilingY, z) >= chance) {
                continue;
            }
            if (!replaceShell(level, cursor.set(x, ceilingY, z), ModBlocks.GLOW_MYCELIUM.get().defaultBlockState())) {
                continue;
            }
            int fringeY = ceilingY - 1;
            if (fringeY >= walkY + 2 && noise(SALT_GLOW_FRINGE, x, fringeY, z) < GLOW_FRINGE_CHANCE) {
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
     * <p>One slice thick along the run, so this fires only on the exact anchor
     * block. That is fine while segments are dense enough that every block of a
     * run is inside somebody's {@link #REACH}; if the carver ever spaces its
     * segments further apart than that, roots start going missing before anything
     * else does.</p>
     */
    private static void dressRoot(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        int cell = Math.floorDiv(t, ROOT_SPACING);
        int axisId = axisId(axis);
        if (t != anchorAlong(SALT_ROOT_ALONG, cell, ROOT_SPACING, axisId)) {
            return;
        }
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        if (noise(SALT_ROOT_KIND, cell, axisId, 0) < ROOT_STANDING_CHANCE) {
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
     * Speckles the two walls, and now and then puts a mineral bud against one.
     *
     * <p>Only raw earth is speckled. Anything else in a wall is either a decoration
     * that already made its choice or a block somebody put there, and neither
     * wants overwriting.</p>
     */
    private static void dressWalls(ServerLevel level, Direction.Axis axis, int t, int min, int max, int centreLine, int walkY) {
        Direction.Axis across = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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

            for (int y = walkY - 1; y < ceilingY; y++) {
                if (!level.isLoaded(cursor.set(wallX, y, wallZ))) {
                    continue;
                }
                if (!level.getBlockState(cursor).is(ModBlocks.DEEP_EARTH.get())) {
                    continue;
                }
                if (noise(SALT_WALL, wallX, y, wallZ) >= WALL_SPECKLE_CHANCE) {
                    continue;
                }
                level.setBlock(cursor, wallMaterial(wallX, y, wallZ).defaultBlockState(), PLACE_FLAGS);

                if (noise(SALT_MINERAL, wallX, y, wallZ) < WALL_MINERAL_CHANCE
                        && !blocksTheWay(openPerp, centreLine, y, walkY)) {
                    fillAir(level, cursor.set(worldX(axis, t, openPerp), y, worldZ(axis, t, openPerp)),
                            Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                                    .setValue(AmethystClusterBlock.FACING, outwards));
                }
            }
        }
    }

    // --- Material tables -----------------------------------------------------

    /**
     * What a stretch of floor is made of.
     *
     * <p>Weighted by hand rather than picked evenly: soft soil is the burrow's own
     * material and has to stay the thing a corridor is normally made of, or none
     * of the rest reads as a change of pace.</p>
     */
    private static Block floorMaterial(int cell, int axisId) {
        int roll = (int) (noise(SALT_FLOOR_KIND, cell, axisId, 0) * 100.0F);
        if (roll < 42) {
            return ModBlocks.LOOSE_SOIL.get();
        }
        if (roll < 62) {
            return Blocks.COARSE_DIRT;
        }
        if (roll < 77) {
            return Blocks.ROOTED_DIRT;
        }
        if (roll < 90) {
            return Blocks.GRAVEL;
        }
        if (roll < 96) {
            return Blocks.CLAY;
        }
        return Blocks.MOSS_BLOCK;
    }

    /** What a speckle in a wall is. All full blocks, so a bud can hang off any of them. */
    private static Block wallMaterial(int x, int y, int z) {
        int roll = (int) (noise(SALT_WALL_KIND, x, y, z) * 100.0F);
        if (roll < 45) {
            return Blocks.ROOTED_DIRT;
        }
        if (roll < 75) {
            return Blocks.COARSE_DIRT;
        }
        if (roll < 92) {
            return Blocks.CLAY;
        }
        return Blocks.MOSS_BLOCK;
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
     * The block a walker's feet sit in at this column: air with something under it,
     * searched outward from {@code aroundY} and downward first.
     *
     * <p>Downward first because the given centre may be anywhere in the height of a
     * corridor, and the floor is the only surface all the measurements hang off.</p>
     */
    private static int walkLevel(ServerLevel level, int x, int aroundY, int z, int search) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= search; d++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int y = aroundY + d * sign;
                if (level.getBlockState(cursor.set(x, y, z)).isAir()
                        && !level.getBlockState(cursor.set(x, y - 1, z)).isAir()) {
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
     * <p>It looks for raw earth or for threads already grown on it, rather than for
     * the first thing that is not air. Otherwise a ceiling that has been decorated
     * once measures a block higher the next time and the decoration creeps upward
     * with every visit. Anything else overhead means somebody has built up there,
     * and then this column gets nothing.</p>
     */
    private static int ceilingOf(ServerLevel level, int x, int walkY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = walkY + 1; y <= walkY + BurrowGeometry.CORRIDOR_HEIGHT + 1; y++) {
            if (!level.isLoaded(cursor.set(x, y, z))) {
                return NO_LEVEL;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.is(ModBlocks.DEEP_EARTH.get()) || state.is(ModBlocks.GLOW_MYCELIUM.get())) {
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
     * time it is dressed, and it would shrink itself out of existence.</p>
     */
    private static boolean isOpen(BlockState state) {
        return state.isAir()
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.WATER)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(ModBlocks.ROOT_BEAM.get());
    }

    /**
     * Whether this block is one nothing was built out of.
     *
     * <p>Raw earth, air, and the palette below - a player will build down here, and
     * none of this may write over what they left. Loose soil is the one grey area:
     * it is both what a corridor floor is made of and a block a player can carry, so
     * a floor they laid themselves can be re-dressed. That is the price of using the
     * mod's own soil as the default floor.</p>
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
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.WATER)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SMALL_AMETHYST_BUD);
    }

    // --- Patches -------------------------------------------------------------

    /**
     * How likely a patch still reaches this far from its middle. Zero past the
     * radius, so a patch has an edge; falling off inside it, so the edge is ragged
     * rather than a circle somebody stamped.
     */
    private static float patchChance(float density, int radius, int alongOffset, int acrossOffset) {
        int distanceSquared = alongOffset * alongOffset + acrossOffset * acrossOffset;
        int limit = (radius + 1) * (radius + 1);
        return distanceSquared >= limit ? 0.0F : density * (1.0F - (float) distanceSquared / limit);
    }

    /**
     * Where in this cell along the run the patch sits.
     *
     * <p>Cells rather than a per block chance, because a pool of light has to be a
     * pool: scattering the same number of lit blocks evenly gives an evenly lit
     * corridor, which is the one thing the burrow must not be. The cell index is
     * the only input, so two runs crossing the same cell agree on where their
     * patches go - which nobody can see through solid earth, and which keeps the
     * answer stable when the run drifts sideways.</p>
     *
     * <p>The anchor is kept to the middle half of its cell rather than allowed
     * anywhere in it. Over a full cell two neighbouring anchors can land back to
     * back or a whole cell apart, which turns two pools of light into one blob and
     * then leaves a stretch twice the intended length unlit. Halving the play
     * halves the spread at both ends and costs nothing that anyone can see.</p>
     */
    private static int anchorAlong(long salt, int cell, int spacing, int axisId) {
        int play = Math.max(1, spacing / 2);
        return cell * spacing + (spacing - play) / 2 + (int) (noise(salt, cell, axisId, 0) * play);
    }

    /** A block or two off the centre line, so that nothing lines up down the middle of a run. */
    private static int jitter(long salt, int cell, int axisId, int amount) {
        return (int) (noise(salt, cell, axisId, 1) * (amount * 2 + 1)) - amount;
    }

    /**
     * Which column a standing root takes: one or two off the centre line, on the
     * side that has the room. Never the centre line itself, and {@link #NO_LEVEL}
     * when the slice is too narrow to give up a column at all.
     */
    private static int standingRootColumn(long salt, int cell, int axisId, int min, int max, int centreLine) {
        int side = noise(salt, cell, axisId, 0) < 0.5F ? -1 : 1;
        int offset = 1 + (int) (noise(salt, cell, axisId, 2) * 2.0F);
        int perp = centreLine + side * offset;
        if (perp < min || perp > max) {
            perp = centreLine - side * offset;
        }
        if (perp < min || perp > max || perp == centreLine) {
            return NO_LEVEL;
        }
        return perp;
    }

    // --- Position derived noise ----------------------------------------------

    /**
     * A stable value in {@code [0, 1)} for one purpose at one place.
     *
     * <p>Hand rolled rather than {@code Mth.getSeed}, which is deprecated, and
     * rather than a {@link RandomSource} per position, which would allocate a few
     * hundred objects per call for three bytes of answer each.</p>
     */
    private static float noise(long salt, int a, int b, int c) {
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

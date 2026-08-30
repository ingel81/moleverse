package net.sgeht.moleverse.dimension;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * What turns a carved chamber into a room a colony uses.
 *
 * <p>A chamber is where a mound maps down, and a mound is where a colony comes
 * to the surface, so the room under one is not a wide spot in a corridor - it is
 * the part of the burrow an animal lives in rather than travels through. That
 * is the whole brief here, and everything below follows from asking what an
 * animal does with a room: it sleeps in it, it stores food in it, and it props
 * the ceiling up because the room is wider than the runs that reach it.</p>
 *
 * <h2>The larder is the anchor</h2>
 *
 * <p>Moles keep <em>worm larders</em>: they bite an earthworm through the head
 * segments to paralyse it and pack it away alive, hundreds at a time, against a
 * winter when the soil is too hard to hunt. It is the one thing a mole does that
 * needs a room, and at {@link BurrowGeometry#SCALE} it is not a detail on a wall
 * but an alcove cut into it with a face of packed worms at the back. Finding one
 * should be the reason the trip down was worth taking.</p>
 *
 * <h2>Why every decision comes from the position</h2>
 *
 * <p>Same rule as {@link TunnelDecorator}, for the same reason: nothing here
 * draws from the {@code random} handed in, every roll is a hash of a block
 * position, and so a chamber is the same room on the tenth visit as on the
 * first. A colony that rearranged its furniture between visits would not be a
 * place. The {@code random} parameter is kept because the caller has one and the
 * signature is worth leaving room in; today it is deliberately unused.</p>
 *
 * <h2>Every write is idempotent, and nothing writes twice</h2>
 *
 * <p>Only two things ever happen to a block: {@link #replaceEarth} turns raw
 * {@link ModBlocks#DEEP_EARTH} into something, and {@link #fillAir} puts
 * something into air. Neither can touch a block a player left, and calling
 * {@link #furnish} again finds its own work already done.</p>
 *
 * <p>That is only half of it, and the floor is where the other half bites. A
 * square of floor can be laid exactly once - the second writer finds soil rather
 * than earth and gives up - so if two things wanted the same square, which one
 * got it would be decided by the order they run in, and the room would change
 * the first time somebody reordered {@link #furnish}. The floor is therefore
 * carved up in advance: the trodden ground by the shaft, the nest and the pillar
 * feet claim their squares from the position alone, {@link #claimed} is the
 * single statement of who owns what, and no two of them overlap.</p>
 *
 * <p>Above the floor the rule relaxes, because it can. {@link #fillAir} and
 * {@link #replaceEarth} never overwrite anything, so where a pillar's capital
 * and a fringe of dome roots want the same block the first one simply has it and
 * the second finds it taken - the same outcome every time, in a fixed order, and
 * nothing to reason about.</p>
 *
 * <h2>When to call it</h2>
 *
 * <p><strong>After the runs are carved, not before.</strong> A larder is cut
 * into the wall, and which parts of the wall are wall depends on where the
 * corridors come in. {@link CorridorCarver#carve} clears deep earth and nothing
 * else, so a niche cut into a wall a corridor later opens would not be removed -
 * it would be left hanging in the mouth of that corridor. Cutting after the runs
 * exist means the probes see the room as it really is, and a side that a
 * corridor took simply gets no larder.</p>
 *
 * <h2>The clamp: writes only</h2>
 *
 * <p>{@link #furnish(ServerLevel, BlockPos, RandomSource, BoundingBox)} takes a
 * box and drops every write outside it, so one room can be furnished a chunk at
 * a time and come out as the room it would have been. Only the writes are
 * bounded. The probes - {@link #isChamber}, {@link #ceilingOf}, the wall test a
 * larder makes - read the whole room whatever box is in force, because they
 * decide <em>what the room is</em>, and a decision that changed with the chunk
 * being worked on is exactly the thing this class was built not to have.</p>
 *
 * <p>The consequence is worth stating plainly: a clamped call still needs the
 * middle of the room loaded, not just the chunk it is writing into. A chunk at
 * the rim that arrives while the centre is unloaded furnishes nothing and waits
 * for a pass that can see the room.</p>
 */
public final class ChamberFurnisher {

    // --- The landing: the one place that is kept clear -----------------------

    /**
     * Nothing stands within this of the middle, at foot or head height.
     *
     * <p>The way home is placed at the centre after this runs, and a player
     * arrives beside it. Both of those are somebody else's numbers - this only
     * has to be at least as large as {@code BurrowTransit}'s arrival offset, and
     * it is the constant to raise if that one ever grows.</p>
     */
    private static final int LANDING_RADIUS = 2;

    /** How far out {@link #isChamber} looks to satisfy itself this is a room and not a corridor. Wider than a corridor is half. */
    private static final int CHAMBER_PROBE = BurrowGeometry.CHAMBER_RADIUS - 2;

    // --- Floor: the room has been walked in -----------------------------------

    /** Side of one patch of floor material. One material per cell, because a floor that changes per square is noise rather than wear. */
    private static final int FLOOR_CELL = 3;

    /** Chance a square inside a patch is actually laid. The rest stays raw earth, and that is what gives a patch an edge. */
    private static final float FLOOR_DENSITY = 0.75F;

    // --- The shaft: where the way up is --------------------------------------

    /**
     * Radius of the ground worn bare around the way out. A room's traffic all
     * goes to one place, and this is where.
     *
     * <p>Derived from {@link #LANDING_RADIUS} rather than picked, because it is
     * also the disc that everything with a height is kept out of: a block laid
     * flat on the floor is the only thing that can safely share ground with a
     * player who arrives standing on it.</p>
     */
    private static final int TRODDEN_RADIUS = LANDING_RADIUS + 1;

    /** Chance at the middle of that patch. The falloff to the edge is what fades it into the rest of the floor. */
    private static final float TRODDEN_DENSITY = 0.85F;

    /** Squared distance out to which the trodden ground is bare packed dirt rather than soil. */
    private static final int TRODDEN_CORE = 4;

    /** Radius of the pool of light in the ceiling over the way out. Small: it is a shaft of light, not a lit ceiling. */
    private static final int SHAFT_GLOW_RADIUS = 2;

    /** Chance at the middle of that pool. Under one so it has a ragged edge instead of being a stamped disc. */
    private static final float SHAFT_GLOW_DENSITY = 0.8F;

    // --- The nest: one corner is a bed ---------------------------------------

    /** How far the nest sits from the middle. Against a wall, out of the traffic - which is where an animal puts its bed. */
    private static final int NEST_DISTANCE = 4;

    /** Half width of the bowl. Two gives a dish about five across: a bed, not a second room. */
    private static final int NEST_RADIUS = 2;

    /** Chance a square of the bed grows a carpet on the moss. Flat, so it costs no headroom and can be stood in. */
    private static final float NEST_CARPET_CHANCE = 0.75F;

    /** Chance a square of the woven rim is there at all. The gaps are what make it a nest edge rather than a kerb. */
    private static final float NEST_RIM_DENSITY = 0.7F;

    /** Chance a rim block is stacked two high. A wall you look over on one side and step over on another reads as woven. */
    private static final float NEST_RIM_HIGH_CHANCE = 0.4F;

    // --- Pillars: the room is wider than the runs, so it needs holding up -----

    /** How far along each of x and z a root stands from the middle. Three each way is just over four blocks out, which leaves the room open and still reads as bearing the dome. */
    private static final int PILLAR_DIAGONAL = 3;

    /** How far a root may wander off its diagonal. Four pillars at exactly ninety degrees read as masonry, which is the one thing they must not. */
    private static final int PILLAR_JITTER = 1;

    /** How far down from the ceiling a root frays sideways. Two courses is a capital; one is a bracket and three is a canopy. */
    private static final int PILLAR_CAPITAL_HEIGHT = 2;

    /** Chance a block of that fraying is filled. Under one, so a root ends in the earth rather than in a plate. */
    private static final float PILLAR_CAPITAL_DENSITY = 0.65F;

    /** Chance a square beside a root's foot is mossed over. A root that has stood a while gathers growth at the bottom. */
    private static final float PILLAR_FOOT_DENSITY = 0.6F;

    // --- The dome: sparse, or the ceiling is a texture ------------------------

    /** Chance a square of the room trails roots from the dome. Sparse on purpose: a ceiling of roots is a hedge. */
    private static final float DOME_FRINGE_CHANCE = 0.06F;

    // --- The larder: the reason to come down here -----------------------------

    /** How many larders a chamber gets. Two: one is a curiosity, four is a shop. */
    private static final int LARDER_COUNT = 2;

    /** How deep the alcove is cut into the wall. Two, so it is a store you step into rather than a shelf you look at. */
    private static final int LARDER_DEPTH = 2;

    /** Half width of the alcove across the wall. One gives a three block opening, which is a doorway at this scale. */
    private static final int LARDER_HALF_WIDTH = 1;

    /** Height of the alcove above the chamber floor. */
    private static final int LARDER_HEIGHT = 3;

    /** Chance a square of the back face is packed with worms rather than left as earth. A cache is lumpy - a tiled wall of worms is a texture. */
    private static final float LARDER_DENSITY = 0.45F;

    /** The same for the floor of the alcove, where the store spills over. Lower, because a floor reads as full much sooner than a wall does. */
    private static final float LARDER_FLOOR_DENSITY = 0.25F;

    /**
     * Where the wall is, measured from the middle of the room.
     *
     * <p>Not probed: {@code CorridorCarver.carveChamber} clears the integer disc
     * of {@link BurrowGeometry#CHAMBER_RADIUS}, and on a cardinal that disc stops
     * one block short of this. Deciding it rather than searching for it is what
     * makes {@link #cutLarder} a validate-then-write: a second visit finds the
     * alcove already open, fails the check, and writes nothing at all.</p>
     *
     * <p>One block short at every height but the lowest, where the carver's fillet
     * leaves the wall two short. That is the sill, and {@link #cutLarder} takes it
     * out of the doorway - the wall itself is still here.</p>
     */
    private static final int WALL_DISTANCE = BurrowGeometry.CHAMBER_RADIUS + 1;

    /**
     * How far from the centre anything here can write.
     *
     * <p>Public because the caller has to have the chunks: the larders are cut
     * outside the room, past what a chamber's own radius covers.</p>
     */
    public static final int REACH = WALL_DISTANCE + LARDER_DEPTH;

    /** Clients see the change and nothing else reacts, exactly as in {@link TunnelDecorator}. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // Salts. Distinct so that two features never agree by accident and land on the same block.
    private static final long SALT_FLOOR_FILL = 0x0C1A_4B00L;
    private static final long SALT_FLOOR_KIND = 0x0C1A_4B01L;
    private static final long SALT_TRODDEN = 0x0C1A_4B02L;
    private static final long SALT_SHAFT_FILL = 0x5A17_0000L;
    private static final long SALT_DOME_FRINGE = 0x5A17_0001L;
    private static final long SALT_NEST_SIDE = 0x0BED_0000L;
    private static final long SALT_NEST_CARPET = 0x0BED_0001L;
    private static final long SALT_NEST_RIM = 0x0BED_0002L;
    private static final long SALT_NEST_RIM_HIGH = 0x0BED_0003L;
    private static final long SALT_PILLAR_X = 0x0B00_7000L;
    private static final long SALT_PILLAR_Z = 0x0B00_7001L;
    private static final long SALT_PILLAR_CAPITAL = 0x0B00_7002L;
    private static final long SALT_PILLAR_FOOT = 0x0B00_7003L;
    private static final long SALT_LARDER_SIDE = 0x0FEE_D000L;
    private static final long SALT_LARDER_FACE = 0x0FEE_D001L;
    private static final long SALT_LARDER_FLOOR = 0x0FEE_D002L;

    /** Returned by a probe that failed to find what it went looking for. */
    private static final int NO_LEVEL = Integer.MIN_VALUE;

    private ChamberFurnisher() {
    }

    /**
     * Called once, right after a chamber has been carved.
     *
     * <p>{@code chamberCentre} follows the carver's convention: it is the
     * <em>walking surface</em> at the middle of the room, so the block below it
     * is floor and the middle of the dome is somewhere above it.</p>
     *
     * <p>Safe to call again on a chamber that is already furnished, and worth it:
     * the first call may have run with half the room's chunks still unloaded, and
     * a second one finishes what it could not reach. Safe to call on a position
     * that turns out not to be a chamber at all - it probes first and returns
     * without touching anything.</p>
     *
     * <p>The {@code random} parameter is not used; see the class javadoc for why
     * that is a decision rather than an oversight.</p>
     */
    public static void furnish(ServerLevel burrow, BlockPos chamberCentre, RandomSource random) {
        furnish(burrow, chamberCentre, random, null);
    }

    /**
     * The same, writing only inside {@code clamp}.
     *
     * <p>Null furnishes the whole room. Anything else is one chunk taking its
     * share: every set piece is placed from the same arithmetic, and the box only
     * decides which of its blocks this call is the one to lay. Run it once per
     * chunk the room touches and the room is furnished - see the class javadoc
     * for what the clamp deliberately does not bound.</p>
     */
    public static void furnish(ServerLevel burrow, BlockPos chamberCentre, RandomSource random,
            @Nullable BoundingBox clamp) {
        int cx = chamberCentre.getX();
        int wy = chamberCentre.getY();
        int cz = chamberCentre.getZ();

        // Nothing here writes further out than a larder's back wall, lower than
        // the floor, or higher than the dome the ceiling probe stops at.
        if (clamp != null && misses(clamp, cx - REACH, wy - 1, cz - REACH,
                cx + REACH, wy + BurrowGeometry.CHAMBER_HEIGHT + 1, cz + REACH)) {
            return;
        }

        if (!isChamber(burrow, cx, wy, cz)) {
            return;
        }

        // The set pieces choose their ground before anything is laid, because the
        // floor pass has to know to keep off it. All three are pure functions of
        // where the room is.
        int nestSide = (int) (noise(SALT_NEST_SIDE, cx, wy, cz) * 4.0F);
        Direction toNest = Direction.from2DDataValue(nestSide);
        int nestX = cx + toNest.getStepX() * NEST_DISTANCE;
        int nestZ = cz + toNest.getStepZ() * NEST_DISTANCE;
        int[][] pillars = pillarFeet(cx, cz, nestX, nestZ);

        dressFloor(burrow, cx, wy, cz, nestX, nestZ, pillars, clamp);
        treadTheShaft(burrow, cx, wy, cz, clamp);
        growNest(burrow, cx, wy, cz, nestX, nestZ, clamp);
        raisePillars(burrow, cx, wy, cz, pillars, clamp);
        hangTheDome(burrow, cx, wy, cz, clamp);
        lightTheShaft(burrow, cx, wy, cz, clamp);
        stockLarders(burrow, cx, wy, cz, nestSide, clamp);
    }

    // --- The clamp -----------------------------------------------------------

    /**
     * Whether a write at this position is this call's to make. No clamp means
     * every position is; a clamped call leaves the rest to the chunk that owns
     * it.
     */
    private static boolean writes(@Nullable BoundingBox clamp, BlockPos pos) {
        return clamp == null || clamp.isInside(pos);
    }

    /**
     * Whether this column is worth probing at all.
     *
     * <p>Everything with a ceiling probe in front of it asks this first: the
     * probe is a dozen block reads up the room and it is pure waste when the
     * column it would decide about is somebody else's to write.</p>
     */
    private static boolean writesColumn(@Nullable BoundingBox clamp, int x, int z, int spread) {
        return clamp == null || clamp.intersects(x - spread, z - spread, x + spread, z + spread);
    }

    /** Whether a box misses the clamp entirely. Six comparisons rather than an allocation. */
    private static boolean misses(BoundingBox clamp, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        return clamp.maxX() < minX || clamp.minX() > maxX
                || clamp.maxY() < minY || clamp.minY() > maxY
                || clamp.maxZ() < minZ || clamp.minZ() > maxZ;
    }

    // --- The floor -----------------------------------------------------------

    /**
     * Lays the floor of the room, everywhere the set pieces have not claimed.
     *
     * <p>One material per three-block cell, the same argument
     * {@link TunnelDecorator} makes for corridors: a floor that picks a material
     * per square is noise, and a floor that picks one per patch is a room that
     * has been used unevenly.</p>
     */
    private static void dressFloor(ServerLevel burrow, int cx, int wy, int cz,
            int nestX, int nestZ, int[][] pillars, @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        int radius = BurrowGeometry.CHAMBER_RADIUS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!withinDisc(dx, dz, radius)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (claimed(x, z, cx, cz, nestX, nestZ, pillars)) {
                    continue;
                }
                if (noise(SALT_FLOOR_FILL, x, floorY, z) >= FLOOR_DENSITY) {
                    continue;
                }
                Block material = floorMaterial(Math.floorDiv(x, FLOOR_CELL), Math.floorDiv(z, FLOOR_CELL));
                replaceEarth(burrow, cursor.set(x, floorY, z), material.defaultBlockState(), clamp);
            }
        }
    }

    /**
     * Ground a set piece has spoken for.
     *
     * <p>The point is not to avoid overwriting - {@link #replaceEarth} could not
     * overwrite a laid floor anyway. It is that a set piece must find raw earth
     * when it gets there, on the second visit as much as the first.</p>
     */
    private static boolean claimed(int x, int z, int cx, int cz, int nestX, int nestZ, int[][] pillars) {
        if (withinDisc(x - cx, z - cz, TRODDEN_RADIUS)) {
            return true;
        }
        if (inNest(x, z, cx, cz, nestX, nestZ)) {
            return true;
        }
        for (int[] pillar : pillars) {
            // The square the root stands on, and no more: the moss it spreads
            // around itself is a carpet laid over whatever floor is there, so
            // those squares still want dressing.
            if (x == pillar[0] && z == pillar[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a square belongs to the bed.
     *
     * <p>Three conditions, and the two subtractions matter as much as the shape.
     * The nest sits four blocks out with a reach of three, so left alone it would
     * run right up to the way home; the trodden ground wins that argument, and
     * what it takes off the near side leaves the bed open towards the room, which
     * is how you want to be able to step into one. The far side runs into the
     * wall and is cut by it, so the wall is the nest's back.</p>
     *
     * <p>One statement of the footprint for two callers: {@link #claimed} keeps
     * the floor pass off these squares and {@link #growNest} lays them. If the
     * two ever disagreed the difference would come out as a ring of bare earth
     * around the bed.</p>
     */
    private static boolean inNest(int x, int z, int cx, int cz, int nestX, int nestZ) {
        return withinDisc(x - nestX, z - nestZ, NEST_RADIUS + 1)
                && withinDisc(x - cx, z - cz, BurrowGeometry.CHAMBER_RADIUS)
                && !withinDisc(x - cx, z - cz, TRODDEN_RADIUS);
    }

    /**
     * What a patch of chamber floor is made of.
     *
     * <p>No gravel, unlike a corridor. A room the colony lives in is trodden
     * soft, and keeping the stony materials to the runs is what makes stepping
     * into a chamber feel like arriving somewhere.</p>
     */
    private static Block floorMaterial(int cellX, int cellZ) {
        int roll = (int) (noise(SALT_FLOOR_KIND, cellX, cellZ, 0) * 100.0F);
        if (roll < 45) {
            return ModBlocks.LOOSE_SOIL.get();
        }
        if (roll < 70) {
            return Blocks.COARSE_DIRT;
        }
        if (roll < 85) {
            return Blocks.ROOTED_DIRT;
        }
        if (roll < 93) {
            return Blocks.MOSS_BLOCK;
        }
        return Blocks.CLAY;
    }

    // --- The shaft -----------------------------------------------------------

    /**
     * Wears the ground bare around the way up.
     *
     * <p>Flush with the floor and nothing else, which is what lets it cover the
     * landing: a player arrives standing here and a decoration with a height
     * would be a decoration they arrive inside of.</p>
     */
    private static void treadTheShaft(ServerLevel burrow, int cx, int wy, int cz, @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -TRODDEN_RADIUS; dx <= TRODDEN_RADIUS; dx++) {
            for (int dz = -TRODDEN_RADIUS; dz <= TRODDEN_RADIUS; dz++) {
                if (!withinDisc(dx, dz, TRODDEN_RADIUS)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                float chance = patchChance(TRODDEN_DENSITY, TRODDEN_RADIUS, dx, dz);
                if (noise(SALT_TRODDEN, x, floorY, z) >= chance) {
                    continue;
                }
                Block material = dx * dx + dz * dz <= TRODDEN_CORE
                        ? Blocks.COARSE_DIRT
                        : ModBlocks.LOOSE_SOIL.get();
                replaceEarth(burrow, cursor.set(x, floorY, z), material.defaultBlockState(), clamp);
            }
        }
    }

    /**
     * Puts a pool of light in the dome directly over the way up.
     *
     * <p>The room needs one landmark that can be found from anywhere in it, and
     * it should be the thing that gets you out. The block straight above the post
     * is lit unconditionally - a shaft whose light failed a dice roll would be a
     * chamber with no light at all in it.</p>
     */
    private static void lightTheShaft(ServerLevel burrow, int cx, int wy, int cz, @Nullable BoundingBox clamp) {
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SHAFT_GLOW_RADIUS; dx <= SHAFT_GLOW_RADIUS; dx++) {
            for (int dz = -SHAFT_GLOW_RADIUS; dz <= SHAFT_GLOW_RADIUS; dz++) {
                if (!withinDisc(dx, dz, SHAFT_GLOW_RADIUS)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (!writesColumn(clamp, x, z, 0)) {
                    continue;
                }
                int ceilingY = ceilingOf(burrow, x, wy, z);
                if (ceilingY == NO_LEVEL) {
                    continue;
                }
                boolean middle = dx == 0 && dz == 0;
                float chance = patchChance(SHAFT_GLOW_DENSITY, SHAFT_GLOW_RADIUS, dx, dz);
                if (!middle && noise(SALT_SHAFT_FILL, x, ceilingY, z) >= chance) {
                    continue;
                }
                replaceEarth(burrow, cursor.set(x, ceilingY, z), glow, clamp);

                // Roots at the rim of the pool, never over the middle: they turn
                // a lit patch into a shaft that light comes down.
                if (!middle) {
                    fillAir(burrow, cursor.set(x, ceilingY - 1, z), Blocks.HANGING_ROOTS.defaultBlockState(), clamp);
                }
            }
        }
    }

    // --- The nest ------------------------------------------------------------

    /**
     * Lines one corner with something soft.
     *
     * <p>A dish of moss inside a rim woven from roots. The rim is what makes it a
     * nest rather than a patch of moss - and it is left ragged and only sometimes
     * two blocks high, because a mole builds a bed by dragging material into a
     * heap, not by laying a course.</p>
     *
     * <p>Nothing is sunk into the floor, which the depression this wants would
     * have called for. A sunken bowl cannot be decided from the position alone -
     * it depends on whether there is a corridor immediately below - and a
     * decision that reads the world is a decision that can come out differently
     * on the next visit. The rim buys the same read for none of that.</p>
     *
     * <p>Which squares are the nest's at all is {@link #inNest}'s answer, and it
     * takes a bite out of both ends of this shape - see there. What is left is a
     * dish with the wall at its back and the room at its mouth.</p>
     */
    private static void growNest(ServerLevel burrow, int cx, int wy, int cz, int nestX, int nestZ,
            @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        int reach = NEST_RADIUS + 1;
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = nestX + dx;
                int z = nestZ + dz;
                if (!inNest(x, z, cx, cz, nestX, nestZ)) {
                    continue;
                }

                if (withinDisc(dx, dz, NEST_RADIUS)) {
                    replaceEarth(burrow, cursor.set(x, floorY, z), Blocks.MOSS_BLOCK.defaultBlockState(), clamp);
                    if (noise(SALT_NEST_CARPET, x, wy, z) < NEST_CARPET_CHANCE) {
                        fillAir(burrow, cursor.set(x, wy, z), Blocks.MOSS_CARPET.defaultBlockState(), clamp);
                    }
                    continue;
                }

                replaceEarth(burrow, cursor.set(x, floorY, z), Blocks.ROOTED_DIRT.defaultBlockState(), clamp);
                if (noise(SALT_NEST_RIM, x, wy, z) >= NEST_RIM_DENSITY) {
                    continue;
                }
                fillAir(burrow, cursor.set(x, wy, z), beam, clamp);
                if (noise(SALT_NEST_RIM_HIGH, x, wy, z) < NEST_RIM_HIGH_CHANCE) {
                    fillAir(burrow, cursor.set(x, wy + 1, z), beam, clamp);
                }
            }
        }
    }

    // --- The pillars ---------------------------------------------------------

    /**
     * Where the roots holding the dome stand.
     *
     * <p>On the diagonals, because the four cardinals are spoken for: the nest
     * takes one and the larders are cut into the others, and a root standing in
     * front of a larder would hide the one thing in the room worth walking to.
     * The jitter is what keeps four of them from reading as a colonnade.</p>
     *
     * <p>A root that would land in the bed, or close enough to the way home to
     * crowd it, is simply dropped. Three roots hold a ceiling up as convincingly
     * as four, and a room with a pillar in the middle of its bed does not.</p>
     */
    private static int[][] pillarFeet(int cx, int cz, int nestX, int nestZ) {
        int[][] found = new int[4][2];
        int count = 0;
        int corner = 0;

        for (int signX = -1; signX <= 1; signX += 2) {
            for (int signZ = -1; signZ <= 1; signZ += 2) {
                int x = cx + signX * (PILLAR_DIAGONAL + jitter(SALT_PILLAR_X, cx, cz, corner, PILLAR_JITTER));
                int z = cz + signZ * (PILLAR_DIAGONAL + jitter(SALT_PILLAR_Z, cx, cz, corner, PILLAR_JITTER));
                corner++;

                if (withinDisc(x - nestX, z - nestZ, NEST_RADIUS + 1)) {
                    continue;
                }
                // Off the trodden ground, which owns those floor squares - and
                // which is wider than the landing, so this keeps a root out of
                // the way home's elbow room for free.
                if (withinDisc(x - cx, z - cz, TRODDEN_RADIUS)) {
                    continue;
                }
                found[count][0] = x;
                found[count][1] = z;
                count++;
            }
        }
        return Arrays.copyOf(found, count);
    }

    /**
     * Stands the roots up and lights the room off them.
     *
     * <p>Each is one column from the floor to the ceiling it holds, fraying
     * sideways over the last courses so it meets the dome rather than butting
     * into it, mossed at the foot where it has stood a while, and lit at the top.
     * The light is the reason they earn their place twice: a room this size lit
     * only from the middle has a dark edge all the way round, and the pillars are
     * already where a lamp would want to be.</p>
     */
    private static void raisePillars(ServerLevel burrow, int cx, int wy, int cz, int[][] pillars,
            @Nullable BoundingBox clamp) {
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int[] pillar : pillars) {
            int x = pillar[0];
            int z = pillar[1];
            // A root writes into its own column and into the four beside it, so
            // the whole nine has to be out of the box before it is skipped.
            if (!writesColumn(clamp, x, z, 1)) {
                continue;
            }
            int ceilingY = ceilingOf(burrow, x, wy, z);
            if (ceilingY == NO_LEVEL) {
                continue;
            }

            for (int y = wy; y < ceilingY; y++) {
                fillAir(burrow, cursor.set(x, y, z), beam, clamp);
            }
            replaceEarth(burrow, cursor.set(x, wy - 1, z), moss, clamp);
            lightCeiling(burrow, cursor, x, ceilingY, z, clamp);

            for (Direction side : Direction.Plane.HORIZONTAL) {
                int nx = x + side.getStepX();
                int nz = z + side.getStepZ();

                // Moss creeping out from the foot, as a carpet over whatever
                // floor is already there rather than as a floor of its own. It
                // costs no headroom, it cannot collide with anything, and it is
                // the reason the floor pass only has to keep off the one square
                // the root actually stands on.
                //
                // Not onto the trodden ground, though: that patch is bare
                // because it is walked on, and moss growing over it would say
                // the opposite.
                if (!withinDisc(nx - cx, nz - cz, TRODDEN_RADIUS)
                        && noise(SALT_PILLAR_FOOT, nx, wy, nz) < PILLAR_FOOT_DENSITY) {
                    fillAir(burrow, cursor.set(nx, wy, nz), Blocks.MOSS_CARPET.defaultBlockState(), clamp);
                }
                for (int y = Math.max(ceilingY - PILLAR_CAPITAL_HEIGHT, wy + 1); y < ceilingY; y++) {
                    if (noise(SALT_PILLAR_CAPITAL, nx, y, nz) < PILLAR_CAPITAL_DENSITY) {
                        fillAir(burrow, cursor.set(nx, y, nz), beam, clamp);
                    }
                }
                lightCeiling(burrow, cursor, nx, ceilingY, nz, clamp);
            }
        }
    }

    /**
     * Threads in the ceiling, but only where the ceiling actually is.
     *
     * <p>The dome slopes, so a neighbour column of a pillar may cap a block lower
     * than the pillar does - and the block at the pillar's ceiling height is then
     * earth with more earth under it. Lighting that buries a lamp in the mass
     * where nobody will ever see it. The test is whether there is room underneath
     * it.</p>
     */
    private static void lightCeiling(ServerLevel burrow, BlockPos.MutableBlockPos cursor, int x, int y, int z,
            @Nullable BoundingBox clamp) {
        if (!burrow.isLoaded(cursor.set(x, y - 1, z)) || !isOpen(burrow.getBlockState(cursor))) {
            return;
        }
        replaceEarth(burrow, cursor.set(x, y, z), ModBlocks.GLOW_MYCELIUM.get().defaultBlockState(), clamp);
    }

    // --- The dome ------------------------------------------------------------

    /**
     * Trails a few roots out of the dome.
     *
     * <p>Sparse, and that is the whole design: a bare ceiling over a room this
     * size reads as unfinished, and a busy one reads as a hedge. Half a dozen
     * across the whole span is enough to tell a player there is something up
     * there.</p>
     */
    private static void hangTheDome(ServerLevel burrow, int cx, int wy, int cz, @Nullable BoundingBox clamp) {
        int radius = BurrowGeometry.CHAMBER_RADIUS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!withinDisc(dx, dz, radius)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                // The roll before the probe: the probe is the expensive half and
                // this fires on one square in sixteen.
                if (noise(SALT_DOME_FRINGE, x, wy, z) >= DOME_FRINGE_CHANCE) {
                    continue;
                }
                if (!writesColumn(clamp, x, z, 0)) {
                    continue;
                }
                int ceilingY = ceilingOf(burrow, x, wy, z);
                if (ceilingY == NO_LEVEL) {
                    continue;
                }
                fillAir(burrow, cursor.set(x, ceilingY - 1, z), Blocks.HANGING_ROOTS.defaultBlockState(), clamp);
            }
        }
    }

    // --- The larders ---------------------------------------------------------

    /**
     * Cuts the worm larders into the walls.
     *
     * <p>Two fixed sides, tried once each. Not "the first two sides that work":
     * a side a corridor has taken simply gets no larder, and the room then has
     * one. Retrying elsewhere would mean a chamber that was entered before its
     * runs were carved ends up with larders in different walls than the same
     * chamber entered afterwards - and the whole class exists to make that
     * impossible.</p>
     */
    private static void stockLarders(ServerLevel burrow, int cx, int wy, int cz, int nestSide,
            @Nullable BoundingBox clamp) {
        int[] free = new int[3];
        int count = 0;
        for (int side = 0; side < 4; side++) {
            if (side != nestSide) {
                free[count++] = side;
            }
        }

        int first = (int) (noise(SALT_LARDER_SIDE, cx, wy, cz) * 3.0F);
        for (int i = 0; i < LARDER_COUNT; i++) {
            cutLarder(burrow, cx, wy, cz, Direction.from2DDataValue(free[(first + i) % 3]), clamp);
        }
    }

    /**
     * One larder: an alcove in the wall with a face of packed worms at the back.
     *
     * <p>Validate, then write. Every block of the box has to be in a loaded chunk
     * and either raw earth or something only this larder could have put there,
     * before the first one is touched. That buys the thing the room is actually
     * being asked about - a wall a corridor has opened fails the test and is left
     * alone - and it survives being cut in pieces.</p>
     *
     * <p><strong>Why "or its own work" rather than "raw earth".</strong> Under a
     * clamp the alcove is cut by whichever chunks it lies in, one part each, and
     * the strict test would let the first chunk write its share and then refuse
     * every later one: the box no longer being untouched earth is exactly what
     * the first pass achieved. Accepting the larder's own products, and only at
     * the positions it writes them, makes the decision the same on every pass
     * while leaving the corridor guard intact - the back face is only ever worms
     * or earth and never air, and the course above and the course below are never
     * written at all, so a run that took this wall still fails.</p>
     *
     * <p>The middle of the back face is packed regardless of the dice. A larder
     * that rolled empty would be an alcove with nothing in it, and there is no
     * reading of the room in which that is the interesting outcome.</p>
     */
    private static void cutLarder(ServerLevel burrow, int cx, int wy, int cz, Direction into,
            @Nullable BoundingBox clamp) {
        int alongX = into.getStepX();
        int alongZ = into.getStepZ();
        // The wall runs across the direction we are cutting into it.
        int acrossX = alongZ;
        int acrossZ = -alongX;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        if (clamp != null && missesLarder(clamp, cx, wy, cz, alongX, alongZ, acrossX, acrossZ)) {
            return;
        }

        // It has to open into the room, or it is a cupboard inside the earth.
        // Read a block above the walking surface, not at it: the chamber's floor
        // layer is filleted, so the block at the foot of the wall is earth left
        // standing on every side of the room, larder or no larder. A block up the
        // wall is at the room's full radius and answers the question that was
        // actually being asked.
        cursor.set(cx + alongX * (WALL_DISTANCE - 1), wy + 1, cz + alongZ * (WALL_DISTANCE - 1));
        if (!burrow.isLoaded(cursor) || !isOpen(burrow.getBlockState(cursor))) {
            return;
        }

        for (int depth = -1; depth <= LARDER_DEPTH; depth++) {
            for (int across = -LARDER_HALF_WIDTH; across <= LARDER_HALF_WIDTH; across++) {
                for (int y = wy - 1; y <= wy + LARDER_HEIGHT; y++) {
                    larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, depth, across, y);
                    if (!burrow.isLoaded(cursor)
                            || !larderUncut(burrow.getBlockState(cursor), depth, across, y - wy)) {
                        return;
                    }
                }
            }
        }

        BlockState larder = ModBlocks.WORM_LARDER.get().defaultBlockState();
        BlockState soil = ModBlocks.LOOSE_SOIL.get().defaultBlockState();

        // The sill. The fillet at the foot of the chamber wall runs right across
        // this doorway, and a store you have to step up and then down into is not
        // one you walk into. Only the columns of the alcove itself, and only at
        // the heights it is hollow: everywhere else the fillet stays, which is
        // what it is for.
        for (int across = -LARDER_HALF_WIDTH; across <= LARDER_HALF_WIDTH; across++) {
            for (int y = wy; y < wy + LARDER_HEIGHT; y++) {
                clearEarth(burrow,
                        larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, -1, across, y), clamp);
            }
        }

        for (int across = -LARDER_HALF_WIDTH; across <= LARDER_HALF_WIDTH; across++) {
            // The hollow, and the floor of it: a store spills over onto its own
            // floor, so some of the worms are underfoot.
            for (int depth = 0; depth < LARDER_DEPTH; depth++) {
                for (int y = wy; y < wy + LARDER_HEIGHT; y++) {
                    clearEarth(burrow,
                            larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, depth, across, y), clamp);
                }
                larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, depth, across, wy - 1);
                boolean packed = noise(SALT_LARDER_FLOOR, cursor.getX(), cursor.getY(), cursor.getZ())
                        < LARDER_FLOOR_DENSITY;
                replaceEarth(burrow, cursor, packed ? larder : soil, clamp);
            }

            // The back face, which is the larder proper.
            for (int y = wy; y < wy + LARDER_HEIGHT; y++) {
                larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, LARDER_DEPTH, across, y);
                boolean middle = across == 0 && y == wy + 1;
                if (!middle && noise(SALT_LARDER_FACE, cursor.getX(), cursor.getY(), cursor.getZ()) >= LARDER_DENSITY) {
                    continue;
                }
                replaceEarth(burrow, cursor, larder, clamp);
            }
        }

        // One lamp in the roof of the alcove. A store nobody can see is a store
        // nobody finds, and a lit recess in a dark wall is what draws a player
        // across the room to look.
        larderPos(cursor, cx, cz, alongX, alongZ, acrossX, acrossZ, 0, 0, wy + LARDER_HEIGHT);
        replaceEarth(burrow, cursor, ModBlocks.GLOW_MYCELIUM.get().defaultBlockState(), clamp);
    }

    /**
     * Whether this block of a larder's box still allows the alcove to be cut.
     *
     * <p>{@code layer} counts from the walking surface, so the alcove's own floor
     * is -1, and {@code depth} counts outward from the wall face, so the sill
     * through the fillet is -1 as well. Raw ground passes everywhere. Past that,
     * each position accepts only what the larder itself writes there and nothing
     * else: air in the hollow and in the sill, worms or soil on its floor, worms
     * on the back face, threads in its roof. The frame around all of that - the
     * course under the back face, the roof either side of the lamp - accepts
     * nothing but ground, and that is what a run through this wall trips
     * over.</p>
     *
     * <p>The sill's own course below the alcove floor is frame like any other, so
     * a corridor that came through here still fails on the back face whatever the
     * doorway looks like.</p>
     */
    private static boolean larderUncut(BlockState state, int depth, int across, int layer) {
        if (isRawGround(state)) {
            return true;
        }
        boolean inHeight = layer >= 0 && layer < LARDER_HEIGHT;
        if (depth == -1) {
            // The sill only speaks for the three blocks it cuts. The course under
            // it is chamber floor and the course over it is chamber wall, both of
            // them dressed by this class long before the larders are stocked, and
            // a larder that refused itself over its own room's floor would be a
            // larder that only ever appeared in an unfurnished chamber.
            return !inHeight || state.isAir();
        }
        if (depth < LARDER_DEPTH && inHeight) {
            return state.isAir();
        }
        if (depth < LARDER_DEPTH && layer == -1) {
            return state.is(ModBlocks.WORM_LARDER.get());
        }
        if (depth == LARDER_DEPTH && inHeight) {
            return state.is(ModBlocks.WORM_LARDER.get());
        }
        if (depth == 0 && across == 0 && layer == LARDER_HEIGHT) {
            return state.is(ModBlocks.GLOW_MYCELIUM.get());
        }
        return false;
    }

    /**
     * Whether a larder's box misses the clamp, and so is not this call's to cut.
     *
     * <p>Worth its own test rather than leaving it to {@link #replaceEarth}: the
     * validation ahead of the writes is some fifty block reads, and three of the
     * four walls of a room are in another chunk from any given one.</p>
     */
    private static boolean missesLarder(BoundingBox clamp, int cx, int wy, int cz,
            int alongX, int alongZ, int acrossX, int acrossZ) {
        // From the sill inwards: the doorway cut through the fillet is a block
        // nearer the middle than the wall face is, and a chunk that holds only
        // that block still has to be the one to cut it.
        int nearX = cx + alongX * (WALL_DISTANCE - 1);
        int nearZ = cz + alongZ * (WALL_DISTANCE - 1);
        int farX = cx + alongX * (WALL_DISTANCE + LARDER_DEPTH);
        int farZ = cz + alongZ * (WALL_DISTANCE + LARDER_DEPTH);
        int spreadX = Math.abs(acrossX) * LARDER_HALF_WIDTH;
        int spreadZ = Math.abs(acrossZ) * LARDER_HALF_WIDTH;

        return misses(clamp,
                Math.min(nearX, farX) - spreadX, wy - 1, Math.min(nearZ, farZ) - spreadZ,
                Math.max(nearX, farX) + spreadX, wy + LARDER_HEIGHT, Math.max(nearZ, farZ) + spreadZ);
    }

    /** One block of a larder box, in world coordinates. {@code depth} counts outward from the wall face. */
    private static BlockPos.MutableBlockPos larderPos(BlockPos.MutableBlockPos cursor, int cx, int cz,
            int alongX, int alongZ, int acrossX, int acrossZ, int depth, int across, int y) {
        int distance = WALL_DISTANCE + depth;
        return cursor.set(
                cx + alongX * distance + acrossX * across,
                y,
                cz + alongZ * distance + acrossZ * across);
    }

    // --- Probes --------------------------------------------------------------

    /**
     * Whether this is a room worth furnishing.
     *
     * <p>Open in the middle with something under it, and still open four blocks
     * out in all four directions - which a corridor never is, because a corridor
     * is five wide. Cheap, and it is the guard that lets {@link #furnish} be
     * called on any position at all.</p>
     *
     * <p>The middle is tested with {@link #isOpen} rather than for air, because
     * by the second visit the way home is standing in it.</p>
     */
    private static boolean isChamber(ServerLevel burrow, int cx, int wy, int cz) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (!burrow.isLoaded(cursor.set(cx, wy, cz)) || !isOpen(burrow.getBlockState(cursor))) {
            return false;
        }
        if (burrow.getBlockState(cursor.set(cx, wy - 1, cz)).isAir()) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            cursor.set(cx + side.getStepX() * CHAMBER_PROBE, wy, cz + side.getStepZ() * CHAMBER_PROBE);
            if (!burrow.isLoaded(cursor) || !isOpen(burrow.getBlockState(cursor))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The block that caps this column.
     *
     * <p>Threads already grown on the dome count as ceiling, the same trick
     * {@link TunnelDecorator} uses: without it a ceiling that has been lit once
     * measures a block higher on the next visit and the whole decoration creeps
     * upward. Anything else overhead means somebody has built up there, and then
     * this column gets nothing.</p>
     *
     * <p>So does the lining, and that one is not a refinement but the difference
     * between a lit room and a dark one: the dome is soil now, so a probe that
     * only knew about deep earth would walk the whole height of the chamber, find
     * neither ceiling nor open space, and answer that this column has none.</p>
     */
    private static int ceilingOf(ServerLevel burrow, int x, int walkY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = walkY + 1; y <= walkY + BurrowGeometry.CHAMBER_HEIGHT + 1; y++) {
            if (!burrow.isLoaded(cursor.set(x, y, z))) {
                return NO_LEVEL;
            }
            BlockState state = burrow.getBlockState(cursor);
            if (isRawGround(state) || state.is(ModBlocks.GLOW_MYCELIUM.get())) {
                return y;
            }
            if (!isOpen(state)) {
                return NO_LEVEL;
            }
        }
        return NO_LEVEL;
    }

    /**
     * Chamber space: air, or something that stands in it without closing it off.
     *
     * <p>The list has to include everything this class and
     * {@link TunnelDecorator} put into open space, or the room measures smaller
     * every time it is furnished. The way home is on it too, because it is
     * standing in the middle of the room from the second visit onward and
     * {@link #isChamber} looks straight at it.</p>
     */
    private static boolean isOpen(BlockState state) {
        return state.isAir()
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.WATER)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(ModBlocks.ROOT_BEAM.get())
                || state.is(ModBlocks.SHRINK_POST.get())
                || state.is(ModBlocks.ROOT_LADDER.get());
    }

    // --- Placement -----------------------------------------------------------

    /**
     * Turns raw ground into something.
     *
     * <p>Raw ground and nothing else - not air, which is a hole a player dug or a
     * corridor somebody else carved, and not a block that is already something,
     * which is either a decoration that made its choice or a block a player put
     * there.</p>
     *
     * <p><strong>Raw ground is two blocks now.</strong> {@code CorridorCarver}
     * lines every surface it cuts with {@link ModBlocks#LOOSE_SOIL}, so by the
     * time a room is furnished its floor, its walls and its ceiling are soil and
     * the deep earth has retreated two or three blocks into the mass. Testing for
     * deep earth alone would leave the whole room undressed - no floor, no moss at
     * a pillar's foot, and, worst of the three, no light in the dome, because the
     * threads are grown into the ceiling by exactly this method.</p>
     *
     * <p>The price is that a block of loose soil a player laid in a chamber can be
     * dressed over on a later pass. That is the same grey area
     * {@code TunnelDecorator} names in its own palette, and it comes with using
     * the mod's own soil as the material the burrow is built out of.</p>
     */
    private static boolean replaceEarth(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if (!writes(clamp, pos) || !burrow.isLoaded(pos)) {
            return false;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!isRawGround(existing)) {
            return false;
        }
        // A second pass over a floor of soil that was already laid as soil has
        // nothing to say. Worth the comparison: the chamber floor's commonest
        // material is the same block the lining is made of.
        if (existing == state) {
            return true;
        }
        return burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /**
     * Ground nobody has made anything of yet: the fill of the dimension, its
     * lining, or a loot pocket in that lining.
     *
     * <p>The nodule entry is what keeps {@code larderUncut}'s all-or-nothing
     * validation honest: the lining carries pockets at a hashed fraction, and a
     * larder box of ~45 lined positions would otherwise be cancelled outright
     * about a quarter of the time - the loot layer silently deleting the worm
     * economy at its source. A larder cutting through a pocket is the same
     * bargain {@code CorridorCarver.clear()} makes for a run.</p>
     *
     * <p>Deliberately the opposite of {@code NestCarver}'s rule, and the
     * asymmetry is not an inconsistency: in the nest the nodules <em>are</em>
     * the trove, placed on purpose, and must survive being dressed around. In a
     * chamber wall a nodule is ambient lining and the larder outranks it.</p>
     */
    private static boolean isRawGround(BlockState state) {
        return state.is(ModBlocks.DEEP_EARTH.get())
                || state.is(ModBlocks.LOOSE_SOIL.get())
                || state.is(ModBlocks.ROOT_NODULE.get());
    }

    /** Opens raw earth up. The same rule as {@link #replaceEarth}, and the only thing that removes anything. */
    private static boolean clearEarth(ServerLevel burrow, BlockPos pos, @Nullable BoundingBox clamp) {
        return replaceEarth(burrow, pos, Blocks.AIR.defaultBlockState(), clamp);
    }

    /** Fills open space. Air only, so a second visit never stacks a decoration onto the one it left. */
    private static void fillAir(ServerLevel burrow, BlockPos pos, BlockState state, @Nullable BoundingBox clamp) {
        if (writes(clamp, pos) && burrow.isLoaded(pos) && burrow.getBlockState(pos).isAir()) {
            burrow.setBlock(pos, state, PLACE_FLAGS);
        }
    }

    // --- Patches -------------------------------------------------------------

    /**
     * The integer disc, the same one {@code CorridorCarver} carves with:
     * {@code radius * radius + radius} is the squared radius of a circle drawn
     * half a block outside the ring, which is what keeps the diagonals from being
     * cut back to a plus sign.
     */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    /**
     * How likely a patch still reaches this far from its middle. Zero past the
     * radius, so a patch has an edge; falling off inside it, so the edge is
     * ragged rather than a circle somebody stamped.
     */
    private static float patchChance(float density, int radius, int dx, int dz) {
        int distanceSquared = dx * dx + dz * dz;
        int limit = (radius + 1) * (radius + 1);
        return distanceSquared >= limit ? 0.0F : density * (1.0F - (float) distanceSquared / limit);
    }

    /** A block or two off where something would otherwise sit exactly. */
    private static int jitter(long salt, int a, int b, int c, int amount) {
        return (int) (noise(salt, a, b, c) * (amount * 2 + 1)) - amount;
    }

    // --- Position derived noise ----------------------------------------------

    /**
     * A stable value in {@code [0, 1)} for one purpose at one place.
     *
     * <p>The same hash {@link TunnelDecorator} uses, and deliberately a copy
     * rather than a shared helper for now: the two classes are the only callers,
     * neither wants to depend on the other, and hoisting it into a utility is a
     * decision for whoever writes the third one.</p>
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
}

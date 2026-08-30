package net.sgeht.moleverse.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.dimension.AlcoveCarver;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.CorridorCarver;
import net.sgeht.moleverse.dimension.CorridorProfile;
import net.sgeht.moleverse.dimension.NestCarver;
import net.sgeht.moleverse.dimension.plan.BurrowFeature;
import net.sgeht.moleverse.dimension.plan.BurrowLedger;
import net.sgeht.moleverse.dimension.plan.BurrowReconciler;
import net.sgeht.moleverse.dimension.plan.CorridorFeature;
import net.sgeht.moleverse.dimension.plan.LarderFeature;
import net.sgeht.moleverse.dimension.plan.NestFeature;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.burrow.RunLevel;
import net.sgeht.moleverse.registry.ModAttachments;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Automated proof that the burrow works, without anybody playing.
 *
 * <p>Declared in {@link ModGameTests}; every method here is a test body and is
 * only ever called through the {@code test_function} registry.</p>
 *
 * <h2>Where the block tests actually run</h2>
 *
 * <p>Not in the burrow. {@code GameTestServer} bakes its world from the flat
 * world preset against an <em>empty</em> {@code LevelStem} registry, so no
 * datapack dimension is created and {@code ModDimensions.burrowLevel} returns
 * null down there. What is under test is {@link CorridorCarver} rather than the
 * dimension JSON anyway, and the carver only ever asks for a {@code ServerLevel}
 * made of {@link ModBlocks#DEEP_EARTH} - so each test builds itself a block of
 * deep earth high above the test area and carves into that.</p>
 *
 * <h2>Three rules the fixtures follow</h2>
 *
 * <p><strong>Force load what you write to.</strong> The runner force loads only
 * the chunks its structure touches, and every one of the carver's writes is
 * guarded by {@code isLoaded} - so a carve into an unloaded chunk does not fail,
 * it silently does nothing and the test passes for the wrong reason. The chunks
 * are never released again: the runner drops every forced chunk in the level when
 * the batch ends, and releasing one by hand could pull it out from under a test
 * that has not ticked yet.</p>
 *
 * <p><strong>A fixed lane per test, not {@code absolutePos}.</strong> This is the
 * rule that had to be learned, and it cost two test runs that failed differently
 * with no code between them. The fixtures used to be anchored at
 * {@code helper.absolutePos}, which is wherever the runner's grid happened to put
 * that test - and <em>every</em> decision the burrow makes is a hash of a world
 * position. Move the fixture and every carpet, every glow pool, every pillar
 * jitter and every lining pocket lands somewhere else, so a latent bug shows up
 * for some placements and not others and no failure ever reproduces. A test whose
 * input changes between runs is not a test. {@link #laneFor} gives each fixture a
 * fixed absolute anchor instead, so a failure is the same failure every time.</p>
 *
 * <p>The lanes are also {@link #LANE_SPACING} apart, which is thirty-two chunks:
 * the fixtures no longer share a chunk, so nothing one test leaves behind - blocks,
 * or a {@code BurrowLedger} on a chunk - can reach another. The height bands below
 * predate that and are kept as belt and braces; they were the only separation when
 * the grid put tests six blocks apart, which is far less than a fixture is
 * wide.</p>
 *
 * <p><strong>Far from spawn, and far from the runner's own grid.</strong> The
 * anchor is deliberately thousands of blocks out. Flat-world generation is cheap,
 * and the distance means no fixture can ever meet a test structure, a spawn
 * platform, or anything else the runner puts in the level.</p>
 */
public final class BurrowGameTests {

    // --- Fixture geometry -----------------------------------------------------

    /** Overworld length of a fixture run. {@link BurrowGeometry#SCALE} turns it into twelve blocks down below. */
    private static final int RUN_LENGTH = 3;

    /**
     * And the length of the run the reconciler test carves.
     *
     * <p>Twenty-four blocks down below, which is longer than a chunk is wide - so
     * the run crosses at least one chunk border whatever the fixture's anchor is
     * aligned to, which the test asserts before it measures anything.</p>
     */
    private static final int CROSSING_RUN_LENGTH = 6;

    /**
     * Overworld depth each block test digs at, chosen so their burrow heights are
     * far apart.
     *
     * <p>Belt and braces since the fixtures moved into lanes of their own - see the
     * class javadoc. It used to be the only thing keeping two fixtures apart, and
     * that was never enough on its own: two rooms in one chunk also share the
     * {@code BurrowLedger} attachment, which no height band separates.</p>
     */
    private static final int CARVE_DEPTH = 64;

    private static final int RECOGNISE_DEPTH = 100;

    /**
     * And the depth the reconciler test digs at. Its burrow height is 170, which
     * is a clear band away from the other two fixtures at 128 and 200.
     */
    private static final int RECONCILE_DEPTH = 85;

    /**
     * And the depth the nest test digs at.
     *
     * <p>Its burrow height is 100: a nest is twelve blocks tall with its lining
     * either side, so it needs about twenty-five, and the lowest fixture above it
     * starts at 125.</p>
     */
    private static final int NEST_DEPTH = 50;

    /**
     * The height the fortress mound fixture builds its patch of ground at.
     *
     * <p>Nothing to do with the burrow: this one is an overworld test and only needs
     * a block of dirt with air over it. Well above the flat world's own surface so
     * the fixture never meets it, and in a lane of its own like the rest.</p>
     */
    private static final int FORTRESS_TEST_Y = 96;

    /**
     * Half the width of the run these tests carve, which is a main run.
     *
     * <p>Taken from the profile rather than from {@code CORRIDOR_WIDTH}: since
     * runs got a profile per level, the geometry constant is only the default and
     * a backbone is deliberately wider than it. The test that caught this was
     * asserting the old constant and failing on a change that was correct.</p>
     */
    private static final int CORRIDOR_RADIUS = CorridorProfile.of(RunLevel.MAIN).radius();

    /**
     * The furthest a main run can ever reach sideways from the straight line
     * between two waypoints.
     *
     * <p>Not {@link #CORRIDOR_RADIUS}. A corridor wanders off the line and swells
     * as it goes, so the far wall of a five wide run is not always at the radius,
     * and an assertion that nothing outside the radius was carved fails on a
     * corridor that is behaving exactly as designed. This is the envelope, which
     * is what "the carve cannot have touched this block" has to be measured
     * against.</p>
     */
    private static final int CORRIDOR_OUTER_RADIUS = CorridorProfile.of(RunLevel.MAIN).outerRadius();

    /**
     * Deep earth left either side of the corridor: enough for the decorator to
     * find a wall and stop, and one block further out than the wall sample the
     * carving test takes at {@link #CORRIDOR_OUTER_RADIUS} plus one.
     */
    private static final int SIDE_PAD = CORRIDOR_OUTER_RADIUS + 2;

    /** Deep earth under the floor. The decorator probes one block below it before it sinks a seep. */
    private static final int FLOOR_PAD = 3;

    /** Deep earth above the ceiling, so the ceiling is a block rather than the edge of the fixture. */
    private static final int ROOF_PAD = 3;

    private static final int FILL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** A colony id no other test uses, so the store assertions can count. */
    private static final int TEST_COLONY = 4242;

    /**
     * Where the block fixtures are built, regardless of where the runner put the
     * test.
     *
     * <p>Fixed, and that is the whole point - see the class javadoc. Far enough out
     * that nothing the runner places can reach it, and a multiple of
     * {@link BurrowGeometry#SCALE} so that inverting a burrow position back to an
     * overworld mound is exact rather than off by the rounding.</p>
     */
    private static final int FIXTURE_ANCHOR = 8192;

    /**
     * How far apart two fixtures are built.
     *
     * <p>Thirty-two chunks. It only has to exceed the widest fixture - the nest, at
     * about thirty-three blocks - but there is no reason to be tight about empty
     * flat world, and chunk-aligned separation means two fixtures can never share a
     * chunk and therefore never share a {@code BurrowLedger} either.</p>
     */
    private static final int LANE_SPACING = 512;

    private BurrowGameTests() {
    }

    /**
     * The fixed anchor for one test's fixture.
     *
     * <p>One lane per block test, numbered rather than named so that adding a test
     * is adding a number. The y is not set here: each fixture derives its own from
     * the depth constant it digs at.</p>
     */
    private static BlockPos laneFor(int lane) {
        return new BlockPos(FIXTURE_ANCHOR + lane * LANE_SPACING, 0, FIXTURE_ANCHOR);
    }

    // --- 1. Geometry ----------------------------------------------------------

    /**
     * The map between the two worlds, in both directions.
     *
     * <p>Pure arithmetic, so this must never fail and must never need a world.
     * Overworld to burrow and back is exact in all three axes - the vertical
     * included, because {@code burrowY} multiplies before {@code overworldY}
     * divides. The other way round is not exact and cannot be: a burrow position
     * carries detail no overworld position can hold. What is asserted there is
     * that the loss is bounded by the scale and that a second pass changes
     * nothing, which is the property callers such as
     * {@code CorridorCarver.alreadyCarved} actually rely on.</p>
     */
    public static void geometryRoundTrip(GameTestHelper helper) {
        helper.assertTrue(BurrowGeometry.CORRIDOR_WIDTH % 2 == 1,
                "CORRIDOR_WIDTH must stay odd, or a corridor has no centre line to lay a floor on");
        helper.assertTrue(BurrowGeometry.burrowY(BurrowGeometry.OVERWORLD_DATUM) == BurrowGeometry.BURROW_DATUM,
                "sea level must land on the burrow datum");
        helper.assertTrue(BurrowGeometry.overworldY(BurrowGeometry.BURROW_DATUM) == BurrowGeometry.OVERWORLD_DATUM,
                "the burrow datum must land back on sea level");

        // Outside the representable band the mapping clamps rather than running
        // off the end of the dimension. What matters is that it stays inside,
        // not that it comes back - a corridor carved at a height that does not
        // exist fails silently and buries whoever arrives there.
        for (int overworldY : clampedHeights()) {
            int mapped = BurrowGeometry.burrowY(overworldY);
            helper.assertTrue(mapped >= BurrowGeometry.MIN_BURROW_Y && mapped <= BurrowGeometry.MAX_BURROW_Y,
                    "y=" + overworldY + " mapped out of the burrow to " + mapped);
        }

        for (BlockPos overworld : overworldSamples()) {
            BlockPos burrow = BurrowGeometry.toBurrow(overworld);
            BlockPos back = BurrowGeometry.toOverworld(burrow);
            helper.assertTrue(back.equals(overworld),
                    "round trip lost " + overworld.toShortString()
                            + ": burrow " + burrow.toShortString() + " came back as " + back.toShortString());

            helper.assertTrue(burrow.getY() == BurrowGeometry.burrowY(overworld.getY()),
                    "toBurrow and burrowY disagree at y=" + overworld.getY());
            helper.assertTrue(back.getY() == BurrowGeometry.overworldY(burrow.getY()),
                    "toOverworld and overworldY disagree at y=" + burrow.getY());

            // The Vec3 overload is a second copy of the same formula and has to
            // agree with the block one wherever both are defined.
            Vec3 asVec = BurrowGeometry.toBurrow(
                    new Vec3(overworld.getX(), overworld.getY(), overworld.getZ()));
            helper.assertTrue(asVec.x == burrow.getX() && asVec.y == burrow.getY() && asVec.z == burrow.getZ(),
                    "the Vec3 and BlockPos overloads of toBurrow disagree at " + overworld.toShortString());
        }

        for (BlockPos burrow : burrowSamples()) {
            BlockPos snapped = BurrowGeometry.toBurrow(BurrowGeometry.toOverworld(burrow));
            helper.assertTrue(Math.abs(burrow.getX() - snapped.getX()) < BurrowGeometry.SCALE
                            && Math.abs(burrow.getZ() - snapped.getZ()) < BurrowGeometry.SCALE,
                    "horizontal snap of " + burrow.toShortString() + " to " + snapped.toShortString()
                            + " exceeded the scale");
            helper.assertTrue(Math.abs(burrow.getY() - snapped.getY()) < BurrowGeometry.VERTICAL_SCALE,
                    "vertical snap of " + burrow.toShortString() + " to " + snapped.toShortString()
                            + " exceeded the vertical scale");

            BlockPos again = BurrowGeometry.toBurrow(BurrowGeometry.toOverworld(snapped));
            helper.assertTrue(again.equals(snapped),
                    "snapping " + burrow.toShortString() + " twice moved it: "
                            + snapped.toShortString() + " then " + again.toShortString());
        }

        helper.succeed();
    }

    /** Sea level, either side of it, the build height limits, and negatives on every axis. */
    private static List<BlockPos> overworldSamples() {
        return List.of(
                new BlockPos(0, BurrowGeometry.OVERWORLD_DATUM, 0),
                new BlockPos(1, BurrowGeometry.OVERWORLD_DATUM + 1, 1),
                new BlockPos(-1, BurrowGeometry.OVERWORLD_DATUM - 1, -1),
                new BlockPos(7, 8, -7),
                new BlockPos(-13, 110, 29),
                new BlockPos(1000, 5, -1000),
                new BlockPos(-1000, 115, 1000));
    }

    /**
     * Heights outside what the burrow can hold. The mapping clamps them on
     * purpose - the dimension is 256 blocks tall at twice the scale, so a
     * superflat world at -60 and a mountain colony at 200 both fall off the end
     * - and the round trip is lossy there by design.
     */
    private static List<Integer> clampedHeights() {
        return List.of(-64, -60, 0, 200, 319);
    }

    /** Positions that are deliberately not on a multiple of the scale, and not all above the datum. */
    private static List<BlockPos> burrowSamples() {
        return List.of(
                new BlockPos(0, BurrowGeometry.BURROW_DATUM, 0),
                new BlockPos(1, BurrowGeometry.BURROW_DATUM + 1, 3),
                new BlockPos(-1, BurrowGeometry.BURROW_DATUM - 1, -3),
                // Inside the band the mapping can represent. Outside it the
                // clamp takes over and the snap is deliberately larger than the
                // scale, which the clamp check above covers instead.
                new BlockPos(-5, BurrowGeometry.MIN_BURROW_Y, 5),
                new BlockPos(4001, BurrowGeometry.MAX_BURROW_Y, -4003));
    }

    // --- 2. Carving -----------------------------------------------------------

    /**
     * A run dug above ground becomes a corridor you can walk down below it.
     *
     * <p>The centre line is checked at head height rather than at the floor,
     * because {@code TunnelDecorator} runs as part of a carve and is allowed to
     * put a moss carpet or a gravel boulder on the floor. Head height on the
     * centre line is the one column it guarantees it never fills, and together
     * with a floor that is still solid that is exactly the claim "you can walk
     * down there".</p>
     *
     * <p>The walls are checked too. A corridor that came out three times too wide
     * would pass every assertion about air.</p>
     */
    public static void carvingClearsGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BurrowLink link = straightRun(laneFor(0), CARVE_DEPTH, 4);

        BlockPos start = burrowPoint(link, 0);
        BlockPos end = burrowPoint(link, link.pointCount() - 1);
        helper.assertTrue(start.getY() == end.getY() && start.getZ() == end.getZ() && end.getX() > start.getX(),
                "fixture is not the straight run along +X it is supposed to be: "
                        + start.toShortString() + " to " + end.toShortString());

        bury(level, start, end);

        int walkY = start.getY();
        int z = start.getZ();
        for (int x = start.getX(); x <= end.getX(); x++) {
            helper.assertTrue(isDeepEarth(level, new BlockPos(x, walkY + 1, z)),
                    "the fixture failed to bury " + new BlockPos(x, walkY + 1, z).toShortString()
                            + " - most likely an unloaded chunk");
        }

        int cleared = CorridorCarver.carve(level, link);
        helper.assertTrue(cleared > 0, "carving the run cleared nothing at all");

        for (int x = start.getX(); x <= end.getX(); x++) {
            BlockPos head = new BlockPos(x, walkY + 1, z);
            helper.assertTrue(level.getBlockState(head).isAir(),
                    "corridor centre line is blocked at head height " + head.toShortString()
                            + " by " + level.getBlockState(head));

            BlockPos floor = new BlockPos(x, walkY - 1, z);
            helper.assertTrue(!level.getBlockState(floor).isAir(),
                    "the carve ate the floor under " + head.toShortString());
        }

        // Walls, sampled at the middle of the run where the corridor is at full
        // width in both directions. Not "still deep earth": the decorator speckles
        // a wall with other solids, which is fine - over-carving is what is wrong.
        //
        // One block outside the envelope rather than one outside the radius. The
        // organic section wanders and swells, so a block at radius plus one is
        // inside what the run is allowed to reach and asserting it survives is
        // asserting the corridor is a pipe.
        int middleX = (start.getX() + end.getX()) / 2;
        for (int side = -1; side <= 1; side += 2) {
            BlockPos wall = new BlockPos(middleX, walkY + 1, z + side * (CORRIDOR_OUTER_RADIUS + 1));
            helper.assertTrue(!level.getBlockState(wall).isAir(),
                    "the corridor is wider than its envelope: " + wall.toShortString() + " was carved away");
        }

        // Carving is meant to be idempotent, which is what lets a mole travel the
        // same run twice without eating whatever the first pass put there.
        helper.assertTrue(CorridorCarver.carve(level, link) == 0,
                "carving the same run twice cleared blocks the second time round");

        helper.succeed();
    }

    /**
     * {@code alreadyCarved} answers false before the run exists and true after.
     *
     * <p>Two waypoints rather than four, on purpose. {@code alreadyCarved} probes
     * the middle waypoint, which for a two point link is the far end of the run -
     * and the far end is outside the reach of the decoration pass that a carve
     * also triggers. With four waypoints the probe would land in the middle of the
     * decorated stretch, where a moss carpet on the floor can legitimately cover
     * it and turn this test flaky a few runs in a hundred.</p>
     */
    public static void carvingIsRecognised(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BurrowLink link = straightRun(laneFor(1), RECOGNISE_DEPTH, 2);

        BlockPos start = burrowPoint(link, 0);
        BlockPos end = burrowPoint(link, link.pointCount() - 1);
        bury(level, start, end);

        helper.assertTrue(!CorridorCarver.alreadyCarved(level, link),
                "alreadyCarved says yes to a run through solid deep earth");

        helper.assertTrue(CorridorCarver.carve(level, link) > 0, "carving the run cleared nothing at all");

        helper.assertTrue(CorridorCarver.alreadyCarved(level, link),
                "alreadyCarved says no to a run that was just carved");

        helper.succeed();
    }

    // --- Fixture --------------------------------------------------------------

    /**
     * A flat run of {@code waypoints} points along +X, anchored beside the test
     * area.
     *
     * <p>The ends are inverted out of burrow space rather than picked freely, so
     * that the corridor the carver derives from them lands in the chunks the test
     * can reach. That costs up to {@link BurrowGeometry#SCALE} blocks of drift,
     * which does not matter - what the corridor is measured against is where the
     * link says its waypoints are, not where the test area is.</p>
     */
    private static BurrowLink straightRun(BlockPos origin, int depth, int waypoints) {
        return straightRun(origin, depth, waypoints, RUN_LENGTH);
    }

    /** The same, over a length the caller chooses - which is how a run is made to cross a chunk border. */
    private static BurrowLink straightRun(BlockPos origin, int depth, int waypoints, int length) {
        BlockPos a = new BlockPos(
                Math.floorDiv(origin.getX(), BurrowGeometry.SCALE),
                depth,
                Math.floorDiv(origin.getZ(), BurrowGeometry.SCALE));
        BlockPos b = a.offset(length, 0, 0);
        return new BurrowLink(TEST_COLONY, a, b, RunLevel.MAIN,
                Collections.nCopies(waypoints, depth), 1, 0L);
    }

    /** Where waypoint {@code index} of a link lands in the burrow. The carver's own two lines. */
    private static BlockPos burrowPoint(BurrowLink link, int index) {
        return BurrowGeometry.toBurrow(BlockPos.containing(link.pointAt(index)));
    }

    /**
     * Packs deep earth around the whole run, so that there is something for the
     * carver to clear.
     *
     * <p>The chunks are force loaded first. Both ends are corridor centres, so the
     * box has to reach a corridor's width sideways, a corridor's height up and a
     * little below the floor, plus the margin the decorator's probes need to find
     * a wall and a ceiling instead of running off into open air.</p>
     */
    private static void bury(ServerLevel level, BlockPos start, BlockPos end) {
        BoundingBox box = fixtureBox(start, end);

        for (ChunkPos chunk : chunksOf(box)) {
            level.setChunkForced(chunk.x, chunk.z, true);
        }

        fill(level, box, ModBlocks.DEEP_EARTH.get().defaultBlockState());
    }

    /**
     * The block of earth a run of these two ends is buried in.
     *
     * <p>Named, rather than computed inside {@link #bury}, because the reconciler
     * test needs the same box twice more: once to work out which chunks the run
     * touches, and once to put the earth back before the second carve.</p>
     */
    private static BoundingBox fixtureBox(BlockPos start, BlockPos end) {
        return new BoundingBox(
                Math.min(start.getX(), end.getX()) - SIDE_PAD,
                Math.min(start.getY(), end.getY()) - FLOOR_PAD,
                Math.min(start.getZ(), end.getZ()) - SIDE_PAD,
                Math.max(start.getX(), end.getX()) + SIDE_PAD,
                Math.max(start.getY(), end.getY()) + BurrowGeometry.CORRIDOR_HEIGHT + ROOF_PAD,
                Math.max(start.getZ(), end.getZ()) + SIDE_PAD);
    }

    private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    level.setBlock(cursor.set(x, y, z), state, FILL_FLAGS);
                }
            }
        }
    }

    /** Every chunk the box reaches into, in a fixed order. */
    private static List<ChunkPos> chunksOf(BoundingBox box) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private static boolean isDeepEarth(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DEEP_EARTH.get());
    }

    // --- 3. The link store ----------------------------------------------------

    /**
     * A run that was walked can be found again, in either direction, with its
     * profile intact.
     *
     * <p>Counted relative to whatever the store already holds rather than against
     * zero: the test level is shared and {@code ColonyStore} is per level, not per
     * test.</p>
     */
    public static void linkStoreRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ColonyStore store = ColonyStore.get(level);

        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos a = origin;
        BlockPos b = origin.offset(40, 3, 0);
        BlockPos elsewhere = origin.offset(-40, 0, 17);
        List<Integer> depths = List.of(62, 61, 60, 61, 63);

        int before = store.linkCount();
        store.record(level, TEST_COLONY, a, b, RunLevel.MAIN, depths);
        helper.assertTrue(store.linkCount() == before + 1,
                "recording a run did not add exactly one link: " + before + " -> " + store.linkCount());

        BurrowLink link = store.linkBetween(a, b);
        helper.assertTrue(link != null, "linkBetween found nothing for the pair that was just recorded");
        helper.assertTrue(link.depths().equals(depths),
                "the depths did not survive the store: wrote " + depths + ", read " + link.depths());
        helper.assertTrue(link.pointCount() == depths.size(),
                "pointCount disagrees with the stored depths: " + link.pointCount() + " vs " + depths.size());
        helper.assertTrue(link.level() == RunLevel.MAIN, "the run level did not survive the store");
        helper.assertTrue(link.colony() == TEST_COLONY, "the colony id did not survive the store");
        helper.assertTrue(link.uses() == 1, "a freshly recorded run should have been used once, not " + link.uses());

        // A run has no direction of its own, so both orders have to find it.
        helper.assertTrue(store.linkBetween(b, a) != null, "linkBetween is not symmetric");
        helper.assertTrue(store.levelBetween(a, b) == RunLevel.MAIN, "levelBetween disagrees with the stored link");
        helper.assertTrue(store.linkBetween(a, elsewhere) == null,
                "linkBetween invented a run between mounds that were never joined");

        helper.assertTrue(store.linksOf(TEST_COLONY).contains(link), "linksOf does not list the run");
        helper.assertTrue(store.linksNear(a, 1).contains(link), "linksNear does not list a run that ends on the spot");

        // Walking it again reshapes the existing link instead of adding a second one.
        List<Integer> reshaped = List.of(70, 69, 68, 69, 71);
        store.record(level, TEST_COLONY, b, a, RunLevel.MAIN, reshaped);
        helper.assertTrue(store.linkCount() == before + 1,
                "walking a known run a second time added another link");

        BurrowLink walkedAgain = store.linkBetween(a, b);
        helper.assertTrue(walkedAgain != null, "the run disappeared when it was walked a second time");
        helper.assertTrue(walkedAgain.depths().equals(reshaped),
                "a reshaped run kept its old profile: " + walkedAgain.depths());
        helper.assertTrue(walkedAgain.uses() == 2, "the use count did not rise: " + walkedAgain.uses());
        helper.assertTrue(walkedAgain.level() == RunLevel.MAIN,
                "a reshaped run must keep the level it was dug at");

        helper.succeed();
    }

    // --- 4. The link codec ----------------------------------------------------

    /**
     * A link survives being written to NBT and read back, and an older file that
     * predates the counters still loads.
     *
     * <p>Cheap and worth having, because the failure mode is silent: a codec that
     * cannot read its own file makes {@code DimensionDataStorage} log and hand back
     * null, {@code computeIfAbsent} builds an empty store, and the next save writes
     * it over every colony the world had.</p>
     */
    public static void linkCodecRoundTrip(GameTestHelper helper) {
        BurrowLink link = new BurrowLink(7,
                new BlockPos(10, 64, -20), new BlockPos(-40, 70, 21),
                RunLevel.MAIN, List.of(62, 61, 60, 61), 3, 12345L);

        Tag encoded = BurrowLink.CODEC.encodeStart(NbtOps.INSTANCE, link).getOrThrow();
        BurrowLink decoded = BurrowLink.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(decoded.equals(link), "a link did not survive NBT: " + link + " became " + decoded);

        helper.assertTrue(encoded instanceof CompoundTag, "a link should encode to a compound, got " + encoded);
        CompoundTag olderFile = ((CompoundTag) encoded).copy();
        olderFile.remove("uses");
        olderFile.remove("last_used");

        BurrowLink fromOlderFile = BurrowLink.CODEC.parse(NbtOps.INSTANCE, olderFile).getOrThrow();
        helper.assertTrue(fromOlderFile.uses() == 1 && fromOlderFile.lastUsed() == 0L,
                "a link without the optional counters did not fall back to the defaults: "
                        + fromOlderFile.uses() + ", " + fromOlderFile.lastUsed());
        helper.assertTrue(fromOlderFile.depths().equals(link.depths()),
                "the depth profile did not survive a file without the counters");

        helper.succeed();
    }

    // --- 5. The reconciler ----------------------------------------------------

    /**
     * A run carved one chunk at a time is the same run as one carved in a single
     * unbounded pass.
     *
     * <p>This is the claim the whole chunk-by-chunk design rests on, and it is not
     * obvious: every chunk carves its own clamped quarter of the corridor with its
     * neighbours still solid earth around it, and the pieces have to add up to a
     * corridor rather than to four stubs with seams between them. The run is made
     * long enough to cross a chunk border on purpose - a fixture that fitted
     * inside one chunk would assert nothing.</p>
     *
     * <p>Proved by carving it twice in the same place. The first pass goes through
     * the reconciler, the block of earth is put back, and the second goes through
     * the feature unbounded; the two results are compared block for block. Same
     * link, same coordinates, so the organic section is drawn from the same
     * position hashes both times and any difference is the clamp's doing.</p>
     *
     * <p>The plan is handed in rather than derived from the store. A run recorded
     * in the store brings a chamber at each end with it, and a room nine blocks
     * high at either end of the fixture would drown the thing being measured.</p>
     *
     * <h2>Both phases, and the second one is the one that bites</h2>
     *
     * <p>The earth is the easy half: a clamped carve drops the writes outside its
     * box and nothing it decides depends on what it can see, so the quarters add up
     * by construction. The dressing is where this test earns its keep, because a
     * chunk clamp is sixteen blocks wide and {@code CorridorCarver.decorateRun}
     * dresses every segment centre whose eight-block reach meets the clamp - so a
     * centre near a border is dressed by two chunks, and by four at a corner, while
     * the single pass dresses each one exactly once. The two agree only if
     * {@code TunnelDecorator} is genuinely idempotent.</p>
     *
     * <p>It was not, and this is the test that found it: {@code walkLevel} probed
     * for the walking surface with a bare air test where every other probe in that
     * class uses {@code isOpen}, so a column already carrying a moss carpet
     * measured a block higher on the second dressing and the slice was re-laid one
     * level up, paving its own carpet over with a moss block. Fixed at the probe
     * and at the carpet, which now sits behind the same {@code blocksTheWay} guard
     * the rest of the furniture does. This assertion is what keeps it fixed - it is
     * the only place that dresses a corridor from several chunks and from one, and
     * compares.</p>
     */
    public static void reconcilerCarvesAcrossChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BurrowLink link = straightRun(
                laneFor(2), RECONCILE_DEPTH, 4, CROSSING_RUN_LENGTH);

        BlockPos start = burrowPoint(link, 0);
        BlockPos end = burrowPoint(link, link.pointCount() - 1);
        helper.assertTrue(start.getX() >> 4 != end.getX() >> 4,
                "the fixture run does not cross a chunk border: " + start.toShortString()
                        + " to " + end.toShortString() + " are both in chunk x=" + (start.getX() >> 4));

        BoundingBox box = fixtureBox(start, end);
        List<ChunkPos> chunks = chunksOf(box);
        bury(level, start, end);

        BurrowFeature corridor = new CorridorFeature(link);
        List<BurrowFeature> plan = List.of(corridor);

        // Every chunk of the fixture is inside the corridor's bounds, so every one
        // of them has exactly this one feature to carve and then to dress.
        int applied = BurrowReconciler.reconcileNow(level, chunks, plan);
        helper.assertTrue(applied == 2 * chunks.size(),
                "the reconciler applied " + applied + " feature(s) over " + chunks.size()
                        + " chunk(s), expected one carve and one dressing each");

        for (ChunkPos chunk : chunks) {
            BurrowLedger ledger = ledgerOf(helper, level, chunk);
            helper.assertTrue(ledger.isCarved(corridor.key(), corridor.contentHash()),
                    "chunk " + chunk + " carved the corridor without writing it down");
            helper.assertTrue(ledger.isDecorated(corridor.key()),
                    "chunk " + chunk + " dressed the corridor without writing it down");
        }

        // The ledger's whole purpose: the second visit is free.
        helper.assertTrue(BurrowReconciler.reconcileNow(level, chunks, plan) == 0,
                "a second reconcile pass found work to do on a settled chunk");

        // Whole, not merely equal: a run assembled from clamped quarters could be
        // identical to an unclamped one and still be four stubs if the carver drew
        // it that way. Walked end to end, across the border the fixture was built
        // to straddle. Head height rather than the floor, because the dressing pass
        // is allowed to put a carpet or a boulder underfoot and is not allowed to
        // put anything in the way of a walker.
        int walkY = start.getY();
        for (int x = start.getX(); x <= end.getX(); x++) {
            BlockPos head = new BlockPos(x, walkY + 1, start.getZ());
            helper.assertTrue(level.getBlockState(head).isAir(),
                    "the corridor is blocked at head height " + head.toShortString()
                            + " by " + level.getBlockState(head)
                            + " - a seam where two chunks met");

            BlockPos floor = new BlockPos(x, walkY - 1, start.getZ());
            helper.assertTrue(!level.getBlockState(floor).isAir(),
                    "a clamped carve ate the floor under " + head.toShortString());
        }

        BlockState[] piecemeal = snapshot(level, box);

        fill(level, box, ModBlocks.DEEP_EARTH.get().defaultBlockState());
        corridor.carveWithin(level, null);
        corridor.decorateWithin(level, null);

        String difference = firstDifference(level, box, piecemeal);
        helper.assertTrue(difference == null,
                "carving and dressing the run chunk by chunk did not match doing it in one pass at "
                        + difference);

        helper.succeed();
    }

    /** The ledger a burrow chunk carries, or the empty one where it carries none. */
    private static BurrowLedger ledgerOf(GameTestHelper helper, ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        helper.assertTrue(loaded != null,
                "chunk " + chunk + " is not loaded - the fixture failed to force load it");

        BurrowLedger ledger = loaded.getExistingDataOrNull(ModAttachments.BURROW_LEDGER);
        return ledger == null ? BurrowLedger.EMPTY : ledger;
    }

    /** Every block of the box, in a fixed order, so two passes can be compared position for position. */
    private static BlockState[] snapshot(ServerLevel level, BoundingBox box) {
        BlockState[] states = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int at = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    states[at++] = level.getBlockState(cursor.set(x, y, z));
                }
            }
        }
        return states;
    }

    /**
     * Where the level first disagrees with a snapshot, described, or null where it
     * does not.
     *
     * <p>The description carries the whole column around the mismatch, both sides
     * side by side, because the position alone has never been enough to work out
     * what happened. Every failure this assertion has ever caught was a probe
     * reading one block differently on a second pass - the walking surface measured
     * a block high, a ceiling that stopped being recognised - and which block that
     * was is visible in the column and nowhere else. It also names the chunk-local
     * coordinates, since a mismatch on a chunk border and one in the middle of a
     * chunk have completely different causes.</p>
     */
    private static @Nullable String firstDifference(
            ServerLevel level, BoundingBox box, BlockState[] expected) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int at = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState found = level.getBlockState(cursor.set(x, y, z));
                    if (!found.equals(expected[at])) {
                        return cursor.toShortString() + " (chunk-local " + (x & 15) + "," + (z & 15)
                                + "): one pass at a time left " + expected[at]
                                + ", the single pass left " + found
                                + "\n" + column(level, box, expected, x, y, z);
                    }
                    at++;
                }
            }
        }
        return null;
    }

    /** The column around a mismatch, both passes side by side. */
    private static String column(ServerLevel level, BoundingBox box, BlockState[] expected,
            int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        StringBuilder lines = new StringBuilder("        y | chunk by chunk | single pass\n");

        for (int at = Math.max(box.minY(), y - 3); at <= Math.min(box.maxY(), y + 3); at++) {
            int index = ((x - box.minX()) * box.getYSpan() + (at - box.minY())) * box.getZSpan()
                    + (z - box.minZ());
            lines.append("        ").append(at).append(at == y ? " * " : " | ")
                    .append(expected[index]).append(" | ")
                    .append(level.getBlockState(cursor.set(x, at, z))).append('\n');
        }
        return lines.toString();
    }

    // --- 6. The ledger --------------------------------------------------------

    /**
     * A feature's fingerprint survives its link going to disk and coming back, and
     * so does a ledger of its own.
     *
     * <p>Both halves matter and they fail differently. A fingerprint that changed
     * across a save would have every chunk in the world re-carve its whole share
     * on the next load - wasteful rather than wrong, and invisible except as a
     * stutter. A ledger codec that cannot read its own file is the {@code
     * ColonyStore} failure again one level down: the parse fails, the chunk starts
     * from an empty ledger, and the corridor is cut a second time over whatever a
     * player had built in it.</p>
     */
    public static void ledgerCodecRoundTrip(GameTestHelper helper) {
        BurrowLink link = new BurrowLink(TEST_COLONY,
                new BlockPos(10, 64, -20), new BlockPos(-40, 70, 21),
                RunLevel.MAIN, List.of(62, 61, 60, 61), 3, 12345L);

        BurrowFeature before = new CorridorFeature(link);
        Tag storedLink = BurrowLink.CODEC.encodeStart(NbtOps.INSTANCE, link).getOrThrow();
        BurrowFeature after =
                new CorridorFeature(BurrowLink.CODEC.parse(NbtOps.INSTANCE, storedLink).getOrThrow());

        helper.assertTrue(before.key().equals(after.key()),
                "a corridor was renamed by a save: " + before.key() + " became " + after.key());
        helper.assertTrue(before.contentHash() == after.contentHash(),
                "a corridor's fingerprint changed across a save: "
                        + before.contentHash() + " became " + after.contentHash());

        BurrowLedger ledger = new BurrowLedger(
                Map.of(before.key(), before.contentHash()), Set.of(before.key()));
        Tag encoded = BurrowLedger.CODEC.codec().encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        BurrowLedger decoded = BurrowLedger.CODEC.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow();

        helper.assertTrue(decoded.equals(ledger),
                "a ledger did not survive NBT: " + ledger + " became " + decoded);
        helper.assertTrue(decoded.isCarved(before.key(), before.contentHash()),
                "a ledger forgot which shape it had carved");
        helper.assertTrue(!decoded.isCarved(before.key(), before.contentHash() + 1),
                "a ledger claims to hold a shape it never carved");
        helper.assertTrue(decoded.isDecorated(before.key()),
                "a ledger forgot that it had dressed the corridor");

        // A chunk written before either field existed still reads, rather than
        // failing to parse and taking the chunk's whole record with it.
        BurrowLedger fromOlderFile =
                BurrowLedger.CODEC.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        helper.assertTrue(fromOlderFile.isEmpty(),
                "a ledger without either field did not come back empty: " + fromOlderFile);

        helper.succeed();
    }

    // --- 7. The colony's anatomy ----------------------------------------------

    /**
     * The same colony gives the same nest, whatever order its runs arrive in.
     *
     * <p>The property the whole plan layer rests on, asked of the one feature that
     * is derived from a colony rather than from a single link. A nest is named after
     * the colony, so a hash that moved would not merely re-carve a room - it would
     * re-carve <em>the</em> room, every time the store was rewritten, for the life of
     * the world.</p>
     *
     * <p>The reordering half is the one worth having. Both spurs are aimed at the
     * nearest corridors, and "nearest" is settled by a sort; a sort that fell back on
     * the order the runs were handed over would put the two spurs in different places
     * after a link was rewritten, which changes the fingerprint without anything
     * about the colony having changed.</p>
     *
     * <p>It also holds the depth rule, which is the thing most likely to be
     * "corrected" by somebody reading {@code ChamberFeature.floorAt} and assuming a
     * nest works the same way. It does not: a chamber goes at the deepest run
     * <em>end</em> at its own mound, and a nest goes at the colony's deepest run
     * <em>level</em> measured from the core. See {@code NestCarver.floorOf}.</p>
     */
    public static void nestPlanIsDeterministic(GameTestHelper helper) {
        BlockPos core = new BlockPos(320, 70, -160);
        Colony colony = new Colony(TEST_COLONY, core, 1234L);

        // The two runs pass the core at the same distance on opposite sides, which
        // is the case the sort has to settle without help: whichever spur is cut
        // first has to be decided by where the corridors are and never by which run
        // the store handed over first.
        BurrowLink feeding = new BurrowLink(TEST_COLONY, core.offset(0, 0, -5), core.offset(14, 0, -5),
                RunLevel.FEEDING, List.of(68, 68, 67, 67, 68, 68, 69), 1, 0L);
        BurrowLink main = new BurrowLink(TEST_COLONY, core.offset(0, 0, 5), core.offset(14, 0, 5),
                RunLevel.MAIN, List.of(66, 66, 65, 65, 66, 66, 67), 1, 0L);

        BurrowFeature first = new NestFeature(NestCarver.nestOf(colony, List.of(feeding, main)));
        BurrowFeature again = new NestFeature(NestCarver.nestOf(colony, List.of(feeding, main)));
        BurrowFeature reordered = new NestFeature(NestCarver.nestOf(colony, List.of(main, feeding)));

        helper.assertTrue(first.key().equals("nest:" + TEST_COLONY),
                "a nest is named after its colony and nothing else, got " + first.key());
        helper.assertTrue(matches(first, again),
                "deriving the same nest twice gave two different features");
        helper.assertTrue(matches(first, reordered),
                "the order the store handed the runs over leaked into the nest: "
                        + first.contentHash() + " became " + reordered.contentHash());

        NestCarver.Nest deep = NestCarver.nestOf(colony, List.of(feeding, main));
        NestCarver.Nest shallow = NestCarver.nestOf(colony, List.of(feeding));
        helper.assertTrue(
                shallow.centre().getY()
                        == BurrowGeometry.burrowY(core.getY() - BurrowConstants.DEPTH_FEEDING),
                "a colony that has only fed put its nest at " + shallow.centre().getY());
        helper.assertTrue(
                deep.centre().getY()
                        == BurrowGeometry.burrowY(core.getY() - BurrowConstants.DEPTH_MAIN),
                "a colony with a backbone must put its nest at the backbone's depth, not at "
                        + deep.centre().getY());

        helper.assertTrue(deep.spurs().size() == 2,
                "a nest beside two runs should have two spurs, got " + deep.spurs().size());
        helper.assertTrue(shallow.spurs().size() == 1,
                "a nest beside one run should have one spur, got " + shallow.spurs().size());
        helper.assertTrue(NestCarver.nestOf(colony, List.of()).spurs().isEmpty(),
                "a colony that has dug nothing must not get spurs to nowhere");

        // The bounds have to cover the room and everything hung off it, or a chunk
        // at the rim is never asked and the nest stops at a chunk border.
        BoundingBox bounds = first.bounds();
        helper.assertTrue(bounds.isInside(NestCarver.warmHollow(deep)),
                "the warm hollow fell outside the nest's own bounds");
        for (NestCarver.Spur spur : deep.spurs()) {
            helper.assertTrue(bounds.isInside(spur.to()),
                    "a spur reaches " + spur.to().toShortString() + ", outside the nest's bounds");
        }

        helper.succeed();
    }

    /**
     * The same deep run gives the same larders, and a feeding run gives none.
     *
     * <p>Three claims, and the third is the one that would fail silently. A larder is
     * named by its run and an <em>index</em> along it rather than by where it ended
     * up: a run's length is fixed by the two mounds it joins, so the index set cannot
     * change, while the depth follows whatever ground the run was last measured
     * against. Name a larder after its position and every re-dug run renames all of
     * them, the ledger sees features it has never heard of, and the alcoves are cut a
     * second time beside the first.</p>
     */
    public static void larderPlanIsDeterministic(GameTestHelper helper) {
        BlockPos a = new BlockPos(200, 70, 40);
        BlockPos b = a.offset(14, 0, 0);
        List<Integer> depths = List.of(66, 66, 65, 65, 66, 66, 67);

        BurrowLink main = new BurrowLink(TEST_COLONY, a, b, RunLevel.MAIN, depths, 1, 0L);
        BurrowLink travelledAgain = new BurrowLink(TEST_COLONY, a, b, RunLevel.MAIN, depths, 9, 4242L);
        BurrowLink feeding = new BurrowLink(TEST_COLONY, a, b, RunLevel.FEEDING, depths, 1, 0L);

        List<AlcoveCarver.Larder> larders = AlcoveCarver.lardersOf(main);
        helper.assertTrue(larders.size() == 1,
                "a deep run of fourteen overworld blocks should carry exactly one larder, got "
                        + larders.size());
        helper.assertTrue(AlcoveCarver.lardersOf(feeding).isEmpty(),
                "a feeding run must carry no larders at all");

        List<AlcoveCarver.Larder> unchanged = AlcoveCarver.lardersOf(travelledAgain);
        helper.assertTrue(unchanged.size() == larders.size(),
                "walking a run again changed how many larders it has");
        for (int i = 0; i < larders.size(); i++) {
            helper.assertTrue(matches(new LarderFeature(larders.get(i)),
                            new LarderFeature(unchanged.get(i))),
                    "walking a run again moved larder " + i);
        }

        // A run re-dug through changed ground keeps its larders' names and changes
        // their fingerprints. That is the whole point of naming them by index.
        BurrowLink reshaped = new BurrowLink(TEST_COLONY, a, b, RunLevel.MAIN,
                List.of(60, 60, 59, 59, 60, 60, 61), 2, 0L);
        List<AlcoveCarver.Larder> moved = AlcoveCarver.lardersOf(reshaped);
        helper.assertTrue(moved.size() == larders.size(),
                "re-digging a run changed how many larders it has");

        BurrowFeature before = new LarderFeature(larders.get(0));
        BurrowFeature after = new LarderFeature(moved.get(0));
        helper.assertTrue(before.key().equals(after.key()),
                "re-digging a run renamed its larder: " + before.key() + " became " + after.key());
        helper.assertTrue(before.contentHash() != after.contentHash(),
                "a larder that dropped six blocks kept its fingerprint " + before.contentHash());

        // The alcove buds off the side of the run rather than sitting on it, and its
        // bounds cover it. Both are what let a chunk find it at all.
        AlcoveCarver.Larder larder = larders.get(0);
        helper.assertTrue(larder.z() != a.getZ() * BurrowGeometry.SCALE,
                "the alcove was cut on the corridor's own centre line rather than beside it");
        helper.assertTrue(before.bounds().isInside(new BlockPos(larder.x(), larder.walkY(), larder.z())),
                "a larder fell outside its own bounds");

        helper.succeed();
    }

    /**
     * A nest carved one chunk at a time is the same nest as one carved in a single
     * unbounded pass.
     *
     * <p>{@link #reconcilerCarvesAcrossChunks}'s claim, asked of the largest room in
     * the dimension - and the dressing half is where it bites, exactly as it does for
     * a corridor. A nest is thirty blocks across and so is bedded by nine chunks;
     * every one of them probes for a ceiling twelve blocks up and writes only its own
     * share, and the pieces have to add up to the room a single pass would have made.
     * They do only as long as every probe reads through what the other passes leave
     * standing - see {@code NestCarver.isOpen}, which is the list that makes it
     * true.</p>
     *
     * <p>The fixture nest is given no runs, so it has no spurs. That is not to make
     * the test easier: a spur is a corridor and corridors already have a test of
     * their own, while a spur here would drag the fixture out to whichever chunk the
     * run happened to be in and drown the room in the comparison.</p>
     */
    public static void nestCarvesAcrossChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = laneFor(3);
        BlockPos core = new BlockPos(
                Math.floorDiv(origin.getX(), BurrowGeometry.SCALE), NEST_DEPTH,
                Math.floorDiv(origin.getZ(), BurrowGeometry.SCALE));

        NestCarver.Nest nest = NestCarver.nestOf(new Colony(TEST_COLONY, core, 0L), List.of());
        BurrowFeature feature = new NestFeature(nest);
        helper.assertTrue(nest.spurs().isEmpty(), "the fixture nest grew spurs it was given no runs for");

        BoundingBox box = feature.bounds();
        List<ChunkPos> chunks = chunksOf(box);
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x, chunk.z, true);
        }
        fill(level, box, ModBlocks.DEEP_EARTH.get().defaultBlockState());

        List<BurrowFeature> plan = List.of(feature);
        int applied = BurrowReconciler.reconcileNow(level, chunks, plan);
        helper.assertTrue(applied == 2 * chunks.size(),
                "the reconciler applied " + applied + " feature(s) over " + chunks.size()
                        + " chunk(s), expected one carve and one dressing each");
        helper.assertTrue(BurrowReconciler.reconcileNow(level, chunks, plan) == 0,
                "a second reconcile pass found work to do on a settled nest");

        // The height contract, read from this side: the block a corridor's ceiling
        // probe would stop at is still open air, which is what makes the decoration
        // sweep leave the room to whoever furnishes rooms.
        BlockPos overhead = nest.centre().above(BurrowGeometry.CORRIDOR_HEIGHT + 1);
        helper.assertTrue(level.getBlockState(overhead).isAir(),
                "the nest is no taller than a corridor at " + overhead.toShortString()
                        + ": " + level.getBlockState(overhead));

        // The warm hollow's signature, which the loot wave hangs off: a moss lid over
        // a course of larders over a course of nodules.
        BlockPos hollow = NestCarver.warmHollow(nest);
        int side = NestCarver.WARM_HOLLOW_SIDE;
        int larders = 0;
        int nodules = 0;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                BlockPos lid = hollow.offset(dx, 0, dz);
                helper.assertTrue(level.getBlockState(lid).is(Blocks.MOSS_BLOCK),
                        "the warm hollow's lid is not moss at " + lid.toShortString()
                                + ": " + level.getBlockState(lid));

                for (int course = 1; course <= NestCarver.WARM_HOLLOW_DEPTH; course++) {
                    BlockPos at = lid.below(course);
                    BlockState state = level.getBlockState(at);
                    if (state.is(ModBlocks.WORM_LARDER.get())) {
                        larders++;
                    } else if (state.is(ModBlocks.ROOT_NODULE.get())) {
                        nodules++;
                    } else {
                        helper.fail("the trove under the warm hollow holds " + state
                                + " at " + at.toShortString());
                    }
                }
            }
        }
        helper.assertTrue(larders == side * side && nodules == side * side,
                "the trove should be a full course of larders over a full course of nodules, found "
                        + larders + " larder(s) and " + nodules + " nodule(s)");

        // And the light the trove is worth anything because of. Not measured as
        // brightness: the light engine runs on the chunk source's own tick and this
        // body finishes inside one, so a brightness read here would be asserting the
        // scheduler rather than the room. What is asserted is the geometry the drop
        // actually depends on - every lid square has a source orthogonally against
        // it, so the air a broken lid leaves reads light eight and the larder under
        // it passes its gate. A source one block further away reads seven and none
        // of them would.
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                BlockPos lid = hollow.offset(dx, 0, dz);
                boolean lit = false;
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    lit |= level.getBlockState(lid.relative(face)).is(ModBlocks.GLOW_MYCELIUM.get());
                }
                helper.assertTrue(lit, "no light stands against the lid square at "
                        + lid.toShortString() + ", so the larder under it can never be lit");
            }
        }

        BlockState[] piecemeal = snapshot(level, box);

        fill(level, box, ModBlocks.DEEP_EARTH.get().defaultBlockState());
        feature.carveWithin(level, null);
        feature.decorateWithin(level, null);

        String difference = firstDifference(level, box, piecemeal);
        helper.assertTrue(difference == null,
                "cutting and bedding the nest chunk by chunk did not match doing it in one pass at "
                        + difference);

        helper.succeed();
    }

    // --- 8. The fortress mound ------------------------------------------------

    /**
     * The heap on a colony's core mound goes up, comes down, and never makes the
     * mound stop being a mound.
     *
     * <p>All three halves have already been the failure. The property is on
     * {@code MoleMound} and not on {@code PreparedMoleMound}, so a guard written
     * against the {@code MOLE_MOUNDS} tag would read it off a state that has none
     * and throw the moment a mole opened a shored-up mound - which is a crash in
     * mob AI, on a server, some hours into a world. The shaft and the heap are two
     * properties of one block and each has to survive the other being set. And a
     * fortress that stopped answering {@link MoleMound#isMound} would drop out of
     * the colony, the network and the way home all at once.</p>
     *
     * <p>The block half only. Which mound gets the heap is
     * {@code MoleBurrowGoal.raiseFortress}, and reaching it needs a mole, a colony
     * and a completed trip - a fixture several times the size of this file, testing
     * a two-line comparison. What is left untested there is deliberate and worth
     * knowing about.</p>
     */
    public static void fortressMoundKeepsItsMound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = laneFor(4).atY(FORTRESS_TEST_Y);
        BlockPos mound = ground.above();

        for (ChunkPos chunk : List.of(new ChunkPos(ground))) {
            level.setChunkForced(chunk.x, chunk.z, true);
        }
        level.setBlock(ground, Blocks.DIRT.defaultBlockState(), FILL_FLAGS);
        level.setBlock(mound, Blocks.AIR.defaultBlockState(), FILL_FLAGS);

        helper.assertTrue(MoleMound.tryPlace(level, mound, false),
                "the fixture failed to place a mound on dirt at " + mound.toShortString());
        helper.assertTrue(!level.getBlockState(mound).getValue(MoleMound.FORTRESS),
                "a freshly dug mound came out as a fortress");

        MoleMound.setFortress(level, mound, true);
        helper.assertTrue(level.getBlockState(mound).getValue(MoleMound.FORTRESS),
                "the heap did not go up: " + level.getBlockState(mound));

        // Still a mound to everything that asks - the tag, and therefore the
        // colony, the network, the point of interest and the way home.
        helper.assertTrue(MoleMound.isMound(level, mound),
                "a fortress mound stopped reading as a mound");

        // The two properties are independent. A mole going down its own core has
        // to be able to open the shaft without flattening the heap.
        MoleMound.setOpen(level, mound, true);
        BlockState open = level.getBlockState(mound);
        helper.assertTrue(open.getValue(MoleMound.OPEN) && open.getValue(MoleMound.FORTRESS),
                "opening the shaft of a fortress mound lost one of the two: " + open);

        MoleMound.setFortress(level, mound, false);
        BlockState flattened = level.getBlockState(mound);
        helper.assertTrue(!flattened.getValue(MoleMound.FORTRESS) && flattened.getValue(MoleMound.OPEN),
                "flattening the heap took the open shaft with it: " + flattened);

        // The guard that keeps this off the block without the property. A prepared
        // mound is in the same tag and carries OPEN but not FORTRESS, so this must
        // be a quiet no-op rather than an exception.
        level.setBlock(mound, ModBlocks.PREPARED_MOLE_MOUND.get().defaultBlockState(), FILL_FLAGS);
        MoleMound.setFortress(level, mound, true);
        helper.assertTrue(level.getBlockState(mound).is(ModBlocks.PREPARED_MOLE_MOUND.get()),
                "setFortress changed a prepared mound instead of leaving it alone");

        // And on nothing at all, which is a mound a player levelled between the
        // mole deciding to use it and getting there.
        level.setBlock(mound, Blocks.AIR.defaultBlockState(), FILL_FLAGS);
        MoleMound.setFortress(level, mound, true);
        helper.assertTrue(level.getBlockState(mound).isAir(),
                "setFortress built something where the mound had been broken");

        helper.succeed();
    }

    /** Whether two derivations of one feature agree about all three questions a chunk asks. */
    private static boolean matches(BurrowFeature one, BurrowFeature other) {
        return one.key().equals(other.key())
                && one.contentHash() == other.contentHash()
                && one.bounds().equals(other.bounds());
    }
}

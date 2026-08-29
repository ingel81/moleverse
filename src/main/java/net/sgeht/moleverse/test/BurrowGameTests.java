package net.sgeht.moleverse.test;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.CorridorCarver;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.burrow.RunLevel;
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
 * <h2>Two rules the fixtures follow</h2>
 *
 * <p><strong>Force load what you write to.</strong> The runner force loads only
 * the chunks its structure touches, and every one of the carver's writes is
 * guarded by {@code isLoaded} - so a carve into an unloaded chunk does not fail,
 * it silently does nothing and the test passes for the wrong reason. The chunks
 * are never released again: the runner drops every forced chunk in the level when
 * the batch ends, and releasing one by hand could pull it out from under a test
 * that has not ticked yet.</p>
 *
 * <p><strong>One height band per test.</strong> The grid spawner puts tests six
 * blocks apart, which is far less than the width of a corridor fixture, so two
 * block tests placed side by side would trample each other. They are separated in
 * y instead, which the spawner never varies.</p>
 */
public final class BurrowGameTests {

    // --- Fixture geometry -----------------------------------------------------

    /** Overworld length of a fixture run. {@link BurrowGeometry#SCALE} turns it into twelve blocks down below. */
    private static final int RUN_LENGTH = 3;

    /**
     * Overworld depth each block test digs at, chosen so their burrow heights are
     * far apart. Nothing else distinguishes the two fixtures, and they share chunks.
     */
    private static final int CARVE_DEPTH = 64;

    private static final int RECOGNISE_DEPTH = 100;

    /** Sideways reach of a corridor from its centre line. Mirrors the carver's own, which is private. */
    private static final int CORRIDOR_RADIUS = (BurrowGeometry.CORRIDOR_WIDTH - 1) / 2;

    /** Deep earth left either side of the corridor: enough for the decorator to find a wall and stop. */
    private static final int SIDE_PAD = CORRIDOR_RADIUS + 4;

    /** Deep earth under the floor. The decorator probes one block below it before it sinks a seep. */
    private static final int FLOOR_PAD = 3;

    /** Deep earth above the ceiling, so the ceiling is a block rather than the edge of the fixture. */
    private static final int ROOF_PAD = 3;

    private static final int FILL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** A colony id no other test uses, so the store assertions can count. */
    private static final int TEST_COLONY = 4242;

    private BurrowGameTests() {
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
        BurrowLink link = straightRun(helper.absolutePos(BlockPos.ZERO), CARVE_DEPTH, 4);

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
        int middleX = (start.getX() + end.getX()) / 2;
        for (int side = -1; side <= 1; side += 2) {
            BlockPos wall = new BlockPos(middleX, walkY + 1, z + side * (CORRIDOR_RADIUS + 1));
            helper.assertTrue(!level.getBlockState(wall).isAir(),
                    "the corridor is wider than CORRIDOR_WIDTH: " + wall.toShortString() + " was carved away");
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
        BurrowLink link = straightRun(helper.absolutePos(BlockPos.ZERO), RECOGNISE_DEPTH, 2);

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
        BlockPos a = new BlockPos(
                Math.floorDiv(origin.getX(), BurrowGeometry.SCALE),
                depth,
                Math.floorDiv(origin.getZ(), BurrowGeometry.SCALE));
        BlockPos b = a.offset(RUN_LENGTH, 0, 0);
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
        int minX = Math.min(start.getX(), end.getX()) - SIDE_PAD;
        int maxX = Math.max(start.getX(), end.getX()) + SIDE_PAD;
        int minZ = Math.min(start.getZ(), end.getZ()) - SIDE_PAD;
        int maxZ = Math.max(start.getZ(), end.getZ()) + SIDE_PAD;
        int minY = Math.min(start.getY(), end.getY()) - FLOOR_PAD;
        int maxY = Math.max(start.getY(), end.getY()) + BurrowGeometry.CORRIDOR_HEIGHT + ROOF_PAD;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
            }
        }

        BlockState earth = ModBlocks.DEEP_EARTH.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(cursor.set(x, y, z), earth, FILL_FLAGS);
                }
            }
        }
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
}

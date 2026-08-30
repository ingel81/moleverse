package net.sgeht.moleverse.dimension;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The short escape shafts that climb off a corridor and stop.
 *
 * <p>Real burrows have them: a steep, narrow stub leaving a run and heading for the
 * surface, dug so that a mole cornered in its own tunnels has somewhere to go that
 * is not along them. They are the one piece of a colony's anatomy that exists
 * purely because something might be chasing you, which is exactly the reading a
 * burrow with no live animal in it needs.</p>
 *
 * <h2>What it does not do, said plainly</h2>
 *
 * <p><strong>A bolt-hole cannot reach the overworld, and the plan that asked for
 * one was wrong about that.</strong> {@code docs/BURROW_LIFE.md} describes it as
 * "ending 2-3 blocks under the overworld surface" so that a player could dig the
 * last metre and surface. There is no such surface to end under. The burrow is its
 * own 256 block box - see {@code docs/BURROW.md} - and the overworld is a different
 * level entirely, reachable only through a chamber's way home. Everything above
 * {@link BurrowGeometry#MAX_BURROW_Y} is the top of a closed dimension and nothing
 * is on the other side of it.</p>
 *
 * <p>So this is what a bolt-hole actually is, and it is worth having on its own
 * terms: a stub that climbs well above the corridor it leaves - {@link #MAX_CLIMB}
 * blocks, three corridor heights - and ends in a plug of loose soil with a patch of
 * light grown into it. From inside the run you see a lit shaft going up and away,
 * which reads as a way out; standing at the top of it you are in diggable soil,
 * which is a vertical pocket worth a spade and the only place in the dimension
 * where you are looking at earth <em>above</em> you rather than beside you. What it
 * is not is an exit, and nothing here pretends otherwise.</p>
 *
 * <h2>The shape</h2>
 *
 * <p>Two blocks across and climbing a block for every block it travels, which is a
 * staircase at forty-five degrees: each slice leaves the block below it standing,
 * exactly as a corridor does, so the risers are one block and the whole thing can
 * be walked up. A shaft any steeper is a hole you fall down rather than a tunnel
 * something fled along.</p>
 *
 * <p>It leaves the corridor level for the first {@link #LEVEL_STEPS} slices, and
 * that is load bearing rather than cosmetic. The first slice sits on the corridor's
 * own centre line so the two are certainly joined whatever the run's wander and
 * swell did to its section - and {@link #BORE_HEIGHT} four is below
 * {@link BurrowGeometry#CORRIDOR_HEIGHT} six, so the corridor's own ceiling over
 * that column is left intact and {@code TunnelDecorator}'s ceiling probe still
 * finds it. A stub that opened the corridor's roof would have the sweep read the
 * slice as a room and stop dressing the run.</p>
 *
 * <h2>Rare, and hashed</h2>
 *
 * <p>About one run in {@link #CHANCE} gets one. Not every run: a colony where every
 * corridor sprouts a shaft is a colony of shafts, and the point of a bolt-hole is
 * that coming across one is a thing that happens rather than a thing you expect.
 * The roll, the bearing and the place along the run are all hashes of block
 * positions, so a colony's bolt-holes are a property of what it dug and come back
 * identical however often the ground is reconciled.</p>
 */
public final class BoltHoles {

    /**
     * Share of runs that get a bolt-hole.
     *
     * <p>Two in five, which is about one per two or three runs once the runs too
     * short or too high to take one have dropped out. Any lower and a colony of a
     * dozen runs might have none at all, which makes the feature something a player
     * hears about rather than finds.</p>
     */
    private static final float CHANCE = 0.4F;

    /**
     * How far the bore reaches from its own middle.
     *
     * <p>A half, at a centre offset half a block on both axes, which is the integer
     * disc's way of saying two blocks across: the four blocks at the middle are
     * inside it and the next ring is not. Two rather than a corridor's five because
     * a bolt-hole is squeezed into rather than walked down.</p>
     */
    private static final double BORE = 0.5;

    /**
     * Blocks of air above each step.
     *
     * <p>Four. On a forty-five degree climb the slice ahead is a block higher, so
     * what a player actually has over their head is three - comfortable, and still
     * two short of {@link BurrowGeometry#CORRIDOR_HEIGHT}, which is what keeps the
     * corridor's own ceiling intact where the stub leaves it. See the class
     * javadoc.</p>
     */
    private static final int BORE_HEIGHT = 4;

    /**
     * Slices the stub travels before it starts climbing.
     *
     * <p>Two: one on the corridor's centre line, which is the only column certain to
     * be open whatever the run's section did, and one beside it. The climb starts on
     * the third, by which point the bore is clear of the corridor's own width.</p>
     */
    private static final int LEVEL_STEPS = 2;

    /**
     * How far the stub climbs, at most.
     *
     * <p>Twelve, which is three corridor heights and visibly above everything else
     * in the run. It is a shape rather than a limit - the point is that the shaft
     * goes up and away, and a stub that climbed a hundred blocks would be a chimney
     * through the whole dimension rather than an escape tunnel off a burrow.</p>
     */
    private static final int MAX_CLIMB = 12;

    /**
     * And the least it may climb and still be worth cutting.
     *
     * <p>Six. Below that the stub does not clear the corridor's own ceiling, so from
     * inside the run it reads as a dent in the wall rather than as a way up.</p>
     */
    private static final int MIN_CLIMB = 6;

    /**
     * How much soil is left over the top of the stub.
     *
     * <p>Three, measured against {@link BurrowGeometry#MAX_BURROW_Y}. This is the
     * honest cap the plan text was missing: there is nothing above the burrow to
     * break through, and a stub that ran to the very top of the dimension's range
     * would end against the build limit instead of in ground. A colony whose
     * corridors are already near the top gets no bolt-holes at all, which is
     * correct.</p>
     */
    private static final int PLUG_MARGIN = 3;

    /** How far past everything the bounds reach, on {@code CorridorFeature}'s argument. */
    private static final int MARGIN = 2;

    /** Clients yes, neighbours no - {@code CorridorCarver}'s reasoning, and the light in a plug must not ask its neighbours anything. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // Salts. Distinct so that two decisions never agree by accident.
    private static final long SALT_PICK = 0x0B01_7000L;
    private static final long SALT_WHERE = 0x0B01_7001L;
    private static final long SALT_SIDE = 0x0B01_7002L;

    private BoltHoles() {
    }

    /**
     * Where a bolt-hole goes, in burrow space.
     *
     * @param run    the run it leaves. Kept so that whoever holds a stub can say
     *               which run produced it without finding it a second time - the plan
     *               layer names its feature after exactly that
     * @param baseX  the low corner of the two by two bore at the corridor, so the
     *               bore covers {@code baseX} and {@code baseX + 1}
     * @param baseZ  the same
     * @param walkY  the corridor's walking surface here, and the stub's first floor
     * @param rise   how far the stub climbs above that
     * @param stepX  which way it travels while it climbs, as a unit cardinal
     * @param stepZ  the same
     */
    public record Stub(BurrowLink run, int baseX, int baseZ, int walkY, int rise, int stepX, int stepZ) {

        /** How many slices are cut: the level ones, and one per block of climb. */
        public int slices() {
            return this.rise + LEVEL_STEPS;
        }

        /** The topmost layer the bore clears - the block the plug sits on. */
        public int crest() {
            return this.walkY + this.rise + BORE_HEIGHT - 1;
        }

        /**
         * Every block cutting this stub can reach: the bore, the plug over it and the
         * soil lining wrapping all of it.
         *
         * <p>The horizontal covers the whole travel of the climb, because the bore
         * moves a block sideways for every block it rises. A chunk is only asked
         * about a feature whose bounds reach into it, so a box drawn to the first
         * slice would leave the top of the shaft in a chunk that never heard of
         * it.</p>
         */
        public BoundingBox bounds() {
            int travel = this.slices() - 1;
            int reach = CorridorCarver.SHELL_MAX + 1;
            int farX = this.baseX + this.stepX * travel;
            int farZ = this.baseZ + this.stepZ * travel;

            return new BoundingBox(
                    Math.min(this.baseX, farX) - reach,
                    this.walkY - CorridorCarver.SHELL_MAX,
                    Math.min(this.baseZ, farZ) - reach,
                    Math.max(this.baseX, farX) + reach,
                    this.crest() + CorridorCarver.SHELL_MAX,
                    Math.max(this.baseZ, farZ) + reach)
                    .inflatedBy(MARGIN);
        }
    }

    /**
     * The bolt-hole this run has, or null where it has none.
     *
     * <p>Pure arithmetic on the link - no level, no block reads, no world at all, so
     * the answer exists before any of the ground does. {@link Junctions#crossingsOf}
     * and {@link LevelShafts#crossingsOf} are the same seam.</p>
     *
     * <p>Three reasons to answer null, and all three are decided from the link alone:
     * the run did not win the roll, the run is too short to have a middle worth
     * leaving from, or its corridor sits close enough to the top of the dimension
     * that there is no room left to climb. The last is the honest form of "a
     * bolt-hole heads for the surface" - see the class javadoc.</p>
     */
    public static @Nullable Stub on(BurrowLink run) {
        if (run.pointCount() < 4) {
            return null;
        }

        // Somewhere around the middle of the run, jittered by a waypoint either way
        // and never at either end: a stub leaving a mound would climb out of a
        // chamber, which already has a way home in it.
        int middle = run.pointCount() / 2;
        BlockPos anchor = CorridorCarver.burrowPoint(run, middle);
        int index = Math.clamp(middle + jitter(SALT_WHERE, anchor), 1, run.pointCount() - 2);

        BlockPos here = CorridorCarver.burrowPoint(run, index);
        if (CorridorCarver.noise(SALT_PICK, here.getX(), here.getY(), here.getZ()) >= CHANCE) {
            return null;
        }

        int rise = Math.min(MAX_CLIMB,
                BurrowGeometry.MAX_BURROW_Y - PLUG_MARGIN - BORE_HEIGHT + 1 - here.getY());
        if (rise < MIN_CLIMB) {
            return null;
        }

        BlockPos before = CorridorCarver.burrowPoint(run, index - 1);
        BlockPos after = CorridorCarver.burrowPoint(run, index + 1);
        Direction away = bearingOf(before, after, here);
        if (away == null) {
            return null;
        }

        return new Stub(run, here.getX(), here.getZ(), here.getY(), rise,
                away.getStepX(), away.getStepZ());
    }

    /**
     * Which way the stub travels: away from the run, along a cardinal.
     *
     * <p>Across the corridor rather than along it, so the shaft leaves through a wall
     * instead of running up the middle of the tunnel it is supposed to be an
     * alternative to. Snapped to a cardinal because the bore is a square: a
     * two-by-two stepping along a diagonal would touch its own neighbours only at the
     * corners and come out as a ladder of disconnected boxes.</p>
     *
     * <p>Null for a run with no plan bearing at all, which the vertical scale makes
     * possible on a steep hillside - two waypoints can land in the same column, and a
     * stub has no side to leave by there.</p>
     */
    private static @Nullable Direction bearingOf(BlockPos before, BlockPos after, BlockPos here) {
        int dx = after.getX() - before.getX();
        int dz = after.getZ() - before.getZ();
        if (dx == 0 && dz == 0) {
            return null;
        }

        // The perpendicular, then the dominant axis of it. A tie goes to x, which is
        // arbitrary and has only to be settled the same way on every call. Neither
        // component can be zero in the branch that reads it: that would need the run
        // to have no bearing at all, which the test above has already turned away.
        int perpX = -dz;
        int perpZ = dx;
        Direction across = Math.abs(perpX) >= Math.abs(perpZ)
                ? (perpX > 0 ? Direction.EAST : Direction.WEST)
                : (perpZ > 0 ? Direction.SOUTH : Direction.NORTH);

        boolean keep = CorridorCarver.noise(SALT_SIDE, here.getX(), here.getY(), here.getZ()) < 0.5F;
        return keep ? across : across.getOpposite();
    }

    // --- Digging --------------------------------------------------------------

    /**
     * Cuts the stub, writing only inside {@code clamp}.
     *
     * <p>Null is the unbounded case. Every slice is decided from the arithmetic
     * before the box is consulted, so a stub cut chunk by chunk is the same stub as
     * one cut in a single call - and cutting it again costs reads and nothing else,
     * because {@link CorridorCarver#discAndShell} only ever clears ground and lines
     * earth.</p>
     *
     * <p>It does not wait for its corridor. The first slice stands on the corridor's
     * centre line and clears whatever is there, so a stub cut into ground the carver
     * has not reached yet is simply a shaft the run opens into when it arrives.</p>
     */
    public static void dig(ServerLevel burrow, Stub stub, @Nullable BoundingBox clamp) {
        if (clamp != null && misses(clamp, stub.bounds())) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int slice = 0; slice < stub.slices(); slice++) {
            int x = stub.baseX() + stub.stepX() * slice;
            int z = stub.baseZ() + stub.stepZ() * slice;
            int walkY = stub.walkY() + Math.max(0, slice - LEVEL_STEPS + 1);

            // The bore and its lining in one sweep, through the carver's own pass:
            // the walls of a bolt-hole have to be the loose soil every other wall
            // down here is, or the shaft is a hole in bedrock and there is nothing
            // to dig at the top of it. The layers either side cut nothing and line
            // only, which is what closes the skin over the top and under the floor.
            for (int layer = -CorridorCarver.SHELL_MAX;
                    layer < BORE_HEIGHT + CorridorCarver.SHELL_MAX; layer++) {
                int away = layer < 0 ? -layer : Math.max(0, layer - (BORE_HEIGHT - 1));
                CorridorCarver.discAndShell(burrow, x + 0.5, walkY + layer, z + 0.5,
                        BORE, away, cursor, clamp);
            }
        }

        lightThePlug(burrow, stub, cursor, clamp);
    }

    /**
     * Grows a patch of light into the plug at the top of the shaft.
     *
     * <p>The whole of what makes a bolt-hole visible. Without it the stub is an unlit
     * hole in a corridor wall that a player walks past; with it there is a lit column
     * going up and away from the run, which is the escape-tunnel reading the feature
     * exists for - and it is honest about what is up there, because the light is
     * growing on soil rather than shining through it.</p>
     *
     * <p>Raw ground only, which makes it idempotent without a second thought: threads
     * are not ground, so a second visit finds its work done, and a block a player has
     * put up there is left alone.</p>
     */
    private static void lightThePlug(ServerLevel burrow, Stub stub, BlockPos.MutableBlockPos cursor,
            @Nullable BoundingBox clamp) {
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        int travel = stub.slices() - 1;
        int x = stub.baseX() + stub.stepX() * travel;
        int z = stub.baseZ() + stub.stepZ() * travel;
        int y = stub.crest() + 1;

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                grow(burrow, cursor.set(x + dx, y, z + dz), glow, clamp);
            }
        }
    }

    /** Turns one block of plug into light. The dimension's one rule about placement: raw ground and nothing else. */
    private static void grow(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if (clamp != null && !clamp.isInside(pos)) {
            return;
        }
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!existing.is(ModBlocks.DEEP_EARTH.get()) && !existing.is(ModBlocks.LOOSE_SOIL.get())) {
            return;
        }
        burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /** A waypoint either way, so two runs of one colony do not both leave from their exact middles. */
    private static int jitter(long salt, BlockPos at) {
        return (int) (CorridorCarver.noise(salt, at.getX(), at.getY(), at.getZ()) * 3.0F) - 1;
    }

    /** Whether a box misses the clamp entirely. Six comparisons rather than an allocation. */
    private static boolean misses(BoundingBox clamp, BoundingBox box) {
        return clamp.maxX() < box.minX() || clamp.minX() > box.maxX()
                || clamp.maxY() < box.minY() || clamp.minY() > box.maxY()
                || clamp.maxZ() < box.minZ() || clamp.minZ() > box.maxZ();
    }
}

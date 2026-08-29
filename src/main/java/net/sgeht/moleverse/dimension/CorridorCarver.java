package net.sgeht.moleverse.dimension;

import java.util.Arrays;
import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * along an axis; a disc is the same width in every direction, which is what
 * {@link BurrowGeometry#CORRIDOR_WIDTH} is supposed to mean. It also matches the
 * chambers, and a round tunnel reads as dug rather than as mined.</p>
 *
 * <p><strong>Nothing but deep earth and air is ever replaced.</strong> Corridors
 * get furniture - roots, mycelium, whatever a later feature puts there - and a
 * second mole travelling the same run must not eat it. That also makes carving
 * idempotent, so {@link #alreadyCarved} is an optimisation rather than a
 * correctness requirement.</p>
 *
 * <p><strong>A ledge is earth that was never carved.</strong> The galleries and
 * staircases in a chamber are not built; they are what is left when the room is
 * cut around them. That is the only form a fitting can take here that survives
 * the rule above - anything written into the air would be a block a later carve
 * is forbidden to touch, and so would hang in the middle of the next, deeper
 * chamber.</p>
 */
public final class CorridorCarver {

    /**
     * Sideways reach from the centre line. An odd
     * {@link BurrowGeometry#CORRIDOR_WIDTH} has a centre block, and this is what
     * is left either side of it.
     */
    private static final int CORRIDOR_RADIUS = (BurrowGeometry.CORRIDOR_WIDTH - 1) / 2;

    /**
     * How many of a chamber's topmost layers curve inwards.
     *
     * <p>Without it a chamber is a drilled silo: full radius right up to a flat
     * lid. The dome costs three lines and is the difference between a room and a
     * shaft.</p>
     */
    private static final int CHAMBER_DOME = 4;

    /**
     * Innermost radius the ledge may occupy. Everything closer to the axis stays
     * clear.
     *
     * <p>The way home is on that axis: the transit post stands on the chamber's
     * centre block and the deepest run leaves through the same column, a corridor
     * {@link #CORRIDOR_RADIUS} wide. Two blocks of margin on top of that means no
     * step can ever be cut across it.</p>
     */
    private static final int LEDGE_INNER_RADIUS = CORRIDOR_RADIUS + 2;

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
     * Carves the whole run.
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
        int points = link.pointCount();
        if (points < 2) {
            return 0;
        }

        // Seeded from the link rather than from the level, so a run that is
        // carved again after its corridor was somehow lost gets the same
        // decoration back. The burrow is a place people learn their way around;
        // furniture that moves between visits would undo that.
        RandomSource random = RandomSource.create(seedOf(link));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        BlockPos previous = burrowPoint(link, 0);
        int cleared = corridorAt(burrow, previous, cursor);

        for (int i = 1; i < points; i++) {
            BlockPos next = burrowPoint(link, i);
            cleared += carveSegment(burrow, previous, next, cursor);

            BlockPos centre = midpoint(previous, next);
            if (burrow.isLoaded(centre)) {
                TunnelDecorator.decorate(burrow, centre, random);
            }

            previous = next;
        }

        return cleared;
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
        int[] levels = galleryLevels(mouthLayers);
        int highest = levels[levels.length - 1];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;

        // The room. Everything the ledge keeps is simply never carved, so the
        // galleries and their staircases are deep earth that was left standing
        // rather than blocks written into the air - which is what keeps them
        // clear of clear()'s rule that only deep earth may be replaced, and what
        // lets a later, deeper chamber absorb an older ledge instead of leaving
        // it hanging.
        for (int layer = 0; layer < BurrowGeometry.CHAMBER_HEIGHT; layer++) {
            cleared += chamberLayer(burrow, burrowCentre, layer, 0, chamberRadiusAt(layer), levels, highest, cursor);
        }

        // The ledge zone again, all the way up. A mouth at the top of the chamber
        // puts its gallery against the ceiling, so this cuts the last layer or two
        // into the roof - out at the ledge only, where the dome had already pulled
        // back. A gallery you cannot stand up in is not a gallery, and neither is
        // the top step of a ramp.
        for (int layer = 0; layer < highest + GALLERY_HEADROOM; layer++) {
            cleared += chamberLayer(burrow, burrowCentre, layer, LEDGE_INNER_RADIUS,
                    BurrowGeometry.CHAMBER_RADIUS, levels, highest, cursor);
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
     * <p>Steps of one block: the cross-section is five wide, so a coarser step
     * would still join up, but only for as long as nobody lowers
     * {@link BurrowGeometry#CORRIDOR_WIDTH}. A unit step is gapless whatever the
     * width, and the cost is reads on blocks that are already air rather than
     * extra writes.</p>
     *
     * <p>Starts at step 1. Step 0 is the waypoint the caller has already cleared,
     * either as the start of the run or as the end of the previous segment.</p>
     */
    private static int carveSegment(ServerLevel burrow, BlockPos from, BlockPos to,
            BlockPos.MutableBlockPos cursor) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        int steps = Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz)));

        int cleared = 0;
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            cleared += corridorAt(burrow,
                    from.getX() + (int) Math.round(dx * t),
                    from.getY() + (int) Math.round(dy * t),
                    from.getZ() + (int) Math.round(dz * t),
                    cursor);
        }
        return cleared;
    }

    private static int corridorAt(ServerLevel burrow, BlockPos centre, BlockPos.MutableBlockPos cursor) {
        return corridorAt(burrow, centre.getX(), centre.getY(), centre.getZ(), cursor);
    }

    /**
     * One cross-section of corridor.
     *
     * <p>{@code y} is the walking surface and the carve goes upwards from it, so
     * {@code y - 1} is never touched. That is the floor, and leaving it rather
     * than filling it is deliberate: two runs one level apart lie four blocks
     * apart down here against a corridor height of six, so their corridors
     * genuinely overlap, and writing deep earth into a floor would be writing it
     * into the middle of somebody else's corridor.</p>
     */
    private static int corridorAt(ServerLevel burrow, int x, int y, int z, BlockPos.MutableBlockPos cursor) {
        int cleared = 0;
        for (int layer = 0; layer < BurrowGeometry.CORRIDOR_HEIGHT; layer++) {
            cleared += disc(burrow, x, y + layer, z, CORRIDOR_RADIUS, cursor);
        }
        return cleared;
    }

    /** One horizontal layer, round. */
    private static int disc(ServerLevel burrow, int centreX, int y, int centreZ, int radius,
            BlockPos.MutableBlockPos cursor) {
        int cleared = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!withinDisc(dx, dz, radius)) {
                    continue;
                }
                cursor.set(centreX + dx, y, centreZ + dz);
                if (clear(burrow, cursor)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    /**
     * The integer disc: {@code radius * radius + radius} rather than
     * {@code radius * radius} is the squared radius of a circle drawn half a block
     * outside the ring, which is what keeps the diagonals from being cut back to a
     * plus sign.
     */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    /**
     * Clears one block, if this is a block we are allowed to clear.
     *
     * <p>The cursor is reused across the whole run and handed straight to
     * {@code setBlock}, which is safe because NeoForge patches {@code Level}
     * to call {@code immutable()} on the position before storing it anywhere.</p>
     *
     * @return true when something was actually turned into air
     */
    private static boolean clear(ServerLevel burrow, BlockPos.MutableBlockPos pos) {
        if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
            return false;
        }

        BlockState state = burrow.getBlockState(pos);
        if (state.isAir() || !state.is(ModBlocks.DEEP_EARTH.get())) {
            return false;
        }

        return burrow.setBlock(pos, Blocks.AIR.defaultBlockState(), CARVE_FLAGS);
    }

    // --- geometry -------------------------------------------------------------

    /**
     * One layer of a chamber, minus whatever the ledge keeps solid.
     *
     * <p>{@code innerRadius} is what the headroom pass uses to stay out of the
     * middle of the room: widening the ceiling over the whole floor would turn
     * the dome into a lid, and only the ring itself needs the air.</p>
     */
    private static int chamberLayer(ServerLevel burrow, BlockPos centre, int layer, int innerRadius,
            int outerRadius, int[] levels, int highestLevel, BlockPos.MutableBlockPos cursor) {
        int cleared = 0;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                if (!withinDisc(dx, dz, outerRadius) || dx * dx + dz * dz < innerRadius * innerRadius) {
                    continue;
                }
                if (isLedge(layer, dx, dz, levels, highestLevel)) {
                    continue;
                }
                cursor.set(centre.getX() + dx, centre.getY() + layer, centre.getZ() + dz);
                if (clear(burrow, cursor)) {
                    cleared++;
                }
            }
        }
        return cleared;
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
     * Radius of chamber layer {@code layer}, counted up from the floor.
     *
     * <p>A quarter ellipse over the top {@link #CHAMBER_DOME} layers. The
     * parameter stops short of 1 so the apex keeps a usable radius instead of
     * closing to a single column - a dome, not a spire.</p>
     */
    private static int chamberRadiusAt(int layer) {
        int flat = BurrowGeometry.CHAMBER_HEIGHT - CHAMBER_DOME;
        if (layer < flat) {
            return BurrowGeometry.CHAMBER_RADIUS;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (CHAMBER_DOME + 1.0), 0.0, 1.0);
        return (int) Math.round(BurrowGeometry.CHAMBER_RADIUS * Math.sqrt(1.0 - t * t));
    }

    /** Waypoint {@code index} of the run, in burrow space. */
    private static BlockPos burrowPoint(BurrowLink link, int index) {
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

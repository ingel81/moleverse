package net.sgeht.moleverse.dimension;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.burrow.MoundNetwork;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Getting a player between the meadow and the burrow below it.
 *
 * <p>The two directions are deliberately not symmetric. Going down is an act of
 * preparation: somebody shored up a molehill and fitted a post to it, and the
 * chamber and the runs that meet there are dug at that moment because nothing
 * exists down there until somebody asks for it. Coming up is only a check - the
 * mound above has to still stand. That asymmetry is the whole safety model of
 * the dimension: a burrow can never be a hole in the middle of somebody's
 * house, because the only openings are the ones a mound already marks.</p>
 *
 * <p><strong>A missing mound closes the door.</strong> It is not corrupt data
 * and it is not an error to recover from - somebody knocked the heap away, and
 * the way out went with it. {@link #leave} refuses rather than inventing a
 * surface position, because guessing would let a player step out inside a wall
 * that grew over the spot in the meantime.</p>
 *
 * <p>Nothing here is stored. Which mound a chamber belongs to is its column and
 * nothing else, so there is no index to keep in sync with a world people dig in
 * - {@link #isWayOut} is a question asked of the ground. Only the horizontal is
 * a mapping; the height a chamber is carved at is a decision, and
 * {@link #chamberFloor} is where that decision lives.</p>
 */
public final class BurrowTransit {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * How far around a mound runs are collected before the ones that actually
     * end at it are kept.
     *
     * <p>A prefilter and nothing more. Every link that touches the mound has an
     * end at distance zero, so the radius only decides how much of the colony is
     * walked through - it can never change the result.</p>
     */
    private static final int LINK_PREFILTER_RADIUS = 32;

    /**
     * How far from the way out a player lands, in burrow blocks.
     *
     * <p>Anything but zero would do; the post occupies the chamber centre and a
     * player arriving in its collision box would be shoved out sideways at an
     * angle nobody chose. Two blocks is far enough to see it standing there.</p>
     */
    private static final int ARRIVAL_OFFSET = 2;

    private BurrowTransit() {
    }

    /**
     * Takes a player from a prepared mound down into the burrow.
     *
     * <p>Everything is carved before the teleport, never after: a player who
     * arrives first and waits for the digging would spend those ticks inside
     * solid earth, and the suffocation damage would be real.</p>
     *
     * @param mound the mound block itself, not the fitting standing on it
     * @return false when there is no burrow to go to, or the mound has gone
     */
    public static boolean enter(ServerPlayer player, ServerLevel overworld, BlockPos mound) {
        MinecraftServer server = overworld.getServer();
        ServerLevel burrow = ModDimensions.burrowLevel(server);
        if (burrow == null) {
            // The dimension failed to load - a pack problem, not a player one,
            // and the only refusal here with nothing the player could do about it.
            LOG.warn("no burrow dimension to enter from {}", mound);
            return false;
        }

        if (!MoleMound.isMound(overworld, mound)) {
            return false;
        }

        // The runs that end at this mound, so a player arrives at a junction
        // rather than in a sealed room. Everything further along is dug as they
        // walk into it.
        List<BurrowLink> runs = runsAt(ColonyStore.get(overworld), mound);
        int floor = chamberFloor(mound, runs);
        BlockPos chamber = BurrowGeometry.toBurrow(mound).atY(floor);

        loadChamberChunks(burrow, chamber);
        CorridorCarver.carveChamber(burrow, chamber, mouthLayers(mound, runs, floor));
        for (BurrowLink run : runs) {
            CorridorCarver.carve(burrow, run);
        }

        placeWayOut(burrow, chamber);

        return teleport(player, burrow, arrivalIn(chamber));
    }

    /**
     * Puts a player back on the surface beside the mound their chamber belongs
     * to.
     *
     * @param chamberCentre any position in the chamber; in practice the post the
     *                      player used. Only its x and z are read - see
     *                      {@link #moundAbove}
     * @return false when the mound above is gone, which is the door having
     *         closed rather than a failure
     */
    public static boolean leave(ServerPlayer player, ServerLevel burrow, BlockPos chamberCentre) {
        ServerLevel overworld = overworldOf(burrow);
        if (overworld == null) {
            LOG.warn("no overworld to leave the burrow into from {}", chamberCentre);
            return false;
        }

        BlockPos mound = moundAbove(overworld, chamberCentre);
        if (mound == null) {
            return false;
        }

        return teleport(player, overworld, surfaceBeside(overworld, mound));
    }

    /**
     * Whether this spot in the burrow still has a mound above it.
     *
     * <p>Asked by the post before it offers to do anything, so a player learns
     * that a way out has closed by looking at it rather than by using it.</p>
     */
    public static boolean isWayOut(ServerLevel burrow, BlockPos burrowPos) {
        ServerLevel overworld = overworldOf(burrow);
        return overworld != null && moundAbove(overworld, burrowPos) != null;
    }

    // --- where a chamber goes -------------------------------------------------

    /** The runs that end at this mound. The radius is only a prefilter; {@code touches} decides. */
    private static List<BurrowLink> runsAt(ColonyStore store, BlockPos mound) {
        return store.linksNear(mound, LINK_PREFILTER_RADIUS).stream()
                .filter(run -> run.touches(mound) && run.pointCount() > 0)
                .toList();
    }

    /**
     * The walking surface of the chamber this mound opens into, in burrow space.
     *
     * <p>Not the mapped mound position, which is the obvious answer and the wrong
     * one. A mound sits on the surface; the runs leaving it were dug two to six
     * blocks under it, and {@link BurrowGeometry#VERTICAL_SCALE} doubles that gap.
     * A chamber carved at the mound's own height would hang four to twelve blocks
     * above every corridor that is supposed to meet in it - a sealed room, and one
     * that would look correct in every screenshot of it.</p>
     *
     * <p>So the chamber goes at the <em>deepest</em> run that ends here. The three
     * run levels lie two blocks apart, which is eight down below, and a chamber is
     * {@link BurrowGeometry#CHAMBER_HEIGHT} nine high - so a floor at the deepest
     * run reaches the mouth of the shallowest one and every corridor between. The
     * shallowest as the reference would leave the deep ones under the floor.</p>
     *
     * <p>A mound whose colony has dug nothing yet has no run to measure, and the
     * feeding level is the fallback because it is the level a first trip uses. If
     * a deeper run appears later, the next arrival carves a deeper chamber and the
     * post is placed again at that new floor - the old one stays up in the ceiling,
     * harmless, and still maps back to this same mound.</p>
     *
     * <p>The minimum is taken after mapping, not before. {@code burrowY} clamps to
     * the dimension's own range, and while the clamp is monotonic - so it cannot
     * reorder two runs - doing the arithmetic in the space the answer is used in
     * means {@link #mouthLayers} can never come out negative, whatever the clamp
     * does to a colony on a mountain or in a superflat world.</p>
     */
    private static int chamberFloor(BlockPos mound, List<BurrowLink> runs) {
        int floor = BurrowGeometry.burrowY(mound.getY() - BurrowConstants.DEPTH_FEEDING);
        for (BurrowLink run : runs) {
            floor = Math.min(floor, BurrowGeometry.burrowY(runEndY(run, mound)));
        }
        return floor;
    }

    /**
     * How far above the chamber floor each run leaves, which is what the carver
     * puts a gallery at.
     *
     * <p>A mouth has no floor under it - the room cleared that - so it can only be
     * entered from the wall, at its own height, on its own bearing. Without these
     * the chamber is carved bare and a player standing on the floor can see the
     * shallower corridors and not reach them.</p>
     *
     * <p>Duplicates and a zero are left in. The carver ignores both, and filtering
     * here would only hide which run produced which gallery.</p>
     *
     * <p>A layer past the top of the chamber is dropped by the carver, which is
     * the right thing to do - a gallery clamped down to the ceiling would only put
     * the player four blocks closer to a mouth still out of reach. Dropping it
     * silently is the problem, so it is logged here. The run keeps its corridor,
     * so what the world ends up with is a real tunnel above the chamber with no
     * way up to it, and nothing else in the game would ever say so.</p>
     *
     * <p>It takes a changed surface to happen at all: the depth of a run is
     * sampled when that run is recorded and only re-measured when it is travelled
     * again, so two runs at one mound can be measured against two different
     * ground heights if somebody raised the ground or a tree grew between the two
     * recordings. Rare, and not self-announcing, which is exactly the combination
     * worth a line in the log.</p>
     */
    private static int[] mouthLayers(BlockPos mound, List<BurrowLink> runs, int floor) {
        int[] layers = runs.stream()
                .mapToInt(run -> BurrowGeometry.burrowY(runEndY(run, mound)) - floor)
                .toArray();

        for (int layer : layers) {
            if (layer >= BurrowGeometry.CHAMBER_HEIGHT) {
                LOG.warn("mound {}: a run leaves {} blocks above the chamber floor, "
                        + "past the {} the chamber is tall - it gets no gallery, "
                        + "so its corridor cannot be reached from inside",
                        mound, layer, BurrowGeometry.CHAMBER_HEIGHT);
            }
        }

        return layers;
    }

    /**
     * The height of a run where it meets this mound.
     *
     * <p>Which end of the link that is has to be asked: ends are stored in the
     * order they were dug, so the mound may be either of them.</p>
     */
    private static int runEndY(BurrowLink run, BlockPos mound) {
        int index = run.a().equals(mound) ? 0 : run.pointCount() - 1;
        return Mth.floor(run.pointAt(index).y);
    }

    /**
     * The mound a burrow position belongs to, or null when it has gone.
     *
     * <p>Only x and z are mapped back. The height cannot be: a chamber is carved
     * at the depth of its runs rather than at the mound's own height, so mapping
     * its y through {@link BurrowGeometry} returns a point several blocks
     * underground, where there is never a mound. The column is asked instead -
     * which is also the more honest question, because the burrow has no surface of
     * its own to mirror and the mound is by definition on top of one.</p>
     */
    private static @Nullable BlockPos moundAbove(ServerLevel overworld, BlockPos burrowPos) {
        BlockPos mapped = BurrowGeometry.toOverworld(burrowPos);
        BlockPos surface = MoundNetwork.surfaceAt(overworld, mapped.getX(), mapped.getZ());
        return MoleMound.isMound(overworld, surface) ? surface : null;
    }

    // --- the pieces -----------------------------------------------------------

    /**
     * The overworld the burrow mirrors.
     *
     * <p>Named outright rather than remembered per player. The burrow is one
     * scaled copy of one level, and a second entrance from somewhere else would
     * map onto the same chambers - so there is only one honest answer to where a
     * chamber leads.</p>
     */
    private static @Nullable ServerLevel overworldOf(ServerLevel burrow) {
        return burrow.getServer().getLevel(Level.OVERWORLD);
    }

    /**
     * Makes the chamber's own chunks exist before anything is carved into them.
     *
     * <p>{@link CorridorCarver} skips every position in an unloaded chunk, and it
     * is right to - forcing chunks along a whole run is how carving a colony
     * turns into a server freeze. The chamber is the one place that cannot be
     * skipped: a player teleported into uncarved deep earth suffocates in it.
     * It is also the one place where forcing is affordable, because a chamber is
     * one radius wide and touches four chunks at the very worst.</p>
     *
     * <p>The mouths of the runs leaving the mound fall inside the same chunks,
     * so this incidentally buys the corridor stubs that keep an arrival from
     * being a sealed room. The rest of each run is carved when somebody walks
     * into it.</p>
     */
    private static void loadChamberChunks(ServerLevel burrow, BlockPos chamber) {
        int radius = BurrowGeometry.CHAMBER_RADIUS;
        int fromX = SectionPos.blockToSectionCoord(chamber.getX() - radius);
        int toX = SectionPos.blockToSectionCoord(chamber.getX() + radius);
        int fromZ = SectionPos.blockToSectionCoord(chamber.getZ() - radius);
        int toZ = SectionPos.blockToSectionCoord(chamber.getZ() + radius);

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                burrow.getChunk(x, z);
            }
        }
    }

    /**
     * Stands the way home at the chamber centre.
     *
     * <p>The centre and nowhere else, because that is the column {@link #leave}
     * and {@link #isWayOut} map back through: a post there gives the mound it
     * came from, and the block's own position is all either of them needs to be
     * asked. The centre is a walking surface by {@code carveChamber}'s
     * convention, so the post stands on the chamber floor rather than in it.</p>
     *
     * <p>Placed on every arrival, not once. A chamber that is carved deeper on a
     * later visit - because a deeper run has been dug since - would otherwise
     * leave its only way home eight blocks up in the new ceiling, out of reach of
     * the floor the player lands on.</p>
     */
    private static void placeWayOut(ServerLevel burrow, BlockPos chamber) {
        BlockState existing = burrow.getBlockState(chamber);
        if (existing.is(ModBlocks.SHRINK_POST.get())) {
            return;
        }
        // Only into space the carver just opened. A second visit finds its own
        // post here; anything else standing in the centre was put there by a
        // player and is not ours to overwrite.
        if (existing.canBeReplaced()) {
            burrow.setBlock(chamber, ModBlocks.SHRINK_POST.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Beside the post rather than inside it. The direction is arbitrary and only has to stay in the chamber. */
    private static Vec3 arrivalIn(BlockPos chamber) {
        return chamber.south(ARRIVAL_OFFSET).getBottomCenter();
    }

    /**
     * A place to stand next to a mound.
     *
     * <p>Not the mound's own block: the fitting that took the player down stands
     * on it, and arriving inside a post is arriving inside a collision box. The
     * four neighbours are tried instead, and the mound itself is the fallback -
     * it has no collision, so worst case the player lands in the heap and steps
     * out of it, which is better than being refused a way home.</p>
     */
    private static Vec3 surfaceBeside(ServerLevel overworld, BlockPos mound) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = mound.relative(direction);
            if (standable(overworld, candidate)) {
                return candidate.getBottomCenter();
            }
        }
        return mound.getBottomCenter();
    }

    /** Two blocks of room to stand in, on something that holds a player up. */
    private static boolean standable(LevelReader level, BlockPos pos) {
        return free(level, pos)
                && free(level, pos.above())
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /**
     * Empty enough to stand in.
     *
     * <p>The test is the collision shape rather than "is it air". Meadows are
     * full of grass, flowers and molehills, none of which are air and all of
     * which a player walks straight through - rejecting them would send nearly
     * every exit to the fallback.</p>
     */
    private static boolean free(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The move itself.
     *
     * <p>{@code teleport(TeleportTransition)} is the one entry point in this
     * version - it decides between the same-dimension and the cross-dimension
     * path itself, fires NeoForge's travel hook, and for a player returns the
     * same instance rather than a rebuilt one. The rotation is carried over
     * unchanged: a player looking down a corridor should still be looking down
     * it on the other side, and having the view snap to a fixed heading on every
     * transition is the kind of thing that reads as a bug.</p>
     *
     * <p>No post-transition action. The portal sound belongs to the nether, and
     * the fitting plays its own.</p>
     */
    private static boolean teleport(ServerPlayer player, ServerLevel target, Vec3 position) {
        TeleportTransition transition = new TeleportTransition(
                target,
                position,
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.DO_NOTHING);
        return player.teleport(transition) != null;
    }
}

package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.block.MoundAttachment;
import net.sgeht.moleverse.dimension.plan.BurrowFeature;
import net.sgeht.moleverse.dimension.plan.BurrowReconciler;
import net.sgeht.moleverse.dimension.plan.ChamberFeature;
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
 * {@link ChamberFeature#floorAt} is where that decision lives.</p>
 *
 * <p><strong>This class no longer carves the burrow.</strong> It used to carve a
 * whole colony on the way down - the chamber, every run leaving the mound, then
 * the shafts and the junctions of the entire network - which is why nothing
 * existed down there until somebody asked for it. That work belongs to
 * {@link BurrowReconciler} now and happens per chunk, as ground becomes live. All
 * that is left here is the one piece that cannot wait: the chamber's own chunk
 * ring, reconciled on the spot so that a player arrives in a finished room rather
 * than inside earth.</p>
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
     * <p>Two blocks, which is far enough to see the rope hanging rather than to
     * arrive looking straight up the inside of it. It used to be a way of dodging
     * the post's collision box; the rope has none, so the reason is now only that
     * a door is easier to find from a step away than from underneath.</p>
     */
    private static final int ARRIVAL_OFFSET = 2;

    /**
     * How far above the chamber floor the bottom of the rope hangs, in burrow
     * blocks.
     *
     * <p>One: the block a standing player's head is in. It is not on the floor,
     * which is the whole difference between a rope and the post it replaced, and
     * it is not out of reach either.</p>
     *
     * <p>Two was the obvious answer and it costs the climb. {@code onClimbable}
     * asks about the block at the entity's <em>feet</em>, and a jump lifts a
     * player about a block and a quarter - so a rope starting two above the floor
     * can never be entered from the floor, and the ladder is a ladder that cannot
     * be climbed. At one, a jump puts the player's feet inside the bottom segment
     * and the climb takes over from there.</p>
     */
    private static final int LADDER_FOOT = 1;

    private BurrowTransit() {
    }

    /**
     * Takes a player from a prepared mound down into the burrow.
     *
     * <p>The chamber ring is finished before the teleport, never after: a player
     * who arrives first and waits for a tick handler would spend those ticks
     * inside solid earth, and the suffocation damage would be real. Everything
     * beyond the ring arrives as they walk into it, which is what the comment
     * here used to promise and now describes.</p>
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

        // Where the room goes is the plan layer's arithmetic, not this class's:
        // a chunk far from any player has to be able to reach the same answer
        // with nobody standing anywhere near the mound.
        ColonyStore store = ColonyStore.get(overworld);
        List<BurrowLink> runs = ChamberFeature.runsAt(
                store.linksNear(mound, LINK_PREFILTER_RADIUS), mound);
        ChamberFeature room = ChamberFeature.of(mound, runs);
        BlockPos chamber = room.centre();

        // The plan derives a colony's chambers from the runs that end at its
        // mounds, so a mound nobody has finished a trip through has none - and a
        // player fitting a post to the first mound of a young colony would arrive
        // inside earth. The room is put into the plan rather than carved beside
        // it, so that it settles in the ledger like any other feature and the
        // reconciles that follow do not cut it a second time.
        List<BurrowFeature> plan = new ArrayList<>(BurrowReconciler.planFor(server));
        if (plan.stream().noneMatch(feature -> feature.key().equals(room.key()))) {
            plan.add(room);
        }

        // Forced rather than queued, and both phases at once. The ring is loaded
        // by the call below, so the dressing pass has real ground to measure in
        // every direction - which is the one place the neighbourhood rule can be
        // set aside safely.
        BurrowReconciler.reconcileNow(burrow, loadChamberChunks(burrow, chamber), plan);

        placeWayOut(burrow, chamber);
        BurrowLife.stock(burrow, chamber);

        return teleport(player, burrow, arrivalIn(chamber));
    }

    /**
     * Puts a player back on the surface beside the mound their chamber belongs
     * to.
     *
     * @param chamberCentre any position in the chamber; in practice the segment
     *                      of rope the player grabbed, which may be any height in
     *                      the centre column. Only its x and z are read - see
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

        // The one chunk the answer lives in is loaded outright. Found in play,
        // 124 refusals in one log: with nobody up top the chunk unloads within
        // a minute of descending, getHeight answers the world floor for an
        // unloaded chunk instead of loading it, and the heightmap "surface" is
        // bedrock at -64 - so the mound read as gone exactly when no mole
        // happened to be keeping the ground warm. A player using the way home
        // is worth one synchronous chunk load; it is the same courtesy enter()
        // extends to the chamber ring on the way down.
        BlockPos mapped = BurrowGeometry.toOverworld(chamberCentre);
        overworld.getChunk(SectionPos.blockToSectionCoord(mapped.getX()),
                SectionPos.blockToSectionCoord(mapped.getZ()));

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
     *
     * <p>Optimistic when the ground above is not loaded. Loading a chunk for a
     * glance would let looking at posts drag overworld terrain in; answering
     * "closed" for an unloaded chunk was the bug that made the way home refuse
     * whenever nobody was up top. So a look trusts the door, and {@link #leave}
     * - which does load the chunk - is where the truth is checked.</p>
     */
    public static boolean isWayOut(ServerLevel burrow, BlockPos burrowPos) {
        ServerLevel overworld = overworldOf(burrow);
        if (overworld == null) {
            return false;
        }
        BlockPos mapped = BurrowGeometry.toOverworld(burrowPos);
        if (!overworld.isLoaded(mapped)) {
            return true;
        }
        return moundAbove(overworld, burrowPos) != null;
    }

    // --- mapping back to the surface ------------------------------------------

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

        // The way down is a shrink post, the shrink post is a fitting, and a
        // fitting is solid - so the heightmap lands above it and the mound that
        // is plainly still there reads as gone. Every player who took the way in
        // hit this on the way out, because the very block that let them in is
        // what hides the mound.
        BlockPos underFitting = MoundAttachment.moundUnder(overworld, surface);
        if (underFitting != null) {
            surface = underFitting;
        }

        if (MoleMound.isMound(overworld, surface)) {
            return surface;
        }

        // Diagnostic for the way home refusing. Everything the decision read, in
        // one line, so a refusal in play can be traced without guessing: the
        // column asked, what the heightmap answered, what stands there, and what
        // the fitting probe saw. Dev runs only.
        if (Boolean.getBoolean("moleverse.devLogging")) {
            BlockPos below = surface.below();
            LOG.info("way out refused: burrow {} -> column ({},{}), surface {} is {}, below is {}, fitting probe {}",
                    burrowPos.toShortString(), mapped.getX(), mapped.getZ(), surface.toShortString(),
                    overworld.getBlockState(surface), overworld.getBlockState(below),
                    underFitting == null ? "found no mound" : "found " + underFitting.toShortString());
        }
        return null;
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
     * Makes the chamber's own chunks exist before anything is carved into them,
     * and says which they were.
     *
     * <p>Every carve in the burrow skips positions in an unloaded chunk, and is
     * right to - forcing chunks along a whole run is how carving a colony turns
     * into a server freeze. The chamber is the one place that cannot be skipped:
     * a player teleported into uncarved deep earth suffocates in it. It is also
     * the one place where forcing is affordable, because a chamber is one radius
     * wide and touches four chunks at the very worst.</p>
     *
     * <p>The mouths of the runs leaving the mound fall inside the same chunks,
     * so this incidentally buys the corridor stubs that keep an arrival from
     * being a sealed room. The rest of each run is carved when somebody walks
     * into it.</p>
     *
     * <p>A ring rather than a single chunk, and the ring is what
     * {@link BurrowReconciler#reconcileNow} is handed. Reconciling only the chunk
     * with the centre in it would leave the quarters of the room that fall in the
     * other three as earth.</p>
     */
    private static List<ChunkPos> loadChamberChunks(ServerLevel burrow, BlockPos chamber) {
        int radius = BurrowGeometry.CHAMBER_RADIUS;
        int fromX = SectionPos.blockToSectionCoord(chamber.getX() - radius);
        int toX = SectionPos.blockToSectionCoord(chamber.getX() + radius);
        int fromZ = SectionPos.blockToSectionCoord(chamber.getZ() - radius);
        int toZ = SectionPos.blockToSectionCoord(chamber.getZ() + radius);

        List<ChunkPos> ring = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                burrow.getChunk(x, z);
                ring.add(new ChunkPos(x, z));
            }
        }
        return ring;
    }

    /**
     * Hangs the way home from the chamber ceiling.
     *
     * <p>The centre column and nowhere else, because that is what {@link #leave}
     * and {@link #isWayOut} map back through: any block of the rope gives the
     * mound it came from, and the block's own position is all either of them
     * needs to be asked. Which is also what lets the way out be a column rather
     * than a single block - the whole rope stands in one column, so every segment
     * of it answers the same question.</p>
     *
     * <p><strong>It hangs; it does not stand.</strong> The walk is top down from
     * the highest layer the dome can have opened, and it stops at the first thing
     * in the way once the rope has started - a rope rests on what it lands on
     * rather than growing through it. Before the rope has started, a blocked
     * layer is only a ceiling lower than the dome allows, so the walk keeps
     * looking. It ends {@link #LADDER_FOOT} above the walking surface, which the
     * chamber centre is by {@code carveChamber}'s convention.</p>
     *
     * <p>Placed on every arrival, not once, and idempotent because of it: the
     * rope finds its own segments and leaves them alone, fills whatever gaps have
     * appeared, and never writes over a block a player put in the column - the
     * same {@code canBeReplaced} courtesy the post placement had. A chamber that
     * is carved deeper on a later visit gets its rope again at the new height,
     * which is why this cannot be done once.</p>
     *
     * <p>A chamber dug before the rope existed has a {@code ShrinkPost} standing
     * in the middle of it, and that is what migrates it: the post goes on the
     * next arrival and the rope replaces it. Broken rather than deleted, so that
     * a player who stood one there themselves gets the block back.</p>
     */
    private static void placeWayOut(ServerLevel burrow, BlockPos chamber) {
        if (burrow.getBlockState(chamber).is(ModBlocks.SHRINK_POST.get())) {
            burrow.destroyBlock(chamber, true);
        }

        BlockState rope = ModBlocks.ROOT_LADDER.get().defaultBlockState();
        boolean hanging = false;

        for (int layer = BurrowGeometry.CHAMBER_HEIGHT - 1; layer >= LADDER_FOOT; layer--) {
            BlockPos at = chamber.atY(chamber.getY() + layer);
            BlockState existing = burrow.getBlockState(at);

            if (existing.is(ModBlocks.ROOT_LADDER.get())) {
                hanging = true;
                continue;
            }
            if (!existing.canBeReplaced()) {
                // Above the rope's top this is the ceiling, and the rope simply
                // starts under it; below the top it is something in the way, and
                // that is where the rope ends.
                if (hanging) {
                    return;
                }
                continue;
            }

            burrow.setBlock(at, rope, Block.UPDATE_ALL);
            hanging = true;
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

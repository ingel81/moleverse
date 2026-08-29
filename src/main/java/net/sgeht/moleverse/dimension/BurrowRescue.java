package net.sgeht.moleverse.dimension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.entity.burrow.MoundNetwork;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The way out of the burrow when there is no way out.
 *
 * <p>{@link BurrowTransit} is a door with a lock on the outside. A chamber leads
 * up only while the mound it maps to still stands, and the mound is a block in
 * somebody else's world: a player breaks it, a piston pushes it, a creeper takes
 * the meadow with it, and every post within reach goes dead at once. The
 * dimension has no beds, no other exit and no minable block, so a visitor at that
 * moment is shut in for good. This class is the answer to that, and to nothing
 * else.</p>
 *
 * <h2>What it is</h2>
 *
 * <p>A mole that finds its run blocked does not look for a door. It digs, and it
 * comes up wherever it happens to be. So does a shut-in player: after
 * {@link #CLIMB_TICKS} of being cut off, the earth gives and they surface above
 * the column they were standing in, in a spray of soil, leaving a fresh molehill
 * behind them.</p>
 *
 * <p>That is deliberately a worse way home than the post, in the two ways that
 * matter. It is <em>slow</em> - minutes against a click. And it comes up
 * <em>where the player is</em> rather than where their entrance was: the burrow
 * is {@link BurrowGeometry#SCALE} times the size of the world above, so anyone
 * who has walked a corridor surfaces some distance from the mound they came down,
 * on whatever happens to be there - a hillside, a rooftop, the sea. The front
 * door is instant and puts you beside your own prepared mound. Nobody who has one
 * will ever wait four minutes to be spat out on a hill instead.</p>
 *
 * <h2>Why it does not ask the colony anything</h2>
 *
 * <p>The case this exists for is the case where everything above has been
 * destroyed, so any mechanism that needs a colony, a link, a second mound or an
 * item in the player's pack is a mechanism that is absent exactly when it is
 * needed. Nothing here reads {@code ColonyStore}. The only question asked of the
 * world above is the heightmap, which every overworld column has whether or not a
 * mole ever lived there.</p>
 *
 * <h2>Why it fires by itself</h2>
 *
 * <p>It would be tidier to hang this off the post refusing - that is the moment a
 * player learns the door has closed. But the post is a block with 0.8 hardness
 * that drops itself, so hanging the only guarantee off it means a player who
 * broke theirs and lost the item is still shut in. A timer needs nothing the
 * player can destroy, which is the whole point.</p>
 *
 * <h2>Chunk safety</h2>
 *
 * <p>Every read of the world above is inside a chunk this class has made sure of.
 * The window is small - {@link #SURFACE_WINDOW} blocks each way, at most four
 * chunks - and it is only touched once per {@link #CHECK_INTERVAL} and only while
 * somebody is actually down there, which is the same bounded, at-the-moment-of-use
 * forcing {@code BurrowTransit.loadChamberChunks} does. Leaving it out is not an
 * option: a player alone in the burrow holds no ticket on their own entrance, so
 * an unforced look would find no mound anywhere and report every visitor
 * stranded.</p>
 */
public final class BurrowRescue {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * How often a player below is asked whether they still have a way out.
     *
     * <p>Twenty seconds, because the check costs a heightmap sweep and up to four
     * forced chunks and the answer cannot change faster than somebody can break a
     * block. It is also the resolution of the countdown the player sees, which is
     * why it is not a minute.</p>
     */
    private static final int CHECK_INTERVAL = 20 * 20;

    /**
     * How long the dig takes, in ticks.
     *
     * <p>Four minutes. Long enough that nobody with a working post would ever
     * choose it, short enough that being shut in is a setback rather than the end
     * of an evening. Provisional, like every number in
     * {@code docs/BURROW.md} - it wants judging in play, not arguing about.</p>
     */
    private static final int CLIMB_TICKS = 20 * 60 * 4;

    /**
     * How far around the player's mapped column the world above is searched, in
     * overworld blocks.
     *
     * <p>Two jobs, one number. It is the reach of the "is there still a door"
     * question, and it is the reach of the search for somewhere to come up.</p>
     *
     * <p>Six is chosen from the geometry rather than by taste. A chamber is
     * {@link BurrowGeometry#CHAMBER_RADIUS} six blocks across down there, which is
     * under two overworld blocks, so anybody standing anywhere in a chamber maps
     * to within two columns of its mound. The rest is slack for a player in the
     * corridor stubs just outside it, so that walking a few paces off the floor of
     * a working chamber does not read as being shut in.</p>
     */
    private static final int SURFACE_WINDOW = 6;

    /**
     * How far above the heightmap a landing spot is looked for.
     *
     * <p>The heightmap gives the first free block over the ground, which is very
     * nearly always somewhere a player fits. Very nearly: a low overhang, a leaf
     * canopy or a slab ceiling leaves it with no headroom, and a player put there
     * suffocates. Eight blocks is enough to climb out from under anything that
     * happens naturally and cheap enough to do for every column in the window.</p>
     */
    private static final int HEADROOM_SCAN = 8;

    private static final String KEY_PREFIX = "message." + Moleverse.MOD_ID + ".burrow.";

    /**
     * When each shut-in player's dig began, by game time.
     *
     * <p>Session state on purpose, and not written to disk. A relog restarts the
     * dig, which delays a rescue and can never prevent one - and the alternative is
     * a saved-data file whose only content is a number that is meaningless the
     * moment somebody replaces the mound. An entry is removed as soon as its player
     * has a way out again or has left the dimension, so the map is normally
     * empty.</p>
     */
    private static final Map<UUID, Long> DIGGING_SINCE = new HashMap<>();

    private BurrowRescue() {
    }

    /**
     * The whole mechanism, driven from the level tick.
     *
     * <p>Returns immediately for every level but the burrow, and immediately again
     * whenever the burrow is empty - which is nearly always. The real work happens
     * once every {@link #CHECK_INTERVAL} and only for players who are actually
     * down there.</p>
     */
    public static void tick(ServerLevel level) {
        if (!ModDimensions.isBurrow(level)) {
            return;
        }

        List<ServerPlayer> below = level.players();
        if (below.isEmpty()) {
            // Also the reset for a fresh world in the same JVM: static state
            // outlives a world unload, and a stale entry from the previous one
            // would be measured against a game time that has gone backwards.
            if (!DIGGING_SINCE.isEmpty()) {
                DIGGING_SINCE.clear();
            }
            return;
        }

        if (level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }

        Set<UUID> present = new HashSet<>(below.size());
        for (ServerPlayer player : below) {
            present.add(player.getUUID());
            check(player, level);
        }

        // Whoever walked out of the dimension between two checks stops digging.
        DIGGING_SINCE.keySet().retainAll(present);
    }

    /**
     * Whether this player has no way out of the burrow.
     *
     * <p>The question is asked of the ground above and of nothing else: is there a
     * mound anywhere in the patch of overworld this player's position maps into.
     * That is the same question {@link BurrowTransit#isWayOut} asks of a post, only
     * asked of the player instead, so that it still has an answer for somebody
     * standing in a corridor with no post in sight.</p>
     *
     * <p>Creative and spectator are never stranded. Neither can be shut in by a
     * missing block, and yanking a builder off their work is the one way this could
     * do harm.</p>
     *
     * <p><strong>Not free.</strong> It forces the overworld chunks it looks at -
     * see the note on chunk safety in the class javadoc - so it belongs on the
     * check cadence and not in a per-tick condition.</p>
     */
    public static boolean stranded(ServerPlayer player, ServerLevel burrow) {
        if (player.level() != burrow || !ModDimensions.isBurrow(burrow)) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }

        ServerLevel overworld = burrow.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            // No world to be rescued into. Reporting "stranded" would only start a
            // countdown that can never finish.
            return false;
        }

        BlockPos column = BurrowGeometry.toOverworld(player.blockPosition());
        loadWindow(overworld, column);
        return !moundInWindow(overworld, column);
    }

    /**
     * Puts a shut-in player on the surface, now.
     *
     * <p>Above the column they are standing in, which is the honest answer to
     * where somebody digging upwards comes out - not above the mound they came
     * down, which is gone, and not at a spawn point, which would be a different
     * game's mechanic. A molehill is left at the spot if the ground takes one: the
     * trace of what just happened, and the same heap a real mole leaves.</p>
     *
     * <p>Public and side-effect-complete on purpose. Nothing else calls it today,
     * but it is the whole of the mechanism and a debug command or a post that
     * wanted to offer an immediate way out could use it as it stands.</p>
     *
     * @return false when there is nowhere safe to put the player, in which case
     *         nothing happened and the dig goes on
     */
    public static boolean rescue(ServerPlayer player, ServerLevel burrow) {
        ServerLevel overworld = burrow.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        BlockPos column = BurrowGeometry.toOverworld(player.blockPosition());
        loadWindow(overworld, column);

        BlockPos landing = landing(overworld, column);
        if (landing == null) {
            LOG.warn("nowhere to surface a shut-in player around {} - the dig goes on", column);
            return false;
        }

        MoleMound.tryPlace(overworld, landing, false);

        // A player standing on a corridor floor has no fall to carry, but this
        // costs nothing and the one promise the rescue makes is that it does not
        // kill anybody.
        player.resetFallDistance();
        if (!teleport(player, overworld, landing.getBottomCenter())) {
            return false;
        }

        surfaced(overworld, player);
        return true;
    }

    // --- the countdown --------------------------------------------------------

    /** One player's check: start the dig, count it down, or call it off. */
    private static void check(ServerPlayer player, ServerLevel burrow) {
        UUID id = player.getUUID();

        if (!stranded(player, burrow)) {
            if (DIGGING_SINCE.remove(id) != null) {
                // Somebody put a mound back, or the player walked to a chamber
                // that still has one. Saying so matters: the countdown was
                // visible, so its ending has to be too.
                player.displayClientMessage(message("reopened",
                        "The way up is open again. The digging stops."), true);
            }
            return;
        }

        long now = burrow.getGameTime();
        Long since = DIGGING_SINCE.get(id);
        if (since == null || now < since) {
            DIGGING_SINCE.put(id, now);
            player.sendSystemMessage(message("shut_in",
                    "There is no way up from here any more. Something starts working "
                            + "its way through the earth above you."));
            return;
        }

        long elapsed = now - since;
        if (elapsed < CLIMB_TICKS) {
            player.displayClientMessage(message("digging",
                    "The earth is giving way... %s seconds",
                    (CLIMB_TICKS - elapsed) / 20), true);
            return;
        }

        if (rescue(player, burrow)) {
            DIGGING_SINCE.remove(id);
        }
    }

    /** What breaking the surface looks and sounds like, at the spot it happened. */
    private static void surfaced(ServerLevel overworld, ServerPlayer player) {
        overworld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        overworld.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.DEEP_EARTH.get().defaultBlockState()),
                player.getX(), player.getY() + 0.2, player.getZ(),
                48, 0.4, 0.3, 0.4, 0.05);
        player.sendSystemMessage(message("surfaced",
                "You break the surface, filthy and a long way from where you went down."));
    }

    // --- the world above ------------------------------------------------------

    /**
     * Makes the window's chunks exist before anything reads them.
     *
     * <p>Deliberately the same shape as {@code BurrowTransit.loadChamberChunks},
     * and for the same reason: this is a small, bounded area that is about to be
     * asked a question whose answer must be true rather than merely available.
     * {@link #SURFACE_WINDOW} is six, so the window is thirteen blocks across and
     * spans four chunks at the very worst.</p>
     */
    private static void loadWindow(ServerLevel overworld, BlockPos column) {
        int fromX = SectionPos.blockToSectionCoord(column.getX() - SURFACE_WINDOW);
        int toX = SectionPos.blockToSectionCoord(column.getX() + SURFACE_WINDOW);
        int fromZ = SectionPos.blockToSectionCoord(column.getZ() - SURFACE_WINDOW);
        int toZ = SectionPos.blockToSectionCoord(column.getZ() + SURFACE_WINDOW);

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                overworld.getChunk(x, z);
            }
        }
    }

    /**
     * Whether any mound at all stands in the window.
     *
     * <p>Any mound, not a prepared one and not one this colony owns. A door is a
     * door: {@code BurrowTransit.leave} only asks whether the column carries a
     * mound, so anything that satisfies it satisfies this.</p>
     *
     * <p>Columns whose chunk is missing are skipped rather than forced a second
     * time. {@link #loadWindow} has already been past, so in practice there are
     * none - this is the guard that keeps a read from generating terrain if that
     * ever stops being true.</p>
     */
    private static boolean moundInWindow(ServerLevel overworld, BlockPos column) {
        for (int dx = -SURFACE_WINDOW; dx <= SURFACE_WINDOW; dx++) {
            for (int dz = -SURFACE_WINDOW; dz <= SURFACE_WINDOW; dz++) {
                int x = column.getX() + dx;
                int z = column.getZ() + dz;
                if (!chunkAt(overworld, x, z)) {
                    continue;
                }
                if (MoleMound.isMound(overworld, MoundNetwork.surfaceAt(overworld, x, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Somewhere in the window a player can be put down without being hurt.
     *
     * <p>The nearest column with a floor wins. Failing that, the nearest column
     * with none - which is water, and which is kept rather than rejected because
     * rejecting it would leave anybody whose run passes under a lake with no way
     * out at all. Surfacing in the sea is a swim; being shut in is forever.</p>
     *
     * <p>Nothing that burns is offered, at the spot or under it. A surface lava
     * pool is the one place in the overworld where the heightmap's answer is a
     * death, and it is cheap to refuse.</p>
     */
    private static @Nullable BlockPos landing(ServerLevel overworld, BlockPos column) {
        BlockPos onGround = null;
        int onGroundSqr = Integer.MAX_VALUE;
        BlockPos afloat = null;
        int afloatSqr = Integer.MAX_VALUE;

        for (int dx = -SURFACE_WINDOW; dx <= SURFACE_WINDOW; dx++) {
            for (int dz = -SURFACE_WINDOW; dz <= SURFACE_WINDOW; dz++) {
                int x = column.getX() + dx;
                int z = column.getZ() + dz;
                if (!chunkAt(overworld, x, z)) {
                    continue;
                }

                BlockPos spot = clearSpotAt(overworld, x, z);
                if (spot == null) {
                    continue;
                }

                int sqr = dx * dx + dz * dz;
                BlockPos below = spot.below();
                if (overworld.getBlockState(below).isFaceSturdy(overworld, below, Direction.UP)) {
                    if (sqr < onGroundSqr) {
                        onGround = spot;
                        onGroundSqr = sqr;
                    }
                } else if (sqr < afloatSqr) {
                    afloat = spot;
                    afloatSqr = sqr;
                }
            }
        }

        return onGround != null ? onGround : afloat;
    }

    /** The lowest spot in this column with room for a player, or null when it has none. */
    private static @Nullable BlockPos clearSpotAt(ServerLevel overworld, int x, int z) {
        int surface = MoundNetwork.surfaceAt(overworld, x, z).getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int up = 0; up <= HEADROOM_SCAN; up++) {
            cursor.set(x, surface + up, z);
            if (!overworld.isInsideBuildHeight(cursor.getY() + 1)) {
                return null;
            }
            if (roomToStand(overworld, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /**
     * Two blocks of space with nothing burning in or under them.
     *
     * <p>The emptiness test is the collision shape rather than "is it air", for the
     * same reason {@code BurrowTransit} uses it: grass, flowers and molehills are
     * not air and a player walks straight through all of them.</p>
     */
    private static boolean roomToStand(LevelReader level, BlockPos pos) {
        return free(level, pos)
                && free(level, pos.above())
                && !burns(level, pos)
                && !burns(level, pos.above())
                && !burns(level, pos.below());
    }

    private static boolean free(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Whether the chunk holding this column is there.
     *
     * <p>{@code Level.isLoaded}, which is the guard the rest of the dimension code
     * uses, rather than {@code LevelReader.hasChunk} or {@code hasChunkAt} - that
     * whole family is deprecated in this version. It wants a block rather than a
     * column, and any height inside the world answers the same question, so sea
     * level stands in for one.</p>
     */
    private static boolean chunkAt(Level level, int x, int z) {
        return level.isLoaded(new BlockPos(x, BurrowGeometry.OVERWORLD_DATUM, z));
    }

    private static boolean burns(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    // --- the pieces -----------------------------------------------------------

    /**
     * The move itself.
     *
     * <p>The same shape as {@code BurrowTransit.teleport}, deliberately not shared
     * with it: that one is private and this class is the one thing in the mod that
     * must keep working when the transit refuses. A copy of six lines is a smaller
     * price than a coupling between a door and the way round it.</p>
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

    /**
     * A line of text with its English written in.
     *
     * <p>{@code translatableWithFallback}, as {@code ShrinkPost} does it: the
     * source locale is produced by a data generator, so a key added here has no
     * entry until the generator is run and a bare translatable would show the raw
     * key in the meantime.</p>
     */
    private static Component message(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(KEY_PREFIX + key, fallback, args);
    }
}

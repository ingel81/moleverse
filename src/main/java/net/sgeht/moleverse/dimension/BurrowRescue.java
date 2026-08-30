package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
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
 * <h2>The rescue asks the colony nothing. The trigger asks it one thing.</h2>
 *
 * <p>The case this exists for is the case where everything above has been
 * destroyed, so any mechanism that needs a colony, a link, a second mound or an
 * item in the player's pack is a mechanism that is absent exactly when it is
 * needed. {@link #rescue} therefore still reads nothing but the heightmap, which
 * every overworld column has whether or not a mole ever lived there.</p>
 *
 * <p><strong>{@link #stranded} does read {@code ColonyStore}, and it had to
 * start.</strong> The window check below was written when the burrow was a
 * chamber and a few stubs, where "more than six overworld blocks from a mound"
 * really did mean walled in. With runs carved end to end it means <em>walking
 * down a corridor</em>, which is the ordinary case, and the first person to
 * explore one got the shut-in message and a countdown while their way home stood
 * untouched a hundred blocks behind them.</p>
 *
 * <p>So the question is asked in two stages, and the order is the safety of it.
 * The cheap window check is the fast pass and answers most of the time. Only when
 * it finds nothing is the real question asked - does the colony whose ground this
 * is still have <em>any</em> mound standing - and only when that also finds
 * nothing does the countdown begin.</p>
 *
 * <p>The second stage can only ever cancel a countdown, never prevent one. An
 * empty store, a missing colony, a run whose ends have all gone: every one of
 * those falls through to stranded, exactly as before. That is what keeps the
 * guarantee in the first paragraph intact - the mechanism still cannot be
 * disabled by destroying things, because destroying things is what makes it
 * fire.</p>
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
 * chunks - and the second stage adds at most {@link #ENDPOINT_PROBES} more, one
 * per mound it looks at. Eight chunks at the very worst, once per
 * {@link #CHECK_INTERVAL}, and only while somebody is actually down there: the
 * same bounded, at-the-moment-of-use forcing
 * {@code BurrowTransit.loadChamberChunks} does.</p>
 *
 * <p>Leaving it out is not an option, and the reason is the whole reason the
 * second stage needs a cap rather than a sweep. A player alone in the burrow
 * holds no ticket on their own entrance, so the chunk unloads, {@code getHeight}
 * answers the world floor for an unloaded chunk rather than loading it, and an
 * unforced look would find no mound anywhere and report every visitor stranded -
 * which is this class's own bug, made worse. So every column it judges is a
 * column it has loaded, and the price of that is what {@link #ENDPOINT_PROBES}
 * bounds.</p>
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
     * <p>Two jobs, one number. It is the reach of the fast pass on the "is there
     * still a door" question, and it is the reach of the search for somewhere to
     * come up.</p>
     *
     * <p>Six is chosen from the geometry rather than by taste. A chamber is
     * {@link BurrowGeometry#CHAMBER_RADIUS} six blocks across down there, which is
     * under two overworld blocks, so anybody standing anywhere in a chamber maps
     * to within two columns of its mound. The rest is slack for a player in the
     * corridor mouths just outside it.</p>
     *
     * <p>It was once the <em>whole</em> of the strandedness question, and that was
     * the bug: a corridor is as long as the colony is wide, so a player halfway
     * down one is nowhere near any mound and was told so. It is now the fast pass
     * and nothing more - see {@link #colonyStillHasADoor} for the question it
     * falls through to.</p>
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

    /**
     * On from the first tick of a development run, off in a shipped game.
     *
     * <p>The same property {@code BurrowReconciler} and {@code BurrowTraversal}
     * read, and here for the reason this whole class exists: a countdown that
     * starts when it should not is a bug a player reports as "it told me I was
     * shut in", with nothing in the world to show which of the two stages
     * decided it.</p>
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    /**
     * How many of the colony's nearest mounds the second stage will look at.
     *
     * <p>A cap and not a search. Each one costs a chunk that may have to be
     * loaded outright, and the question is only ever "is there <em>a</em> door" -
     * so the four nearest answer it as well as forty would in every case except a
     * colony whose four nearest heaps have all been taken and whose fifth has not,
     * which is a countdown that stops the next time the player walks a little
     * further.</p>
     */
    private static final int ENDPOINT_PROBES = 4;

    /**
     * How long a second-stage verdict is trusted, in ticks. Ten seconds.
     *
     * <p>A guard on {@link #stranded} as a public method rather than an
     * optimisation of the tick path: the only caller today asks every
     * {@link #CHECK_INTERVAL}, which is twice this, so on that path it never
     * fires. It is here because the second stage forces chunks and the method is
     * documented as reusable, and anything that called it on a faster cadence
     * would otherwise force four chunks a call.</p>
     *
     * <p>Ten seconds is also short enough that the verdict cannot outlive its
     * subject: a player covers about fifty burrow blocks in that time, which is a
     * dozen overworld ones, well inside the colony box the verdict was taken
     * about.</p>
     */
    private static final int VERDICT_COOLDOWN = 20 * 10;

    /**
     * What the second stage last found for each player, and until when.
     *
     * <p>Read twice: once to skip the sweep, and once by {@link #check} so that
     * the line it writes when a countdown genuinely starts can name the mounds
     * that were looked at. Session state and cleaned up beside
     * {@link #DIGGING_SINCE}, for the same reasons.</p>
     */
    private static final Map<UUID, Doors> DOORS = new HashMap<>();

    /**
     * Who the second stage is currently keeping out of a countdown.
     *
     * <p>Only so the line that says so is written once per occasion instead of
     * once per check. Membership is the state, not a timer, so it costs nothing to
     * keep and it cannot go stale - every check either adds or removes.</p>
     */
    private static final Set<UUID> SAVED = new HashSet<>();

    /**
     * One second-stage answer.
     *
     * @param until    game time this stops being trusted
     * @param standing whether any of the mounds looked at was still there
     * @param checked  which ones were looked at, for the log
     */
    private record Doors(long until, boolean standing, List<BlockPos> checked) {
    }

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
            if (!DOORS.isEmpty()) {
                DOORS.clear();
            }
            if (!SAVED.isEmpty()) {
                SAVED.clear();
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

        // Whoever walked out of the dimension between two checks stops digging,
        // and takes their remembered verdict with them.
        DIGGING_SINCE.keySet().retainAll(present);
        DOORS.keySet().retainAll(present);
        SAVED.retainAll(present);
    }

    /** One line, if anybody is listening. */
    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
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
        if (moundInWindow(overworld, column)) {
            SAVED.remove(player.getUUID());
            return false;
        }

        // The fast pass found nothing, which with real corridors is the ordinary
        // case rather than an emergency. Ask the question that actually decides
        // it before starting a clock somebody can see.
        Doors doors = doorsOf(player, overworld, column, burrow.getGameTime());
        if (doors.standing()) {
            // Only when it changes. This branch is the common one for anybody
            // walking a corridor, so a line per check would be a line every
            // twenty seconds for the whole visit - and the thing worth seeing is
            // the moment the second stage took over from the first.
            if (SAVED.add(player.getUUID())) {
                say("{} at {} is out of the {} block window, but {} still stands - not stranded",
                        player.getScoreboardName(), column, SURFACE_WINDOW,
                        doors.checked().isEmpty() ? "a mound" : doors.checked().getLast());
            }
            return false;
        }

        SAVED.remove(player.getUUID());
        return true;
    }

    /**
     * Whether the colony whose ground this is still has a mound standing, cached
     * for {@link #VERDICT_COOLDOWN}.
     */
    private static Doors doorsOf(ServerPlayer player, ServerLevel overworld, BlockPos column, long now) {
        Doors cached = DOORS.get(player.getUUID());
        if (cached != null && now < cached.until()) {
            return cached;
        }

        Doors fresh = colonyStillHasADoor(overworld, column, now);
        DOORS.put(player.getUUID(), fresh);
        return fresh;
    }

    /**
     * Does this ground's colony have any way up left at all.
     *
     * <p>Mound positions are not stored anywhere as such - a colony is a list of
     * runs, and every run remembers the two mounds it joins, so the endpoints
     * <em>are</em> the mounds.</p>
     *
     * <p><strong>By colony where there is one, by radius only where there is
     * not.</strong> A radius looks like the simpler question and gets this wrong:
     * {@code chooseExit} weights its pick towards the far side of the network, so
     * a mature colony has runs most of the width of its own box - over a hundred
     * and twenty blocks - and a player halfway along one of those is further from
     * either end than any radius that does not also reach into the neighbouring
     * colony. Asking {@code linksOf} for the colony whose box this column is in
     * has no such gap and cannot bleed across a border either. The radius is kept
     * for the one case it is right for: a column inside no colony's box at all,
     * where there is no colony to ask.</p>
     *
     * <p>Each mound looked at gets its chunk loaded outright first, on the
     * courtesy {@code BurrowTransit.leave} learnt the hard way: with nobody above,
     * the chunk unloads within a minute of descending, {@code getHeight} answers
     * the world floor for an unloaded chunk rather than loading it, and every
     * mound in the colony reads as gone at exactly the moment nothing is keeping
     * the ground warm. Getting that wrong here does not refuse a door, it starts a
     * countdown - so it is the more expensive mistake of the two.</p>
     *
     * <p>An empty answer - no runs recorded, or none of the nearest few still
     * standing - means stranded. That is the direction this has to fail in.</p>
     */
    private static Doors colonyStillHasADoor(ServerLevel overworld, BlockPos column, long now) {
        long until = now + VERDICT_COOLDOWN;

        ColonyStore store = ColonyStore.get(overworld);
        Colony colony = store.at(column);
        List<BurrowLink> runs = colony != null
                ? store.linksOf(colony.id())
                : store.linksNear(column, BurrowConstants.COLONY_EXTENT);
        if (runs.isEmpty()) {
            return new Doors(until, false, List.of());
        }

        // Without repeats: a mound is an end of as many runs as it has, and
        // looking at the same heap four times would spend the whole budget on one
        // column. Nearest first, so the cap keeps the mounds most likely to be the
        // player's actual way home.
        Set<BlockPos> ends = new LinkedHashSet<>();
        for (BurrowLink run : runs) {
            ends.add(run.a());
            ends.add(run.b());
        }
        List<BlockPos> mounds = new ArrayList<>(ends);
        mounds.sort(Comparator.comparingDouble(mound -> mound.distSqr(column)));

        List<BlockPos> checked = new ArrayList<>(ENDPOINT_PROBES);
        for (BlockPos mound : mounds) {
            if (checked.size() >= ENDPOINT_PROBES) {
                break;
            }
            checked.add(mound);

            overworld.getChunk(SectionPos.blockToSectionCoord(mound.getX()),
                    SectionPos.blockToSectionCoord(mound.getZ()));
            if (MoleMound.isMound(overworld,
                    MoundNetwork.surfaceAt(overworld, mound.getX(), mound.getZ()))) {
                return new Doors(until, true, checked);
            }
        }

        return new Doors(until, false, checked);
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
            Doors doors = DOORS.get(id);
            // Named, because "it told me I was shut in" is otherwise a report with
            // nothing behind it. If this line lists mounds that are plainly still
            // standing, the bug is in the probe rather than in the world.
            say("{} shut in at {}: no mound within {}, and {} - the dig starts",
                    player.getScoreboardName(),
                    BurrowGeometry.toOverworld(player.blockPosition()),
                    SURFACE_WINDOW,
                    doors == null || doors.checked().isEmpty()
                            ? "no colony run is recorded near here at all"
                            : "none of " + doors.checked() + " is still standing");
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

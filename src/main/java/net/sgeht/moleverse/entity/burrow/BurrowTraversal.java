package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.entity.TravellingMole;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * Turns an overworld mole's trip into something a player standing in the burrow
 * can be run over by.
 *
 * <p>This is the other half of {@code docs/BURROW_LIFE.md} 2d, and it is the same
 * shape as {@code BurrowReconciler.linkChanged}: overworld code that has just
 * decided something calls into burrow-side code, on the server thread, mid tick.
 * Nothing polls, nothing is stored, and nothing survives a reload. A trip is an
 * event that lasts a few hundred ticks, and so is everything here.</p>
 *
 * <h2>Only along a run that has already been dug</h2>
 *
 * <p>A trip whose pair of mounds has no {@link BurrowLink} yet gets no traversal
 * at all, and that is a hard rule rather than a simplification. The link is
 * written when the mole surfaces, and the corridor below is carved from the link -
 * so on the trip that creates one there is no corridor down there to run down, and
 * an apparition on that route would be a giant mole swimming through solid earth
 * where the player cannot see it and could not reach it. The second trip along the
 * same pair is the first one anybody sees, which is right: a run has to be dug
 * before it is a run.</p>
 *
 * <p>The path is taken from the stored link and never from the route the mole is
 * walking. The two agree on all but one trip - the one where a mole re-digs an
 * established run through ground that has changed height - and on that trip the
 * route is the new profile while the corridor is still cut to the old one. The
 * link is where the air is, so the link is what is walked.</p>
 *
 * <h2>One traversal per trip, and only while somebody is there</h2>
 *
 * <p>Nothing spawns until a player is in the burrow near the point the mole has
 * reached. The gate is deliberately the current point of the run rather than the
 * whole polyline: a run is a couple of hundred blocks long down here, so "within
 * sixty-four of the path" is satisfied by a player standing at the far end of a
 * corridor the mole will never reach in their lifetime, and what that buys is an
 * entity frozen in an unloaded chunk. Where the mole is now is the only part of
 * the path anybody can be run over by.</p>
 *
 * <p>Once it has been and gone it does not come back. A player who walks out of
 * range ends the traversal and walking back in does not restart it - the mole
 * above is one animal making one journey, and two apparitions of it would be two
 * moles.</p>
 */
public final class BurrowTraversal {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * On from the first tick of a Gradle run, off in a shipped game.
     *
     * <p>{@code BurrowReconciler}'s gate rather than {@code BurrowLog}'s, because
     * this is burrow-side code and shares that class's logger. The consequence is
     * worth knowing while reading a log: {@code /moleverse mole log off} silences
     * the mole's own commentary and not these lines. In a development run both are
     * on, which is the case that matters.</p>
     *
     * <p>The reason there is any logging here at all is the failure mode this
     * class is most prone to and least able to explain: <em>nothing happens</em>.
     * Three gates stand between a mole going under and a player seeing anything -
     * a run has to have been recorded, somebody has to be near it, and the
     * corridor has to have been carved there - and every one of them fails
     * silently and correctly. A player who spends an evening below and sees no
     * giant mole has no way to tell which of the three it was, or whether the
     * feature is simply broken. So each gate says so once.</p>
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    /**
     * How near a player has to be for the mole to appear, in burrow blocks.
     *
     * <p>Sixty-four is four overworld blocks at {@link BurrowGeometry#SCALE},
     * which is roughly the distance a corridor stops being a straight sight line
     * anyway. The keep range is half again as much, so a player who backs off a
     * few blocks does not switch it on and off.</p>
     */
    private static final double SPAWN_RANGE = 64.0;

    private static final double KEEP_RANGE = 96.0;

    /**
     * How far around a burrow position {@link #runNear} collects links, in
     * overworld blocks.
     *
     * <p>{@code linksNear} filters on the ends of a run, and a run is at most a
     * few dozen overworld blocks long, so this catches every corridor that could
     * pass anywhere near - and, being overworld blocks, it is a quarter of the
     * distance it sounds like down below. The same number {@code BurrowLife} uses
     * to find corridors to put animals in.</p>
     */
    private static final int LINK_SEARCH_RADIUS = 96;

    // --- the ambient lane -----------------------------------------------------

    /**
     * How long between one scheduled traversal and the next, in ticks: two
     * minutes plus up to two more.
     *
     * <p>Rare enough that meeting one is an event and not traffic. The burrow's
     * own scratching ambience runs at forty-five to a hundred and twenty seconds,
     * and this is deliberately the slower of the two: the scratching is the rumour
     * and this is the animal, and a rumour that is always immediately confirmed
     * stops being one.</p>
     */
    private static final int AMBIENT_MIN_DELAY = 2400;
    private static final int AMBIENT_DELAY_SPREAD = 2400;

    /**
     * The property that makes the burrow's two rare events happen often enough to
     * watch, and why it is not {@code DevGate}'s umbrella.
     *
     * <p>Every other development instrument in this mod obeys one rule: <em>a dev
     * instrument must not change the game.</em> {@code DevGate} gates them at
     * registration precisely so a shipped jar carries the code with no way in.
     * This one is different in kind - it does not add an instrument, it moves a
     * tuning number, and something that moves a tuning number is exactly what the
     * umbrella must never imply. Turning it on under {@code moleverse.dev} would
     * mean every ordinary development run was quietly observing rates nobody
     * ships, which is how a session spends an evening tuning the wrong
     * thing.</p>
     *
     * <p>So: its own property, set on purpose, never by {@code configureEach}. It
     * is declared here rather than beside the umbrella because the two answer
     * different questions and only these two classes ask this one - see the
     * report note about hoisting it if a third ever does.</p>
     */
    public static final String FAST_EVENTS_PROPERTY = "moleverse.devFastEvents";

    /** Read once, at class initialisation - the property arrives long before any mod class loads. */
    public static final boolean FAST_EVENTS = Boolean.getBoolean(FAST_EVENTS_PROPERTY);

    /** Fifteen to thirty seconds instead of two to four minutes. */
    private static final int FAST_AMBIENT_DELAY = 300;
    private static final int FAST_AMBIENT_SPREAD = 300;

    /** Whether the one line about the fast rates has been written yet. */
    private static boolean fastEventsAnnounced;

    /**
     * Says once, and out loud, that the numbers in this log are not shipping
     * numbers.
     *
     * <p>Deliberately <em>not</em> behind {@code DEV_LOGGING}. The one reader this
     * line exists for is somebody looking at a log wondering why a giant mole came
     * past three times in a minute, and gating the answer behind a second flag is
     * how they end up concluding the rates are broken.</p>
     */
    static void announceFastEvents() {
        if (!fastEventsAnnounced) {
            fastEventsAnnounced = true;
            LOG.info("fast event rates active - {} is set, these are not shipping rates",
                    FAST_EVENTS_PROPERTY);
        }
    }

    /** How near a run has to pass a player to be worth scheduling on. */
    private static final double AMBIENT_RANGE = 64.0;

    /**
     * How far short of the player the scheduled mole enters the run, and how much
     * run has to be left beyond them.
     *
     * <p>The lead-in is what makes it an approach rather than an appearance: it
     * breaks the floor a good way off, and at the scheduled lane's seven blocks a
     * second that is the better part of seven seconds of something coming. The
     * tail is the other half of the same idea - a mole that dives back into the
     * earth the moment it draws level was not passing through, it was
     * visiting.</p>
     *
     * <p><strong>The lead must stay under {@link #SPAWN_RANGE}.</strong> Nothing
     * enforces it and it is easy to break by tuning one of the two: the mole is
     * armed at the lead point and only becomes visible once the spawn gate opens,
     * so a lead beyond the gate's reach means it travels invisibly until it comes
     * within range and then appears out of nothing, which is the one failure this
     * whole approach was built to avoid.</p>
     */
    private static final double AMBIENT_LEAD = 48.0;
    private static final double AMBIENT_MIN_AFTER = 24.0;

    /**
     * How fast a scheduled traversal walks its run, in burrow blocks per tick.
     *
     * <p>Derived and not chosen: it is exactly what a real trip works out to -
     * the overworld's three blocks a second, stretched by {@link BurrowGeometry#SCALE}.
     * The two lanes have to be indistinguishable, and a number picked by hand here
     * would drift away from the one the trip lane gets for free.</p>
     */
    private static final double AMBIENT_SPEED =
            BurrowConstants.UNDERGROUND_SPEED_PER_TICK * BurrowGeometry.SCALE;

    /**
     * What the scheduled lane multiplies that by.
     *
     * <p>Three fifths, and it is the one number where the two lanes are allowed to
     * differ. The trip lane may not: its whole claim is that it is showing a
     * journey happening in the overworld, and a mole that took half again as long
     * as the animal it mirrors would be a lie the log could catch. Nothing is
     * being mirrored here, so the pace is free - and what it buys is weight. The
     * giant is now four and nine tenths blocks across and fills a feeding run; at
     * the full rate something that size going past is a blur, and heavy things
     * that move fast read as small things filmed close up.</p>
     *
     * <p>It also lengthens every pause the beats can afford, because the budget in
     * {@code TravellingMole.pauseSpan} is measured in fractions of the run per
     * tick: a slower run has more slack in it, so the stops get longer without
     * anything being said about them twice.</p>
     */
    private static final double AMBIENT_PACE = 0.6;

    /**
     * Trips with a traversal to their name, by the id of the mole making them.
     *
     * <p>Keyed by the entity id rather than by the UUID because it is only ever
     * read on the tick the mole itself is being ticked, and a reused id from a
     * previous world is cleared by {@link #tripStarted} before it can be read.
     * Static for the same reason {@code BurrowReconciler}'s queue is: there is
     * nothing to hang it off that both dimensions can see.</p>
     */
    private static final Map<Integer, Traversal> ACTIVE = new HashMap<>();

    /*
     * Plain HashMaps, touched from two dimensions, and that is safe - but it is
     * alarming enough on sight to be worth saying why. The trip lane is reached
     * from mole AI in the overworld's tick; the ambient lane from this class's own
     * tick in the burrow's. A server ticks its levels one after another on the
     * main thread, so the two can never overlap, and neither can be reached from a
     * chunk worker or a network thread - nothing in either path is called from
     * anywhere but a level tick. A concurrent map would buy nothing and would
     * suggest a guarantee that does not exist elsewhere in this package.
     */

    /**
     * Scheduled traversals, by the id of the colony whose run is being walked.
     *
     * <p>Keyed by colony because that is also the rate limit: one giant mole at a
     * time in one colony's runs, whichever lane put it there. Two would not be
     * wrong so much as cheap - the thing is meant to be an event.</p>
     */
    private static final Map<Integer, Traversal> AMBIENT = new HashMap<>();

    /** Game time the next scheduled traversal may be considered, or unset. */
    private static long nextAmbientTime = Long.MIN_VALUE;

    private BurrowTraversal() {
    }

    // --- what a trip tells it -------------------------------------------------

    /**
     * A mole has just gone under. Works out whether there is a corridor to mirror
     * it in, and if so arms a traversal - it does not spawn anything yet.
     *
     * @param overworld the level the trip is happening in, which is where the
     *                  colony store lives
     * @param entry     the mound it went down, so the run can be walked in the
     *                  direction the mole is actually travelling
     */
    public static void tripStarted(ServerLevel overworld, Mole mole, BlockPos entry, BlockPos exit) {
        sweepDead();

        // Told to leave rather than merely forgotten. A mole that starts a second
        // trip while its last apparition is still walking is a goal that was torn
        // down without stop() running, and dropping the map entry alone would
        // leave a giant mole in a corridor with nothing driving it - the orphan
        // timer would collect it two seconds later, which is two seconds of an
        // animal standing still in front of somebody.
        Traversal previous = ACTIVE.remove(mole.getId());
        if (previous != null && previous.entity != null && !previous.entity.isRemoved()) {
            say("traversal ended: {} its mole started another trip - digging away", who(mole));
            previous.entity.digAway();
        }

        ServerLevel burrow = ModDimensions.burrowLevel(overworld.getServer());
        if (burrow == null) {
            say("no traversal: {} the burrow dimension is not loaded", who(mole));
            return;
        }

        BurrowLink link = ColonyStore.get(overworld).linkBetween(entry, exit);
        if (link == null || link.pointCount() < 2) {
            // The commonest reason by far, and the one that reads as a broken
            // feature: a pair of mounds is only recorded once a trip between
            // them has finished cleanly, so the first trip along any run can
            // never be mirrored. The second one can.
            say("no traversal: {} no run recorded between {} and {} yet - a first trip has nothing to mirror",
                    who(mole), where(entry), where(exit));
            return;
        }

        // Deliberately no colony check here. Arming costs a map entry and a
        // polyline, and a trip that turns out to have nobody near it should never
        // have been able to hold a colony against a scheduled run that does. The
        // spawn gate in drive() decides, and it decides with the answer in front
        // of it rather than a few hundred ticks early.
        TunnelWalk.Path path = TunnelWalk.Path.ofLink(link, entry);
        ACTIVE.put(mole.getId(), new Traversal(burrow, path, link.colony()));
        say("traversal armed: {} run {} to {}, {} point(s), {} blocks of corridor",
                who(mole), where(entry), where(exit), link.pointCount(), round(path.length()));
    }

    /**
     * One tick of the trip. Spawns, feeds or ends the traversal.
     *
     * <p>Everything this class does that is not a decision happens here, because
     * this is the one moment per tick when the trip's own progress is a fresh
     * number. The mole above is moved by {@code BurrowRoute.advance} immediately
     * before it, so the fraction handed on has not yet been anything else.</p>
     */
    public static void tripProgressed(Mole mole, BurrowRoute route) {
        Traversal trip = ACTIVE.get(mole.getId());
        if (trip == null) {
            return;
        }

        double length = route.length();
        double fraction = length <= 1.0E-6 ? 1.0 : route.travelled() / length;
        if (!drive(trip, fraction, who(mole))) {
            ACTIVE.remove(mole.getId());
        }
    }

    /**
     * One tick of one traversal, whichever lane armed it.
     *
     * <p>Both lanes are the same machine from here on. The only thing that
     * differs is where the fraction came from - a mole's trip in the overworld,
     * or a scheduler counting on its own - and by the time it reaches this method
     * it is a number either way. That is what lets the ambient lane exist without
     * a second copy of the gates, the spawn or the despawn.</p>
     *
     * @return false when the traversal is over and the caller should forget it
     */
    private static boolean drive(Traversal trip, double fraction, String who) {
        TravellingMole apparition = trip.entity;
        if (apparition != null && apparition.isRemoved()) {
            // It left on its own - somebody hit it, or it was cut off from its
            // progress for long enough to give up. Either way this run has had
            // its one traversal.
            say("traversal ended: {} gone before the run was - struck, or cut off from its progress", who);
            return false;
        }

        Vec3 at = trip.path.at(fraction);

        if (apparition == null) {
            // Each refusal is remembered rather than logged. It is asked again
            // every tick and the answer rarely changes, so saying it here would
            // be several hundred identical lines; saying it once at the end, when
            // it is known to have held throughout, is the same information and is
            // readable.
            if (!anyoneWithin(trip.burrow, at, SPAWN_RANGE)) {
                trip.gate = "no player came within " + round(SPAWN_RANGE) + " blocks of the run";
                return true;
            }
            String blocked = corridorBlockedAt(trip.burrow, at);
            if (blocked != null) {
                trip.gate = blocked;
                return true;
            }

            // The last gate, and the only one that enforces one mole per colony.
            // Both lanes may be armed on the same runs; this is the moment the
            // question stops being hypothetical, so this is where it is asked.
            String taken = busyColony(trip.colony) ? "another giant mole"
                    : WeaselIncursion.isRunningIn(trip.colony) ? "a weasel incursion"
                    : null;
            if (taken != null) {
                // Said once per traversal rather than per tick: the answer holds
                // for as long as the other animal does, which is most of a run.
                if (trip.gate == null || !trip.gate.startsWith("the colony was held")) {
                    say("traversal held back: {} colony #{}'s runs are taken by {}",
                            who, trip.colony, taken);
                }
                trip.gate = "the colony was held by " + taken + " for the whole run";
                return true;
            }

            apparition = spawn(trip, fraction);
            if (apparition == null) {
                trip.gate = "the entity could not be added to the burrow";
                return true;
            }
            trip.entity = apparition;
            trip.gate = null;
            say("traversal spawned: {} at {}, {}% along the run",
                    who, where(BlockPos.containing(at)), Math.round(fraction * 100.0));
        } else if (!anyoneWithin(trip.burrow, apparition.position(), KEEP_RANGE)) {
            say("traversal ended: {} no player left within {} blocks - digging away", who, round(KEEP_RANGE));
            apparition.digAway();
            return false;
        }

        // A target, not a position. What the apparition does around it - easing
        // off, stopping to sniff, hurrying to make the time up - is its own
        // business, and it is tied back to this number by a rubber band rather
        // than pinned to it. The entity cannot tell which lane is feeding it, and
        // that is the whole design of the ambient one.
        apparition.setTripFraction(fraction);
        return true;
    }

    /**
     * The mole is on its way back up. The apparition digs out where it stands and
     * removes itself once it is buried.
     *
     * <p>Safe to call for a trip that never had one, and safe to call twice: the
     * goal ends the underground phase in one place and cleans itself up in
     * another, and both of them say so rather than one of them having to know
     * whether the other already did.</p>
     */
    public static void tripEnded(Mole mole) {
        Traversal trip = ACTIVE.remove(mole.getId());
        if (trip == null) {
            return;
        }

        if (trip.entity != null && !trip.entity.isRemoved()) {
            trip.entity.digAway();
            say("traversal ended: {} the trip is over - digging away", who(mole));
        } else if (trip.entity == null) {
            // The line the whole gate exists for. A run was there to mirror and
            // nothing was ever shown, and this names which of the remaining two
            // gates stayed shut for the length of the trip.
            say("no traversal: {} armed but never shown - {}",
                    who(mole), trip.gate == null ? "reason not recorded" : trip.gate);
        }
    }

    /**
     * Drops everything still armed.
     *
     * <p>For a world being unloaded, on the same reasoning as
     * {@code BurrowReconciler.forget}: the map is static, so a single-player
     * client leaving one world for another would otherwise carry the first one's
     * moles into the second, where their ids mean somebody else.</p>
     */
    public static void forget() {
        ACTIVE.clear();
        AMBIENT.clear();
        nextAmbientTime = Long.MIN_VALUE;
    }

    // --- the colony works its runs whether anybody is upstairs or not ---------

    /**
     * One burrow tick: moves the scheduled traversals along, and now and then
     * starts another.
     *
     * <p><strong>Why this lane exists.</strong> The trip lane mirrors a mole
     * actually travelling in the overworld, and in single player that mole is
     * almost never running. A player standing in the burrow is not standing in the
     * overworld, so the chunks over their colony unload, no mole ticks, no trip
     * happens, and a mechanism that waits for one waits for ever. The first
     * playtest of it found exactly that: an evening below ground and nothing
     * seen.</p>
     *
     * <p>So the fiction absorbs the constraint rather than fighting it, and it is
     * a better fiction for it: <em>the colony works its runs whether anyone is
     * watching upstairs or not.</em> {@code docs/BURROW_LIFE.md} 2d already argues
     * that nobody can stand in both worlds at once and that plausible timing at
     * the seams is all the synchrony that is observable. With nobody above, there
     * are no seams left to be plausible at - so a scheduled traversal is not a
     * substitute for a real one, it is the same statement with the unobservable
     * half dropped.</p>
     *
     * <p>The trip lane stays exactly as it was. It is still the right answer when
     * somebody <em>is</em> above - a second player, a multiplayer server - and the
     * two cannot collide, because a colony holds one traversal at a time.</p>
     */
    public static void tick(ServerLevel burrow) {
        driveAmbient();
        considerAmbient(burrow);
    }

    /** Advances every scheduled traversal by its own rate, and retires the finished ones. */
    private static void driveAmbient() {
        if (AMBIENT.isEmpty()) {
            return;
        }

        Iterator<Traversal> running = AMBIENT.values().iterator();
        while (running.hasNext()) {
            Traversal trip = running.next();
            String who = colonyTag(trip.colony);
            trip.fraction = Math.min(1.0, trip.fraction + trip.rate);

            if (!drive(trip, trip.fraction, who)) {
                running.remove();
                continue;
            }

            if (trip.fraction >= 1.0) {
                if (trip.entity != null) {
                    // Up to the rubber band's slack short of the end, which is
                    // where the animal itself is rather than where the schedule
                    // says. It dives into the floor there, and a corridor is
                    // floor all the way along.
                    trip.entity.digAway();
                    say("traversal ended: {} reached the far end of the run - digging away", who);
                } else {
                    say("no traversal: {} the run finished with nothing shown - {}",
                            who, trip.gate == null ? "reason not recorded" : trip.gate);
                }
                running.remove();
            }
        }
    }

    /**
     * Whether it is time for another, and if so whether there is a run to walk.
     *
     * <p>The timer is checked before anything else and is the whole of the cost on
     * an ordinary tick. When it does fire, the search is one walk over the
     * colony's links per player below - a few dozen polylines every few minutes,
     * against a dimension that is carving chunks on the same tick.</p>
     *
     * <p>The first delay is a full one rather than immediate, on
     * {@code BurrowScratching}'s reasoning: stepping out of the shaft should never
     * be answered by a giant mole in the same second.</p>
     */
    private static void considerAmbient(ServerLevel burrow) {
        if (burrow.players().isEmpty()) {
            // The clock does not turn in an empty dimension. Letting it run would
            // mean the first person down the shaft inherits whatever was left on
            // it, which is sometimes nothing at all.
            nextAmbientTime = Long.MIN_VALUE;
            return;
        }

        long now = burrow.getGameTime();
        if (nextAmbientTime == Long.MIN_VALUE) {
            nextAmbientTime = now + ambientDelay(burrow);
            return;
        }
        if (now < nextAmbientTime) {
            return;
        }
        nextAmbientTime = now + ambientDelay(burrow);

        for (ServerPlayer player : burrow.players()) {
            if (!player.isSpectator()) {
                armAmbient(burrow, player);
            }
        }
    }

    private static int ambientDelay(ServerLevel burrow) {
        if (FAST_EVENTS) {
            announceFastEvents();
            return FAST_AMBIENT_DELAY + burrow.getRandom().nextInt(FAST_AMBIENT_SPREAD);
        }
        return AMBIENT_MIN_DELAY + burrow.getRandom().nextInt(AMBIENT_DELAY_SPREAD);
    }

    /**
     * Finds a run near this player and puts a mole on it.
     *
     * <p>The direction is chosen so the player is passed rather than arrived at:
     * whichever way round leaves more run beyond them wins, and the mole enters
     * the floor {@link #AMBIENT_LEAD} blocks short of where it will meet them. A
     * run with too little left on the far side is skipped rather than shortened -
     * an animal that surfaces, draws level and immediately dives has not gone
     * anywhere.</p>
     *
     * <p>Candidates are gathered and one is drawn at random. Taking the nearest
     * would wear the same corridor into a thoroughfare while the rest of the
     * colony stayed silent.</p>
     */
    private static void armAmbient(ServerLevel burrow, ServerPlayer player) {
        ServerLevel overworld = burrow.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        BlockPos above = BurrowGeometry.toOverworld(player.blockPosition());
        List<BurrowLink> nearby = ColonyStore.get(overworld).linksNear(above, LINK_SEARCH_RADIUS);
        if (nearby.isEmpty()) {
            say("no ambient traversal: no colony run is recorded anywhere near {}",
                    where(player.blockPosition()));
            return;
        }

        Vec3 at = player.position();
        List<Traversal> candidates = new ArrayList<>();
        boolean colonyBusy = false;

        for (BurrowLink link : nearby) {
            if (link.pointCount() < 2) {
                continue;
            }
            if (busyColony(link.colony())) {
                colonyBusy = true;
                continue;
            }

            TunnelWalk.Path forward = TunnelWalk.Path.ofLink(link, link.a());
            double length = forward.length();
            double here = forward.nearestFraction(at);
            if (forward.at(here).distanceToSqr(at) > AMBIENT_RANGE * AMBIENT_RANGE) {
                continue;
            }

            boolean useForward = 1.0 - here >= here;
            TunnelWalk.Path path = useForward ? forward : forward.reversed();
            double meets = useForward ? here : 1.0 - here;
            if ((1.0 - meets) * length < AMBIENT_MIN_AFTER) {
                continue;
            }

            double start = Math.max(0.0, meets - AMBIENT_LEAD / Math.max(1.0, length));
            if (corridorBlockedAt(burrow, path.at(start)) != null) {
                continue;
            }

            Traversal trip = new Traversal(burrow, path, link.colony());
            trip.fraction = start;
            trip.rate = AMBIENT_SPEED * AMBIENT_PACE / Math.max(1.0, length);
            candidates.add(trip);
        }

        if (candidates.isEmpty()) {
            say("no ambient traversal: {}", colonyBusy
                    ? "the colony already has a giant mole walking its runs"
                    : "no run near enough, long enough or carved far enough to walk");
            return;
        }

        Traversal chosen = candidates.get(burrow.getRandom().nextInt(candidates.size()));
        AMBIENT.put(chosen.colony, chosen);
        say("ambient traversal: {} armed on a run of {} blocks, entering {}% along",
                colonyTag(chosen.colony), round(chosen.path.length()),
                Math.round(chosen.fraction * 100.0));
    }

    /**
     * Whether this colony already has a giant mole in its runs, from either lane.
     *
     * <p>A walk over a handful of entries rather than an index, asked once per
     * link per scheduling attempt - which is once every few minutes.</p>
     */
    /**
     * Whether a giant mole is in this colony's runs right now, from either lane.
     *
     * <p>For {@code WeaselIncursion}, which must not put a second corridor-filling
     * animal into a corridor that already has one. Two of them passing in a five
     * wide run would be two bodies in the same space, and the fiction of either
     * would not survive the other.</p>
     */
    public static boolean hasTraversal(int colony) {
        return busyColony(colony);
    }

    /**
     * Whether this colony has a giant mole in its runs <em>right now</em>.
     *
     * <p><strong>A living entity, and not an armed entry.</strong> That
     * distinction is the whole of a starvation found in a live log: with a mole
     * active above, the trip lane armed every few seconds and every one of those
     * ended {@code armed but never shown - no player came within 64 blocks}, while
     * the ambient lane - the one that routes past the player by construction - was
     * turned away with {@code the colony already has one on its runs}. Hopes were
     * holding the slot against the only lane that was going to fill it.</p>
     *
     * <p>So arming is free and non-exclusive, and the invariant is enforced where
     * it can actually be broken: at the spawn. Both lanes may be armed at once,
     * and whichever reaches its spawn gate first - which means whichever really
     * has a player in range and a carved corridor - is the one that gets the
     * colony. That gives both directions the right answer without a rule about
     * lanes: an unshown arm of either kind blocks nothing, and a mole already
     * walking blocks everything.</p>
     */
    private static boolean busyColony(int colony) {
        for (Traversal trip : AMBIENT.values()) {
            if (trip.colony == colony && alive(trip)) {
                return true;
            }
        }
        for (Traversal trip : ACTIVE.values()) {
            if (trip.colony == colony && alive(trip)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alive(Traversal trip) {
        return trip.entity != null && !trip.entity.isRemoved();
    }

    private static String colonyTag(int colony) {
        return "[colony #" + colony + "]";
    }

    // --- a run to set off down ------------------------------------------------

    /**
     * A run to walk, and how far along it the animal already is.
     *
     * <p>The fraction matters as much as the path. A mole's traversal joins its
     * run at the beginning because the trip above did; an animal that lives down
     * here joins wherever it happens to be standing, and everything after that -
     * where it comes out of the floor, how far it has left to go - is measured
     * from that number.</p>
     */
    public record Run(TunnelWalk.Path path, double from) {

        /** How much of the run is still ahead, in burrow blocks. */
        public double remaining() {
            return this.path.length() * (1.0 - this.from);
        }
    }

    /**
     * Picks a run whose corridor passes near this spot, to travel away down.
     *
     * <p>For the burrow's own animals rather than for a mole's trip: nothing above
     * ground has decided anything, so the choice of run <em>is</em> the behaviour.
     * The rule is proximity and nothing else - a corridor within {@code range} of
     * where the animal stands is one it could plausibly dig sideways into, and one
     * further off would be a teleport with a story attached.</p>
     *
     * <p>The direction is decided by how much run is left rather than rolled: from
     * a point a fifth of the way along, going the short way is a journey that ends
     * before it has read as one. Whichever end is further wins, and a run with less
     * than {@code minTravel} either way is refused rather than shortened.</p>
     *
     * <p>Candidates are gathered and then one is picked at random, not the nearest.
     * At a junction three corridors pass within a few blocks of each other, and an
     * animal that always took the nearest would wear a groove between the same two
     * chambers for the life of the world.</p>
     *
     * @param at        where the animal is, in burrow blocks
     * @param range     how near a corridor has to pass to be worth digging into
     * @param minTravel the shortest journey worth making, in burrow blocks
     * @return a run oriented so that travel goes from {@link Run#from} to 1, or
     *         null when there is nothing near enough or long enough
     */
    public static @Nullable Run runNear(ServerLevel burrow, Vec3 at, double range, double minTravel,
            RandomSource random) {
        MinecraftServer server = burrow.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }

        BlockPos above = BurrowGeometry.toOverworld(BlockPos.containing(at));
        List<BurrowLink> nearby = ColonyStore.get(overworld).linksNear(above, LINK_SEARCH_RADIUS);
        if (nearby.isEmpty()) {
            return null;
        }

        List<Run> candidates = new ArrayList<>();
        for (BurrowLink link : nearby) {
            if (link.pointCount() < 2) {
                continue;
            }

            TunnelWalk.Path forward = TunnelWalk.Path.ofLink(link, link.a());
            double here = forward.nearestFraction(at);
            if (forward.at(here).distanceToSqr(at) > range * range) {
                continue;
            }

            double ahead = forward.length() * (1.0 - here);
            double behind = forward.length() * here;
            if (Math.max(ahead, behind) < minTravel) {
                continue;
            }

            candidates.add(ahead >= behind
                    ? new Run(forward, here)
                    : new Run(forward.reversed(), 1.0 - here));
        }

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    // --- the pieces -----------------------------------------------------------

    /**
     * Whether the corridor has been cut where the mole would come out.
     *
     * <p>One block read, a block above the walking surface, which
     * {@code CorridorCarver.walkway} clears unconditionally and which no dressing
     * pass fills - the roof this corridor hangs things from is four blocks
     * higher. Earth there means the reconciler has not reached this stretch yet -
     * a corridor whose far end is still waiting on a chunk - and a mole emerging
     * into earth is a rumble from inside a wall with nothing to show for it.</p>
     *
     * <p>Plainly {@code isAir} rather than the wider "is this open" that
     * {@code TunnelDecorator} keeps to itself. The question here is only whether
     * the run has been cut, and a second copy of that list would be one more thing
     * to keep in step for no gain: a block a player put in the way costs this one
     * tick, and the next tick asks about a different block.</p>
     */
    private static @Nullable String corridorBlockedAt(ServerLevel burrow, Vec3 at) {
        BlockPos above = BlockPos.containing(at.x, at.y + 1.0, at.z);
        if (!burrow.isPositionEntityTicking(above)) {
            return "the burrow chunk at " + where(above) + " never ticked";
        }
        // Two answers rather than one, because they call for opposite things. A
        // chunk that is not ticking is somebody standing too far away; earth
        // where the corridor should be is the reconciler not having caught up,
        // and that is a worldgen question rather than a traversal one.
        return burrow.getBlockState(above).isAir()
                ? null
                : "the corridor at " + where(above) + " is not carved yet";
    }

    // --- the commentary -------------------------------------------------------

    /**
     * One line, if anybody is listening.
     *
     * <p>The arguments are built before this is entered, so a shipped game still
     * pays for the {@code String.format} in {@link #who}. Every call site here
     * runs once per trip rather than once per tick - a trip is several hundred
     * ticks and several hundred block reads - so the cost is not worth an
     * {@code if} around each of them.</p>
     */
    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
    }

    /**
     * {@code [#42 @-118,64,301]} - the same prefix {@code BurrowLog} puts on
     * every line, so a mole can be followed from its trip into the burrow and
     * back without the two logs having to be read differently.
     */
    private static String who(Mole mole) {
        return String.format("[#%d @%d,%d,%d]",
                mole.getId(), mole.getBlockX(), mole.getBlockY(), mole.getBlockZ());
    }

    private static String where(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static long round(double blocks) {
        return Math.round(blocks);
    }

    /** Whether anybody in the burrow is close enough to this point to care. */
    private static boolean anyoneWithin(ServerLevel burrow, Vec3 at, double range) {
        double rangeSqr = range * range;
        for (ServerPlayer player : burrow.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(at) <= rangeSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts one into the world, armed and already buried.
     *
     * <p>{@code arm} places it before it is added rather than after, because the
     * packet that introduces an entity to a client carries the position it was
     * added at - see the reasoning on that method.</p>
     */
    private static @Nullable TravellingMole spawn(Traversal trip, double fraction) {
        TravellingMole apparition =
                ModEntities.TRAVELLING_MOLE.get().create(trip.burrow, EntitySpawnReason.EVENT);
        if (apparition == null) {
            return null;
        }

        apparition.arm(trip.burrow, trip.path, fraction);
        return trip.burrow.addFreshEntity(apparition) ? apparition : null;
    }

    /**
     * Forgets trips whose apparition is gone.
     *
     * <p>Run when a trip starts rather than on a tick, because that is the only
     * moment this class is reached without a mole to ask about, and because a
     * traversal whose goal was torn down mid-trip - a chunk unloading under a
     * mole - never says so. The map is therefore bounded by the number of moles
     * that have started a trip since the last one, which is one.</p>
     */
    private static void sweepDead() {
        sweepDead(ACTIVE);
        // The ambient lane is driven every tick and retires its own, so this
        // finds nothing there in practice. It is swept anyway because the
        // per-colony rate limit reads both maps, and a stale entry in either
        // would lock a colony out of ever getting another.
        sweepDead(AMBIENT);
    }

    private static void sweepDead(Map<Integer, Traversal> lane) {
        Iterator<Traversal> waiting = lane.values().iterator();
        while (waiting.hasNext()) {
            TravellingMole apparition = waiting.next().entity;
            if (apparition != null && apparition.isRemoved()) {
                waiting.remove();
            }
        }
    }

    /** One armed trip: where it would run, and what is running it, once there is one. */
    private static final class Traversal {

        private final ServerLevel burrow;
        private final TunnelWalk.Path path;

        /** Whose runs these are. The rate limit is per colony, across both lanes. */
        private final int colony;

        /**
         * The ambient lane's own progress along the run, and how much of it a tick
         * adds. Unused by the trip lane, which is handed a fraction by a mole in
         * another dimension instead.
         */
        private double fraction;
        private double rate;

        /** Null until somebody is near enough to be shown one. */
        private @Nullable TravellingMole entity;

        /**
         * Why nothing has been shown yet, for the one line at the end of the trip.
         *
         * <p>Overwritten every tick rather than kept as a history: what is worth
         * saying is the gate that was still shut when the trip finished, and a
         * traversal that spawned clears it because there is then nothing to
         * explain.</p>
         */
        private @Nullable String gate;

        private Traversal(ServerLevel burrow, TunnelWalk.Path path, int colony) {
            this.burrow = burrow;
            this.path = path;
            this.colony = colony;
        }
    }
}

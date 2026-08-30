package net.sgeht.moleverse.entity.burrow;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.entity.critter.BurrowCritter;
import net.sgeht.moleverse.entity.predator.Shrew;
import net.sgeht.moleverse.entity.predator.Weasel;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The weasel incursion: the burrow's one real emergency.
 *
 * <p>{@code docs/BURROW_LIFE.md} 2b, after the playtest sent the weasel back to
 * the drawing board. A real weasel outsizes a mole and hunts it down its own
 * runs, so at mole scale it cannot be a thing that wanders into a corridor - it
 * has to be corridor-filling, and something corridor-filling cannot be a spawn.
 * It was taken off the biome list and is this instead: very rare, announced,
 * survivable, and over one way or another inside a minute or two.</p>
 *
 * <h2>It is the giant mole's machinery pointed the other way</h2>
 *
 * <p>Everything about how it moves is {@link TunnelWalk} - the same guided walk,
 * the same width clamp, the same floor probing, the same slide through the floor
 * at either end. What it does <em>not</em> share with the mole is the point:</p>
 *
 * <ul>
 * <li>The mole is an apparition and cannot be touched. This is an animal. It
 *     keeps its health, its damage, its loot and its death, and killing it ends
 *     the incursion - which is what {@code not_today} already advances on.</li>
 * <li>The mole passes through. This hunts: it swerves at whatever is in the run
 *     ahead of it, player or critter alike, and bites.</li>
 * <li>The mole is silent until it arrives. This is announced, and the
 *     announcement is most of the event.</li>
 * </ul>
 *
 * <h2>Why the bolt-hole works, and it is not a rule</h2>
 *
 * <p>The plan names the bolt-hole niche as the counterplay, and nothing here
 * enforces that - it falls out of two things being true at once. The bite box is
 * grown forward out of the animal's own bounding box and is never inflated
 * sideways, so its reach across the run is exactly its own width; and a bolt-hole
 * is a stub that climbs steeply <em>off the ceiling</em>, while this walks the
 * floor and is 3.85 blocks tall against a corridor's six. A player up one is
 * above everything the weasel is. A larder alcove is a different matter - those
 * are lateral, and {@link #LUNGE_LEAN} is the number that keeps a swerve from
 * reaching into one.</p>
 *
 * <h2>One at a time, and never over a mole</h2>
 *
 * <p>A single incursion anywhere in the burrow, not one per colony. With one
 * player below there is one colony that matters, and a second event running
 * unwatched somewhere else would be cost without an audience. It also refuses to
 * start in a colony whose runs already have a giant mole in them - see
 * {@link BurrowTraversal#hasTraversal} for why two corridor-filling animals in
 * one corridor is not a thing that can be allowed to happen.</p>
 */
public final class WeaselIncursion {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    // --- how rare ---------------------------------------------------------------

    /**
     * How long between two considerations, in ticks: thirty to forty-five real
     * minutes of somebody actually being below.
     *
     * <p>Rarer than anything else in the dimension by an order of magnitude, and
     * deliberately measured in time spent down there rather than in world time -
     * the clock does not turn in an empty burrow, so a player who visits for ten
     * minutes an evening meets one every few evenings rather than never.</p>
     *
     * <p>A consideration is not an incursion. It still has to find a run with a
     * chamber near enough, in a colony with no giant mole in it, so the interval
     * between two that actually happen is longer than this.</p>
     */
    private static final int CONSIDER_DELAY = 20 * 60 * 30;
    private static final int CONSIDER_SPREAD = 20 * 60 * 15;

    /**
     * Two to three minutes instead of thirty to forty-five, under
     * {@link BurrowTraversal#FAST_EVENTS_PROPERTY}.
     *
     * <p>Still the rarest thing in the burrow even at this rate, and that is on
     * purpose: an incursion every twenty seconds would be a different mechanic
     * being tested, not this one at speed. Two minutes is about as often as it can
     * happen and still have its announcement land as a warning rather than as a
     * metronome.</p>
     */
    private static final int FAST_CONSIDER_DELAY = 20 * 60 * 2;
    private static final int FAST_CONSIDER_SPREAD = 20 * 60;

    /** Same reach {@code BurrowTraversal} uses to collect a colony's runs. */
    private static final int LINK_SEARCH_RADIUS = 96;

    /** How near the entry chamber has to be, so the announcement and the arrival belong together. */
    private static final double ENTRY_RANGE = 96.0;

    /** How much run has to lie beyond the player, or it arrives and immediately leaves. */
    private static final double MIN_RUN_AHEAD = 32.0;

    /** How far the player may be off the run and still count as being on it. */
    private static final double ON_RUN_RANGE = 48.0;

    // --- the announcement -------------------------------------------------------

    /**
     * How long the warning lasts before anything comes down the run: ten to
     * fifteen seconds.
     *
     * <p>The whole design of this event is in this number. Long enough to find a
     * bolt-hole, draw a sword or run; short enough that the decision is made
     * badly. Nothing about the incursion is a surprise, which is what separates it
     * from a mob that walked round a corner.</p>
     */
    private static final int ANNOUNCE_TICKS = 200;
    private static final int ANNOUNCE_SPREAD = 100;

    /** Ticks between hisses, and how far the sound carries at the start and the end. */
    private static final int HISS_INTERVAL = 30;
    private static final float HISS_FROM = 0.6F;
    private static final float HISS_TO = 2.4F;

    /**
     * Ticks between panic impulses, and how far from the entry the critters feel
     * it.
     *
     * <p>Pulsed rather than set once, and that is not a nicety. A critter's own
     * stroll goal will take the navigation back the moment it wants to, so a
     * single {@code moveTo} is a flee that lasts until the next idle roll.
     * Re-issuing it every second for the length of the warning is what makes it
     * look like a stream of animals coming past rather than one that twitched.</p>
     */
    private static final int PANIC_INTERVAL = 20;
    private static final double PANIC_RANGE = 40.0;
    private static final double PANIC_SPEED = 1.6;

    // --- the hunt ---------------------------------------------------------------

    /**
     * Blocks a tick while hunting.
     *
     * <p>Between the giant mole's two: faster than the scheduled mole, which is
     * ambling, and slower than a real trip, which is an errand. A weasel going
     * somewhere on purpose.</p>
     */
    private static final double HUNT_SPEED = 0.45;

    /** How far ahead of its own body the bite reaches. */
    private static final double LUNGE_REACH = 2.0;

    /**
     * How far across the run it will swerve at something, in blocks.
     *
     * <p>Capped well under what the width clamp would allow, and this is the one
     * place the incursion deliberately gives up reach. At a larder alcove the
     * clamp measures the alcove as open corridor and would let the animal lean
     * three or four blocks into its mouth; a player who ducked into one would have
     * gone somewhere and been followed anyway. One block is enough to read as a
     * swerve and not enough to leave the run.</p>
     */
    private static final double LUNGE_LEAN = 1.0;

    /** How long the slide out of the run takes at either end. */
    private static final int TRANSITION_TICKS = 24;

    /** How far a player may get before the hunt is called off. */
    private static final double KEEP_RANGE = 96.0;

    /**
     * The weasel's own way down a run.
     *
     * <p>Almost no weave: at 4.95 blocks across in a five-wide run the clamp
     * resolves to nothing anyway, and what little a backbone run would give it is
     * spent on the swerve instead. The transitions are quick and steep - it comes
     * out of the chamber floor fast, which is the difference between an arrival
     * and an entrance.</p>
     */
    private static final TunnelWalk.Style STYLE = new TunnelWalk.Style(
            0.8,     // drift amplitude, in blocks it would like
            4.0,     // swells of that drift over a whole run
            0.2,     // daylight left between body and wall
            0.25F,   // how fast the drift follows its clamp: quick, for the swerve
            5.0,     // how far a transition carries it along the run
            4.5,     // how far under the floor a transition starts and ends
            -55.0F,  // nose up as it comes out
            50.0F,   // nose down as it goes in
            0.4F);   // how fast the heading follows the actual movement

    /** Where an incursion is in its own short life. */
    private enum Phase {
        /** Nothing is coming yet, but something is making a noise about it. */
        ANNOUNCING,
        /** On the run, biting whatever is in it. */
        HUNTING,
        /** Sinking back into the floor, after which it is gone. */
        LEAVING
    }

    /**
     * The one incursion, or null.
     *
     * <p>Static and singular, like {@code BurrowReconciler}'s queue and for the
     * same reason: there is nothing that both dimensions can see to hang it off,
     * and it is only ever touched from the burrow's own level tick.</p>
     */
    private static @Nullable Incursion active;

    /** Game time the next consideration is due, or unset. */
    private static long nextConsideration = Long.MIN_VALUE;

    private WeaselIncursion() {
    }

    // --- the tick ---------------------------------------------------------------

    /** One burrow tick: run the incursion if there is one, otherwise wonder about starting one. */
    public static void tick(ServerLevel burrow) {
        if (active != null) {
            advance(burrow);
            return;
        }
        consider(burrow);
    }

    /**
     * Drops the incursion. For a world being unloaded, on the same reasoning as
     * {@code BurrowTraversal.forget}.
     */
    public static void forget() {
        active = null;
        nextConsideration = Long.MIN_VALUE;
    }

    /** Whether a weasel is on its way or already in the runs. */
    public static boolean isRunning() {
        return active != null;
    }

    /**
     * Whether this colony has one, announced or arrived.
     *
     * <p>Read by {@code BurrowTraversal}'s spawn gate, which is the other half of
     * the same rule its own lanes obey: two corridor-filling animals must not end
     * up in one corridor. Unlike a traversal's own arming, an announced incursion
     * <em>does</em> hold the colony before anything exists - the announcement is
     * the event, a player has already heard it, and a giant mole strolling through
     * the middle of it would make nonsense of both.</p>
     */
    public static boolean isRunningIn(int colony) {
        return active != null && active.colony == colony;
    }

    // --- deciding ---------------------------------------------------------------

    private static void consider(ServerLevel burrow) {
        if (burrow.players().isEmpty()) {
            // The clock does not turn in an empty dimension: this is measured in
            // time spent below, not in time passed.
            nextConsideration = Long.MIN_VALUE;
            return;
        }

        long now = burrow.getGameTime();
        if (nextConsideration == Long.MIN_VALUE) {
            nextConsideration = now + delay(burrow);
            return;
        }
        if (now < nextConsideration) {
            return;
        }
        nextConsideration = now + delay(burrow);

        for (ServerPlayer player : burrow.players()) {
            if (!player.isSpectator() && arm(burrow, player)) {
                return;
            }
        }
    }

    private static int delay(ServerLevel burrow) {
        if (BurrowTraversal.FAST_EVENTS) {
            BurrowTraversal.announceFastEvents();
            return FAST_CONSIDER_DELAY + burrow.getRandom().nextInt(FAST_CONSIDER_SPREAD);
        }
        return CONSIDER_DELAY + burrow.getRandom().nextInt(CONSIDER_SPREAD);
    }

    /**
     * Looks for a run this player is on whose chamber is near enough to come in
     * through.
     *
     * <p>The entry is a <em>chamber</em> and not a point on the run, because that
     * is how a real weasel gets into a mole's tunnels - down a molehill. The end
     * of the link is where the chamber is, so the run is walked from whichever end
     * is nearer the player, and the player has to be far enough along it that the
     * animal has somewhere to come from.</p>
     */
    private static boolean arm(ServerLevel burrow, ServerPlayer player) {
        ServerLevel overworld = burrow.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        BlockPos above = BurrowGeometry.toOverworld(player.blockPosition());
        List<BurrowLink> nearby = ColonyStore.get(overworld).linksNear(above, LINK_SEARCH_RADIUS);
        Vec3 at = player.position();

        for (BurrowLink link : nearby) {
            if (link.pointCount() < 2 || BurrowTraversal.hasTraversal(link.colony())) {
                continue;
            }

            TunnelWalk.Path forward = TunnelWalk.Path.ofLink(link, link.a());
            double here = forward.nearestFraction(at);
            if (forward.at(here).distanceToSqr(at) > ON_RUN_RANGE * ON_RUN_RANGE) {
                continue;
            }

            // In from whichever chamber is nearer, so the warning and the animal
            // arrive together rather than half a minute apart.
            boolean fromA = forward.at(0.0).distanceToSqr(at) <= forward.at(1.0).distanceToSqr(at);
            TunnelWalk.Path path = fromA ? forward : forward.reversed();
            double meets = fromA ? here : 1.0 - here;
            double length = path.length();

            if (meets * length > ENTRY_RANGE || (1.0 - meets) * length < MIN_RUN_AHEAD) {
                continue;
            }

            active = new Incursion(path, link.colony(),
                    ANNOUNCE_TICKS + burrow.getRandom().nextInt(ANNOUNCE_SPREAD));
            say("incursion announced: [colony #{}] entering at {}, {} blocks of run, player {} blocks along",
                    link.colony(), where(path.at(0.0)), Math.round(length),
                    Math.round(meets * length));
            return true;
        }

        say("no incursion: no run near {} with a chamber inside {} blocks and free of a giant mole",
                where(at), Math.round(ENTRY_RANGE));
        return false;
    }

    // --- running it -------------------------------------------------------------

    private static void advance(ServerLevel burrow) {
        Incursion run = active;
        if (run == null) {
            return;
        }
        run.phaseTicks++;

        Weasel weasel = run.weasel;
        if (weasel != null && (weasel.isRemoved() || weasel.isDeadOrDying())) {
            // Killed, or gone with its chunk. Either way the event is over and the
            // advancement has already fired off the kill itself.
            say("incursion ended: [colony #{}] the weasel is gone at {}%",
                    run.colony, Math.round(run.fraction * 100.0));
            active = null;
            return;
        }

        switch (run.phase) {
            case ANNOUNCING -> announce(burrow, run);
            case HUNTING -> hunt(burrow, run);
            case LEAVING -> leave(burrow, run);
        }
    }

    /** The warning: something making a noise in the chamber, and everything small leaving. */
    private static void announce(ServerLevel burrow, Incursion run) {
        Vec3 entry = run.path.at(0.0);

        if (run.phaseTicks % HISS_INTERVAL == 1) {
            // Growing, so the thing a player notices is not the sound but that it
            // is getting louder.
            float through = (float) run.phaseTicks / run.announceFor;
            burrow.playSound(null, entry.x, entry.y, entry.z, ModSounds.WEASEL_HISS.get(),
                    SoundSource.HOSTILE, Mth.lerp(through, HISS_FROM, HISS_TO), 0.9F);
        }

        if (run.phaseTicks % PANIC_INTERVAL == 1) {
            scatterCritters(burrow, entry);
        }

        if (run.phaseTicks < run.announceFor) {
            return;
        }

        Weasel weasel = ModEntities.WEASEL.get().create(burrow, EntitySpawnReason.EVENT);
        if (weasel == null) {
            say("incursion ended: [colony #{}] the weasel could not be created", run.colony);
            active = null;
            return;
        }

        weasel.beginIncursion();
        run.walk = TunnelWalk.along(run.path, STYLE, burrow.getRandom());
        run.walk.placeAtStart(weasel, burrow);
        if (!burrow.addFreshEntity(weasel)) {
            say("incursion ended: [colony #{}] the weasel could not be added to the burrow", run.colony);
            active = null;
            return;
        }

        run.weasel = weasel;
        run.phase = Phase.HUNTING;
        run.phaseTicks = 0;
        say("incursion entering: [colony #{}] weasel #{} out of the chamber at {}",
                run.colony, weasel.getId(), where(entry));
    }

    /**
     * Sends every small thing near the entry past the player.
     *
     * <p>Their own navigation, aimed away from the chamber, and no new goal on the
     * critters to make it work - which is the whole reason it is done this way.
     * The direction is <em>away from the entry</em> rather than towards the
     * player, because the two are the same thing whenever the player is down the
     * run and the first is true even when they are not.</p>
     */
    private static void scatterCritters(ServerLevel burrow, Vec3 entry) {
        AABB around = AABB.ofSize(entry, PANIC_RANGE * 2.0, 16.0, PANIC_RANGE * 2.0);
        // Shrews as well as the small life, and that is the {@code Weasel}
        // javadoc's own line made true: a player who sees the shrews leave has
        // been warned by the ecosystem rather than by a health bar.
        for (PathfinderMob critter : burrow.getEntitiesOfClass(PathfinderMob.class, around,
                mob -> mob instanceof BurrowCritter || mob instanceof Shrew)) {
            Vec3 away = critter.position().subtract(entry).horizontal();
            if (away.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 to = critter.position().add(away.normalize().scale(PANIC_RANGE * 0.5));
            critter.getNavigation().moveTo(to.x, critter.getY(), to.z, PANIC_SPEED);
        }
    }

    /** Down the run, biting whatever is in it. */
    private static void hunt(ServerLevel burrow, Incursion run) {
        Weasel weasel = run.weasel;
        TunnelWalk walk = run.walk;
        if (weasel == null || walk == null) {
            active = null;
            return;
        }

        double length = walk.length();
        walk.advanceBy(length <= 1.0E-6 ? 1.0 : HUNT_SPEED / length);
        run.fraction = walk.progress();

        LivingEntity prey = biteAt(burrow, weasel);
        if (prey != null) {
            // A swerve rather than a chase: the run is still the run, and the
            // lean is bounded so a target that has left it is a target that got
            // away. Aimed at the part of their offset that is across the corridor.
            Vec3 look = Vec3.directionFromRotation(0.0F, weasel.getYRot());
            Vec3 offset = prey.position().subtract(weasel.position()).horizontal();
            double lateral = offset.x * -look.z + offset.z * look.x;
            walk.leanTo(Mth.clamp(lateral, -LUNGE_LEAN, LUNGE_LEAN));
        } else {
            walk.weaveFreely();
        }

        walk.place(weasel, burrow, TunnelWalk.Stage.TRAVELLING, 1.0F);

        if (prey != null) {
            // Vanilla's invulnerability window is the attack cooldown: a hit that
            // did not land returns false and takes the sound with it.
            weasel.doHurtTarget(burrow, prey);
        }

        if (walk.finished()) {
            say("incursion leaving: [colony #{}] the far chamber, run walked out", run.colony);
            run.phase = Phase.LEAVING;
            run.phaseTicks = 0;
        } else if (noPlayerNear(burrow, weasel)) {
            say("incursion leaving: [colony #{}] no player within {} blocks at {}%",
                    run.colony, Math.round(KEEP_RANGE), Math.round(run.fraction * 100.0));
            run.phase = Phase.LEAVING;
            run.phaseTicks = 0;
        }
    }

    /**
     * The nearest thing in the run ahead of it that is worth biting.
     *
     * <p>The box is the animal's own, grown forward and never sideways - see the
     * bolt-hole note on the class. Players and the burrow's small life alike: a
     * weasel that walked past a shrew to reach a player would say the dimension
     * exists for the player's benefit, which is the same argument its own target
     * selector already makes above ground.</p>
     */
    private static @Nullable LivingEntity biteAt(ServerLevel burrow, Weasel weasel) {
        Vec3 look = Vec3.directionFromRotation(0.0F, weasel.getYRot());
        AABB reach = weasel.getBoundingBox().expandTowards(look.scale(LUNGE_REACH));

        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity prey : burrow.getEntitiesOfClass(LivingEntity.class, reach,
                entity -> entity != weasel && entity.isAlive() && isPrey(entity))) {
            double distance = prey.distanceToSqr(weasel);
            if (distance < nearest) {
                nearest = distance;
                best = prey;
            }
        }
        return best;
    }

    private static boolean isPrey(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        // Everything small that lives down here, and nothing that is itself an
        // event: a weasel biting a giant mole would be two apparitions arguing.
        return entity instanceof BurrowCritter || entity instanceof Shrew;
    }

    /** Into the floor, and gone. */
    private static void leave(ServerLevel burrow, Incursion run) {
        Weasel weasel = run.weasel;
        TunnelWalk walk = run.walk;
        if (weasel == null || walk == null) {
            active = null;
            return;
        }

        walk.weaveFreely();
        walk.place(weasel, burrow, TunnelWalk.Stage.LEAVING,
                TunnelWalk.eased(run.phaseTicks, TRANSITION_TICKS));

        if (run.phaseTicks >= TRANSITION_TICKS) {
            // slipAway leaves the puff of soil the ordinary withdrawal does, which
            // is the same statement: it went into the wall.
            weasel.endIncursion();
            weasel.slipAway();
            say("incursion ended: [colony #{}] the weasel went into the earth", run.colony);
            active = null;
        }
    }

    private static boolean noPlayerNear(ServerLevel burrow, Weasel weasel) {
        for (ServerPlayer player : burrow.players()) {
            if (!player.isSpectator()
                    && player.position().distanceToSqr(weasel.position()) <= KEEP_RANGE * KEEP_RANGE) {
                return false;
            }
        }
        return true;
    }

    // --- the pieces -------------------------------------------------------------

    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
    }

    private static String where(Vec3 at) {
        return Mth.floor(at.x) + "," + Mth.floor(at.y) + "," + Mth.floor(at.z);
    }

    /** One event: where it runs, how far it has got, and what is running it. */
    private static final class Incursion {

        private final TunnelWalk.Path path;
        private final int colony;
        private final int announceFor;

        private Phase phase = Phase.ANNOUNCING;
        private int phaseTicks;
        private double fraction;

        private @Nullable TunnelWalk walk;
        private @Nullable Weasel weasel;

        private Incursion(TunnelWalk.Path path, int colony, int announceFor) {
            this.path = path;
            this.colony = colony;
            this.announceFor = announceFor;
        }
    }
}

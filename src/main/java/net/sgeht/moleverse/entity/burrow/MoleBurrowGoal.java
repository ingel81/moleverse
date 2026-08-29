package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.block.MoundAttachment;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.registry.ModSounds;
import net.sgeht.moleverse.tag.ModTags;

/**
 * The burrowing state machine.
 *
 * <p>It is a {@link Goal} rather than a free-standing controller for one reason:
 * a goal can claim {@code MOVE} and {@code LOOK} and thereby shut the strolling
 * and looking goals up for the duration. A controller running beside them would
 * spend the whole trip fighting a {@code WaterAvoidingRandomStrollGoal} that
 * keeps re-issuing a path. It sits at priority 0 and declares itself
 * uninterruptable, so once a mole is in the ground nothing takes him out of it
 * but this class.</p>
 *
 * <p>Where the mounds are and how the path between them is checked are two other
 * concerns and live in {@link MoundNetwork} and {@link BurrowRoute}. What is left
 * here is the sequence: when to want it, what to refuse, and what to do at each
 * step.</p>
 */
public class MoleBurrowGoal extends Goal {

    private final Mole mole;
    private final RandomSource random;

    /** No trip is worked out before this tick. Carries the cooldown; fright ignores it. */
    private int nextAttemptTick;

    /**
     * The short delay after a refusal, which fright does <em>not</em> ignore.
     *
     * <p>Kept apart from the cooldown because the two exist for opposite
     * reasons. Skipping the cooldown is the point of fright - a mole that cannot
     * escape because it dug five seconds ago looks broken. Skipping the refusal
     * delay only means recomputing an answer that has not changed: a player
     * standing next to a mole that cannot dig would make it search its whole
     * network twice a second, for as long as they stand there.</p>
     */
    private int nextRetryTick;

    /**
     * No <em>new</em> hole is dug before this tick.
     *
     * <p>Separate from the trip timer on purpose. Running the network is what a
     * mole does all day and is never rationed; breaking fresh ground changes the
     * world and is. Sharing one timer between them meant a mole that had just
     * extended its network then sat on the new mound for a minute instead of
     * using the tunnels it had dug.</p>
     */
    private int nextNewHoleTick;

    /**
     * Tick the mole last came up, and what the boredom timer counts from.
     *
     * <p>Deliberately not "last tick it stood still". A wandering goal keeps a
     * mole moving almost continuously, so a timer that waits for stillness
     * hardly ever fires - which is why an established mole spent its life on the
     * surface instead of in its own network. How long it has been up is the
     * thing actually being asked about.</p>
     */
    private int surfacedTick;

    /** The colony this trip belongs to. Resolved when the entry is known. */
    private Colony colony;

    /**
     * How deep this trip runs. Taken from the link when the pair has been
     * travelled before, rolled when it has not.
     */
    private RunLevel run = RunLevel.FEEDING;

    /**
     * Whether the trip reached its exit rather than ending early.
     *
     * <p>Only a clean arrival is written down. A run that stopped in open air,
     * in water, at the edge of the ticking area, or was sent back to its entry by
     * the roof guard has geometry that describes no tunnel, and storing it would
     * put a corridor in the burrow below where no mole ever went.</p>
     */
    private boolean arrivedCleanly;

    /**
     * Set when the colony is full and there was nowhere left to dig, read by
     * {@link MoleEmigrateGoal}.
     *
     * <p>Deliberately not cleared by {@link #stop()}: the wish comes out of a
     * refusal, which never starts the goal in the first place, and it has to
     * outlive the attempt that produced it.</p>
     */
    private boolean leaveWish;

    /**
     * When the mole first found itself on ground it cannot dig, or -1 while it
     * stands on soil. Only the span matters: a mole that walks off a path block
     * clears this long before the rescue in {@link #canUse()} looks at it.
     */
    private int strandedSinceTick = -1;

    /**
     * How long this particular stay above ground lasts, drawn fresh each time it
     * surfaces. Keeps a mole from popping up on a metronome.
     */
    private int dwellTicks = BurrowConstants.SURFACE_DWELL_MIN;

    /** Start of the current state, for the two animation lengths and the approach timeout. */
    private int stateEnteredTick;

    private int travelStartTick;

    private @Nullable BlockPos entry;
    private boolean entryIsNew;
    private @Nullable BlockPos exit;
    private boolean exitIsNew;

    /** Where he actually came up. Equals {@link #exit} unless the route was cut short. */
    private @Nullable BlockPos emergeAt;

    /** The mound whose shaft stands open, remembered so it is closed again exactly once. */
    private @Nullable BlockPos openedMound;

    private @Nullable BurrowRoute route;
    private boolean fleeing;

    /** Whether he actually went under. A trip that never started earns no 90 second cooldown. */
    private boolean wentUnder;

    /**
     * Whether this trip actually put a mound into the world.
     *
     * <p>That, and not the plan, is what the long cooldown rations. A trip
     * between two existing mounds still digs a fresh one when the route is cut
     * short and it surfaces somewhere new - and a trip that planned a fresh dig
     * may end up placing nothing at all.</p>
     */
    private boolean placedMound;

    /**
     * A trip demanded by {@code /moleverse mole burrow}, and the callback that
     * tells whoever typed it what came of it.
     *
     * <p>It is a callback rather than a return value because the decision is not
     * made when the command runs: {@link #canUse()} is asked by the goal selector
     * on its own schedule, roughly every second server tick.</p>
     */
    private @Nullable Consumer<String> forcedBy;

    /**
     * Whether the last decision was a refusal.
     *
     * <p>Read by the strolling goal. A mole that cannot dig where it stands has
     * to be free to walk off and try elsewhere - that is what spreads a territory
     * out rather than stacking it, and without it the mole would stand on a
     * mound forever, since strolling is otherwise switched off near one.</p>
     */
    private boolean refusing;

    public MoleBurrowGoal(Mole mole) {
        this.mole = mole;
        this.random = mole.getRandom();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // The route is advanced by a fraction of a block per tick and checked
        // before every step. Half of that at half the rate is not the same thing.
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    /**
     * DEBUG. Makes the next decision skip the cooldown and the boredom timer.
     *
     * <p>The guards and the search are deliberately left in place. They are what
     * a refusal is made of, and handing that reason back is half the point of the
     * command - a mole that will not dig is the failure mode worth looking at.</p>
     *
     * @param report called once, on the server thread, with what happened
     */
    public void forceBurrow(Consumer<String> report) {
        this.forcedBy = report;
    }

    /** True while the mole has been turned down and should look elsewhere. */
    public boolean isRefusing() {
        return this.refusing;
    }

    @Override
    public boolean canUse() {
        if (this.mole.getBurrowState().isBusy()) {
            return false;
        }
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return false;
        }

        int now = this.mole.tickCount;

        boolean forced = this.forcedBy != null;

        LivingEntity hurtBy = this.mole.getLastHurtByMob();
        boolean struck = hurtBy != null
                && now - this.mole.getLastHurtByMobTimestamp() <= BurrowConstants.FLEE_MEMORY;

        // Two lookups, because they ask different questions. Fright ignores
        // creative players; an offered worm does not, or someone in creative
        // could call a mole over and never be allowed to calm it - which is
        // exactly how this gets tested. The food radius is the wider one, so
        // the mole is not scared away halfway through walking to the worm.
        Player scary = level.getNearestPlayer(
                this.mole.getX(), this.mole.getY(), this.mole.getZ(),
                BurrowConstants.PLAYER_SCARE_DISTANCE, true);
        // Picked by what they are holding, not by who is closest: an empty
        // handed player standing nearer would otherwise mask the one with the
        // worm, and the mole would dig away from an offer it could see.
        Player offering = this.nearestPlayerOffering(level);

        // A mole called over by a worm, or one that has just been fed, is not
        // frightened and not bored either - it is waiting. Digging out from
        // under the player's hand is the same failure as diving from the worm.
        boolean settled = this.mole.isInLove()
                || (offering != null && offering.isHolding(this.mole::isFood));

        Player tooClose = settled ? null : this.withinScareRange(scary);
        LivingEntity threat = struck ? hurtBy : tooClose;
        boolean fleeingNow = threat != null;

        // Fright ignores the cooldown. A mole that has just been hit, or that a
        // player is walking up to, must go now - waiting out a timer is the one
        // thing it cannot do, and being unable to escape because it dug five
        // seconds ago is exactly the moment the mechanic looks broken.
        int waitUntil = fleeingNow ? this.nextRetryTick : this.nextAttemptTick;
        if (!forced && now < waitUntil) {
            return false;
        }

        boolean bored = !settled && now - this.surfacedTick >= this.dwellTicks;
        if (!forced && !fleeingNow && !bored) {
            return false;
        }

        BlockPos origin = this.mole.blockPosition();
        BlockState ground = level.getBlockState(origin.below());
        boolean diggable = ground.is(ModTags.Blocks.MOLE_DIGGABLE);

        // Before the guards, because the guard for this case only asks the mole
        // to walk on, and the whole point of the rescue is the spot where
        // walking on leads nowhere.
        if (diggable) {
            this.strandedSinceTick = -1;
        } else if (this.strandedSinceTick < 0) {
            this.strandedSinceTick = now;
        } else if (now - this.strandedSinceTick >= BurrowConstants.STRANDED_RESCUE_DELAY
                && this.rescueFromStranding(level, origin)) {
            this.delayNextAttempt();
            return false;
        }

        // Guards before the log line, not after: a baby or a leashed mole fails
        // them on every attempt for as long as it lives, and logging the wish
        // first would bury every real refusal under that noise.
        if (!this.passesGuards(diggable)) {
            this.delayNextAttempt();
            return false;
        }

        String why = forced ? "commanded"
                : struck ? "flee"
                : tooClose != null ? "player too close"
                : "bored";
        BurrowLog.wanted(this.mole, why, ground, diggable);

        if (!this.planTrip(level, origin, fleeingNow ? threat.position() : null)) {
            this.delayNextAttempt();
            return false;
        }

        this.refusing = false;
        // A trip was found after all, so there is nothing to leave for.
        this.leaveWish = false;
        this.fleeing = fleeingNow;
        this.report("digging in, entry " + (this.entryIsNew ? "fresh" : "reused")
                + ", exit " + (this.exitIsNew ? "fresh" : "reused")
                + ", " + Math.round(this.route.length()) + " blocks to travel");
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // The machine ends itself by going back to WANDERING; stop() then cleans up.
        return this.mole.getBurrowState().isBusy();
    }

    @Override
    public void start() {
        this.mole.getNavigation().stop();

        boolean atTheEntry = this.entryIsNew
                || this.mole.position().distanceToSqr(this.entry.getCenter())
                        <= BurrowConstants.ENTRY_REACH_DISTANCE * BurrowConstants.ENTRY_REACH_DISTANCE;

        if (atTheEntry) {
            this.beginBurrowing("digging in where he stands");
            return;
        }

        this.mole.setBurrowState(BurrowState.APPROACHING,
                this.fleeing ? "fleeing to a known mound" : "walking to a known mound");
        this.stateEnteredTick = this.mole.tickCount;
        this.mole.getNavigation().moveTo(
                this.entry.getX() + 0.5, this.entry.getY(), this.entry.getZ() + 0.5,
                this.fleeing ? BurrowConstants.FLEE_APPROACH_SPEED : BurrowConstants.APPROACH_SPEED);
    }

    @Override
    public void tick() {
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return;
        }
        switch (this.mole.getBurrowState()) {
            case APPROACHING -> this.tickApproaching(level);
            case BURROWING -> this.tickBurrowing(level);
            case UNDERGROUND -> this.tickUnderground(level);
            case EMERGING -> this.tickEmerging(level);
            case WANDERING -> {
                // Finished this tick; canContinueToUse ends the goal next round.
            }
        }
    }

    /**
     * The single place a mole is put back together, so a forced end leaves the
     * same state behind as a clean one.
     */
    @Override
    public void stop() {
        BurrowState state = this.mole.getBurrowState();

        if (this.mole.level() instanceof ServerLevel level) {
            if (state.isBelowGround()) {
                // Only reachable when something removed the goal mid-trip. He is
                // still inside solid ground at this point, so he goes up first.
                BurrowLog.recovered(this.mole, "trip stopped while underground");
                this.mole.pushToSurface(level);
            }
            this.closeOpenedMound(level);
        }

        this.mole.endUnderground();
        if (state.isBusy()) {
            this.mole.setBurrowState(BurrowState.WANDERING, "goal stopped");
        }

        this.dwellTicks = Mth.nextInt(this.random,
                BurrowConstants.SURFACE_DWELL_MIN, BurrowConstants.SURFACE_DWELL_MAX);

        int cooldown = this.wentUnder ? this.dwellTicks : BurrowConstants.REFUSAL_RETRY_DELAY;
        if (this.placedMound) {
            this.nextNewHoleTick = this.mole.tickCount + BurrowConstants.NEW_HOLE_COOLDOWN;
        }

        this.entry = null;
        this.exit = null;
        this.colony = null;
        this.run = RunLevel.FEEDING;
        this.arrivedCleanly = false;
        this.emergeAt = null;
        this.openedMound = null;
        this.route = null;
        this.fleeing = false;
        this.entryIsNew = false;
        this.exitIsNew = false;
        this.placedMound = false;
        // Cleared here as well as on a successful plan. Left standing, it keeps
        // the strolling goal switched on for the whole of the next trip and the
        // stay above ground after it - which is the mole wandering the meadow
        // again, the very thing the goal was written to stop.
        this.refusing = false;
        this.surfacedTick = this.mole.tickCount;
        this.nextAttemptTick = this.mole.tickCount + cooldown;
        // Only a trip that really happened clears the retry throttle. An
        // attempt that started and then failed - a mound it could not walk to,
        // a site that stopped taking one - would otherwise loop at full speed
        // for as long as a player stands nearby.
        // Even an escape earns the shortest pause. Without it a player standing
        // between two mounds makes the mole surface and dive on the same tick,
        // over and over - the escape is what suppresses the randomised stay, so
        // it has to bring its own floor.
        this.nextRetryTick = this.mole.tickCount
                + (this.wentUnder ? BurrowConstants.SURFACE_DWELL_MIN : BurrowConstants.REFUSAL_RETRY_DELAY);
        this.wentUnder = false;
    }

    // --- deciding -------------------------------------------------------------

    /**
     * The cheap conditions, each closing a case that is otherwise undefined.
     * Every {@code Mob} is {@code Leashable} in this version, so a leashed mole
     * burrowing sixty-four blocks is reachable by accident rather than by design.
     */
    private boolean passesGuards(boolean diggable) {
        String refusal = null;
        boolean elsewhereWouldHelp = false;
        if (this.mole.isBaby()) {
            refusal = "baby - a juvenile only follows its mother down";
        } else if (this.mole.isLeashed()) {
            refusal = "leashed";
        } else if (this.mole.isPassenger()) {
            refusal = "riding something";
        } else if (this.mole.isVehicle()) {
            refusal = "carrying a passenger";
        } else if (!this.mole.onGround()) {
            refusal = "not on the ground";
        } else if (this.mole.isInWater()) {
            refusal = "in water";
        } else if (!diggable) {
            refusal = "ground is not diggable";
            elsewhereWouldHelp = true;
        }

        if (refusal == null) {
            return true;
        }

        // Ground underfoot is about the place, not the mole: standing on a path
        // block or a stone platform, walking off is the answer. The rest -
        // juvenile, leashed, riding, carrying, in the air - travel with the mole
        // and would only make it pace.
        if (elsewhereWouldHelp) {
            this.refuseAndMoveOn(refusal);
        } else {
            this.refuse(refusal);
        }
        return false;
    }

    /**
     * Every refusal goes through here: into the log always, and back to a waiting
     * {@code /moleverse mole burrow} once.
     */
    private void refuse(String why) {
        this.refuseHere(why, false);
    }

    /**
     * A refusal that walking somewhere else would actually solve.
     *
     * <p>Only these nudge the mole on. The permanent ones - a juvenile, a leash,
     * water underfoot - repeat every three seconds for the rest of its life, and
     * pushing it into a fresh walk each time would leave it pacing without the
     * pauses that make an animal look like one.</p>
     */
    private void refuseAndMoveOn(String why) {
        this.refuseHere(why, true);
    }

    private void refuseHere(String why, boolean elsewhereWouldHelp) {
        this.refusing = true;
        if (elsewhereWouldHelp) {
            // Ask the stroll goal to set off now. On its own it decides to walk
            // on about one evaluation in a hundred and twenty, so the mole would
            // stand there re-planning the same impossible trip for a dozen
            // seconds before anything could change.
            this.mole.wanderNow();
        }
        BurrowLog.refused(this.mole, why);
        this.report("refused - " + why);
    }

    /** True while this mole has been told its colony is full and has nowhere to dig. */
    public boolean wantsToLeave() {
        return this.leaveWish;
    }

    public void clearLeaveWish() {
        this.leaveWish = false;
    }

    /** Answers a forced attempt at most once. Does nothing when nobody asked. */
    private void report(String what) {
        Consumer<String> waiting = this.forcedBy;
        if (waiting != null) {
            this.forcedBy = null;
            waiting.accept(what);
        }
    }

    private boolean planTrip(ServerLevel level, BlockPos origin, @Nullable Vec3 threat) {
        MoundNetwork.Scan scan = MoundNetwork.scan(level, origin);
        BurrowLog.scanFinished(this.mole, scan.mounds().size(), scan.densityCapReached());

        BlockPos nearest = scan.nearest();
        if (nearest != null) {
            // Reusing a mound is what keeps an established mole in its own network
            // instead of carving new holes everywhere, and it adds nothing to the
            // density count - which is why the cap is not asked here.
            this.entry = nearest;
            this.entryIsNew = false;
        } else if (!MoleMound.canPlaceAt(level, origin)) {
            this.refuseAndMoveOn("no room for a mound where he stands");
            return false;
        } else {
            this.entry = origin;
            this.entryIsNew = true;
        }

        // Which colony's ground this is, and if none, whether one may start
        // here. The entry decides it rather than where the mole stands: an
        // existing mound just outside a border belongs to whoever owns the
        // ground it sits on, not to the animal that walked up to it.
        ColonyStore colonies = ColonyStore.get(level);
        this.colony = colonies.at(this.entry);
        if (this.colony == null) {
            this.colony = colonies.found(this.entry, level.getGameTime());
            if (this.colony == null) {
                // The unclaimed band around an existing colony. Walking on is
                // the answer, and it is what pushes a new colony far enough away
                // to be one - but the band is eighty blocks wide, and a random
                // stroll crosses it by luck alone. Measured: one mole spent seven
                // and a half minutes in there, a hundred and fourteen refusals,
                // before it stumbled out. So this asks to emigrate, exactly as a
                // full colony does, and MoleEmigrateGoal walks it out on a
                // bearing instead.
                this.leaveWish = true;
                this.refuseAndMoveOn("too near another colony to start one here");
                return false;
            }
            BurrowLog.colonyFounded(this.mole, this.colony.id(), this.colony.core());
        }

        MoundNetwork.Members network = MoundNetwork.build(level, this.entry);
        BurrowLog.networkBuilt(this.mole, network.mounds().size(), network.chainDepth(), network.farthest());

        return this.chooseExitAndRoute(level, network, threat, scan.densityCapReached());
    }

    private boolean chooseExitAndRoute(ServerLevel level, MoundNetwork.Members network, @Nullable Vec3 threat,
            boolean crowded) {
        // Now and then, strike out for somewhere new even though the network has
        // an exit to offer. Preferring what exists is right most of the time -
        // it is what stops a meadow filling with holes - but always preferring
        // it means a territory freezes at two mounds and never grows again.
        //
        // Under threat that goes the other way round, and mostly new ground
        // wins: bolting to a hole the pursuer is standing beside is no escape,
        // and a mole chased between two known mounds is a mole that never got
        // away. Only the density cap still says no.
        float chance = threat != null
                ? BurrowConstants.FLEE_EXPLORE_CHANCE
                : BurrowConstants.EXPLORE_CHANCE;
        boolean mayDig = this.mole.tickCount >= this.nextNewHoleTick;
        boolean explore = mayDig && !crowded && this.random.nextFloat() < chance;

        BlockPos chosen = explore
                ? null
                : MoundNetwork.chooseExit(level, this.random, network, this.entry, this.colony, threat);
        if (chosen != null) {
            this.exit = chosen;
            this.exitIsNew = false;
        } else {
            // Also gated: this is the fallback when the network offers nothing,
            // and without the check it would dig the very hole the timer is
            // there to ration.
            this.exit = mayDig
                    ? MoundNetwork.findFreshSite(level, this.random, this.entry, this.colony, threat)
                    : null;

            // Exploring is a preference, not a demand. If no fresh site is free,
            // fall back to the network rather than refusing a trip that was
            // perfectly possible.
            if (this.exit == null && explore) {
                this.exit = MoundNetwork.chooseExit(level, this.random, network, this.entry, this.colony, threat);
                if (this.exit != null) {
                    this.exitIsNew = false;
                    this.route = this.routeTo(level, this.exit);
                    BurrowLog.targetChosen(this.mole, this.entry, this.entryIsNew, this.exit, false,
                            this.route.length(), this.route.waypointCount());
                    return true;
                }
            }

            if (this.exit == null) {
                // The wish to leave is guarded by the `exit == null` above, and
                // that makes it far narrower than "the colony is full". It means
                // full *and* no trip available at all - and a full colony is the
                // least likely place to run out of trips, because being full is
                // having thirty-two destinations.
                //
                // Measured: two moles, one colony, eight hours of game time.
                // The colony reached the cap inside the first hour and then made
                // 2578 trips without a single refusal, so this branch was never
                // entered and no mole ever wanted to leave. The full-colony half
                // of MoleEmigrateGoal has therefore never run; only the band half
                // has. Whether a full colony *should* push somebody out is an
                // open design question rather than a bug, and it is recorded as
                // one in docs/WORKLOG.md.
                boolean full = network.mounds().size() >= BurrowConstants.NETWORK_MAX_MEMBERS;
                if (full) {
                    this.leaveWish = true;
                }

                // The crowded case: mounds all around but every one of them too
                // close to be worth the trip, and no room for a fifth anywhere in
                // reach. He wanders off and tries again from somewhere else,
                // which is what spreads a territory out instead of stacking it.
                this.refuseAndMoveOn((full ? "colony is full: "
                        : crowded ? "density cap reached: "
                        : !mayDig ? "still resting from the last new hole: "
                        : "no valid exit: ")
                        + "no network member beyond " + BurrowConstants.MIN_EXIT_DISTANCE
                        + " blocks and no fresh site was available");
                return false;
            }
            this.exitIsNew = true;
        }

        this.route = this.routeTo(level, this.exit);
        BurrowLog.targetChosen(this.mole, this.entry, this.entryIsNew, this.exit, this.exitIsNew,
                this.route.length(), this.route.waypointCount());
        return true;
    }

    // --- the states -----------------------------------------------------------

    private void tickApproaching(ServerLevel level) {
        double reachSqr = BurrowConstants.ENTRY_REACH_DISTANCE * BurrowConstants.ENTRY_REACH_DISTANCE;
        if (this.mole.position().distanceToSqr(this.entry.getCenter()) <= reachSqr) {
            this.beginBurrowing("reached the entry mound");
            return;
        }

        int waited = this.mole.tickCount - this.stateEnteredTick;
        boolean timedOut = waited >= BurrowConstants.APPROACH_TIMEOUT;
        // The navigation needs a tick or two to produce a path before "done"
        // means anything; asking immediately reads as an exhausted path.
        boolean pathGone = waited > 4 && this.mole.getNavigation().isDone();
        if (!timedOut && !pathGone) {
            return;
        }

        // Logged, but deliberately not through refuse(): the mole is about to
        // dig, so marking it as turned down would send it wandering off after a
        // perfectly successful trip.
        BurrowLog.recovered(this.mole, timedOut
                ? "approach timed out - digging here instead"
                : "path to the entry mound exhausted - digging here instead");

        if (this.digHereInstead(level)) {
            this.beginBurrowing("gave up approaching");
        } else {
            this.abort("gave up approaching and there is nothing to dig here either");
        }
    }

    /**
     * Turns a failed approach into a dig on the spot. The exit has to be checked
     * again: the mole is now somewhere else, and an exit that was far enough from
     * the old entry can easily be next door to this one.
     */
    private boolean digHereInstead(ServerLevel level) {
        // Rationed like any other fresh hole. Without this an approach that
        // times out digs one in the middle of the cooldown, and a mole that
        // keeps failing to reach its mounds would dig its way across a meadow.
        if (this.mole.tickCount < this.nextNewHoleTick) {
            return false;
        }

        BlockPos here = this.mole.blockPosition();
        if (!MoundNetwork.hasRoomForMound(level, here) || !MoleMound.canPlaceAt(level, here)) {
            return false;
        }

        int minSqr = BurrowConstants.MIN_EXIT_DISTANCE * BurrowConstants.MIN_EXIT_DISTANCE;
        if (this.exit.distSqr(here) < minSqr) {
            return false;
        }

        this.entry = here;
        this.entryIsNew = true;
        this.route = this.routeTo(level, this.exit);
        return true;
    }

    private void tickBurrowing(ServerLevel level) {
        this.holdStill();
        if (this.mole.tickCount - this.stateEnteredTick < BurrowConstants.BURROW_TICKS) {
            return;
        }

        if (!this.openEntryMound(level)) {
            this.abort("the entry no longer takes a mound");
            return;
        }

        // One loud scoop where he goes in. The muffled ones follow him along the
        // surface; this is the last one heard from close up.
        level.playSound(null, this.entry.getX() + 0.5, this.entry.getY(), this.entry.getZ() + 0.5,
                ModSounds.MOLE_DIG.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);

        this.aimAlongRoute();
        this.mole.beginUnderground();
        this.mole.snapTo(this.route.position());
        this.travelStartTick = this.mole.tickCount;
        this.wentUnder = true;
        this.mole.setBurrowState(BurrowState.UNDERGROUND, "burrow animation finished");
    }

    private void tickUnderground(ServerLevel level) {
        BurrowRoute.Progress progress = this.route.advance(level);

        // Even after a failure this is the last valid point, which is where he
        // is supposed to end up.
        this.mole.snapTo(this.route.position());
        this.surfaceTrace(level);

        switch (progress) {
            case TRAVELLING -> {
            }
            case ARRIVED -> {
                this.arrivedCleanly = true;
                this.beginEmerging(level, "route finished");
            }
            case NOT_ENTITY_TICKING -> {
                BurrowLog.recovered(this.mole, "waypoint not entity-ticking");
                this.beginEmerging(level, "route left the ticking area");
            }
            case NOT_SOLID -> {
                BurrowLog.recovered(this.mole, "waypoint not solid");
                this.beginEmerging(level, "route ran into open air");
            }
            case LIQUID -> {
                BurrowLog.recovered(this.mole, "liquid on the route");
                this.beginEmerging(level, "route ran into liquid");
            }
        }
    }

    private void tickEmerging(ServerLevel level) {
        this.holdStill();
        if (this.mole.tickCount - this.stateEnteredTick < BurrowConstants.EMERGE_TICKS) {
            return;
        }

        this.placeExitMound(level);
        this.recordLink(level);
        // Whatever a player has fitted to this mound hears about the arrival.
        MoundAttachment.notifySurfaced(level, this.emergeAt, this.mole);
        level.playSound(null, this.mole.getX(), this.mole.getY(), this.mole.getZ(),
                ModSounds.MOLE_SURFACE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        this.mole.setBurrowState(BurrowState.WANDERING, "emerge animation finished");
    }

    // --- steps ----------------------------------------------------------------

    private void beginBurrowing(String reason) {
        this.mole.getNavigation().stop();
        this.mole.setBurrowState(BurrowState.BURROWING, reason);
        this.stateEnteredTick = this.mole.tickCount;
    }

    private void beginEmerging(ServerLevel level, String reason) {
        BurrowLog.travelFinished(this.mole, this.mole.tickCount - this.travelStartTick,
                this.route.travelled(), this.route.estimatedTicks());

        Vec3 at = this.route.position();
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        BlockPos surfaced = MoundNetwork.surfaceAt(level, x, z);

        // A fitting on a prepared mound is solid, so the heightmap - and with it
        // surfaceAt - lands above the fitting instead of on the mound. Resolve
        // back down to the mound before anything is judged, or the checks below
        // see a hollow block in the column and call the fitting a roof.
        BlockPos underFitting = MoundAttachment.moundUnder(level, surfaced);
        if (underFitting != null) {
            surfaced = underFitting;
        }

        // A hill the route climbed into and a roof both put the heightmap far
        // above the mole; only one of them has a room in between, and surfacing
        // through a room means standing on someone's house. Walking the column
        // is what tells them apart. Distance alone cannot: the route deliberately
        // climbs slower than rising ground does, so on any slope the gap grows
        // with the length of the trip and would condemn every uphill journey.
        //
        // The column on its own does not tell them apart either. A village wall
        // stands solid from the soil to the eaves, so a mole coming up inside
        // one passes this test and is set down on the ridge - where nothing is
        // diggable, every later trip is refused, and it paces the roof until
        // somebody pushes it off. What a wall does not have is soil under the
        // spot it leads to, so the ground underfoot is asked as well.
        String blocked = null;
        if (!isSolidColumn(level, x, z, Mth.floor(at.y) + 1, surfaced.getY())) {
            blocked = "ground above is built over";
        } else if (!isNaturalGround(level, surfaced)) {
            blocked = "the surface above is not soil";
        }
        if (blocked != null) {
            BurrowLog.recovered(this.mole, blocked + " - returning to the entry");
            surfaced = MoundNetwork.surfaceAt(level, this.entry.getX(), this.entry.getZ());
            // Sent home, so the route describes nothing that exists. Whatever the
            // travel said, this is not a run to write down.
            this.arrivedCleanly = false;
        }

        this.emergeAt = surfaced;

        // Out of the ground before physics are handed back, otherwise the first
        // tick above ground is spent being squeezed out of a block.
        this.mole.endUnderground();
        this.mole.snapTo(this.emergeAt.getBottomCenter());
        this.mole.setDeltaMovement(Vec3.ZERO);

        this.mole.setBurrowState(BurrowState.EMERGING, reason);
        this.stateEnteredTick = this.mole.tickCount;
    }

    /**
     * The nearest player actually holding something this mole eats.
     */
    private @Nullable Player nearestPlayerOffering(ServerLevel level) {
        double rangeSqr = BurrowConstants.FOOD_NOTICE_DISTANCE * BurrowConstants.FOOD_NOTICE_DISTANCE;
        Player best = null;
        double bestSqr = rangeSqr;

        for (Player player : level.players()) {
            if (player.isSpectator() || !player.isHolding(this.mole::isFood)) {
                continue;
            }
            double distSqr = this.mole.distanceToSqr(player);
            if (distSqr <= bestSqr) {
                best = player;
                bestSqr = distSqr;
            }
        }
        return best;
    }

    /**
     * Whether this player is near enough to send the mole under.
     *
     * <p>Sneaking buys a great deal of ground - it is the only way to watch a
     * mole rather than watch it leave.</p>
     */
    private @Nullable Player withinScareRange(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        double range = BurrowConstants.PLAYER_SCARE_DISTANCE;
        double scare = player.isCrouching() ? range * BurrowConstants.SNEAK_SCARE_FACTOR : range;
        return this.mole.distanceToSqr(player) <= scare * scare ? player : null;
    }

    /**
     * Whether everything between two heights in one column is solid ground.
     *
     * <p>About fifteen block reads, once per trip. It is the cavity that makes
     * surfacing wrong, not the height.</p>
     */
    /**
     * Whether a mole set down here would be standing on soil rather than on
     * something built. {@code MOLE_MOUND_PLACEABLE} is the list to ask: it
     * already answers "is this the top of the ground", and an emerge that lands
     * anywhere else leaves a mole that can never dig again from where it stands.
     */
    private static boolean isNaturalGround(ServerLevel level, BlockPos surfaced) {
        return level.getBlockState(surfaced.below()).is(ModTags.Blocks.MOLE_MOUND_PLACEABLE);
    }

    private static boolean isSolidColumn(ServerLevel level, int x, int z, int fromY, int toY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = fromY; y < toY; y++) {
            if (!level.getBlockState(cursor.set(x, y, z)).isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** Opens the shaft he goes down, digging the mound first when there is none yet. */
    private boolean openEntryMound(ServerLevel level) {
        if (!MoleMound.isMound(level, this.entry)) {
            // The entry was picked as an existing mound, which is why the plan
            // never asked whether another one would fit. Up to five seconds of
            // walking pass before this runs, and a player can knock that mound
            // away in the meantime - digging a replacement here would push the
            // area over the cap through the one path that never checked it.
            if (!MoundNetwork.hasRoomForMound(level, this.entry)) {
                return false;
            }

            BlockState support = level.getBlockState(this.entry.below());
            BlockState replaced = level.getBlockState(this.entry);
            if (!MoleMound.tryPlace(level, this.entry, true)) {
                return false;
            }
            BurrowLog.moundPlaced(this.mole, this.entry, support, replaced);
            this.placedMound = true;
        } else {
            MoleMound.setOpen(level, this.entry, true);
        }

        this.openedMound = this.entry;
        // The entity keeps its own copy because the goal is thrown away when the
        // chunk unloads, and a shaft left open is a change to the world that has
        // to be undone even if this trip never finishes.
        this.mole.setOpenShaft(this.entry);
        return true;
    }

    private void closeOpenedMound(ServerLevel level) {
        if (this.openedMound != null) {
            MoleMound.setOpen(level, this.openedMound, false);
        }
        // Also clears the entity's copy, which is what survives a save. Done
        // unconditionally: if the goal was rebuilt after a reload it holds no
        // position, but the mole may still be carrying one.
        this.mole.setOpenShaft(null);
    }

    /**
     * The second mound of the trip, at wherever he actually came up.
     *
     * <p>Placing one only holds where it is still legal - the support may have
     * been mined away and water may have flowed in while he was down there. A
     * mole that surfaces without a mound is a much better outcome than one that
     * refuses to surface.</p>
     */
    private void placeExitMound(ServerLevel level) {
        if (MoleMound.isMound(level, this.emergeAt)) {
            // The reused exit is still standing. Nothing to dig, only to shut.
            MoleMound.setOpen(level, this.emergeAt, false);
            return;
        }

        // The density cap has to hold here too, not only when a fresh site is
        // picked. A trip that ends early - a wall in the way, a chunk that
        // stopped ticking - surfaces the mole a stride from where it went in,
        // and without this check every one of those drops another mound beside
        // the entry until the whole area is capped and the mole refuses to dig
        // at all. Coming up without a mound is the better failure.
        if (!MoundNetwork.hasRoomForMound(level, this.emergeAt)) {
            BurrowLog.recovered(this.mole, "surfaced where the mounds are already too dense - none placed");
            return;
        }

        // Only when the trip was cut short. A mole that arrived where it meant
        // to has earned its mound even if rounding the site onto block
        // coordinates pulled it a few centimetres inside the minimum - checking
        // the successful case too would silently deny roughly one dig in thirty
        // its second mound, and the log would read like normal behaviour.
        int minSqr = BurrowConstants.MIN_EXIT_DISTANCE * BurrowConstants.MIN_EXIT_DISTANCE;
        if (!this.emergeAt.equals(this.exit) && this.emergeAt.distSqr(this.entry) < minSqr) {
            BurrowLog.recovered(this.mole, "surfaced too close to the entry - no second mound");
            return;
        }

        BlockState support = level.getBlockState(this.emergeAt.below());
        BlockState replaced = level.getBlockState(this.emergeAt);
        if (MoleMound.tryPlace(level, this.emergeAt, false)) {
            BurrowLog.moundPlaced(this.mole, this.emergeAt, support, replaced);
            this.placedMound = true;
        } else {
            BurrowLog.recovered(this.mole, this.emergeAt.equals(this.exit)
                    ? "target mound gone and the site no longer takes one - surfaced without a mound"
                    : "surfaced early where no mound fits");
        }
    }

    /** Ends the trip without a dig. The goal stops on the next tick and cleans up. */
    private void abort(String why) {
        this.refuseAndMoveOn(why);
        this.mole.setBurrowState(BurrowState.WANDERING, "aborted");
    }

    private void holdStill() {
        this.mole.getNavigation().stop();
        // Vertical movement is left alone so gravity still settles him on the ground.
        this.mole.setDeltaMovement(this.mole.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /**
     * Points the mole along the route before he goes in. The whole body turns,
     * which is why the dig animation needs no yaw of its own.
     */
    private void aimAlongRoute() {
        double dx = this.exit.getX() - this.entry.getX();
        double dz = this.exit.getZ() - this.entry.getZ();
        if (dx == 0.0 && dz == 0.0) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        this.mole.setYRot(yaw);
        this.mole.setYHeadRot(yaw);
        this.mole.yBodyRot = yaw;
    }

    /**
     * Dust and a muffled scoop on the ground above him. This is the only thing a
     * player sees of the trip, so it is emitted from the surface over his current
     * position rather than from the entity, which is two blocks down.
     */
    private void surfaceTrace(ServerLevel level) {
        boolean dust = this.mole.tickCount % BurrowConstants.DUST_INTERVAL == 0;
        boolean scoop = this.mole.tickCount % BurrowConstants.DIG_SOUND_INTERVAL == 0;
        if (!dust && !scoop) {
            return;
        }

        Vec3 at = this.route.position();
        BlockPos surface = MoundNetwork.surfaceAt(level, Mth.floor(at.x), Mth.floor(at.z));
        BlockPos ground = surface.below();

        if (dust) {
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground), ground),
                    surface.getX() + 0.5, surface.getY() + 0.1, surface.getZ() + 0.5,
                    3, 0.2, 0.0, 0.2, 0.02);
        }

        if (scoop) {
            level.playSound(null, surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                    ModSounds.MOLE_DIG.get(), SoundSource.NEUTRAL,
                    BurrowConstants.DIG_SOUND_VOLUME, BurrowConstants.DIG_SOUND_PITCH);
        }
    }

    /**
     * Carries a mole that has been standing on something it cannot dig back to
     * the nearest soil.
     *
     * <p>The refusal this follows already asks the mole to walk on, and that is
     * the right answer nearly every time. It is no answer at all where walking
     * cannot reach soil: on a roof, a platform, any ledge the mob pathfinder
     * refuses to drop off. Nothing changes there on its own, and the mole spends
     * the rest of the world's life refusing every three seconds where it
     * stands - a state with no visible cause, which is the failure mode this
     * whole mechanic is most prone to.</p>
     *
     * @return false when no soil is in reach, in which case nothing moved and
     *         the ordinary refusal follows
     */
    private boolean rescueFromStranding(ServerLevel level, BlockPos origin) {
        BlockPos ground = MoundNetwork.nearestDiggableSurface(
                level, origin, BurrowConstants.STRANDED_RESCUE_RADIUS);
        if (ground == null) {
            return false;
        }

        this.mole.getNavigation().stop();
        this.mole.putDownAt(ground);
        this.strandedSinceTick = -1;
        BurrowLog.recovered(this.mole, "stranded on ground he cannot dig - put back on soil");
        return true;
    }

    /**
     * Builds the route to the chosen exit and settles what depth it runs at.
     *
     * <p>A pair of mounds that has been travelled before keeps the level of that
     * run; only a pair with no link yet rolls. Letting an established run change
     * depth would leave the burrow below with two corridors where the colony has
     * one.</p>
     */
    private BurrowRoute routeTo(ServerLevel level, BlockPos exit) {
        RunLevel known = ColonyStore.get(level).levelBetween(this.entry, exit);
        this.run = known != null ? known
                : this.random.nextFloat() < BurrowConstants.MAIN_RUN_CHANCE ? RunLevel.MAIN
                : RunLevel.FEEDING;
        return BurrowRoute.between(level, this.entry, exit, this.run);
    }

    /**
     * Writes the finished run down, so the burrow below has something to mirror
     * and a colony's backbone can be told from its everyday holes.
     *
     * <p>Both ends have to be mounds at this moment. The exit is placed a tick
     * earlier and can be refused - the site filled up, a player took the mound -
     * and a link to a hole that is not there would only be pruned again on the
     * next query.</p>
     */
    private void recordLink(ServerLevel level) {
        if (!this.arrivedCleanly || this.colony == null || this.entry == null || this.emergeAt == null) {
            return;
        }
        if (!MoleMound.isMound(level, this.entry) || !MoleMound.isMound(level, this.emergeAt)) {
            return;
        }

        List<Integer> depths = new ArrayList<>(this.route.waypointCount());
        for (Vec3 point : this.route.waypoints()) {
            depths.add(Mth.floor(point.y));
        }

        ColonyStore.get(level).record(level, this.colony.id(), this.entry, this.emergeAt, this.run, depths);
        BurrowLog.linkRecorded(this.mole, this.entry, this.emergeAt, this.run, depths.size());
    }

    private void delayNextAttempt() {
        this.nextAttemptTick = this.mole.tickCount + BurrowConstants.REFUSAL_RETRY_DELAY;
        // Fright waits a shorter beat. The refusal delay is there to stop a
        // pointless search repeating; being hit is new information, and a mole
        // that ignores a blow for three seconds because it stepped off a ledge
        // reads as broken.
        this.nextRetryTick = this.mole.tickCount + BurrowConstants.SURFACE_DWELL_MIN;
    }
}

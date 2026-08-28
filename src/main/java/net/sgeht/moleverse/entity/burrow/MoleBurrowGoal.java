package net.sgeht.moleverse.entity.burrow;

import java.util.EnumSet;
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

    /** Last tick the mole was moving. The boredom timer counts from here. */
    private int lastMovedTick;

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

    @Override
    public boolean canUse() {
        if (this.mole.getBurrowState().isBusy()) {
            return false;
        }
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return false;
        }

        int now = this.mole.tickCount;
        if (this.mole.getDeltaMovement().horizontalDistanceSqr() > BurrowConstants.STILL_THRESHOLD) {
            this.lastMovedTick = now;
        }

        boolean forced = this.forcedBy != null;

        LivingEntity hurtBy = this.mole.getLastHurtByMob();
        boolean struck = hurtBy != null
                && now - this.mole.getLastHurtByMobTimestamp() <= BurrowConstants.FLEE_MEMORY;
        Player tooClose = this.playerTooClose(level);
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

        boolean bored = now - this.lastMovedTick >= BurrowConstants.BURROW_IDLE_DELAY;
        if (!forced && !fleeingNow && !bored) {
            return false;
        }

        BlockPos origin = this.mole.blockPosition();
        BlockState ground = level.getBlockState(origin.below());
        boolean diggable = ground.is(ModTags.Blocks.MOLE_DIGGABLE);

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

        // Two different cooldowns, because two different things happened. Digging
        // a fresh hole added a mound to the world and is rationed. Travelling
        // between mounds that already exist costs the world nothing, so a mole
        // with a network of its own can live in it instead of walking the
        // surface between rare trips.
        int cooldown = !this.wentUnder ? BurrowConstants.REFUSAL_RETRY_DELAY
                : this.placedMound ? BurrowConstants.BURROW_COOLDOWN
                : BurrowConstants.NETWORK_TRIP_COOLDOWN;

        this.entry = null;
        this.exit = null;
        this.emergeAt = null;
        this.openedMound = null;
        this.route = null;
        this.fleeing = false;
        this.entryIsNew = false;
        this.exitIsNew = false;
        this.placedMound = false;
        this.lastMovedTick = this.mole.tickCount;
        this.nextAttemptTick = this.mole.tickCount + cooldown;
        this.nextRetryTick = this.mole.tickCount;
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
        }

        if (refusal == null) {
            return true;
        }
        this.refuse(refusal);
        return false;
    }

    /**
     * Every refusal goes through here: into the log always, and back to a waiting
     * {@code /moleverse mole burrow} once.
     */
    private void refuse(String why) {
        BurrowLog.refused(this.mole, why);
        this.report("refused - " + why);
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
            this.refuse("no room for a mound where he stands");
            return false;
        } else {
            this.entry = origin;
            this.entryIsNew = true;
        }

        MoundNetwork.Members network = MoundNetwork.build(level, this.entry);
        BurrowLog.networkBuilt(this.mole, network.mounds().size(), network.chainDepth(), network.farthest());

        return this.chooseExitAndRoute(level, network, threat, scan.densityCapReached());
    }

    private boolean chooseExitAndRoute(ServerLevel level, MoundNetwork.Members network, @Nullable Vec3 threat,
            boolean crowded) {
        BlockPos chosen = MoundNetwork.chooseExit(level, this.random, network, this.entry, threat);
        if (chosen != null) {
            this.exit = chosen;
            this.exitIsNew = false;
        } else {
            this.exit = MoundNetwork.findFreshSite(level, this.random, this.entry);
            if (this.exit == null) {
                // The crowded case: mounds all around but every one of them too
                // close to be worth the trip, and no room for a fifth anywhere in
                // reach. He wanders off and tries again from somewhere else,
                // which is what spreads a territory out instead of stacking it.
                this.refuse((crowded ? "density cap reached: " : "no valid exit: ")
                        + "no network member beyond " + BurrowConstants.MIN_EXIT_DISTANCE
                        + " blocks and no fresh site was free");
                return false;
            }
            this.exitIsNew = true;
        }

        this.route = BurrowRoute.between(level, this.entry, this.exit);
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

        this.refuse(timedOut
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
        this.route = BurrowRoute.between(level, this.entry, this.exit);
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
            case ARRIVED -> this.beginEmerging(level, "route finished");
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

        // A hill the route climbed into and a roof both put the heightmap far
        // above the mole; only one of them has a room in between, and surfacing
        // through a room means standing on someone's house. Walking the column
        // is what tells them apart. Distance alone cannot: the route deliberately
        // climbs slower than rising ground does, so on any slope the gap grows
        // with the length of the trip and would condemn every uphill journey.
        if (!isSolidColumn(level, x, z, Mth.floor(at.y) + 1, surfaced.getY())) {
            BurrowLog.recovered(this.mole, "ground above is built over - returning to the entry");
            surfaced = MoundNetwork.surfaceAt(level, this.entry.getX(), this.entry.getZ());
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
     * The player who has come close enough to send the mole underground, if any.
     *
     * <p>Sneaking buys a great deal of ground - it is the only way to watch a
     * mole rather than watch it leave. A player in creative or spectator mode
     * scares nobody, so a mound field can be inspected without emptying it.</p>
     */
    private @Nullable Player playerTooClose(ServerLevel level) {
        double range = BurrowConstants.PLAYER_SCARE_DISTANCE;

        // The flag reads backwards: true means "exclude creative". Filtering in
        // the query rather than after it matters - asking for the nearest player
        // and then discarding the answer would let one creative player standing
        // closer hide every survival player behind them.
        Player nearest = level.getNearestPlayer(
                this.mole.getX(), this.mole.getY(), this.mole.getZ(), range, true);
        if (nearest == null) {
            return null;
        }

        // A player holding out food is not a threat, and a mole in love has
        // better things to do. Without this the proximity trigger fires exactly
        // when someone tries to use an earthworm: the burrow goal sits at
        // priority 0 and would take the movement away from the tempt goal every
        // single time, so the worm would look broken.
        if (nearest.isHolding(this.mole::isFood) || this.mole.isInLove()) {
            return null;
        }

        double scare = nearest.isCrouching() ? range * BurrowConstants.SNEAK_SCARE_FACTOR : range;
        return this.mole.distanceToSqr(nearest) <= scare * scare ? nearest : null;
    }

    /**
     * Whether everything between two heights in one column is solid ground.
     *
     * <p>About fifteen block reads, once per trip. It is the cavity that makes
     * surfacing wrong, not the height.</p>
     */
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
        this.refuse(why);
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

    private void delayNextAttempt() {
        this.nextAttemptTick = this.mole.tickCount + BurrowConstants.REFUSAL_RETRY_DELAY;
        this.nextRetryTick = this.nextAttemptTick;
    }
}

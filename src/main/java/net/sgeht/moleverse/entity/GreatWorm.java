package net.sgeht.moleverse.entity;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.burrow.BurrowTraversal;
import net.sgeht.moleverse.entity.burrow.TunnelWalk;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The great worm: the earthworm of {@code ModItems.EARTHWORM}, met at the scale
 * of the burrow.
 *
 * <p>The burrow is the mole tunnel network at four times the size, and the
 * fiction is that the player has shrunk to a quarter. Nothing about this animal
 * is invented for the dimension - it is the same creature a mole is after up
 * above, and it is several blocks long because down here everything is. Meeting
 * one filling a corridor is meant to be the moment that lands.</p>
 *
 * <p>Harmless in every direction: it has no attack, no target selector, no panic
 * goal, and nothing tempts it. It crawls and it travels, and that is the whole
 * behaviour.</p>
 *
 * <h2>It goes places now</h2>
 *
 * <p>The first version only strolled, and the verdict on it was that it existed
 * without ever travelling: a four block animal turning in circles in one stretch
 * of corridor is furniture. Every few minutes it now digs into the floor where it
 * stands, joins a run whose corridor passes within a few blocks, flows down it as
 * a guided traversal, and settles back into the earth at the far end - which is
 * usually another chamber, so the colony's worms slowly redistribute themselves
 * around it rather than each one haunting the spot it was stocked at.</p>
 *
 * <p>The travelling is {@link TunnelWalk}, the same machinery the giant mole
 * uses, and everything the two disagree about is in {@link #STYLE} and
 * {@link #FLOW_SPEED}. The worm is slower - it is a worm - and weaves harder:
 * being narrower than a mole leaves it more play in the same corridor, and a
 * worm that hugs one wall and then the other looks like a worm, while one that
 * holds the middle looks like a train.</p>
 *
 * <p>It has no sweep and never will. The design has said from the start that a
 * player walks <em>past</em> a great worm, which is why it is slow and why it
 * yields when pushed - a corridor event that also hurt would be a corridor
 * hazard, and the burrow has predators for that.</p>
 *
 * <h2>Why {@code PathfinderMob} and not {@code Animal}</h2>
 *
 * <p>{@link net.minecraft.world.entity.animal.Animal} would bring breeding, love
 * mode, a food item, a synched age and a baby variant, and would demand a
 * {@code getBreedOffspring} that could only be a stub. None of it is wanted here
 * - a worm the player can breed is a different design decision, and one nobody
 * has taken. {@code PathfinderMob} is exactly ground pathfinding plus a goal
 * selector, which is all the crawl needs. The one thing {@code Animal} does that
 * is worth keeping is {@link #removeWhenFarAway}, copied below.</p>
 */
public class GreatWorm extends PathfinderMob {

    /**
     * How fast it strolls, as a multiple of {@link Attributes#MOVEMENT_SPEED}.
     *
     * <p>Low enough that a player never has to wait behind one, because a player
     * can walk past it - see the hitbox note in {@code ModEntities}.</p>
     */
    private static final double STROLL_SPEED = 0.6;

    /**
     * How much bigger than its registered box it is drawn and measured at.
     *
     * <p>Not four, which is the scale everything the burrow is <em>built</em> to
     * uses. A real earthworm's proportions say longer and thinner rather than
     * simply bigger, and at four this animal would fill a corridor end to end and
     * become an obstacle instead of an encounter. Half again reads as the
     * corridor's old inhabitant standing next to the four-times fauna - large,
     * and not the largest thing down here.</p>
     */
    private static final double WORM_SCALE = 1.5;

    // --- roaming --------------------------------------------------------------

    /**
     * What it is doing, and in what order.
     *
     * <p>Six states because the journey has six honest parts, and they are
     * symmetric: it sinks out of the room it is in, rises onto the run, flows,
     * sinks off the run, and rises into the room at the far end. The two plain
     * slides are its own; the two on the run are {@link TunnelWalk.Stage}.</p>
     *
     * <p>None of it is saved. A worm unloaded mid-journey comes back a strolling
     * worm wherever the chunk had it, which is the right outcome and is why
     * nothing here uses {@code setNoAi} or {@code setNoGravity} - both of those
     * survive to NBT and would leave a frozen, floating worm behind. See
     * {@link #isEffectiveAi}.</p>
     */
    private enum Roam {
        /** Goals on, doing what it always did. */
        STROLLING,
        /** Sinking into the floor where it stood. */
        SINKING,
        /** Rising out of the run's floor, wherever along it it joined. */
        JOINING,
        /** Travelling the run. */
        FLOWING,
        /** Sinking back into the run's floor at the far end. */
        PARTING,
        /** Rising out of the floor there, and then strolling again. */
        SETTLING
    }

    /** Shortest and longest wait between journeys, in ticks: two to five minutes. */
    private static final int ROAM_DELAY = 2400;
    private static final int ROAM_SPREAD = 3600;

    /** Ticks before trying again when nothing was near enough or long enough. */
    private static final int ROAM_RETRY = 200;

    /**
     * How near a corridor has to pass for the worm to dig into it, in burrow
     * blocks, and the shortest journey worth the trouble.
     *
     * <p>Eight is the whole of the licence this takes with position. Between
     * sinking here and rising there the worm is inside earth and invisible, and it
     * moves at most this far sideways in that interval - close enough that the
     * disappearance and the reappearance read as one animal going through the soil
     * rather than as two events. Widen it and the join becomes a teleport.</p>
     */
    private static final double JOIN_RANGE = 8.0;
    private static final double MIN_JOURNEY = 40.0;

    /** How long each plain slide through the floor takes, and how deep it goes. */
    private static final int SLIDE_TICKS = 22;
    private static final double SLIDE_DEPTH = 3.0;

    /** Nose angles for those slides. Positive lowers the nose. */
    private static final float SLIDE_DIVE_PITCH = 35.0F;
    private static final float SLIDE_RISE_PITCH = -20.0F;

    /** How long the two transitions on the run take. */
    private static final int JOIN_TICKS = 24;

    /**
     * Blocks a tick while travelling.
     *
     * <p>About three fifths of the giant mole's twelve blocks a second, which is
     * the whole difference in one number: a mole runs its errand and a worm flows.
     * It is also slow enough that a player who meets one head on can turn round and
     * outwalk it, which the design has always wanted to stay true.</p>
     */
    private static final double FLOW_SPEED = 0.36;

    /**
     * The worm's own way of getting down a corridor.
     *
     * <p>Nearly twice the mole's drift amplitude against a narrower body, so it
     * spends most of a run against one wall or the other. The transitions are
     * gentler at both ends - a worm has no shoulders to throw into the earth, it
     * pours into it - and shallower, because it is barely a block and a half tall
     * even at {@link #WORM_SCALE}.</p>
     */
    private static final TunnelWalk.Style STYLE = new TunnelWalk.Style(
            4.0,     // drift amplitude, in blocks it would like
            3.0,     // swells of that drift over a whole run
            0.15,    // daylight left between body and wall
            0.10F,   // how fast the drift follows its clamp: slow, so it leans
            4.0,     // how far a transition carries it along the run
            2.5,     // how far under the floor a transition starts and ends
            -25.0F,  // nose up as it comes out
            30.0F,   // nose down as it goes in
            0.25F);  // how fast the heading follows the actual movement

    /** How far a footfall-equivalent carries, in blocks. Quiet: a worm is not a footstep. */
    private static final double SLITHER_DISTANCE = 15.0;

    private Roam roam = Roam.STROLLING;

    /** Ticks in the current state. */
    private int roamTicks;

    /** Game tick at which the next journey may be considered. */
    private int nextRoamTick;

    /** The run being travelled, or null while strolling. */
    private @Nullable TunnelWalk walk;

    /** Where the current slide started from, and how far along it has to go. */
    private double slideFromY;

    /** Blocks covered since the last slither. */
    private double sinceSlither;

    public GreatWorm(EntityType<? extends GreatWorm> type, Level level) {
        super(type, level);
        // The scale attribute is baked into the supplier and never goes dirty,
        // so the dimension cache from Entity's constructor would stay unscaled
        // forever - a 4x model on a 1x box. AgeableMob sets the precedent.
        this.refreshDimensions();
        this.nextRoamTick = ROAM_DELAY + this.random.nextInt(ROAM_SPREAD);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // See WORM_SCALE. The attribute and not the renderer, because it
                // scales the entity's own box too - and the box is what the
                // corridor-width clamp measures the weave against.
                .add(Attributes.SCALE, WORM_SCALE)
                // A big animal, and one the player is meant to walk past rather
                // than through: enough health that a stray swing does not kill
                // it, little enough that killing one on purpose is quick.
                .add(Attributes.MAX_HEALTH, 20.0)
                // Slower than a sniffer, which is the slowest thing vanilla has.
                // Combined with STROLL_SPEED this is about three blocks in five
                // seconds - a crawl, not a walk.
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.FOLLOW_RANGE, 8.0)
                // Several blocks of worm should not skid down a corridor when the
                // player bumps it. It still gets pushed aside, which is the
                // interaction that matters in a corridor.
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        // Not fear of water - the stroll goal already avoids it. This is only so
        // that a worm that ends up in water floats instead of drowning silently.
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Deliberately the plain vanilla goal, and deliberately nothing else.
        // "Prefer to be in a tunnel" needs no code: the goal picks a target
        // within ten blocks and drops any position it cannot path to, and inside
        // a five block wide corridor there is nowhere else for it to go. Going
        // somewhere else entirely is the journey below, which is not a goal at
        // all - a wide hitbox in a narrow tube is exactly what ground
        // pathfinding is worst at, and the whole point of TunnelWalk is that the
        // route is known in advance and never has to be searched for.
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));

        // No look goals on purpose. An earthworm is blind, and the model has no
        // head bone driven by look angles, so LookAtPlayerGoal would cost a goal
        // slot to move nothing.
    }

    /**
     * The one switch that turns the goals off while it travels.
     *
     * <p>{@code isEffectiveAi} gates the whole of {@code Mob.serverAiStep} -
     * sensing, both selectors, the navigation, the move and look controls - and
     * separately gates {@code travel} in {@code LivingEntity.aiStep}, which is
     * where gravity is applied. One override therefore stops everything that could
     * fight the scripted position, and it stops nothing else.</p>
     *
     * <p>Deliberately this and not {@code setNoAi(true)}, which does the same job
     * through a flag that is written to NBT. A worm unloaded halfway down a
     * corridor would come back with its AI switched off for good - the same trap
     * {@code Mole} avoids by deciding its invulnerability from state rather than
     * setting the saved flag. Nothing about the journey survives a reload, so
     * nothing about it may be saved.</p>
     */
    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.roam == Roam.STROLLING;
    }

    /** True while it is somewhere between the floor here and the floor at the far end. */
    public boolean isTravelling() {
        return this.roam != Roam.STROLLING;
    }

    // --- ticking --------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel burrow)) {
            return;
        }

        if (this.roam == Roam.STROLLING) {
            this.considerJourney(burrow);
            return;
        }

        this.roamTicks++;
        switch (this.roam) {
            case SINKING -> this.tickSinking(burrow);
            case JOINING -> this.tickJoining(burrow);
            case FLOWING -> this.tickFlowing(burrow);
            case PARTING -> this.tickParting(burrow);
            case SETTLING -> this.tickSettling(burrow);
            default -> {
            }
        }
    }

    /**
     * Whether it is time to go somewhere, and whether there is anywhere to go.
     *
     * <p>The timer is checked before the search, and the search is the expensive
     * half by a long way - it walks the colony's links and builds a polyline for
     * each. Once every few minutes per worm, and only two of them live near a
     * chamber, so the cost is a rounding error against the corridor those links
     * describe.</p>
     */
    private void considerJourney(ServerLevel burrow) {
        if (this.tickCount < this.nextRoamTick || this.isLeashed() || this.isPassenger() || this.isVehicle()) {
            return;
        }

        BurrowTraversal.Run run =
                BurrowTraversal.runNear(burrow, this.position(), JOIN_RANGE, MIN_JOURNEY, this.random);
        if (run == null) {
            // No corridor near enough, or none with a journey left in it. A worm
            // stocked in a chamber with one short run is the normal case for this,
            // so it is a short wait rather than a full one.
            this.nextRoamTick = this.tickCount + ROAM_RETRY;
            return;
        }

        this.walk = TunnelWalk.along(run.path(), STYLE, this.random);
        this.walk.setProgress(run.from());

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        // Not setNoGravity, which is saved. Physics off is a plain field and the
        // goals are off through isEffectiveAi, so between them nothing moves this
        // animal but the code below.
        this.noPhysics = true;
        this.slideFromY = this.getY();
        this.enter(Roam.SINKING);

        burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.WORM_SLITHER.get(), SoundSource.NEUTRAL, 0.7F, 1.0F);
    }

    private void enter(Roam next) {
        // Six states in a journey, so six lines - and the fraction says where on
        // the run each switch happened, which is the number that tells a stuck
        // worm from a slow one.
        say("worm #{}: {} -> {}{}", this.getId(), this.roam, next,
                this.walk == null ? "" : " at " + Math.round(this.walk.progress() * 100.0) + "%");
        this.roam = next;
        this.roamTicks = 0;
    }

    /**
     * On from the first tick of a development run, off in a shipped game.
     *
     * <p>The same property and logger the rest of the burrow uses. A roaming worm
     * is invisible for most of its journey - it is inside the earth at both ends
     * and in a corridor nobody may be standing in between - so the only way to
     * tell one that is travelling from one that has stopped travelling is to have
     * it say so.</p>
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
    }

    /** Down into the floor where it stood, and then away to the run. */
    private void tickSinking(ServerLevel burrow) {
        this.slide(burrow, this.slideFromY, -SLIDE_DEPTH, SLIDE_TICKS);
        if (this.roamTicks >= SLIDE_TICKS) {
            this.enter(Roam.JOINING);
            // Aimed and placed on the run before anything is drawn there: the
            // worm is buried at both ends of this instant, so the sideways step
            // to the corridor - at most JOIN_RANGE - happens inside solid earth.
            this.walk.placeAtStart(this, burrow);
        }
    }

    /** Up out of the run's floor. */
    private void tickJoining(ServerLevel burrow) {
        this.walk.place(this, burrow, TunnelWalk.Stage.ENTERING, TunnelWalk.eased(this.roamTicks, JOIN_TICKS));
        this.castSoil(burrow, 8);
        if (this.roamTicks >= JOIN_TICKS) {
            this.enter(Roam.FLOWING);
        }
    }

    /** Along the run, at its own pace and nobody else's. */
    private void tickFlowing(ServerLevel burrow) {
        TunnelWalk walk = this.walk;
        double length = walk.length();
        walk.advanceBy(length <= 1.0E-6 ? 1.0 : FLOW_SPEED / length);
        walk.place(this, burrow, TunnelWalk.Stage.TRAVELLING, 1.0F);

        this.sinceSlither += walk.blocksMoved();
        if (this.sinceSlither >= SLITHER_DISTANCE) {
            this.sinceSlither = 0.0;
            // Its body over the earth, and nothing else. There is no recorded
            // voice for this animal yet, and a borrowed one would be somebody
            // else's creature - see docs/BURROW_LIFE.md 6 on the sound pipeline.
            burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ROOTED_DIRT_STEP, SoundSource.NEUTRAL, 0.5F, 0.4F);
        }

        if (walk.finished()) {
            this.enter(Roam.PARTING);
        }
    }

    /** Down into the run's floor at the far end. */
    private void tickParting(ServerLevel burrow) {
        this.walk.place(this, burrow, TunnelWalk.Stage.LEAVING, TunnelWalk.eased(this.roamTicks, JOIN_TICKS));
        this.castSoil(burrow, 8);
        if (this.roamTicks >= JOIN_TICKS) {
            // Taken from where the worm actually is and not from the guide's own
            // height at the end of the run. The two are a block apart wherever the
            // floor was dressed or dug, and a slide that started from the other
            // one would begin with a jump.
            this.slideFromY = this.getY();
            this.enter(Roam.SETTLING);
        }
    }

    /** Up out of the floor there, and back to being an ordinary worm. */
    private void tickSettling(ServerLevel burrow) {
        // Rises exactly as far as parting sank it, and not by SLIDE_DEPTH. The
        // two are different numbers, and using the wrong one leaves the worm half
        // a block over the floor at the end of the journey - a hop that gravity
        // fixes a moment later and that anybody watching would see.
        this.slide(burrow, this.slideFromY, STYLE.transitionSink(), SLIDE_TICKS);
        if (this.roamTicks < SLIDE_TICKS) {
            return;
        }

        // Everything set for the journey is undone here and nowhere else, so a
        // worm that arrives is indistinguishable from one that never left.
        this.noPhysics = false;
        this.setXRot(0.0F);
        this.walk = null;
        this.sinceSlither = 0.0;
        this.nextRoamTick = this.tickCount + ROAM_DELAY + this.random.nextInt(ROAM_SPREAD);
        this.enter(Roam.STROLLING);
    }

    /**
     * A plain vertical slide through the floor, with the soil it disturbs.
     *
     * <p>Not {@link TunnelWalk}'s transition and deliberately kept apart from it.
     * That one is about joining a run - it needs a guide, a heading and a corridor
     * to measure against, and it carries the animal along the run as it goes. This
     * is a worm going straight down where it happens to be standing, which may be
     * the middle of a chamber with no run anywhere near it. Sharing the two would
     * mean giving the guided version a mode in which it has no guide.</p>
     */
    private void slide(ServerLevel burrow, double fromY, double distance, int span) {
        float t = TunnelWalk.eased(this.roamTicks, span);
        this.setPos(this.getX(), fromY + distance * t, this.getZ());
        this.setDeltaMovement(Vec3.ZERO);
        // Tipping down as it goes in, levelling out as it comes up. The two are
        // not the same curve run backwards: a worm arriving should finish level,
        // and one that finished nose-up would snap flat on the tick the goals
        // came back.
        this.setXRot(distance < 0.0
                ? Mth.lerp(t, 0.0F, SLIDE_DIVE_PITCH)
                : Mth.lerp(t, SLIDE_RISE_PITCH, 0.0F));

        if (this.roamTicks % 4 == 1) {
            burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.8F, 0.5F);
        }
        this.castSoil(burrow, 6);
    }

    /** Whatever it is moving through, thrown about a bit. */
    private void castSoil(ServerLevel burrow, int count) {
        BlockPos under = BlockPos.containing(this.getX(), this.getY() - 0.4, this.getZ());
        TunnelWalk.castSoil(burrow, under, this.getX(), this.getY() + 0.2, this.getZ(), count, 0.7, 0.2);
    }

    /**
     * Never despawns, the way an animal does not.
     *
     * <p>{@code PathfinderMob} inherits {@code Mob}'s "yes" here, which would let
     * a worm placed by hand or from a spawn egg vanish as soon as the player
     * walked away from it. Since there is no natural spawning yet, every worm in
     * a world was put there on purpose.</p>
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Not shoved off a run it is halfway down.
     *
     * <p>Only while travelling. A strolling worm being pushed aside in a corridor
     * is the interaction the design asks for and it keeps it; a travelling one is
     * on a line measured against the walls a tick ago, and a shove would put it
     * somewhere nothing had probed.</p>
     */
    @Override
    public boolean isPushable() {
        return !this.isTravelling() && super.isPushable();
    }
}

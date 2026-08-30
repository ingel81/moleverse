package net.sgeht.moleverse.entity.predator;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/**
 * The weasel: the reason a corridor is not just a corridor.
 *
 * <p>One at a time, rarely, and it hunts everything down here including the
 * player. It is the only mob in this mod that will cross a room to reach
 * someone, and the design brief for it was never "a strong monster" - it was
 * "the corridor stops feeling safe". Sixteen blocks of notice, a slow approach a
 * player can watch coming, and then five damage out of a burst they had judged
 * as out of range. See {@link ProwlAndLungeGoal}, which is where that lives.</p>
 *
 * <p>It also eats shrews, which is the part that makes the burrow read as a
 * place rather than as a spawn list. A player who sees the shrews leave has been
 * warned by the ecosystem rather than by a health bar.</p>
 *
 * <h2>It leaves rather than dies</h2>
 *
 * <p>Below a third of its health it breaks off and goes - {@link WithdrawGoal},
 * including the vanishing and the two conditions on it. Deliberately not a
 * fight to the death: this is an event, and an event a player drove off is a
 * better memory than one they ground down. It is also what makes
 * {@code mole_pelt} at one in four a real drop rather than a formality, because
 * getting to the kill is the hard part.</p>
 *
 * <p>The pelt is the drop for the plainest possible reason: it is what the
 * animal has been eating. A weasel small enough to follow a mole down its own
 * tunnel is a weasel that follows moles down their own tunnels.</p>
 */
public class Weasel extends BurrowPredator {

    /**
     * How fast it walks, prowls and lunges, as multiples of its movement speed.
     *
     * <p>The prowl is under the stroll, which looks like a mistake and is the
     * whole trick. An animal that speeds up when it notices you is a threat you
     * can hear coming; an animal that <em>slows down</em> is one that has decided
     * something about you. The lunge is then measured against the prowl rather
     * than against a walk, which is why the ratio between these two matters more
     * than either number.</p>
     */
    private static final double STROLL_SPEED = 1.0;
    private static final double PROWL_SPEED = 0.75;
    private static final double LUNGE_SPEED = 1.7;

    /**
     * The fraction of its health below which it withdraws.
     *
     * <p>A third, which on sixteen health is a little over five - two solid hits
     * with a stone sword. Set lower it never triggers, because the blow that took
     * it under the threshold was also the last one; set higher it triggers on the
     * first hit and the animal never fights at all.</p>
     */
    private static final float WOUNDED = 1.0F / 3.0F;

    /**
     * How long between hisses while it is hunting, in {@code Mob}'s units.
     *
     * <p>Two and a half times the vanilla eighty, and this is the number that
     * keeps the animal from talking itself out of a job. The hiss exists to say
     * "it has seen you" once, in the middle of an approach a player is already
     * watching; at the default interval the same approach would carry six of
     * them and the sixth would be furniture.</p>
     */
    private static final int HISS_INTERVAL = 200;

    /**
     * Shortest gap between two chitters, in ticks.
     *
     * <p>The chitter fires on taking a target, and a target is not a stable
     * thing: a shrew that rounds a corner is dropped and re-taken, and a weasel
     * flicking between a shrew and a player would otherwise chatter every second
     * without moving. Five seconds is longer than that churn and shorter than
     * the walk down a corridor, so a real second decision still gets its
     * sound.</p>
     */
    private static final int CHITTER_COOLDOWN = 100;

    /**
     * Correction for the one borrowed sound left, the bite.
     *
     * <p>This used to be a {@code getVoicePitch} override covering everything the
     * animal said, because everything it said was a fox's and a fox is four times
     * its size. Its voice is its own now - see
     * {@code art/generators/elevenlabs_sounds.py} - so the shift would be a
     * second transposition on files already recorded at weasel scale. It stays
     * exactly where it is still true: on {@code FOX_BITE}, which is an impact and
     * not a voice, and which no generated file replaces yet.</p>
     */
    private static final float BORROWED_PITCH = 1.35F;

    /** {@link #tickCount} of the last chitter. See {@link #CHITTER_COOLDOWN}. */
    private int lastChitter = -CHITTER_COOLDOWN;

    public Weasel(EntityType<? extends Weasel> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                // At mole scale the player is a quarter size, so full scale would be 3.6 wide and vanilla pathfinding stalls wide boxes in a five-wide run; three is the navigable ceiling and still fills a corridor.
                .add(Attributes.SCALE, 3.0)
                // Eight hearts, the same as a zombie. Enough that the fight is a
                // fight; low enough that WOUNDED is reachable before the kill.
                .add(Attributes.MAX_HEALTH, 16.0)
                // Five: over a zombie, under a vindicator. Two of these on an
                // unarmoured player is most of a life bar, which is the number
                // that makes a corridor a decision.
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                // Just above the shrew's, so a shrew that runs early gets away
                // and one that runs late does not.
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                // Sixteen. The range it notices at is the range it withdraws
                // past in WithdrawGoal, and having one number for both is what
                // makes "it saw you" and "it got away" the same distance.
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // First, so that once it is running nothing below it can take the
        // navigation back and turn the retreat into another approach.
        this.goalSelector.addGoal(1, new WithdrawGoal(this));

        this.goalSelector.addGoal(2, new ProwlAndLungeGoal(this, PROWL_SPEED, LUNGE_SPEED));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // Not gated on the wound. Something that goes on hitting a retreating
        // weasel has its full attention, even though the retreat still wins the
        // navigation - which is a weasel backing away from its attacker rather
        // than one turning its back on it.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        // Shrews before players, and it is not a balance decision. A weasel that
        // ignored the shrews to walk past them at a player would say the burrow
        // exists for the player's benefit. Taking the shrew first is what makes
        // the dimension look like it was already running before anyone arrived.
        //
        // Both are shut off once it is wounded, and the condition has to live
        // here rather than on WithdrawGoal - see the note on that class's flags.
        // The selector's arguments are ignored on purpose: this is a question
        // about the weasel, and the selector is the only hook the vanilla goal
        // offers that is asked before a target is taken.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Shrew.class, 10, true, false, (target, level) -> !this.isWounded()));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false, (target, level) -> !this.isWounded()));
    }

    /** Whether it has taken enough to break off. See {@link WithdrawGoal}. */
    public boolean isWounded() {
        return this.getHealth() < this.getMaxHealth() * WOUNDED;
    }

    /**
     * Gone.
     *
     * <p>The puff is the whole of the feedback and it is worth the four lines.
     * An entity that simply stops existing is indistinguishable from a despawn
     * bug, and this one removes itself on purpose at a moment a player may well
     * be looking in its direction. Soil rather than smoke, because what a weasel
     * does at the end of a corridor is go into the wall.</p>
     */
    public void slipAway() {
        if (this.level() instanceof ServerLevel server && !this.isRemoved()) {
            server.sendParticles(ParticleTypes.MYCELIUM,
                    this.getX(), this.getY() + 0.2, this.getZ(),
                    10, 0.25, 0.15, 0.25, 0.01);
        }
        this.discard();
    }

    /**
     * Silent unless it is hunting something.
     *
     * <p>Returning null here is the whole design of this animal in one line.
     * {@code Mob.makeSound} takes null and does nothing with it, so the timer
     * goes on cycling and produces no sound at all while the weasel is strolling
     * - and a corridor that has a weasel in it is indistinguishable, by ear, from
     * one that does not. The hiss is not an ambient. It is the moment the animal
     * stops being scenery, and it can only mean that if the player has heard
     * nothing from it until then.</p>
     */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.getTarget() == null ? null : ModSounds.WEASEL_HISS.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return HISS_INTERVAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.WEASEL_HURT.get();
    }

    /**
     * Its own yelp again, as a stand-in.
     *
     * <p>No death sound has been generated for it. The same argument as on the
     * shrew applies and applies harder here, because this animal usually
     * {@link #slipAway}s rather than dying - so the death sound is the rarer of
     * the two and the one least worth spending a borrowed fox on.</p>
     */
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WEASEL_HURT.get();
    }

    /**
     * Chitters when it decides on something.
     *
     * <p>This is the hunt starting, and it is deliberately not the lunge.
     * {@link ProwlAndLungeGoal} owns the lunge and knows the range it happens at,
     * but a sound there would arrive inside the burst it is meant to announce -
     * too late to be a warning and too close to the bite to be heard as separate.
     * Taking a target happens up to sixteen blocks out, before the approach, so
     * the chatter is the thing a player hears and then spends the next few
     * seconds watching arrive.</p>
     *
     * <p>Checked after {@code super} and against {@link #getTarget} rather than
     * against the argument, because NeoForge's {@code LivingChangeTargetEvent}
     * can cancel or substitute the target, and a sound for a target the weasel
     * did not actually take is a sound with nothing behind it.</p>
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = this.getTarget();
        super.setTarget(target);
        LivingEntity after = this.getTarget();
        if (after != null && after != before && !this.level().isClientSide()
                && this.tickCount - this.lastChitter >= CHITTER_COOLDOWN) {
            this.lastChitter = this.tickCount;
            this.makeSound(ModSounds.WEASEL_CHITTER.get());
        }
    }

    /** The bite. Loud enough to be the thing a player turns round for. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            this.playSound(SoundEvents.FOX_BITE, 0.7F, this.getVoicePitch() * BORROWED_PITCH);
        }
        return hit;
    }

    // --- the incursion --------------------------------------------------------

    /**
     * How much bigger it is while running a colony's runs, on top of the walking
     * scale above.
     *
     * <p>Five and a half all told, and the number comes off the corridor rather
     * than off the giant mole it is meant to outsize. A weasel's box is 0.9 wide
     * against a mole's 0.7, so the mole's seven would put this animal at 6.3
     * across and it would not fit down a five-wide feeding run at all. Five and a
     * half gives 4.95 - a hair over the mole's 4.9, which is what the plan asks
     * for, and a hair under the run, which is what makes it possible.</p>
     *
     * <p>A modifier and not a new base value, because the base is what a summoned
     * weasel gets and three is the widest box vanilla pathfinding still steers
     * down a corridor. Transient, so it cannot be saved onto an animal whose
     * incursion ended while the chunk was unloaded.</p>
     */
    private static final double INCURSION_SCALE = 5.5;

    private static final AttributeModifier INCURSION_SIZE = new AttributeModifier(
            Moleverse.id("incursion"), INCURSION_SCALE - 3.0, AttributeModifier.Operation.ADD_VALUE);

    /**
     * Whether a {@code WeaselIncursion} is driving this animal.
     *
     * <p>Not saved, and that is the same decision the great worm's roam mode
     * makes for the same reason: everything about an incursion is a moment, and an
     * animal reloaded out of one has to come back as an ordinary weasel rather
     * than as a scripted one with nothing scripting it.</p>
     */
    private boolean onIncursion;

    /**
     * Turns it into the thing that comes down the run.
     *
     * <p>{@code noPhysics} so a body this wide is never wedged by the corridor it
     * is being steered along, and the size modifier. What is deliberately
     * <em>not</em> switched off is anything that makes it a creature: it keeps its
     * health, its damage, its loot and its hurt sounds, because the whole point of
     * this one against the giant mole is that it can be fought.</p>
     */
    public void beginIncursion() {
        this.onIncursion = true;
        this.getNavigation().stop();
        this.setTarget(null);
        this.noPhysics = true;

        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(INCURSION_SIZE.id())) {
            scale.addTransientModifier(INCURSION_SIZE);
            // Adding a modifier only marks the attribute dirty; the box is not
            // rebuilt until refreshDirtyAttributes runs inside the next tick. The
            // incursion measures the corridor against this body before that tick
            // happens, so it is asked for outright rather than waited for.
            this.refreshDimensions();
        }
    }

    /** Hands it back to its own goals, at whatever size it started as. */
    public void endIncursion() {
        this.onIncursion = false;
        this.noPhysics = false;

        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(INCURSION_SIZE.id());
        }
    }

    public boolean isOnIncursion() {
        return this.onIncursion;
    }

    /**
     * The one switch that keeps the goals off while it is being steered.
     *
     * <p>{@code isEffectiveAi} gates the whole of {@code Mob.serverAiStep} -
     * sensing, both selectors, the navigation and every control - and separately
     * gates {@code travel}, which is where gravity is applied. Deliberately this
     * and not {@code setNoAi}, which does the same job through a flag that is
     * written to NBT: a weasel unloaded mid-incursion would come back with its AI
     * switched off for good. The same trap the great worm avoids the same way.</p>
     */
    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.onIncursion;
    }

    /** Not shoved off a line that was measured against the walls a tick ago. */
    @Override
    public boolean isPushable() {
        return !this.onIncursion && super.isPushable();
    }
}

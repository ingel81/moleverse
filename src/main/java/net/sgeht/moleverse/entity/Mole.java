package net.sgeht.moleverse.entity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.sgeht.moleverse.debug.MoleDebug;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The mole. Small, passive, close to the ground.
 *
 * <p>Behaviour is deliberately plain for now: flee, wander, look around. The one
 * thing that already sets it apart is the rearing pose it falls into when it
 * stands still.</p>
 *
 * <p>That pose is split in two. The secondary motion - head sweeping, snout
 * twitching, paws shifting - comes from the {@code mole_peek} keyframe animation
 * and is driven by {@link #peekAnimationState}. The body angle itself is
 * <em>not</em> a keyframe channel but a plain number, {@link #peekAmount},
 * applied to the root part in the model. Keyframing the root meant fighting
 * three separate coordinate conversions at once; a single blend factor is
 * explicit, tunable at runtime and reusable for aiming a digging mole in any
 * direction later.</p>
 *
 * <p>The digging animations exist but the behaviour behind them does not. What
 * drives them here is a <strong>debug harness</strong> - {@link MoleDebug#forceDig}
 * for the sustained dig pose and two play counters for the one-shots - marked as
 * such at every method. Phase 3 of {@code docs/MOLEHILL.md} replaces all of it
 * with the burrowing state machine, which starts and stops the very same
 * animation states from its state changes. Nothing in the harness is meant to
 * survive that; the blend factors and the animation states are.</p>
 *
 * <p>Taming, tunnel digging and surfacing with finds are the plan for later
 * versions; see {@code docs/MOLE_DESIGN.md}.</p>
 */
public class Mole extends Animal {

    /** Ticks a mole must stand still before it rears up to look around. */
    private static final int PEEK_DELAY = 70;

    /** Ticks between two peeks. Slightly longer than the animation itself. */
    private static final int PEEK_INTERVAL = 200;

    /** How long the mole holds the reared pose, in ticks. */
    private static final int PEEK_HOLD = 120;

    /** Squared horizontal speed below which the mole counts as standing still. */
    private static final double STILL_THRESHOLD = 1.0E-5;

    /** Fraction of the remaining distance the blend factor covers per tick. */
    private static final float PEEK_BLEND_SPEED = 0.12F;

    /**
     * The same for the dig aim, and faster: diving in is a lunge, not the
     * settling a mole does when it rears up. At this rate the aim is all but
     * complete after eleven ticks, roughly half of {@code mole_burrow}, so the
     * mole points the right way well before that animation ends.
     */
    private static final float DIG_BLEND_SPEED = 0.2F;

    /** Length of {@code mole_burrow} in ticks. Matches the exported 1.2 s. */
    private static final int BURROW_TICKS = 24;

    /** Length of {@code mole_emerge} in ticks. Matches the exported 0.8 s. */
    private static final int EMERGE_TICKS = 16;

    public final AnimationState peekAnimationState = new AnimationState();

    /** Ambient loop. Started once and never stopped; the model fades it by speed. */
    public final AnimationState idleAnimationState = new AnimationState();

    /** Alternating paws scooping. Loops while digging; where it digs is a body angle, not a channel. */
    public final AnimationState digAnimationState = new AnimationState();

    /** Diving in. Plays once, {@value #BURROW_TICKS} ticks long. */
    public final AnimationState burrowAnimationState = new AnimationState();

    /** Coming back up. Plays once, {@value #EMERGE_TICKS} ticks long. */
    public final AnimationState emergeAnimationState = new AnimationState();

    private int peekCooldown = PEEK_DELAY;
    private int peekTicksLeft;

    /** 0 while crawling, 1 while fully reared up. Client side only. */
    private float peekAmount;
    private float peekAmountLast;

    /** 0 while level, 1 while fully aimed at the dig direction. Client side only. */
    private float digAmount;
    private float digAmountLast;

    // DEBUG ONLY, see the class comment. The counters start at whatever the
    // panel is showing, so a mole that spawns later does not replay the last
    // request; they run on both sides because the hold is a server decision.
    private int burrowRequestSeen = MoleDebug.burrowRequest;
    private int emergeRequestSeen = MoleDebug.emergeRequest;
    private int burrowTicksLeft;
    private int emergeTicksLeft;

    public Mole(EntityType<? extends Mole> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        this.updateDebugOneShots();

        // The poses are cosmetic, so they are driven client side only.
        if (this.level().isClientSide()) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            this.updatePeek();
            this.updateDigAim();
        } else if (MoleDebug.forcePeek || MoleDebug.forceDig || this.playingOneShot()) {
            this.holdStillForTuning();
        }
    }

    /**
     * Keeps the mole where it is while a pose is being judged. Without this the
     * goals keep wandering off and the pose is impossible to look at.
     */
    private void holdStillForTuning() {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /**
     * DEBUG ONLY. The entire phase-2 test harness for the two one-shot
     * animations: {@link MoleDebug} counts requests, every mole plays each new
     * one once. Phase 3 deletes this and starts the same states from
     * {@code BURROWING} and {@code EMERGING} instead.
     *
     * <p>Runs on both sides. The animation states are a client visual, but the
     * countdown also tells {@link #tick()} to pin the mole while an animation is
     * playing, and that is a server decision.</p>
     */
    private void updateDebugOneShots() {
        if (MoleDebug.burrowRequest != this.burrowRequestSeen) {
            this.burrowRequestSeen = MoleDebug.burrowRequest;
            this.burrowAnimationState.start(this.tickCount);
            this.burrowTicksLeft = BURROW_TICKS;
        } else if (this.burrowTicksLeft > 0 && --this.burrowTicksLeft == 0) {
            // A non-looping keyframe animation holds its last frame for good,
            // so something has to stop it or the mole freezes nose-down.
            this.burrowAnimationState.stop();
        }

        if (MoleDebug.emergeRequest != this.emergeRequestSeen) {
            this.emergeRequestSeen = MoleDebug.emergeRequest;
            this.emergeAnimationState.start(this.tickCount);
            this.emergeTicksLeft = EMERGE_TICKS;
        } else if (this.emergeTicksLeft > 0 && --this.emergeTicksLeft == 0) {
            this.emergeAnimationState.stop();
        }
    }

    /** True while a debug-triggered one-shot animation is still playing. */
    private boolean playingOneShot() {
        return this.burrowTicksLeft > 0 || this.emergeTicksLeft > 0;
    }

    /**
     * Blend factor for the dig aim, plus the dig cycle that goes with it.
     *
     * <p>Only the condition is the debug harness. The blend below is the real
     * mechanism: phase 3 keeps it and feeds it from its state enum instead of
     * from {@link MoleDebug#forceDig}.</p>
     */
    private void updateDigAim() {
        this.digAmountLast = this.digAmount;

        boolean digging = MoleDebug.forceDig;
        this.digAmount = Mth.lerp(DIG_BLEND_SPEED, this.digAmount, digging ? 1.0F : 0.0F);

        // Kept scooping while the body angle blends back out, so the paws do not
        // snap to rest under a mole that is still tipped into the ground.
        this.digAnimationState.animateWhen(digging || this.digAmount > 0.01F, this.tickCount);
    }

    private void updatePeek() {
        this.peekAmountLast = this.peekAmount;

        boolean wantsToPeek = this.decideWhetherToPeek();

        if (wantsToPeek) {
            this.peekAmount = Mth.lerp(PEEK_BLEND_SPEED, this.peekAmount, 1.0F);
        } else {
            this.peekAmount = Mth.lerp(PEEK_BLEND_SPEED, this.peekAmount, 0.0F);
        }
    }

    private boolean decideWhetherToPeek() {
        // mole_emerge is authored to end in the rearing pose, and that pose is
        // not part of the animation - it is this blend factor. Driving it while
        // the mole surfaces is the handoff phase 3 makes from EMERGING; without
        // it the animation and the pose do not chain, they jump. The peek
        // keyframes stay out of the way meanwhile: emerge has its own head and
        // paw motion.
        if (this.emergeTicksLeft > 0) {
            return true;
        }

        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > STILL_THRESHOLD;

        if (moving) {
            this.peekAnimationState.stop();
            this.peekCooldown = PEEK_DELAY;
            this.peekTicksLeft = 0;
            return false;
        }

        if (this.peekTicksLeft > 0) {
            this.peekTicksLeft--;
            return true;
        }

        if (--this.peekCooldown <= 0) {
            this.peekAnimationState.start(this.tickCount);
            this.peekCooldown = PEEK_INTERVAL;
            this.peekTicksLeft = PEEK_HOLD;
            return true;
        }

        this.peekAnimationState.stop();
        return false;
    }

    /** Blend factor between crawling and fully reared up, interpolated for rendering. */
    public float getPeekAmount(float partialTick) {
        return Mth.lerp(partialTick, this.peekAmountLast, this.peekAmount);
    }

    /** Blend factor between level and fully aimed at the dig direction, interpolated for rendering. */
    public float getDigAmount(float partialTick) {
        return Mth.lerp(partialTick, this.digAmountLast, this.digAmount);
    }

    /**
     * Nothing tempts a mole yet. Once there is a food item worth digging for,
     * this decides what it is and unlocks breeding along with it.
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.MOLE_SNIFF.get();
    }
}

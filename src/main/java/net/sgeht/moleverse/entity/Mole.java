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

    public final AnimationState peekAnimationState = new AnimationState();

    private int peekCooldown = PEEK_DELAY;
    private int peekTicksLeft;

    /** 0 while crawling, 1 while fully reared up. Client side only. */
    private float peekAmount;
    private float peekAmountLast;

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

        // The rearing pose is cosmetic, so it is driven client side only.
        if (this.level().isClientSide()) {
            this.updatePeek();
        } else if (MoleDebug.forcePeek) {
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

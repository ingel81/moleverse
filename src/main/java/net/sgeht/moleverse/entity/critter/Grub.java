package net.sgeht.moleverse.entity.critter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The grub: the reason to light a larder.
 *
 * <p>Everything else in the burrow is atmosphere. This one is a mechanic, and
 * the only one in the creature waves that can take something away from a
 * player. A grub looks for a worm larder, sits against it, and if that larder
 * is still dark a minute later it eats it - the block is gone and does not come
 * back until the moles re-dig the room, which re-carves the shape and not the
 * food.</p>
 *
 * <p>That turns the burrow's lighting from decoration into upkeep. A larder
 * under a shaft lantern keeps; a larder in an unlit alcove is on a timer. It is
 * also why the light threshold here is 8 and not some rounder number: block
 * light falls one level per block and glow mycelium emits 9, so eight is
 * exactly "there is a light source adjacent to this larder". The player who
 * works that out has learnt the burrow's one real rule.</p>
 *
 * <h2>Two safety valves, and why each exists</h2>
 *
 * <p><b>One larder each.</b> A grub that has eaten is full for good - it keeps
 * its fat, stops looking, and goes back to milling about. Without this, one
 * grub that survives long enough clears an entire alcove, and the mechanic
 * stops being upkeep and becomes a wipe. It is stored, so a reload does not
 * hand a full grub a fresh appetite.</p>
 *
 * <p><b>No larder, no grub.</b> If two minutes pass with nothing to eat within
 * range, it leaves. Grubs are meant to be found near larders; one that spawned
 * in an empty corridor is a wandering hazard nobody will ever connect to a
 * cause, and it would still be there when the player finally brings it a
 * larder. Self-cleaning is cheaper than being clever about where they spawn -
 * and the search that clears the timer is one the goal is running anyway, so
 * the grubs that pay for a full scan are exactly the ones about to be
 * removed.</p>
 */
public class Grub extends BurrowCritter {

    /**
     * Whether this grub has had its larder.
     *
     * <p>Synched because the client draws a full grub differently, and there is
     * no way for it to work the answer out - the block that was eaten is gone
     * from both sides and says nothing about who ate it.</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_FED =
            SynchedEntityData.defineId(Grub.class, EntityDataSerializers.BOOLEAN);

    /** How far it will look for a larder, and how far up and down. */
    public static final int SEARCH_RANGE = 16;
    public static final int SEARCH_HEIGHT = 5;

    /** How close it has to be to count as at the larder. Just over one block. */
    public static final double REACH = 3.2;

    /**
     * How long it chews, in ticks. A minute.
     *
     * <p>Long enough that a player who walks in on one has time to do something
     * about it, which is the difference between a mechanic and a mugging. The
     * chew restarts from nothing if the larder is lit in the meantime, so a
     * torch thrown down at the last second is a torch that worked.</p>
     */
    public static final int CHEW_TICKS = 1200;

    /**
     * The light level a larder has to reach to be safe: this or above.
     *
     * <p>See the class note. Eight is the number the burrow's own lighting
     * makes meaningful, not a taste decision.</p>
     */
    public static final int SAFE_LIGHT = 8;

    /** How long it will go on not finding a larder before it gives up and leaves. */
    public static final int GIVE_UP_TICKS = 2400;

    /** How long the swelling takes once it has eaten. */
    private static final int FATTEN_TICKS = 30;

    private static final double STROLL_SPEED = 0.8;

    /** Ticks since it last had a larder in range. Server side; the goal resets it. */
    private int withoutLarder;

    /**
     * The swelling, 0 to 1, advanced on both sides.
     *
     * <p>Not synched, and does not need to be: it is a ramp towards
     * {@link #DATA_FED}, which is, so both sides reach the same place from
     * whatever they were at. A client that joins late sees a grub that is
     * already fat, which is correct.</p>
     */
    private float fatten;
    private float fattenOld;

    public Grub(EntityType<? extends Grub> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // At mole scale the player is a quarter size, so the soil fauna at the scale the shrink implies.
                .add(Attributes.SCALE, 4.0)
                // Three: one hit with anything sharp, three with a fist. It is
                // a bag of fluid and should die like one - the threat is the
                // timer, never the fight.
                .add(Attributes.MAX_HEALTH, 3.0)
                // The slowest thing in the mod. A grub crossing a room is
                // something a player has time to notice and time to reach.
                .add(Attributes.MOVEMENT_SPEED, 0.06)
                .add(Attributes.FOLLOW_RANGE, SEARCH_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatLarderGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));

        // No panic goal. A grub that ran would be a grub a player could lose,
        // and the whole point is that it is slow enough to deal with.
    }

    /** Whether it has already had its one larder. */
    public boolean isFed() {
        return this.entityData.get(DATA_FED);
    }

    /** Called by {@link EatLarderGoal} when the last block goes down. */
    public void setFed() {
        this.entityData.set(DATA_FED, true);
    }

    /** Called by {@link EatLarderGoal} whenever it can see a larder at all. */
    public void noticeLarder() {
        this.withoutLarder = 0;
    }

    /** How swollen it is, for the renderer. */
    public float getFatten(float partialTick) {
        return Mth.lerp(partialTick, this.fattenOld, this.fatten);
    }

    /**
     * Advances the swelling, on both sides, and runs the give-up clock.
     *
     * <p>In {@code tick} rather than {@code customServerAiStep} because half of
     * it is the client's: {@code customServerAiStep} never runs there, and a
     * grub that swelled only on the server would pop to its new size on the
     * next full entity sync instead of growing into it.</p>
     */
    @Override
    public void tick() {
        super.tick();

        this.fattenOld = this.fatten;
        float target = this.isFed() ? 1.0F : 0.0F;
        this.fatten = Mth.approach(this.fatten, target, 1.0F / FATTEN_TICKS);

        if (this.level().isClientSide()) {
            return;
        }

        // A grub that has eaten has no reason to be near a larder any more, so
        // the clock would otherwise start running on an animal that is doing
        // exactly what it is supposed to.
        if (this.isFed()) {
            return;
        }
        if (++this.withoutLarder >= GIVE_UP_TICKS && this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.MYCELIUM,
                    this.getX(), this.getY() + 0.1, this.getZ(),
                    6, 0.15, 0.05, 0.15, 0.0);
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Fed", this.isFed());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        boolean fed = input.getBooleanOr("Fed", false);
        this.entityData.set(DATA_FED, fed);
        // Straight to full rather than swelling again on every world load.
        this.fatten = fed ? 1.0F : 0.0F;
        this.fattenOld = this.fatten;
    }

    /**
     * Bursts.
     *
     * <p>A grub drops nothing, so without this the only feedback for killing
     * one is that it stopped existing - and the animal a player is most likely
     * to attack on sight is the one with the least to say about it. The squish
     * and the spray are the whole reward.</p>
     */
    @Override
    public void die(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel server && !this.isRemoved()) {
            server.sendParticles(ParticleTypes.ITEM_SLIME,
                    this.getX(), this.getY() + 0.15, this.getZ(),
                    14, 0.12, 0.08, 0.12, 0.02);
        }
        super.die(damageSource);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    /** The squish. It is the one sound this animal is remembered for. */
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }
}

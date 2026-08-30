package net.sgeht.moleverse.entity.critter;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The soil beetle: the burrow's silverfish, minus the malice.
 *
 * <p>Ground beetles are the other thing a spade turns up, and unlike a
 * silverfish this one never attacks anything. What it does is leave. It walks
 * the corridors, and when it finds itself in a lit stretch it walks out of it -
 * which is the whole of its contribution to the dimension, because it is what
 * makes a player's lantern visibly do something to the world rather than just
 * to the gamma.</p>
 *
 * <p>Neutral in the strict sense: no target selector, no attack goal, no
 * attributes for either. Hitting one gets a chitin flake and a beetle that was
 * already trying to be somewhere else.</p>
 */
public class SoilBeetle extends BurrowCritter {

    /**
     * Block light it will not tolerate, and the level it settles back down at.
     *
     * <p>Eleven is chosen against the burrow's own lighting rather than picked
     * as a round number. Glow mycelium emits 9 and block light falls one per
     * block, so a corridor lit by the ceiling growth alone sits at 8 or below a
     * step away from it - beetles live there quite happily, which is what makes
     * the burrow feel inhabited. A torch at 14 or a shaft lantern clears 11
     * over a real radius, and that is the space they give up.</p>
     */
    private static final int FLEE_LIGHT = 11;
    private static final int SETTLE_LIGHT = 8;

    /** How fast it scuttles, and how fast it leaves. */
    private static final double STROLL_SPEED = 1.0;

    /**
     * Barely faster than a stroll, on purpose.
     *
     * <p>The brief for this animal is that it flees light <em>slowly</em>. A
     * beetle that bolts reads as frightened of the player, which is the shrew's
     * job in a later wave; a beetle that ambles out of the light reads as an
     * animal that simply prefers the dark, which is the truth.</p>
     */
    private static final double FLEE_SPEED = 1.15;

    public SoilBeetle(EntityType<? extends SoilBeetle> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // At mole scale the player is a quarter size, so the soil fauna at the scale the shrink implies.
                .add(Attributes.SCALE, 4.0)
                // Two hits with a fist, one with anything else. It is armoured
                // compared with a worm and that is all the difference needed.
                .add(Attributes.MAX_HEALTH, 4.0)
                // Quick for its size. A beetle that crawls is a woodlouse.
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 8.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Above the stroll, so a beetle that wanders into a lit patch turns
        // round rather than finishing its walk through it.
        this.goalSelector.addGoal(1, new FleeLightGoal(this, FLEE_LIGHT, SETTLE_LIGHT, FLEE_SPEED));

        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));

        // No panic goal, and that is the difference between neutral and prey.
        // A beetle that is hit keeps doing what it was doing, which at this
        // size reads as armour rather than as stupidity.
    }

    /**
     * The one of the three that makes a noise when it moves.
     *
     * <p>{@link BurrowCritter} silences footsteps because two of these animals
     * have no feet. This one has six, and a dry tick from the dark before
     * anything is visible is most of what the animal is for. Quiet enough that
     * it never carries further than the beetle can be found.</p>
     */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.08F, 1.4F);
    }

    /**
     * A dry tick from the dark, and rarely.
     *
     * <p>Three hundred rather than the vanilla eighty. Beetles come in ones and
     * twos and this is the animal's only voice, so the click has to be the thing
     * that makes a player stop rather than the thing they stop hearing. Volume is
     * {@code BurrowCritter.QUIET} and needs no help here.</p>
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BEETLE_CLICK.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }
}

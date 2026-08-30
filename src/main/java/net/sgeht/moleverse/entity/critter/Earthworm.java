package net.sgeht.moleverse.entity.critter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The small worm: what a mole's larder is actually packed with.
 *
 * <p>Not a contradiction of {@code GreatWorm}, and worth being clear about
 * because the two share a name and a ramp. The burrow is the tunnel network at
 * four times life size and the player is shrunk to a quarter, so a full-grown
 * earthworm down there is four blocks long - that is the great worm, and it is
 * the joke the dimension is built on. This one is three quarters of a block,
 * which is a young worm a few centimetres long: the size moles actually take by
 * the hundred, and the size a larder is stocked with. Same species, same
 * colours, a different year of its life.</p>
 *
 * <p>It is the burrow's chicken. No attack, nothing it wants, nothing it
 * defends. It crawls, it panics when something hurts it, and every so often it
 * goes down into the floor and is gone.</p>
 */
public class Earthworm extends BurrowCritter {

    /** How fast it crawls, as a multiple of {@link Attributes#MOVEMENT_SPEED}. */
    private static final double STROLL_SPEED = 0.7;

    /**
     * And how fast it runs, which is barely faster.
     *
     * <p>A worm cannot outrun anything and should not look as though it thinks
     * it can. What the panic goal buys is direction - away - not speed.</p>
     */
    private static final double PANIC_SPEED = 1.4;

    /**
     * One tick in this many, a worm standing on loose soil digs in and is gone.
     *
     * <p>About a minute and a half of average life once it has settled, which
     * does two things at once. It is the natural cap: worms spawn continuously
     * and something has to take them away again, and a worm going into the
     * ground is a better answer than a worm blinking out at the despawn radius.
     * And when it happens in front of a player it is the single most
     * earthworm-like thing this animal does.</p>
     */
    private static final int BURROW_AWAY_CHANCE = 1800;

    /**
     * How long a worm has to have existed before it may leave.
     *
     * <p>Twenty seconds. Without it a worm can spawn and vanish inside the same
     * breath, which from the player's side is a texture flickering rather than
     * an animal doing something.</p>
     */
    private static final int SETTLE_IN_TICKS = 400;

    public Earthworm(EntityType<? extends Earthworm> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // At mole scale the player is a quarter size, so the soil fauna at the scale the shrink implies.
                .add(Attributes.SCALE, 4.0)
                // Two hearts' worth would be generous. One hit from anything
                // kills it, which is correct for prey and keeps the loot honest.
                .add(Attributes.MAX_HEALTH, 2.0)
                // Slower than the great worm, which is already slower than a
                // sniffer. At this and STROLL_SPEED it covers about a block in
                // a second and a half.
                .add(Attributes.MOVEMENT_SPEED, 0.09)
                .add(Attributes.FOLLOW_RANGE, 6.0);
    }

    @Override
    protected void registerGoals() {
        // Not fear of water. This is so a worm that ends up in a puddle floats
        // instead of drowning out of sight.
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Panic only from damage - the plain constructor keys on
        // DamageTypeTags.PANIC_CAUSES, so being looked at, walked past or stood
        // next to does nothing. An earthworm is blind and deaf; it finds out
        // about a player by being trodden on.
        this.goalSelector.addGoal(1, new PanicGoal(this, PANIC_SPEED));

        // The plain vanilla stroll, for the same reason the great worm uses it:
        // "prefer to be in a tunnel" needs no code when the tunnel is the only
        // thing there is to path into.
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));

        // No look goals. A worm has no eyes and the model has no head bone
        // driven by look angles, so one would cost a slot to move nothing.
    }

    /**
     * Now and then, digs into the floor and leaves.
     *
     * <p>Only from loose soil - the corridor floor - because that is the one
     * block down there a worm could actually get into, and requiring it means
     * the trick never fires on a player's stone platform.</p>
     *
     * <p>Not while it has recently been hurt: a worm that vanishes the moment
     * it is struck reads as a despawn bug rather than as an escape, and the
     * player is owed the drop.</p>
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        if (this.tickCount < SETTLE_IN_TICKS
                || !this.onGround()
                || this.getLastHurtByMob() != null
                || this.random.nextInt(BURROW_AWAY_CHANCE) != 0) {
            return;
        }

        BlockPos floor = this.blockPosition().below();
        BlockState state = level.getBlockState(floor);
        if (!state.is(ModBlocks.LOOSE_SOIL.get())) {
            return;
        }

        // The three-argument option lets each crumb pick its texture from the
        // block it came out of, which is what makes the puff read as that floor
        // rather than as a generic grey burst.
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state, floor),
                this.getX(), this.getY() + 0.05, this.getZ(),
                12, 0.15, 0.02, 0.15, 0.02);
        level.playSound(null, this.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK,
                SoundSource.NEUTRAL, 0.3F, 1.5F);
        this.discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }
}

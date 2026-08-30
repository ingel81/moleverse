package net.sgeht.moleverse.entity.critter;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.dimension.ModDimensions;

/**
 * What the earthworm, the soil beetle and the grub have in common.
 *
 * <p>Deliberately very little. Three small animals that share a base class tend
 * to grow a fourth animal's worth of switches in it, so this holds only the
 * three things all of them genuinely share: where they are allowed to spawn,
 * how loud they are, and the fact that none of them has feet worth hearing.
 * Everything that distinguishes one from another - the goals, the attributes,
 * the sounds, the drops - lives in its own class, so that removing one creature
 * is removing its files and its registration lines and nothing else.</p>
 *
 * <h2>Why {@code PathfinderMob}</h2>
 *
 * <p>The same reason {@code GreatWorm} gives: {@link net.minecraft.world.entity.animal.Animal}
 * would bring breeding, love mode, a food item and an age, and would demand a
 * {@code getBreedOffspring} that could only be a stub. None of these three
 * breeds. {@code PathfinderMob} is ground pathfinding plus a goal selector,
 * which is all any of them needs.</p>
 *
 * <h2>They despawn, and that is the design</h2>
 *
 * <p>{@code Mob.removeWhenFarAway} returns true and is deliberately not
 * overridden here - the opposite of {@code GreatWorm}, which is placed by hand
 * and stocked by {@code BurrowLife} and would be lost if it wandered out of
 * range. These three spawn naturally and continuously, so the ordinary despawn
 * is what keeps a corridor from silting up with them. It is also the reason
 * none of them stores anything a player would miss.</p>
 */
public abstract class BurrowCritter extends PathfinderMob {

    /**
     * How loud anything any of them says is.
     *
     * <p>They are between a third and a half of a block long. A hurt sound at
     * full volume from something that size carries further than the animal can
     * be seen, which is how a burrow full of quiet detail turns into a burrow
     * that squeaks.</p>
     */
    protected static final float QUIET = 0.25F;

    protected BurrowCritter(EntityType<? extends BurrowCritter> type, Level level) {
        super(type, level);

        // Makes the collision box catch up with Attributes.SCALE. Without this
        // the model is four times the size and the box is not, and a beetle
        // stands half inside the wall it is walking past.
        //
        // The cause is a cache and an ordering. Entity's constructor sets
        // this.dimensions to the raw EntityType dimensions, and it runs before
        // LivingEntity's constructor has built the AttributeMap - which is why
        // getScale() carries a null check on it. Scale is applied in
        // LivingEntity.getDimensions(Pose), which only ever reaches the cached
        // field through refreshDimensions().
        //
        // Nothing calls that on our behalf. LivingEntity does call it for
        // SCALE, but only from refreshDirtyAttributes, which walks the
        // attributes marked dirty - and an attribute is marked dirty by
        // setBaseValue or by a modifier being added at runtime. A base value
        // baked into the AttributeSupplier by createAttributes is never set
        // that way, so it is never dirty, so the box never updates. The
        // renderer meanwhile reads getScale() live every frame
        // (LivingEntityRenderer sets state.scale from it), which is the whole
        // asymmetry.
        //
        // The constructor is the seam because it is the first moment the
        // attribute map exists, and because there is nothing to wait for: the
        // client builds the same map from the same supplier, and no attribute
        // packet is sent for a value that was never dirty. AgeableMob does the
        // same thing by hand when a mob stops being a baby.
        this.refreshDimensions();
    }

    /**
     * Where any of the three may appear, once a biome has decided to spawn one.
     *
     * <p>Two conditions, and the interesting one is the first.</p>
     *
     * <p>The dimension check looks redundant - {@code moleverse:burrow} is the
     * only biome that lists these animals, and only the burrow uses that biome.
     * It is insurance against the biome being placed somewhere else, by a
     * datapack or by a later wave of this mod, and the failure it prevents is
     * the one this mod cannot afford: a grub eating a worm larder that a player
     * built on the surface.</p>
     *
     * <p>The second is {@link Mob#checkMobSpawnRules}, which asks only that the
     * block below can be stood on. Explicitly <em>not</em>
     * {@code Animal.checkAnimalSpawnRules}, which the mole uses: that one wants
     * light above 8 and a block in {@code ANIMALS_SPAWNABLE_ON}, and the burrow
     * has neither - its floor is loose soil in the dark. Reusing it would have
     * produced three creatures that never spawn and no error to say why.</p>
     */
    public static boolean checkBurrowSpawnRules(
            EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason,
            BlockPos pos, RandomSource random) {
        return ModDimensions.isBurrow(level) && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    /** Block light where the animal is standing. The burrow has no sky light to add. */
    protected int lightHere() {
        return this.level().getBrightness(LightLayer.BLOCK, this.blockPosition());
    }

    @Override
    protected float getSoundVolume() {
        return QUIET;
    }

    /**
     * No footsteps.
     *
     * <p>Two of these three have no feet at all, and the third's are one texel
     * wide. Vanilla plays a block's step sound for any mob that moves, which on
     * an animal this size is a stone slab walking. {@code SoilBeetle} puts one
     * back, quietly, because a beetle is the one of them that would make a
     * sound worth hearing.</p>
     */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }
}

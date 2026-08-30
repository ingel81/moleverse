package net.sgeht.moleverse.entity.predator;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.sgeht.moleverse.dimension.ModDimensions;

/**
 * What the shrew and the weasel have in common.
 *
 * <p>The same deliberately small amount {@code BurrowCritter} holds for the
 * passives: where they are allowed to appear, and nothing else. Two predators
 * that share a base class will grow a third predator's worth of switches in it
 * given the chance, so the hunting, the attributes, the sounds and the drops all
 * live in the animal's own class - and removing one of them is removing its
 * files and its registration lines.</p>
 *
 * <h2>Why {@code Monster} and not {@code BurrowCritter}</h2>
 *
 * <p>{@link Monster} is not just {@code PathfinderMob} with a bad attitude. It
 * carries {@code Enemy}, which is what makes a mob count towards the hostile cap
 * and stop a player sleeping; {@code SoundSource.HOSTILE}, so a player's hostile
 * volume slider works on it; the swing timer that a melee attack needs; and
 * {@code getWalkTargetValue} scored against light, which makes a predator prefer
 * dark ground without a single line of code from us. Reimplementing that on top
 * of the critter base would be reimplementing the thing this dimension is about.</p>
 *
 * <h2>The light gate, and why it is written out</h2>
 *
 * <p>{@link Monster#checkMonsterSpawnRules} is the standard rule and this is not
 * it, which needs justifying because reusing vanilla is the house rule.</p>
 *
 * <p>That method delegates its darkness test to the dimension type, through
 * {@code monsterSpawnBlockLightLimit} and {@code monsterSpawnLightTest}. The
 * burrow's dimension type sets both to zero, so the standard rule resolves down
 * here to <em>block light exactly 0</em> - the pitch-dark corridor and nothing
 * else. That is a stricter gate than the burrow wants and, worse, it is a gate
 * this package does not own: a later tweak to the dimension type for fog or
 * ceiling height would silently move it.</p>
 *
 * <p>So the number is here, at {@link #MAX_SPAWN_LIGHT}, and it is seven because
 * eight is already the burrow's meaning of "lit". {@code Grub.SAFE_LIGHT} is
 * eight for the reason spelled out there - glow mycelium emits nine and block
 * light falls one per block, so eight is exactly "there is a light source beside
 * this" - and a player who has learnt that a lit larder keeps has learnt at the
 * same time that a lit corridor is empty. One number, one lesson.</p>
 *
 * <p>What is kept from vanilla is everything else the standard rule does: no
 * hostiles on Peaceful, a spawner may ignore the light, and the block below has
 * to be something that can be stood on.</p>
 */
public abstract class BurrowPredator extends Monster {

    /**
     * Block light at or below which a predator may appear.
     *
     * <p>See the class note: seven is one under {@code Grub.SAFE_LIGHT}, so a
     * larder that is safe from grubs stands in a stretch that is safe from
     * teeth.</p>
     */
    public static final int MAX_SPAWN_LIGHT = 7;

    protected BurrowPredator(EntityType<? extends BurrowPredator> type, Level level) {
        super(type, level);
        // The scale attribute is baked into the supplier and never goes dirty,
        // so the dimension cache from Entity's constructor would stay unscaled
        // forever - a 4x model on a 1x box. AgeableMob sets the precedent.
        this.refreshDimensions();
    }

    /**
     * Where either of them may appear, once the biome has decided to spawn one.
     *
     * <p>Three conditions, and only the middle one is ours. The dimension check
     * is the same insurance {@code BurrowCritter} carries - the burrow biome is
     * the only thing that lists these animals and only the burrow uses that
     * biome, but a datapack could place it anywhere, and a weasel in a player's
     * cellar is a bug report nobody could diagnose.</p>
     */
    public static boolean checkBurrowMonsterSpawnRules(
            EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason,
            BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && ModDimensions.isBurrow(level.getLevel())
                && (EntitySpawnReason.ignoresLightRequirements(reason)
                        || level.getBrightness(LightLayer.BLOCK, pos) <= MAX_SPAWN_LIGHT)
                && checkMobSpawnRules(type, level, reason, pos, random);
    }

    /** Block light where the animal is standing. The burrow has no sky light to add. */
    protected int lightHere() {
        return this.level().getBrightness(LightLayer.BLOCK, this.blockPosition());
    }
}

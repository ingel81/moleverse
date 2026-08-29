package net.sgeht.moleverse.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

/**
 * The great worm: the earthworm of {@code ModItems.EARTHWORM}, met at the scale
 * of the burrow.
 *
 * <p>The burrow is the mole tunnel network at four times the size, and the
 * fiction is that the player has shrunk to a quarter. Nothing about this animal
 * is invented for the dimension - it is the same creature a mole is after up
 * above, and it is four blocks long because down here everything is. Meeting one
 * filling a corridor is meant to be the moment that lands.</p>
 *
 * <p>Harmless in every direction: it has no attack, no target selector, no panic
 * goal, and nothing tempts it. It crawls, and that is the whole behaviour.</p>
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

    public GreatWorm(EntityType<? extends GreatWorm> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // A big animal, and one the player is meant to walk past rather
                // than through: enough health that a stray swing does not kill
                // it, little enough that killing one on purpose is quick.
                .add(Attributes.MAX_HEALTH, 20.0)
                // Slower than a sniffer, which is the slowest thing vanilla has.
                // Combined with STROLL_SPEED this is about three blocks in five
                // seconds - a crawl, not a walk.
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.FOLLOW_RANGE, 8.0)
                // Four blocks of worm should not skid down a corridor when the
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
        // a five block wide corridor there is nowhere else for it to go. A
        // custom goal would only reproduce what the geometry already does, and
        // would have to be tuned against a dimension that is still being built.
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED));

        // No look goals on purpose. An earthworm is blind, and the model has no
        // head bone driven by look angles, so LookAtPlayerGoal would cost a goal
        // slot to move nothing.
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
}

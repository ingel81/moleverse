package net.sgeht.moleverse.entity.predator;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Close slowly, then cover the last few blocks all at once.
 *
 * <p>A weasel that walks at one speed is a zombie with better fur. The two
 * phases are the whole character of the animal: it comes down the corridor at
 * something under a walk, which is slow enough that a player who sees it has
 * time to feel it coming, and then inside {@link #LUNGE_RANGE} it is suddenly
 * the fastest thing in the dimension. The threat is not the five damage; it is
 * that the distance a player judged as safe turns out not to have been.</p>
 *
 * <h2>Speed, not keyframes</h2>
 *
 * <p>Per the project rule, the phase is a number and not an animation. It is one
 * call to {@code PathNavigation.setSpeedModifier} per tick, which is how vanilla
 * itself does the same thing - {@code AvoidEntityGoal} switches between a walk
 * and a sprint the moment whatever it is running from gets within seven blocks.
 * The visible burst comes out of the model for free, because the gait in
 * {@code WeaselModel} is driven by distance covered: the animal that is moving
 * twice as fast is undulating twice as fast, and neither class had to know about
 * the other.</p>
 *
 * <h2>Why the speed is set after the vanilla tick and not instead of it</h2>
 *
 * <p>{@link MeleeAttackGoal} only calls {@code moveTo} when it re-paths, every
 * four to eleven ticks, and {@code moveTo} is what resets the navigation's speed
 * to the goal's own. Setting the modifier afterwards, every tick, therefore wins
 * whichever tick it is - and needs no access to the private field vanilla keeps
 * that speed in. Overriding {@code tick} entirely to path by hand was the other
 * option and it means owning the re-path interval, the line-of-sight test and
 * the attack cooldown, none of which this class has an opinion about.</p>
 */
public class ProwlAndLungeGoal extends MeleeAttackGoal {

    /**
     * Distance at which the prowl becomes a lunge, in blocks.
     *
     * <p>Five, measured against the corridor rather than against the animal. A
     * burrow corridor is about three blocks across, so five is roughly "as soon
     * as it is in the same stretch of tunnel as you" - and it is beyond the reach
     * of every melee weapon in the game, which is the point. A player who waits
     * to swing until the weasel is in range has already let it choose the moment.</p>
     */
    private static final double LUNGE_RANGE = 5.0;

    private final PathfinderMob weasel;
    private final double prowlSpeed;
    private final double lungeSpeed;

    public ProwlAndLungeGoal(PathfinderMob weasel, double prowlSpeed, double lungeSpeed) {
        // Follows an unseen target, unlike the shrew's goal. A weasel that lost
        // its quarry round a corner and gave up would be a weasel that never
        // catches anything, because a burrow is nothing but corners.
        super(weasel, prowlSpeed, true);
        this.weasel = weasel;
        this.prowlSpeed = prowlSpeed;
        this.lungeSpeed = lungeSpeed;
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = this.weasel.getTarget();
        if (target == null) {
            return;
        }
        boolean close = this.weasel.distanceToSqr(target) <= LUNGE_RANGE * LUNGE_RANGE;
        this.weasel.getNavigation().setSpeedModifier(close ? this.lungeSpeed : this.prowlSpeed);
    }
}

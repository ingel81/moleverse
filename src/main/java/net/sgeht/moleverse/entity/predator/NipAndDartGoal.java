package net.sgeht.moleverse.entity.predator;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Bite once, run, come back. The shrew's whole relationship with a player.
 *
 * <p>A shrew has six hit points and does two damage. Standing in front of a
 * player trading blows, which is what {@link MeleeAttackGoal} on its own
 * produces, it dies in one swing and contributes nothing but a drop. What makes
 * an animal that weak worth having in the dimension is that it does not stand
 * there: it darts in out of the dark, takes a bite, and is four blocks away
 * before the swing lands. Three of them doing that in a corridor is pressure of
 * a kind no single stronger mob provides, and it is pressure a player answers
 * with light rather than with armour.</p>
 *
 * <h2>Why it extends the vanilla goal rather than replacing it</h2>
 *
 * <p>Everything before the bite is ordinary melee: path to the target, keep the
 * path fresh as it moves, look at it, respect the attack cooldown, check line of
 * sight. {@link MeleeAttackGoal} is a hundred and fifty lines of exactly that
 * and it is correct. What is new here is only what happens <em>after</em> a hit
 * connects, so that is the only thing overridden - the alternative was a private
 * copy of the vanilla goal with four lines changed, which is the version that
 * quietly stops matching vanilla two updates later.</p>
 *
 * <h2>The dart</h2>
 *
 * <p>{@link #checkAndPerformAttack} is where the hit is noticed, because it is
 * the one method vanilla calls only when an attack actually lands - hooking
 * {@code tick} would have meant re-deriving "did it connect" from the cooldown,
 * and hooking {@code doHurtTarget} on the mob would have fired for thorns and
 * for anything else that ever damages through the shrew.</p>
 *
 * <p>While darting, {@code super.tick()} is not called at all. It would re-path
 * towards the target every few ticks and undo the retreat; letting it run and
 * then overriding the navigation afterwards produced a shrew that shuddered
 * between the two. The dart ends on a timer rather than on arrival, so a shrew
 * whose escape route is blocked gives up and turns round instead of standing in
 * a corner recomputing a path it cannot walk.</p>
 */
public class NipAndDartGoal extends MeleeAttackGoal {

    /**
     * How long a shrew stays away after biting, in ticks.
     *
     * <p>Two and a half seconds, which is longer than it sounds. The point of
     * comparison is the vanilla attack cooldown of twenty ticks: at anything
     * under that the shrew is back in range before it may bite again and the
     * dart is decoration. At this length there is a real gap in which the player
     * can turn, move, or put a torch down, and it is that gap that makes several
     * shrews read as a pack taking turns rather than as one animal stuttering.</p>
     */
    private static final int DART_TICKS = 50;

    /** How far it tries to get, and how far up and down it will look for it. */
    private static final int DART_RADIUS = 6;
    private static final int DART_HEIGHT = 3;

    /** How fast it leaves. Faster than it arrives - the bite is the brave part. */
    private static final double DART_SPEED = 1.4;

    private final PathfinderMob shrew;
    private int darting;

    public NipAndDartGoal(PathfinderMob shrew, double approachSpeed) {
        super(shrew, approachSpeed, true);
        this.shrew = shrew;
    }

    @Override
    public void start() {
        super.start();
        this.darting = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.darting = 0;
    }

    @Override
    public void tick() {
        if (this.darting > 0) {
            this.darting--;
            // Still facing the thing it just bit. A shrew that runs away looking
            // over its shoulder is a shrew that is coming back, which is exactly
            // what it is about to do.
            LivingEntity target = this.shrew.getTarget();
            if (target != null) {
                this.shrew.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            return;
        }
        super.tick();
    }

    /**
     * Takes the bite, then leaves.
     *
     * <p>The retreat point comes from {@link DefaultRandomPos#getPosAway}, the
     * same call {@code AvoidEntityGoal} uses, so "away from that" is answered by
     * the pathfinder rather than by a direction this class invented. If it
     * refuses - a dead end, a corner - the shrew simply does not dart, and the
     * goal falls back to ordinary melee for one cycle. That is the right failure:
     * a cornered shrew fighting is more sensible than a cornered shrew running
     * into a wall.</p>
     */
    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        boolean landing = this.canPerformAttack(target);
        super.checkAndPerformAttack(target);
        if (!landing) {
            return;
        }

        Vec3 away = DefaultRandomPos.getPosAway(this.shrew, DART_RADIUS, DART_HEIGHT, target.position());
        if (away == null) {
            return;
        }
        if (this.shrew.getNavigation().moveTo(away.x, away.y, away.z, DART_SPEED)) {
            this.darting = DART_TICKS;
        }
    }
}

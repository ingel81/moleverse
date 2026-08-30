package net.sgeht.moleverse.entity.predator;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A hurt weasel stops hunting, leaves, and is not there in the morning.
 *
 * <p>The weasel is an event rather than a fight. Beating one is meant to be
 * memorable, and what makes it memorable is that it does not stay to be killed:
 * hurt it badly enough and it breaks off, goes into the dark, and is gone. A
 * player who drove one off has a story; a player who chased a limping animal
 * down a corridor and clubbed it has a chore.</p>
 *
 * <p>It is also the honest way to give a rare mob a rare drop. The pelt is a one
 * in four chance <em>on a kill</em>, and the withdrawal is what makes a kill
 * something other than the default outcome of meeting one.</p>
 *
 * <h2>Why the vanishing lives in this goal</h2>
 *
 * <p>It could have been a check in {@code customServerAiStep}, which is where
 * {@code Earthworm} and {@code Grub} do their leaving. It is here because the
 * condition is not a property of the animal, it is the end of this behaviour:
 * far enough away, long enough gone, and nobody watching. Splitting it would put
 * half a decision in each of two files and a flag between them.</p>
 *
 * <h2>Nobody watching, and why that is two tests</h2>
 *
 * <p>Distance alone is not enough - a burrow corridor runs straight for twenty
 * blocks and a player can watch a weasel the whole way down it, so a purely
 * distance-based vanish would happen in plain sight and read as a despawn bug.
 * Line of sight alone is not enough either, because a weasel that steps behind a
 * pillar two blocks from a player would blink out of existence at arm's length.
 * Both, and the animal disappears where a real one does: round the corner and
 * well away.</p>
 */
public class WithdrawGoal extends Goal {

    /** How fast it leaves. Everything it has - this is the one thing it is doing. */
    private static final double FLEE_SPEED = 1.5;

    /** How far it tries to get in one leg of the retreat, and how far up and down. */
    private static final int FLEE_RADIUS = 14;
    private static final int FLEE_HEIGHT = 5;

    /**
     * How far a player has to be before the weasel may slip away, in blocks.
     *
     * <p>Sixteen, which is the range it hunts at. An animal that vanishes closer
     * than it could have found you is an animal that vanished while it was still
     * a threat, and the player is owed the difference.</p>
     */
    private static final double UNSEEN_RANGE = 16.0;

    /**
     * How long it must have been retreating before it may go, in ticks.
     *
     * <p>Five seconds. Without it a weasel hurt round a corner with no player in
     * range goes on the very tick it is wounded, which from the player's side is
     * a mob that was killed by the blow that did not kill it.</p>
     */
    private static final int SETTLE_TICKS = 100;

    private final Weasel weasel;
    private int retreating;

    public WithdrawGoal(Weasel weasel) {
        this.weasel = weasel;
        // MOVE only, which is the whole of what this goal needs and is all a
        // flag here could buy anyway. Taking Goal.Flag.TARGET as well looks like
        // it would stop the hunting goals from handing a fleeing weasel a fresh
        // quarry, and it does nothing of the kind: a mob has two independent
        // GoalSelectors and each keeps its own set of locked flags, so a flag
        // held in the goal selector is invisible to the target selector. What
        // actually keeps the target clear is the condition on the two hunting
        // goals in Weasel.registerGoals.
        //
        // Not LOOK either, deliberately. LookAtPlayerGoal is left free to run,
        // so a weasel leaving still glances back at whoever hurt it.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.weasel.isWounded();
    }

    @Override
    public boolean canContinueToUse() {
        // Not conditioned on the wound. A weasel healed by a regeneration effect
        // half way down the corridor turning round and coming back would be
        // funny exactly once.
        return !this.weasel.isRemoved();
    }

    @Override
    public void start() {
        this.retreating = 0;
        this.weasel.setTarget(null);
        this.weasel.setAggressive(false);
    }

    @Override
    public void stop() {
        this.retreating = 0;
        this.weasel.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.retreating++;

        if (this.weasel.getNavigation().isDone()) {
            flee();
        }

        if (this.retreating >= SETTLE_TICKS && alone()) {
            this.weasel.slipAway();
        }
    }

    /**
     * Picks somewhere further from the nearest player and paths to it.
     *
     * <p>Away from the player rather than away from whatever last hit it: by the
     * time this goal runs the attacker reference may be a wolf that has since
     * died, and the thing a retreating weasel is actually avoiding is the
     * corridor a player is standing in. With nobody in range it walks anywhere at
     * all, which is what carries it out of a dead end it was cornered in.</p>
     */
    private void flee() {
        Player player = this.weasel.level().getNearestPlayer(this.weasel, UNSEEN_RANGE);
        Vec3 away = player == null
                ? DefaultRandomPos.getPos(this.weasel, FLEE_RADIUS, FLEE_HEIGHT)
                : DefaultRandomPos.getPosAway(this.weasel, FLEE_RADIUS, FLEE_HEIGHT, player.position());
        if (away != null) {
            this.weasel.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        }
    }

    /** No player near enough to see it, or none with a clear line to it. */
    private boolean alone() {
        Player player = this.weasel.level().getNearestPlayer(this.weasel, UNSEEN_RANGE);
        return player == null || !this.weasel.hasLineOfSight(player);
    }
}

package net.sgeht.moleverse.entity.critter;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Walks out of a lit patch and into the dark, without ever running.
 *
 * <p>This is what makes light the burrow's safety currency rather than a
 * decoration. A player who hangs a shaft lantern has cleared a stretch of
 * corridor, and the way that has to read is beetles leaving it - not beetles
 * refusing to enter, which nobody can see happen.</p>
 *
 * <h2>Why it does not use {@code FleeSunGoal}</h2>
 *
 * <p>The vanilla goal that looks like this one is about sky light and shade,
 * and there is no sky in the burrow. What is here is block light from mycelium,
 * lanterns and torches, so the test is {@link LightLayer#BLOCK} and the escape
 * is any position with less of it.</p>
 *
 * <h2>Picking the dark spot</h2>
 *
 * <p>{@link LandRandomPos#getPos(PathfinderMob, int, int, java.util.function.ToDoubleFunction)}
 * generates ten reachable candidates and keeps the one that scores highest, so
 * scoring a position at minus its light level asks it for the darkest place the
 * animal can actually walk to. Doing it this way rather than by hand is worth
 * saying out loud: the hard part of "go somewhere darker" is not finding a dark
 * block, it is finding one the pathfinder agrees exists, and that is the part
 * this call already solves.</p>
 *
 * <h2>The two thresholds</h2>
 *
 * <p>It starts fleeing above {@code flee} and stops below {@code settle}. One
 * threshold produces an animal that oscillates on the edge of a lit pool,
 * starting and abandoning a path every few ticks, which looks like a stutter
 * rather than a decision. The gap between the two is the hysteresis that turns
 * it into a walk.</p>
 */
public class FleeLightGoal extends Goal {

    /** How far it will look for somewhere darker, and how far up and down. */
    private static final int SEARCH_RADIUS = 12;
    private static final int SEARCH_HEIGHT = 4;

    /**
     * How much darker a candidate has to be before it is worth the walk.
     *
     * <p>Without it the goal fires on any lit block and settles for a
     * neighbouring one that is a single level darker, so the animal shuffles
     * sideways for ever inside the same pool of light.</p>
     */
    private static final int WORTH_MOVING_FOR = 3;

    private final PathfinderMob mob;
    private final int flee;
    private final int settle;
    private final double speed;

    private double targetX;
    private double targetY;
    private double targetZ;

    public FleeLightGoal(PathfinderMob mob, int flee, int settle, double speed) {
        this.mob = mob;
        this.flee = flee;
        this.settle = settle;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        int here = light(this.mob.blockPosition());
        if (here <= this.flee) {
            return false;
        }

        Vec3 refuge = LandRandomPos.getPos(this.mob, SEARCH_RADIUS, SEARCH_HEIGHT,
                pos -> -light(pos));
        if (refuge == null || light(BlockPos.containing(refuge)) > here - WORTH_MOVING_FOR) {
            return false;
        }

        this.targetX = refuge.x;
        this.targetY = refuge.y;
        this.targetZ = refuge.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && light(this.mob.blockPosition()) > this.settle;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    private int light(BlockPos pos) {
        return this.mob.level().getBrightness(LightLayer.BLOCK, pos);
    }
}

package net.sgeht.moleverse.entity.burrow;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.Mole;

/**
 * A juvenile goes down with an adult rather than digging a shaft of its own.
 *
 * <p>The whole goal is a mirror. It copies the adult's {@link BurrowState} onto
 * the baby and snaps the baby to wherever she is, and everything that being
 * inside the ground means - invisibility, damage immunity, no physics, no
 * gravity, the dig pose, the two one-shot animations - falls out of that state on
 * both entities without a line of code here. {@link MoleBurrowGoal} is therefore
 * untouched by any of this: it never learns that anyone is following.</p>
 *
 * <p>"Mother" is the nearest adult mole, not a remembered parent. Moles do not
 * breed yet, so there is no parentage to remember; when there is, this is the one
 * method that has to change.</p>
 *
 * <p>Every failure ends the same way, which is the fallback the plan asks for:
 * the baby stops following, comes back to the surface if it was already under,
 * and stands there. It never ends up stuck in the ground and never travels on a
 * route of its own.</p>
 */
public class MoleFollowMotherGoal extends Goal {

    private final Mole baby;

    /** The adult being followed. Only meaningful while the goal runs. */
    private @Nullable Mole mother;

    public MoleFollowMotherGoal(Mole baby) {
        this.baby = baby;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // The baby is snapped to a mother who moves a fraction of a block per
        // tick. At half the rate it would visibly lag behind her.
        return true;
    }

    @Override
    public boolean isInterruptable() {
        // Once the baby is in the ground, nothing may take it out but this class.
        return false;
    }

    @Override
    public boolean canUse() {
        if (!this.baby.isBaby() || this.baby.getBurrowState().isBusy()) {
            return false;
        }
        if (!(this.baby.level() instanceof ServerLevel) || !this.babyCanTravel()) {
            return false;
        }

        Mole found = this.findDivingAdult();
        if (found == null) {
            return false;
        }
        this.mother = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // The mirror ends itself by putting the baby back to WANDERING.
        return this.baby.getBurrowState().isBusy();
    }

    @Override
    public void start() {
        this.baby.getNavigation().stop();
        this.baby.setBurrowState(BurrowState.BURROWING, "following an adult down");
    }

    @Override
    public void tick() {
        Mole her = this.mother;
        if (her == null || !her.isAlive() || her.isRemoved() || her.level() != this.baby.level()) {
            // She died, was removed, or her chunk went away underneath her. The
            // baby has no route of its own to finish, so it simply comes back up.
            BurrowLog.recovered(this.baby, "the adult it was following is gone");
            this.endFollowing("lost the adult it was following");
            return;
        }

        switch (her.getBurrowState()) {
            case BURROWING -> this.holdStill();
            case UNDERGROUND -> this.rideAlong(her);
            case EMERGING -> this.surfaceWith(her);
            case WANDERING, APPROACHING -> {
                // She finished, or her own trip was cut short - a forced stop
                // takes her straight from UNDERGROUND back to WANDERING, so the
                // baby can still be inside the ground when this is reached.
                this.endFollowing("the adult it was following came back up");
            }
        }
    }

    /**
     * The single place a following baby is put back together, so a forced end
     * leaves the same state behind as a clean one.
     */
    @Override
    public void stop() {
        if (this.baby.getBurrowState().isBelowGround()) {
            // Only reachable when something removed the goal mid-trip.
            BurrowLog.recovered(this.baby, "stopped following while underground");
        }
        this.endFollowing("escort stopped");
        this.mother = null;
    }

    /**
     * Ends the escort from any state at all. Surfacing comes before the state
     * change, because it is the state that keeps the baby weightless and out of
     * collision: clearing it first would hand physics back to a mole still two
     * blocks inside the terrain.
     */
    private void endFollowing(String reason) {
        if (this.baby.getBurrowState().isBelowGround() && this.baby.level() instanceof ServerLevel level) {
            this.baby.pushToSurface(level);
        }
        this.baby.endUnderground();
        if (this.baby.getBurrowState().isBusy()) {
            this.baby.setBurrowState(BurrowState.WANDERING, reason);
        }
    }

    // --- the steps ------------------------------------------------------------

    /**
     * The adult whose dive this baby joins: the nearest one that is standing in
     * her burrow animation, which is the 1.2 second window in which she has
     * committed to the trip but has not left the surface yet.
     */
    private @Nullable Mole findDivingAdult() {
        AABB nearby = this.baby.getBoundingBox().inflate(BurrowConstants.BABY_FOLLOW_RADIUS);
        Mole nearest = null;
        // Seeded with the radius so the box the query needs does not quietly
        // widen it by a factor of root three in the corners.
        double best = BurrowConstants.BABY_FOLLOW_RADIUS * BurrowConstants.BABY_FOLLOW_RADIUS;

        for (Mole other : this.baby.level().getEntitiesOfClass(Mole.class, nearby,
                candidate -> !candidate.isBaby() && candidate.getBurrowState() == BurrowState.BURROWING)) {
            double distance = other.distanceToSqr(this.baby);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    /** Whether the baby is in a state to be taken anywhere at all. */
    private boolean babyCanTravel() {
        return !this.baby.isLeashed()
                && !this.baby.isPassenger()
                && !this.baby.isVehicle()
                && this.baby.onGround()
                && !this.baby.isInWater();
    }

    private void rideAlong(Mole her) {
        if (!this.baby.getBurrowState().isBelowGround()) {
            this.baby.beginUnderground();
            this.baby.setBurrowState(BurrowState.UNDERGROUND, "travelling with an adult");
        }

        // Exactly her position rather than a trailing offset: both are invisible
        // down there, and her position is the only one already proven to be
        // solid, liquid-free and entity-ticking by the route check.
        this.baby.snapTo(her.position());
        this.baby.setYRot(her.getYRot());
        this.baby.setYHeadRot(her.getYRot());
        this.baby.yBodyRot = her.yBodyRot;
    }

    private void surfaceWith(Mole her) {
        if (this.baby.getBurrowState() == BurrowState.EMERGING) {
            return;
        }

        // She has already been lifted out of the ground and put on the surface by
        // the time she reaches EMERGING, so her position is a spot known to be
        // free. The two of them overlap for a moment and push apart on their own.
        this.baby.endUnderground();
        this.baby.snapTo(her.position());
        this.baby.setDeltaMovement(Vec3.ZERO);
        this.baby.setBurrowState(BurrowState.EMERGING, "surfacing with an adult");
    }

    private void holdStill() {
        this.baby.getNavigation().stop();
        // Vertical movement is left alone so gravity still settles it on the ground.
        this.baby.setDeltaMovement(this.baby.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }
}

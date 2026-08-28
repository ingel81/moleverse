package net.sgeht.moleverse.entity.burrow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.sgeht.moleverse.entity.Mole;

/**
 * Wandering, but only for a mole that has nowhere to be.
 *
 * <p>A mole is an animal of the underground. Its default is to be in a tunnel,
 * to look out of one of its own holes, or to be digging towards a new one -
 * walking about on the surface is what it does when it has no burrow yet and
 * needs to find somewhere to make one.</p>
 *
 * <p>The ordinary strolling goal knows nothing of that and sends the mole off
 * across the meadow the moment it surfaces, which left it living on top of its
 * network rather than in it. Here the goal simply switches itself off once the
 * mole has mounds within reach: at that point every trip starts at a hole it is
 * already standing on, and the seconds in between are for looking around, not
 * for going anywhere.</p>
 */
public class MoleSurfaceStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final Mole mole;

    public MoleSurfaceStrollGoal(Mole mole, double speed) {
        super(mole, speed);
        this.mole = mole;
    }

    @Override
    public boolean canUse() {
        // Base goal first. It only wants to start a walk on about one
        // evaluation in a hundred and twenty, and asking it first is what
        // keeps the mound lookup behind that same odds.
        return super.canUse() && this.hasNowhereToBe();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && this.hasNowhereToBe();
    }

    /**
     * True while no mound is within reach, or while the mole has been refused a
     * trip and needs to look somewhere else.
     *
     * <p>The second half matters as much as the first. With strolling switched
     * off near a mound, a mole that cannot dig - the area is full, no exit is far
     * enough, nowhere takes a fresh hole - would stand on its own molehill
     * indefinitely. Walking off is precisely the answer in that case, and it is
     * what spreads a territory outward instead of stacking it in one spot.</p>
     *
     * <p>Asked only after the base goal has already decided it wants to walk,
     * which it does on roughly one evaluation in a hundred and twenty. The
     * lookup itself is not cheap - it sweeps twenty-five chunk columns - so the
     * order of those two conditions is the difference between a query every
     * other tick and one every few seconds.</p>
     */
    private boolean hasNowhereToBe() {
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return true;
        }

        MoleBurrowGoal burrowing = this.mole.getBurrowGoal();
        if (burrowing != null && burrowing.isRefusing()) {
            return true;
        }

        return !MoundNetwork.anyMoundNear(level, this.mole.blockPosition());
    }
}

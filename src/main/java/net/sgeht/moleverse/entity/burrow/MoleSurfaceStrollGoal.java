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
        return this.hasNowhereToBe() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.hasNowhereToBe() && super.canContinueToUse();
    }

    /**
     * True while no mound is within reach.
     *
     * <p>Cheap: one point-of-interest count, and only while the base goal would
     * otherwise be starting or continuing a walk.</p>
     */
    private boolean hasNowhereToBe() {
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return true;
        }
        return MoundNetwork.scan(level, this.mole.blockPosition()).nearest() == null;
    }
}

package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * Render state for the mole.
 *
 * <p>Rendering never touches the entity directly. Everything the model needs is
 * copied here once per frame by {@link MoleRenderer#extractRenderState}.</p>
 */
public class MoleRenderState extends LivingEntityRenderState {

    /** Secondary motion of the rearing pose: head sweep, snout twitch, paws. */
    public final AnimationState peekAnimationState = new AnimationState();

    /** 0 while crawling, 1 while fully reared up. Drives the body angle. */
    public float peekAmount;
}

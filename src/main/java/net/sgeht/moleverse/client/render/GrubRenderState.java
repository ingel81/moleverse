package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the grub.
 *
 * <p>One field, and it earns its class. Everything else the model needs -
 * distance covered, speed, age - {@link LivingEntityRenderState} already
 * carries, but how full a grub is cannot be worked out from any of them: the
 * larder it ate is gone from the client's world too, and says nothing about who
 * ate it.</p>
 */
public class GrubRenderState extends LivingEntityRenderState {

    /** 0 while hungry, 1 once it has had its larder and swollen up. */
    public float fatten;
}

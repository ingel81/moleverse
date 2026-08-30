package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.predator.Weasel;

/**
 * Renderer for the weasel.
 *
 * <p>No render state of its own, for the same reason the shrew has none: the
 * whole animation is a function of speed and age, and both are already on
 * {@link LivingEntityRenderState}. The prowl and the lunge look different
 * because the animal is moving at different speeds, not because anything is
 * synched to say which it is doing.</p>
 *
 * <p>The shadow is wider than the shrew's by more than the hitbox is, and that
 * is on purpose: the model overhangs its box lengthwise by two thirds, and a
 * shadow cut to the box leaves the ends of a long animal floating.</p>
 */
public class WeaselRenderer extends MobRenderer<Weasel, LivingEntityRenderState, WeaselModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/weasel.png");

    public WeaselRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaselModel(context.bakeLayer(WeaselModel.LAYER)), 0.5F);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}

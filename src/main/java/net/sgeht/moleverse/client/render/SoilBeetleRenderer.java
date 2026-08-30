package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.critter.SoilBeetle;

/**
 * Renderer for the soil beetle.
 *
 * <p>No render state of its own. The gait comes off the distance the animal has
 * covered and the antenna off its age, both of which
 * {@link LivingEntityRenderState} carries - and "is it in the light" is
 * deliberately not asked here, because the beetle's answer to light is to walk
 * out of it rather than to look different in it.</p>
 */
public class SoilBeetleRenderer extends MobRenderer<SoilBeetle, LivingEntityRenderState, SoilBeetleModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/soil_beetle.png");

    public SoilBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new SoilBeetleModel(context.bakeLayer(SoilBeetleModel.LAYER)), 0.25F);
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

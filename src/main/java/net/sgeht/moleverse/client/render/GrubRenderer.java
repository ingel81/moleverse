package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.critter.Grub;

/**
 * Renderer for the grub.
 *
 * <p>The one of the three that needs a render state, because how full a grub is
 * is not derivable from anything the base state carries. See
 * {@link GrubRenderState}.</p>
 */
public class GrubRenderer extends MobRenderer<Grub, GrubRenderState, GrubModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/grub.png");

    public GrubRenderer(EntityRendererProvider.Context context) {
        super(context, new GrubModel(context.bakeLayer(GrubModel.LAYER)), 0.2F);
    }

    @Override
    public GrubRenderState createRenderState() {
        return new GrubRenderState();
    }

    @Override
    public void extractRenderState(Grub entity, GrubRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Interpolated on the entity rather than snapped per frame, so a grub
        // that has just eaten swells over the second and a half it should
        // rather than popping between two ticks.
        state.fatten = entity.getFatten(partialTick);
    }

    @Override
    public Identifier getTextureLocation(GrubRenderState state) {
        return TEXTURE;
    }
}

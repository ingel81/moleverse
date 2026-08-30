package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.predator.Shrew;

/**
 * Renderer for the shrew.
 *
 * <p>No render state of its own. The gait comes off the distance the animal has
 * covered and the sniffing off its age, both of which
 * {@link LivingEntityRenderState} already carries - and "is it about to bite"
 * is deliberately not asked, because the shrew's tell is that it is suddenly
 * closer, not that it looks different.</p>
 */
public class ShrewRenderer extends MobRenderer<Shrew, LivingEntityRenderState, ShrewModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/shrew.png");

    public ShrewRenderer(EntityRendererProvider.Context context) {
        super(context, new ShrewModel(context.bakeLayer(ShrewModel.LAYER)), 0.25F);
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

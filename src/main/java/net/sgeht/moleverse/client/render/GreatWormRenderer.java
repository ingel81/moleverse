package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.GreatWorm;

/**
 * Renderer for the great worm.
 *
 * <p>No render state of its own, unlike {@link MoleRenderer}. The crawl is a
 * function of {@code walkAnimationPos}, {@code walkAnimationSpeed} and
 * {@code ageInTicks}, all three of which {@link LivingEntityRenderState} already
 * carries. A subclass would exist only to hold nothing.</p>
 */
public class GreatWormRenderer extends MobRenderer<GreatWorm, LivingEntityRenderState, GreatWormModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/great_worm.png");

    public GreatWormRenderer(EntityRendererProvider.Context context) {
        // Shadow radius. The worm is four blocks long but only about one and a
        // half wide, and the shadow is a circle: sized to the body, not to the
        // length, because a circle that reaches the tail is a circle that
        // reaches just as far out to the sides.
        super(context, new GreatWormModel(context.bakeLayer(GreatWormModel.LAYER)), 0.8F);
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

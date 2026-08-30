package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.critter.Earthworm;

/**
 * Renderer for the small earthworm.
 *
 * <p>No render state of its own, for the same reason {@link GreatWormRenderer}
 * has none: the crawl is a function of {@code walkAnimationPos},
 * {@code walkAnimationSpeed} and {@code ageInTicks}, all three of which
 * {@link LivingEntityRenderState} already carries. A subclass would exist to
 * hold nothing.</p>
 */
public class EarthwormRenderer extends MobRenderer<Earthworm, LivingEntityRenderState, EarthwormModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/earthworm.png");

    public EarthwormRenderer(EntityRendererProvider.Context context) {
        // Shadow radius, sized to the body rather than to the length: the
        // shadow is a circle, and one that reached the tail would reach just as
        // far out to the sides of an animal a quarter of a block wide.
        super(context, new EarthwormModel(context.bakeLayer(EarthwormModel.LAYER)), 0.15F);
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

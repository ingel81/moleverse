package net.sgeht.moleverse.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.debug.MoleDebug;
import net.sgeht.moleverse.entity.Mole;

/** Renderer for the mole. */
public class MoleRenderer extends MobRenderer<Mole, MoleRenderState, MoleModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/mole.png");

    public MoleRenderer(EntityRendererProvider.Context context) {
        // The last argument is the shadow radius. The mole is low and small.
        super(context, new MoleModel(context.bakeLayer(MoleModel.LAYER)), 0.35F);
    }

    @Override
    public MoleRenderState createRenderState() {
        return new MoleRenderState();
    }

    @Override
    public void extractRenderState(Mole entity, MoleRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (MoleDebug.forcePeek) {
            // Debug aid: hold the pose so a value can be judged without waiting.
            entity.peekAnimationState.startIfStopped(entity.tickCount);
            state.peekAmount = 1.0F;
        } else {
            state.peekAmount = entity.getPeekAmount(partialTick);
        }
        state.peekAnimationState.copyFrom(entity.peekAnimationState);

        state.digAmount = entity.getDigAmount(partialTick);

        // Still read from the tuning values, and deliberately so: every mole in
        // phase 3 dives straight down, and MoleDebug already holds that settled
        // number as its default. Putting a constant on the entity to look
        // per-mole would be a pretence. The angles start differing per mole in
        // 0.3, when a tunnel is carved at an angle rather than dropped into.
        state.digPitchDegrees = MoleDebug.digPitchDegrees;
        state.digYawDegrees = MoleDebug.digYawDegrees;

        state.idleAnimationState.copyFrom(entity.idleAnimationState);
        state.digAnimationState.copyFrom(entity.digAnimationState);
        state.burrowAnimationState.copyFrom(entity.burrowAnimationState);
        state.emergeAnimationState.copyFrom(entity.emergeAnimationState);
    }

    @Override
    public Identifier getTextureLocation(MoleRenderState state) {
        return TEXTURE;
    }
}

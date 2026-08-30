package net.sgeht.moleverse.client.render;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.neoforge.client.entity.animation.json.AnimationHolder;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.debug.MoleDebug;

/**
 * Mole model.
 *
 * <p>The geometry in {@link #createBodyLayer()} is generated: it comes straight
 * from the Blockbench export of {@code art/mole.bbmodel}. Do not edit it by
 * hand, change the model and re-export instead. Only this surrounding class is
 * written by hand, because Blockbench has no export template for this Minecraft
 * version. See {@code docs/MODEL_WORKFLOW.md}.</p>
 */
public class MoleModel extends EntityModel<MoleRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("mole"), "main");

    /**
     * Turns walk speed into the walk cycle's amplitude. Shared with the idle
     * loop, which fades out along the same curve so the two cross over cleanly.
     */
    private static final float WALK_SPEED_SCALE = 2.5F;

    /**
     * How fast the walk cycle runs for a given distance covered.
     *
     * <p>A mole has short legs and takes small, quick steps; at the value the
     * cycle was first written with it looked like it was wading. This is the
     * dial for that - the animation itself is unchanged, only how much of it
     * plays per block travelled.</p>
     */
    private static final float WALK_CYCLE_SPEED = 5.0F;

    private static final AnimationHolder WALK = getAnimation(Moleverse.id("mole_walk"));
    private static final AnimationHolder PEEK = getAnimation(Moleverse.id("mole_peek"));
    private static final AnimationHolder IDLE = getAnimation(Moleverse.id("mole_idle"));
    private static final AnimationHolder DIG = getAnimation(Moleverse.id("mole_dig"));
    private static final AnimationHolder BURROW = getAnimation(Moleverse.id("mole_burrow"));
    private static final AnimationHolder EMERGE = getAnimation(Moleverse.id("mole_emerge"));

    private final KeyframeAnimation walk;
    private final KeyframeAnimation peek;
    private final KeyframeAnimation idle;
    private final KeyframeAnimation dig;
    private final KeyframeAnimation burrow;
    private final KeyframeAnimation emerge;

    /**
     * The bone every other part hangs from. Rotating it aims the whole mole,
     * which is what the rearing pose needs - and what aiming a digging mole in
     * an arbitrary direction will need later.
     */
    private final ModelPart moleRoot;

    public MoleModel(ModelPart root) {
        super(root);
        this.moleRoot = root.getChild("root");
        this.walk = WALK.get().bake(root);
        this.peek = PEEK.get().bake(root);
        this.idle = IDLE.get().bake(root);
        this.dig = DIG.get().bake(root);
        this.burrow = BURROW.get().bake(root);
        this.emerge = EMERGE.get().bake(root);
    }

    @Override
    public void setupAnim(MoleRenderState state) {
        // Resets every part to its rest pose. Everything below is additive.
        super.setupAnim(state);

        this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, WALK_CYCLE_SPEED, WALK_SPEED_SCALE);
        this.applyIdle(state);

        this.dig.apply(state.digAnimationState, state.ageInTicks);
        this.burrow.apply(state.burrowAnimationState, state.ageInTicks);
        this.emerge.apply(state.emergeAnimationState, state.ageInTicks);

        // Secondary motion of the rearing pose: head sweep, snout twitch, paws.
        // The body angle deliberately is not part of this animation.
        this.peek.apply(state.peekAnimationState, state.ageInTicks);

        // Both body angles come last. None of the keyframe animations above has
        // a channel on the root, so the two neither disturb nor are disturbed by
        // anything else - the order between them and the keyframes is free.
        this.applyDigAim(state.digAmount, state.digPitchDegrees, state.digYawDegrees);
        this.applyPeekPose(state.peekAmount);
    }

    /**
     * Breathing, snout twitch, ear flick - the motion that keeps a standing
     * mole from looking like a prop.
     *
     * <p>Faded out by walking speed rather than switched off, along the same
     * curve the walk cycle fades in on, so the two cross over instead of the
     * mole snapping out of a half-taken breath the moment it sets off. The
     * amplitude scale is only reachable through the raw
     * {@link KeyframeAnimation#apply(long, float)}, which is why the time is
     * passed explicitly here.</p>
     */
    private void applyIdle(MoleRenderState state) {
        float amount = 1.0F - Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);
        if (amount <= 0.001F || !state.idleAnimationState.isStarted()) {
            return;
        }

        this.idle.apply(state.idleAnimationState.getTimeInMillis(state.ageInTicks), amount);
    }

    /**
     * Aims the whole mole by a fraction of the given angles.
     *
     * <p>The same reasoning as {@link #applyPeekPose}, and the reason the dig
     * cycle is authored level and direction-neutral: a keyframe channel on the
     * root has to survive three coordinate conversions, and one baked animation
     * per direction would be needed on top of that. As two numbers here, one
     * cycle serves digging straight down, digging at an angle and horizontal
     * tunnelling alike.</p>
     *
     * <p>Yaw is an offset from the body's facing, not a world angle - the model
     * is already in entity space by the time this runs. No positional correction
     * belongs here: the root pivot sits at the hips, so the mole swings its nose
     * into the ground and leaves its hindquarters where they are.</p>
     *
     * @param amount 0 while level, 1 while fully aimed
     * @param pitchDegrees positive lowers the nose, 90 is straight down
     * @param yawDegrees deviation from the body's facing
     */
    private void applyDigAim(float amount, float pitchDegrees, float yawDegrees) {
        if (amount <= 0.001F) {
            return;
        }

        this.moleRoot.xRot += amount * (float) Math.toRadians(pitchDegrees);
        this.moleRoot.yRot += amount * (float) Math.toRadians(yawDegrees);
    }

    /**
     * Tips the whole mole back by a fraction of the configured angle.
     *
     * <p>Kept out of the keyframe animation on purpose. A keyframe channel on
     * the root has to survive three separate coordinate conversions between
     * Blockbench and the game, which makes it near impossible to reason about.
     * Here the numbers mean exactly what they say, in model space, and they come
     * from {@link MoleDebug}, so they can be tuned with /moleverse peek while
     * looking straight at the mole.</p>
     *
     * @param amount 0 while crawling, 1 while fully reared up
     */
    private void applyPeekPose(float amount) {
        if (amount <= 0.001F) {
            return;
        }

        this.moleRoot.xRot += amount * (float) Math.toRadians(MoleDebug.peekPitchDegrees);
        this.moleRoot.y += amount * MoleDebug.peekOffsetY;
        this.moleRoot.z += amount * MoleDebug.peekOffsetZ;
    }

    // --- generated by Blockbench, do not edit ------------------------------
    // The root pivot sits at the hips, not at the centre of the body. Rearing
    // up rotates around that point, which is what keeps the mole in place
    // instead of swinging it through the ground.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 22.0F, 4.5F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -3.0F, -1.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 12).addBox(-3.0F, -2.5F, -5.0F, 6.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -3.0F, -3.5F));

        body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(52, 15).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 4.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(20, 12).addBox(-2.5F, -1.0F, -4.0F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -4.0F, -8.5F));

        head.addOrReplaceChild("snout", CubeListBuilder.create()
                .texOffs(38, 12).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(52, 12).addBox(-1.0F, 0.5F, -5.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -3.0F));

        PartDefinition frontLegRight = root.addOrReplaceChild("front_leg_right", CubeListBuilder.create()
                .texOffs(0, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -4.0F, -6.5F));

        frontLegRight.addOrReplaceChild("front_paw_right", CubeListBuilder.create()
                .texOffs(26, 0).addBox(-3.0F, 0.0F, -3.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(26, 7).addBox(0.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(32, 7).addBox(-1.5F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(38, 7).addBox(-3.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.3142F, -0.2094F));

        PartDefinition frontLegLeft = root.addOrReplaceChild("front_leg_left", CubeListBuilder.create()
                .texOffs(8, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -4.0F, -6.5F));

        frontLegLeft.addOrReplaceChild("front_paw_left", CubeListBuilder.create()
                .texOffs(44, 0).addBox(-1.0F, 0.0F, -3.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(44, 7).addBox(-1.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(50, 7).addBox(0.5F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(56, 7).addBox(2.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, -0.3142F, 0.2094F));

        root.addOrReplaceChild("hind_leg_right", CubeListBuilder.create()
                .texOffs(16, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(32, 21).addBox(-1.0F, 3.0F, -1.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -2.0F, -0.5F));

        root.addOrReplaceChild("hind_leg_left", CubeListBuilder.create()
                .texOffs(24, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(42, 21).addBox(-1.0F, 3.0F, -1.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -2.0F, -0.5F));

        return LayerDefinition.create(meshdefinition, 64, 32);
    }
}

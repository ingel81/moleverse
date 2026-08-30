package net.sgeht.moleverse.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.sgeht.moleverse.Moleverse;

/**
 * Earthworm model: the great worm's crawl, eighteen segments long and thin.
 *
 * <p>Same mechanism and deliberately so - see {@link GreatWormModel} for the
 * long version of why peristalsis is arithmetic and not keyframes, and why the
 * segments are siblings rather than a chain. The short version is that a worm
 * has no legs, so there is no step to key; what it has is a wave of contraction
 * running head to tail, which is a sine with one phase offset per segment.</p>
 *
 * <p>Eighteen segments, and the count is the point. The five segment worm was
 * judged in game as too short, twice: under three times as long as wide, a
 * dropped sausage, where a real earthworm runs ten. This one is thirty-nine
 * units against four wide, and the length comes from more joints rather than
 * deeper boxes because the chain only improves with count - the lateral S
 * keeps one period over the whole body ({@link #LATERAL_PHASE} divides by the
 * count), so the yaw per joint <em>falls</em> as segments are added and the
 * curve gets smoother, never springier. The segments are siblings, so nothing
 * compounds down the line either way.</p>
 *
 * <p>The amplitudes are not the great worm's. Every offset here is in model
 * units and this animal is a third of that one's length, so lifts and sways
 * scale down with it - but not proportionally: a small worm wriggles harder
 * than a large one. The scale-based effects (bulge, contraction) are unitless
 * and slightly larger than the great worm's outright, because on a three unit
 * segment a five percent swell is invisible.</p>
 *
 * <p>The geometry in {@link #createBodyLayer()} is the Blockbench export of
 * {@code art/earthworm.bbmodel}, whose cubes and UV packing come in turn from
 * {@code art/generators/critter_shapes.py} - the girth profile is literally the
 * great worm's function (variant C of the worm sheet), sampled eighteen times,
 * with the prostomium, clitellum saddle and tail paddle riding their segments'
 * bones. Do not edit it by hand. Only this surrounding class is written by
 * hand, because Blockbench has no export template for this Minecraft version;
 * see {@code docs/MODEL_WORKFLOW.md}.</p>
 */
public class EarthwormModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("earthworm"), "main");

    /** Head first, tail last. The wave runs down this array in order. */
    private static final String[] SEGMENTS = {
            "head", "body1", "body2", "body3", "body4", "body5", "body6",
            "body7", "body8", "body9", "body10", "body11", "body12", "body13",
            "body14", "body15", "body16", "tail",
    };

    /** Distance between two segment pivots. Mirrors {@code WORM_PITCH} in the generator. */
    private static final float PITCH = 2.0F;

    /**
     * Phase difference between one segment and the next.
     *
     * <p>An eighth of a turn, half the great worm's quarter: a contraction
     * wave spans eight of these short segments, so a little over two waves
     * ride the body at once - the same on-screen picture the great worm's
     * eight segments give, kept deliberately as the count tripled. At the old
     * quarter turn the eighteen segment body carried four and a half waves
     * and shimmered rather than crawled.</p>
     */
    private static final float SEGMENT_PHASE = Mth.TWO_PI / 8.0F;

    /** The same for the lateral wave, at half the frequency: one gentle S over the body. */
    private static final float LATERAL_PHASE = Mth.TWO_PI / SEGMENTS.length;

    /**
     * Radians of wave per unit of {@code walkAnimationPos}.
     *
     * <p>Higher than the great worm's 1.8. Driving the phase off distance
     * travelled is what keeps the contraction from sliding along a worm that
     * has stopped, and a short animal covers less ground per wave, so it needs
     * more wave per block to look like it is pulling itself along.</p>
     */
    private static final float WAVE_SPEED = 2.4F;

    /** Radians of wave per tick on top of that, which is the whole idle animation. */
    private static final float IDLE_WAVE_SPEED = 0.14F;

    /** Turns walk speed into amplitude. Saturates at about the speed it crawls at. */
    private static final float WALK_SPEED_SCALE = 4.0F;

    /** How much of the full amplitude a standing worm keeps. */
    private static final float IDLE_AMOUNT = 0.35F;

    /** How far a segment swells where it contracts, and how far it shortens. */
    private static final float BULGE = 0.10F;
    private static final float CONTRACT = 0.13F;

    /** How far the stretched part of the wave lifts and reaches, in model units. */
    private static final float LIFT = 0.30F;
    private static final float SLIDE = 0.25F;

    /**
     * Half-width of the lateral S, in model units.
     *
     * <p>Raised from the short worm's 0.7: a thirty-nine unit body can wind
     * a unit off its line and still read as one animal. The yaw each joint
     * takes to follow the curve still <em>fell</em> with the change, from
     * 0.44 radians to 0.17, because {@link #segmentYaw} divides the phase
     * step by the segment count - which is the checked property that keeps a
     * longer chain smoother rather than springier.</p>
     */
    private static final float SWAY = 1.0F;

    private final ModelPart[] segments;

    public EarthwormModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("root");
        this.segments = new ModelPart[SEGMENTS.length];
        for (int i = 0; i < SEGMENTS.length; i++) {
            this.segments[i] = body.getChild(SEGMENTS[i]);
        }
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // Resets every segment to its rest pose, scales included. Everything
        // below is additive on top of that.
        super.setupAnim(state);

        float phase = state.walkAnimationPos * WAVE_SPEED + state.ageInTicks * IDLE_WAVE_SPEED;
        float moving = Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);
        float amount = IDLE_AMOUNT + (1.0F - IDLE_AMOUNT) * moving;

        for (int i = 0; i < this.segments.length; i++) {
            ModelPart segment = this.segments[i];

            // Positive is the contracted half of the wave: short, thick and
            // planted. Negative is the stretched half, which is also the half
            // that lifts and reaches - that one-sidedness is the mechanism by
            // which a worm actually moves, and the reason this is not a bob.
            float squeeze = Mth.sin(phase - i * SEGMENT_PHASE);
            float stretched = Math.max(0.0F, -squeeze);
            segment.xScale += amount * BULGE * squeeze;
            segment.yScale += amount * BULGE * squeeze;
            segment.zScale -= amount * CONTRACT * squeeze;
            // Negative y is up: the boxes hang from the floor at y = 0.
            segment.y -= moving * LIFT * stretched;
            segment.z -= moving * SLIDE * stretched;

            // The lateral S, only while it is going somewhere. A resting worm
            // keeps working slowly but does not steer.
            float lateral = 0.5F * phase - i * LATERAL_PHASE;
            segment.x += moving * SWAY * Mth.sin(lateral);
            segment.yRot += segmentYaw(moving, lateral);
        }
    }

    /**
     * The angle that keeps a segment pointing along the curve its neighbours
     * put it on.
     *
     * <p>The centres follow {@code x = a·sin(θ)} with {@code θ} falling by
     * {@link #LATERAL_PHASE} every {@link #PITCH} units of body, so the slope is
     * {@code -a·(LATERAL_PHASE/PITCH)·cos(θ)} and at these angles the yaw is the
     * slope. Without it the boxes stay square to the axis while their centres
     * move, which reads as a stack sliding sideways rather than a body
     * bending.</p>
     */
    private static float segmentYaw(float moving, float lateral) {
        return -moving * SWAY * (LATERAL_PHASE / PITCH) * Mth.cos(lateral);
    }

    // --- Blockbench export of art/earthworm.bbmodel, do not edit -----------
    // Each segment is its own bone with its pivot on the floor at its own
    // centre, so a vertical swell grows upwards out of the ground instead of
    // sinking through it and a lengthwise pulse stays centred. The boxes are
    // three units deep on a two unit pitch: the one unit of overlap is what
    // stops a contracting segment from tearing a gap open. Variant C's
    // features ride their segments' bones as second boxes: the prostomium on
    // the head, the clitellum saddle - half a unit proud each side and a unit
    // above, saddle-painted so its underside stays skin - on the segment a
    // third of the way back, and the flat tail paddle on the last.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot()
                .addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(12, 17).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 3.0F)
                .texOffs(56, 0).addBox(-1.0F, -2.5F, -2.5F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, -17.0F));

        root.addOrReplaceChild("body1", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -15.0F));

        root.addOrReplaceChild("body2", CubeListBuilder.create()
                .texOffs(12, 12).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -13.0F));

        root.addOrReplaceChild("body3", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -11.0F));

        root.addOrReplaceChild("body4", CubeListBuilder.create()
                .texOffs(14, 0).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -9.0F));

        root.addOrReplaceChild("body5", CubeListBuilder.create()
                .texOffs(28, 0).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F)
                .texOffs(0, 17).addBox(-2.5F, -4.0F, -0.5F, 5.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, -7.0F));

        root.addOrReplaceChild("body6", CubeListBuilder.create()
                .texOffs(42, 0).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -5.0F));

        root.addOrReplaceChild("body7", CubeListBuilder.create()
                .texOffs(0, 6).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -3.0F));

        root.addOrReplaceChild("body8", CubeListBuilder.create()
                .texOffs(14, 6).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -1.0F));

        root.addOrReplaceChild("body9", CubeListBuilder.create()
                .texOffs(28, 6).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 1.0F));

        root.addOrReplaceChild("body10", CubeListBuilder.create()
                .texOffs(42, 6).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 3.0F));

        root.addOrReplaceChild("body11", CubeListBuilder.create()
                .texOffs(24, 12).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 5.0F));

        root.addOrReplaceChild("body12", CubeListBuilder.create()
                .texOffs(36, 12).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 7.0F));

        root.addOrReplaceChild("body13", CubeListBuilder.create()
                .texOffs(48, 12).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 9.0F));

        root.addOrReplaceChild("body14", CubeListBuilder.create()
                .texOffs(22, 17).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 11.0F));

        root.addOrReplaceChild("body15", CubeListBuilder.create()
                .texOffs(32, 17).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 13.0F));

        root.addOrReplaceChild("body16", CubeListBuilder.create()
                .texOffs(42, 17).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 15.0F));

        root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(52, 17).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 3.0F)
                .texOffs(0, 22).addBox(-2.0F, -1.0F, 0.5F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 17.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}

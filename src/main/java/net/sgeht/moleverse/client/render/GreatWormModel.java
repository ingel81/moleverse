package net.sgeht.moleverse.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.sgeht.moleverse.Moleverse;

/**
 * Great worm model: eight segments and a crawl that is a formula, not keyframes.
 *
 * <p>The geometry in {@link #createBodyLayer()} is generated - it comes from the
 * Blockbench export of {@code art/great_worm.bbmodel}, whose girth profile and
 * UV packing in turn come from {@code art/generators/great_worm_shape.py}. Do
 * not edit it by hand. Only this surrounding class is written by hand, because
 * Blockbench has no export template for this Minecraft version. See
 * {@code docs/MODEL_WORKFLOW.md}.</p>
 *
 * <h2>Why there is no animation JSON</h2>
 *
 * <p>A worm has no legs, so there is no step to key. What it has is peristalsis:
 * one wave of contraction after another running from the mouth to the tail, each
 * segment doing the same thing a fixed fraction of a cycle after the one in
 * front of it. That is a sine with a phase offset per segment, and as eight
 * lines of arithmetic it is exact, loops seamlessly, and stays locked to the
 * distance the animal has actually covered - a keyframed version would have to
 * hit eight bones at four poses each and would still slip against the ground the
 * moment the worm walked at any speed but the one it was authored at.</p>
 *
 * <h2>Why the segments are siblings and not a chain</h2>
 *
 * <p>Every segment hangs off {@code root} directly. Chaining them head to tail
 * would be the obvious way to bend a body, but then each rotation compounds down
 * the whole line and the tail whips; keeping the amplitude small enough to stop
 * that leaves the front of the animal barely moving. As siblings, each segment's
 * lateral offset is read straight off the wave, the shape of the body <em>is</em>
 * the wave, and {@link #segmentYaw} only has to turn each segment to the local
 * tangent so the boxes meet along the curve instead of shearing past each
 * other.</p>
 */
public class GreatWormModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("great_worm"), "main");

    /** Head first, tail last. The wave runs down this array in order. */
    private static final String[] SEGMENTS = {
            "head", "body1", "body2", "body3", "body4", "body5", "body6", "tail",
    };

    /**
     * Distance between two segment pivots, in model units. Mirrors {@code PITCH}
     * in {@code art/generators/great_worm_shape.py}; it is needed here to turn a
     * lateral offset into the angle that matches it.
     */
    private static final float PITCH = 7.0F;

    /**
     * Phase difference between one segment and the next, in radians. A quarter
     * turn puts a full contraction wave across four segments, so two of them are
     * visible on the body at once - which is what a crawling earthworm looks
     * like. Larger and the whole animal pulses as one; smaller and the segments
     * alternate and it reads as a caterpillar.
     */
    private static final float SEGMENT_PHASE = Mth.HALF_PI;

    /** The same for the lateral wave, at half the frequency: one gentle S over the body. */
    private static final float LATERAL_PHASE = Mth.TWO_PI / SEGMENTS.length;

    /**
     * Radians of wave per unit of {@code walkAnimationPos}.
     *
     * <p>Driving the phase off distance travelled rather than off time is what
     * keeps the wave from sliding along a worm that has stopped: {@code
     * walkAnimationPos} advances with the ground covered, so the contraction
     * that carries the animal forward stays put relative to the floor.</p>
     */
    private static final float WAVE_SPEED = 1.8F;

    /**
     * Radians of wave per tick, on top of the above.
     *
     * <p>This is the whole idle animation. A standing worm is not still - it
     * keeps working slowly - and adding a small time term to the same phase
     * gives that for nothing, with no second animation to cross-fade into and no
     * seam where one takes over from the other. One full pass down the body
     * takes about six seconds at rest.</p>
     */
    private static final float IDLE_WAVE_SPEED = 0.1F;

    /** Turns walk speed into the crawl's amplitude. Saturates at about the speed it strolls at. */
    private static final float WALK_SPEED_SCALE = 4.0F;

    /** How much of the full amplitude a standing worm keeps. */
    private static final float IDLE_AMOUNT = 0.3F;

    /** How far a segment swells sideways and upwards where it contracts. */
    private static final float BULGE = 0.07F;

    /** How far it shortens along the body at the same time. Bounded by the two units the boxes overlap. */
    private static final float CONTRACT = 0.10F;

    /** How far the stretched, thin part of the wave lifts off the floor, in model units. */
    private static final float LIFT = 1.1F;

    /** How far it reaches forward while it is lifted. */
    private static final float SLIDE = 0.7F;

    /** Half-width of the lateral S, in model units. The body is 22 wide, so this stays a suggestion. */
    private static final float SWAY = 2.2F;

    private final ModelPart[] segments;

    public GreatWormModel(ModelPart root) {
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

            // Peristalsis. Positive is the contracted half of the wave: short,
            // thick and planted. Negative is the stretched half, which is also
            // the half that lifts and reaches - that is the whole mechanism by
            // which a worm moves, and the reason the lift is one-sided rather
            // than a symmetric bob.
            float squeeze = Mth.sin(phase - i * SEGMENT_PHASE);
            float stretched = Math.max(0.0F, -squeeze);
            segment.xScale += amount * BULGE * squeeze;
            segment.yScale += amount * BULGE * squeeze;
            segment.zScale -= amount * CONTRACT * squeeze;
            // Negative y is up: the boxes hang from the floor at y = 0 down to
            // -height, because the Blockbench exporter flips the axis.
            segment.y -= moving * LIFT * stretched;
            segment.z -= moving * SLIDE * stretched;

            // The lateral S, at rest only while the worm is standing still. A
            // resting worm keeps breathing but does not steer.
            float lateral = 0.5F * phase - i * LATERAL_PHASE;
            segment.x += moving * SWAY * Mth.sin(lateral);
            segment.yRot += segmentYaw(moving, lateral);
        }
    }

    /**
     * The angle that keeps a segment pointing along the curve its neighbours put
     * it on.
     *
     * <p>The centres follow {@code x = a·sin(θ)} with {@code θ} falling by
     * {@link #LATERAL_PHASE} every {@link #PITCH} units of body, so the slope of
     * that line is {@code dx/dz = -a·(LATERAL_PHASE/PITCH)·cos(θ)}, and for the
     * small angles involved the yaw is the slope. Without it the segments stay
     * square to the axis while their centres move, which reads as a stack of
     * boxes sliding sideways rather than as a body bending.</p>
     */
    private static float segmentYaw(float moving, float lateral) {
        return -moving * SWAY * (LATERAL_PHASE / PITCH) * Mth.cos(lateral);
    }

    // --- generated by Blockbench, do not edit ------------------------------
    // Each segment is its own bone with its pivot on the floor at its own
    // centre, so a vertical swell grows upwards out of the ground instead of
    // sinking through it, and a lengthwise pulse stays centred on the segment.
    // The boxes are nine units long on a seven unit pitch: the two units of
    // overlap are what stops a contracting segment from tearing a gap open.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(54, 53).addBox(-7.0F, -12.0F, -4.5F, 14.0F, 12.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(80, 76).addBox(-4.0F, -9.0F, -8.5F, 8.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -24.5F));

        root.addOrReplaceChild("body1", CubeListBuilder.create()
                .texOffs(62, 27).addBox(-10.0F, -16.0F, -4.5F, 20.0F, 16.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -17.5F));

        root.addOrReplaceChild("body2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-11.0F, -18.0F, -4.5F, 22.0F, 18.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -10.5F));

        root.addOrReplaceChild("body3", CubeListBuilder.create()
                .texOffs(62, 0).addBox(-11.0F, -18.0F, -4.5F, 22.0F, 18.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -3.5F));

        root.addOrReplaceChild("body4", CubeListBuilder.create()
                .texOffs(0, 27).addBox(-11.0F, -17.0F, -4.5F, 22.0F, 17.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 3.5F));

        root.addOrReplaceChild("body5", CubeListBuilder.create()
                .texOffs(0, 53).addBox(-9.0F, -14.0F, -4.5F, 18.0F, 14.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 10.5F));

        root.addOrReplaceChild("body6", CubeListBuilder.create()
                .texOffs(0, 76).addBox(-6.0F, -10.0F, -4.5F, 12.0F, 10.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 17.5F));

        root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(42, 76).addBox(-5.0F, -9.0F, -4.5F, 10.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, 95).addBox(-4.0F, -6.0F, 3.5F, 8.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 24.5F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}

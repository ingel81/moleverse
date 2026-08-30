package net.sgeht.moleverse.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.sgeht.moleverse.Moleverse;

/**
 * Grub model: a slow pulse along a body that is already bent.
 *
 * <p>The bow is not animated. It is in the rest pose, computed by
 * {@code art/generators/critter_shapes.py} as an offset and a matching angle
 * per segment and carried into the model through
 * {@code art/grub.bbmodel}, because a grub is bent the same way all the time and a posture
 * that never changes is geometry rather than animation. What
 * {@link #setupAnim} adds is the pulse, and the two simply superpose: the bow
 * is the constant term of the same lateral field the wave is the varying term
 * of. This is the split the project's "poses: code, not keyframes" note asks
 * for, taken one step further - a pose with no blend factor at all does not
 * even need to be code.</p>
 *
 * <h2>The swelling</h2>
 *
 * <p>A fed grub is fatter, and only in the middle. Scaling the whole animal up
 * would move the head away from the body and lift the tail off the floor - the
 * segments are siblings on the floor, not a chain - and it would read as the
 * game drawing the same grub larger rather than as this grub having eaten. The
 * two middle segments take almost all of it and the ends take a token amount,
 * which is where a larva actually stores a meal.</p>
 *
 * <p>The pulse gets slower as it fills, too. That is one multiply and it is the
 * difference between full and merely bigger.</p>
 */
public class GrubModel extends EntityModel<GrubRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("grub"), "main");

    /** Head first, tail last. The head capsule rides on the first of these. */
    private static final String[] SEGMENTS = {
            "body0", "body1", "body2", "body3", "body4", "body5",
    };

    /**
     * Distance between two segment pivots. Mirrors {@code GRUB_PITCH} in the
     * generator - widened from two after the ring pass came out crumpled in
     * game: the fat silhouette has to come from girth, not from compression.
     */
    private static final float PITCH = 3.0F;

    /**
     * Phase difference between one segment and the next.
     *
     * <p>A sixth of a turn: one contraction wave stretched across the six
     * segment body. On a grub the single slow wave is the point - anything
     * shorter reads as shivering, and a fat larva does not shiver, it
     * heaves.</p>
     */
    private static final float SEGMENT_PHASE = Mth.TWO_PI / 6.0F;

    private static final float LATERAL_PHASE = Mth.TWO_PI / SEGMENTS.length;

    /** Radians of wave per unit covered, and per tick on top of it. */
    private static final float WAVE_SPEED = 2.0F;
    private static final float IDLE_WAVE_SPEED = 0.09F;

    /** Turns walk speed into amplitude. */
    private static final float WALK_SPEED_SCALE = 5.0F;

    /** How much of the full amplitude a resting grub keeps. It never quite stops. */
    private static final float IDLE_AMOUNT = 0.45F;

    /** How far a segment swells and shortens on the contracted half of the wave. */
    private static final float BULGE = 0.08F;
    private static final float CONTRACT = 0.10F;

    /** How far it heaves itself along, in model units. */
    private static final float LIFT = 0.25F;
    private static final float SLIDE = 0.20F;

    /** How far the body waddles sideways. A grub does not steer so much as slop. */
    private static final float SWAY = 0.5F;

    /** How much wider and taller the middle of a full grub is, and the ends. */
    private static final float FAT_MIDDLE = 0.28F;
    private static final float FAT_ENDS = 0.10F;

    /** How much a full grub slows down. */
    private static final float FAT_TORPOR = 0.35F;

    private final ModelPart[] segments;

    public GrubModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("root");
        this.segments = new ModelPart[SEGMENTS.length];
        for (int i = 0; i < SEGMENTS.length; i++) {
            this.segments[i] = body.getChild(SEGMENTS[i]);
        }
    }

    @Override
    public void setupAnim(GrubRenderState state) {
        // Restores the bow along with everything else, so the wave below adds
        // to the resting shape rather than replacing it.
        super.setupAnim(state);

        float fat = Mth.clamp(state.fatten, 0.0F, 1.0F);
        float torpor = 1.0F - FAT_TORPOR * fat;

        float phase = (state.walkAnimationPos * WAVE_SPEED + state.ageInTicks * IDLE_WAVE_SPEED) * torpor;
        float moving = Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);
        float amount = IDLE_AMOUNT + (1.0F - IDLE_AMOUNT) * moving;

        for (int i = 0; i < this.segments.length; i++) {
            ModelPart segment = this.segments[i];

            // The two inner segments carry the meal; the ends only hint at it.
            boolean middle = i == 2 || i == 3;
            float swell = fat * (middle ? FAT_MIDDLE : FAT_ENDS);
            segment.xScale += swell;
            segment.yScale += swell;

            float squeeze = Mth.sin(phase - i * SEGMENT_PHASE);
            float stretched = Math.max(0.0F, -squeeze);
            segment.xScale += amount * BULGE * squeeze;
            segment.yScale += amount * BULGE * squeeze;
            segment.zScale -= amount * CONTRACT * squeeze;
            segment.y -= moving * LIFT * stretched;
            segment.z -= moving * SLIDE * stretched;

            float lateral = 0.5F * phase - i * LATERAL_PHASE;
            segment.x += moving * SWAY * Mth.sin(lateral);
            segment.yRot -= moving * SWAY * (LATERAL_PHASE / PITCH) * Mth.cos(lateral);
        }
    }

    // --- Blockbench export of art/grub.bbmodel, do not edit ----------------
    // Rebuild-grade pass: six segments on a three unit pitch, bowed into the
    // C a curl-grub is named for (GRUB_CURL 1.1 - five joints make the same C
    // cost only twelve degrees at the steepest one). Each segment carries its
    // ring on its own bone; the amber head capsule is two boxes (dome and
    // face-plate) on body0, and the six folded true legs ride the front two
    // segments' bones as capsule-toned stubs - the one anatomical mark that
    // separates a chafer grub from a maggot. The bow leans the other way in
    // the .bbmodel; the exporter mirrors X and nobody knows which way a grub
    // curls.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot()
                .addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("body0", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-2.5F, -5.0F, -2.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(36, 19).addBox(-3.0F, -5.5F, -1.0F, 6.0F, 4.0F, 2.0F)
                .texOffs(52, 10).addBox(-1.5F, -4.0F, -4.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(58, 0).addBox(-1.0F, -3.0F, -4.5F, 2.0F, 2.0F, 1.0F)
                .texOffs(52, 19).addBox(1.5F, -1.2F, -1.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(56, 19).addBox(1.5F, -1.2F, 0.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(60, 19).addBox(-2.5F, -1.2F, -1.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(46, 26).addBox(-2.5F, -1.2F, 0.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -7.5F, 0.0000F, 0.2123F, 0.0000F));

        root.addOrReplaceChild("body1", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-2.5F, -5.0F, -2.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(0, 26).addBox(-3.0F, -5.5F, -1.0F, 6.0F, 4.0F, 2.0F)
                .texOffs(50, 26).addBox(1.5F, -1.2F, -1.0F, 1.0F, 1.0F, 1.0F)
                .texOffs(54, 26).addBox(-2.5F, -1.2F, -1.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(0.6F, 0.0F, -4.5F, 0.0000F, 0.1726F, 0.0000F));

        root.addOrReplaceChild("body2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -6.0F, -2.0F, 6.0F, 6.0F, 4.0F)
                .texOffs(0, 19).addBox(-3.5F, -6.5F, -1.0F, 7.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(1.0F, 0.0F, -1.5F, 0.0000F, 0.0665F, 0.0000F));

        root.addOrReplaceChild("body3", CubeListBuilder.create()
                .texOffs(20, 0).addBox(-3.0F, -6.0F, -2.0F, 6.0F, 6.0F, 4.0F)
                .texOffs(18, 19).addBox(-3.5F, -6.5F, -1.0F, 7.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(1.0F, 0.0F, 1.5F, 0.0000F, -0.0665F, 0.0000F));

        root.addOrReplaceChild("body4", CubeListBuilder.create()
                .texOffs(18, 10).addBox(-2.5F, -5.0F, -2.0F, 5.0F, 5.0F, 4.0F)
                .texOffs(16, 26).addBox(-3.0F, -5.5F, -1.0F, 6.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.6F, 0.0F, 4.5F, 0.0000F, -0.1726F, 0.0000F));

        root.addOrReplaceChild("body5", CubeListBuilder.create()
                .texOffs(36, 10).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(32, 26).addBox(-2.5F, -4.5F, -1.0F, 5.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.5F, 0.0000F, -0.2123F, 0.0000F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}

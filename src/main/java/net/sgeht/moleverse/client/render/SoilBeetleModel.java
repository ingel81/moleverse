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
 * Soil beetle model: a tripod gait and an antenna that flicks.
 *
 * <p>Six legs, and the only arrangement of them that reads as an insect is the
 * alternating tripod: front-left, middle-right and rear-left swing together
 * while the other three stand, then they swap. Any other pairing looks like a
 * mammal with too many legs. That is the whole of {@link #TRIPOD} - one sign
 * per leg, and the gait falls out of it.</p>
 *
 * <h2>Why the legs swing rather than step</h2>
 *
 * <p>They rotate about Y, sweeping fore and aft, with only a slight lift. A
 * beetle here is four tenths of a block long and the player is four times its
 * height, so it is always seen from above - and from above a leg's whole motion
 * is the sweep. A proper lift would also need somewhere to lift to, and there
 * are two units of clearance under the shell.</p>
 *
 * <p>The lift that is there rotates about Z and not X, which is worth stating
 * because the obvious guess is wrong: the legs lie along the X axis, so an X
 * rotation spins each one about its own length and moves nothing visible. Z is
 * the axis that swings the far end up. Its sign is opposite on the two sides -
 * the left legs reach towards model +X and the right ones towards -X, and the
 * same rotation therefore sends them opposite ways.</p>
 *
 * <h2>Why the antenna does not use a plain sine</h2>
 *
 * <p>A sine sways. An antenna flicks: still for most of a second, then a fast
 * sweep and back. {@link #twitch} is a sine raised to an odd power, which keeps
 * the sign and the smooth loop while spending most of the cycle near zero and
 * the rest of it near the extremes. It costs one multiply more than the sine it
 * replaces and it is the difference between an insect and a wind chime.</p>
 *
 * <p>Geometry is the Blockbench export of {@code art/soil_beetle.bbmodel},
 * whose cubes and UV packing come from {@code art/generators/critter_shapes.py}.
 * Do not edit it by hand.</p>
 */
public class SoilBeetleModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("soil_beetle"), "main");

    private static final String[] LEGS = {
            "leg_l0", "leg_l1", "leg_l2", "leg_r0", "leg_r1", "leg_r2",
    };

    /**
     * Which of the two tripods each leg belongs to, in {@link #LEGS} order.
     *
     * <p>Left front, left middle, left rear, then the same on the right. The
     * alternation down each side and the opposition across the animal is the
     * whole gait.</p>
     */
    private static final float[] TRIPOD = {1.0F, -1.0F, 1.0F, -1.0F, 1.0F, -1.0F};

    /** Which side each leg is on: +1 reaches towards model +X. */
    private static final float[] SIDE = {1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F};

    /** Radians of gait per unit of distance covered. */
    private static final float STEP_SPEED = 1.6F;

    /**
     * How far a leg sweeps fore and aft, and how far the swinging half lifts.
     *
     * <p>The swing shrank from 0.55 after an in-game screenshot showed the
     * six legs tangling under the body. At twenty degrees on top of the
     * resting splay, a tip swings about 1.4 units fore and aft - and the
     * sockets sit two and a half apart, so counter-phased neighbours can no
     * longer meet. The geometry closed the other half of the tangle: the
     * elbow now sits a unit past the shell's rim and the tibia stands wholly
     * outside it, so no part of a leg can reach the midline at any phase.</p>
     */
    private static final float LEG_SWING = 0.35F;
    private static final float LEG_LIFT = 0.22F;

    /** How far the shell rocks and rises, in model units, at twice the step rate. */
    private static final float BODY_BOB = 0.22F;
    private static final float BODY_ROLL = 0.07F;

    /** Turns walk speed into gait amplitude. */
    private static final float WALK_SPEED_SCALE = 3.5F;

    /** Radians of antenna cycle per tick. One sweep every four seconds or so. */
    private static final float ANTENNA_SPEED = 0.08F;

    /** How far a feeler swings, and how far the head turns with it. */
    private static final float ANTENNA_SWING = 0.45F;
    private static final float HEAD_TURN = 0.12F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart[] legs;
    private final ModelPart antennaLeft;
    private final ModelPart antennaRight;

    public SoilBeetleModel(ModelPart root) {
        super(root);
        ModelPart parts = root.getChild("root");
        this.body = parts.getChild("body");
        this.head = parts.getChild("head");
        this.legs = new ModelPart[LEGS.length];
        for (int i = 0; i < LEGS.length; i++) {
            this.legs[i] = parts.getChild(LEGS[i]);
        }
        this.antennaLeft = parts.getChild("antenna_l");
        this.antennaRight = parts.getChild("antenna_r");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);

        float gait = state.walkAnimationPos * STEP_SPEED;
        float moving = Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);

        for (int i = 0; i < this.legs.length; i++) {
            ModelPart leg = this.legs[i];
            float swing = Mth.sin(gait) * TRIPOD[i];
            leg.yRot += moving * LEG_SWING * swing;
            // Only the half of the cycle that is swinging forward comes off the
            // ground. A leg that rises and falls symmetrically is a leg that
            // never pushes against anything.
            float lift = Math.max(0.0F, swing);
            leg.zRot -= SIDE[i] * moving * LEG_LIFT * lift;
        }

        // Twice the leg rate, because both tripods plant once per cycle. The
        // roll is what stops the bob from reading as a hop.
        this.body.y -= moving * BODY_BOB * Mth.abs(Mth.sin(gait));
        this.body.zRot += moving * BODY_ROLL * Mth.sin(gait);

        float feeler = twitch(state.ageInTicks * ANTENNA_SPEED);
        this.antennaLeft.yRot += ANTENNA_SWING * feeler;
        // A quarter cycle behind, so the two never sweep as a pair - which is
        // the thing that would make them read as horns rather than as feelers.
        this.antennaRight.yRot -= ANTENNA_SWING * twitch(state.ageInTicks * ANTENNA_SPEED + Mth.HALF_PI);
        this.head.yRot += HEAD_TURN * feeler;
    }

    /**
     * A sine that spends most of its time at rest.
     *
     * <p>Fifth power, so the sign and the seamless loop survive but the middle
     * of the range is flattened towards zero and only the peaks are reached.
     * See the class note for why a plain sine is the wrong shape here.</p>
     */
    private static float twitch(float phase) {
        float s = Mth.sin(phase);
        float squared = s * s;
        return s * squared * squared;
    }

    // --- Blockbench export of art/soil_beetle.bbmodel, do not edit ---------
    // Rebuild-grade pass: the shell carries a raised pronotum shield over its
    // front third and two overhanging elytra behind it with the suture groove
    // between them - the pronotum/elytra break as real geometry. The head is
    // small, tucked under the shield's edge, with a mandible either side.
    // Each leg is three segments on one bone (coxa out-and-up past the rim,
    // tibia down outside the shell, tarsus foot-box lying forward on the
    // ground), and the three hips span the whole flank - 6.5 units front to
    // rear, after the clustered version read as bunched in game. Each feeler
    // is scape, kinked flagellum and club on its bone.
    //
    // The l and r bones sit where their names say they do in Java, which means
    // they were authored the other way round - the exporter mirrors X. See
    // docs/MODEL_WORKFLOW.md step 4.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot()
                .addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -3.0F, -4.0F, 7.0F, 3.0F, 8.0F)
                .texOffs(18, 11).addBox(-3.0F, -4.5F, -4.0F, 6.0F, 2.0F, 3.0F)
                .texOffs(30, 0).addBox(0.5F, -4.5F, -1.0F, 3.0F, 2.0F, 6.0F)
                .texOffs(0, 11).addBox(-3.5F, -4.5F, -1.0F, 3.0F, 2.0F, 6.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-1.5F, -2.5F, -2.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(8, 19).addBox(0.2F, -1.5F, -3.5F, 1.0F, 1.0F, 2.0F)
                .texOffs(14, 19).addBox(-1.2F, -1.5F, -3.5F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, -2.0F, -4.0F));

        root.addOrReplaceChild("leg_l0", CubeListBuilder.create()
                .texOffs(6, 23).addBox(-1.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(58, 0).addBox(2.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(20, 19).addBox(2.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(3.5F, -2.0F, -3.5F, 0.0000F, -0.4189F, 0.0000F));

        root.addOrReplaceChild("leg_l1", CubeListBuilder.create()
                .texOffs(16, 23).addBox(-1.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(52, 11).addBox(2.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(26, 19).addBox(2.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(3.5F, -2.0F, 0.0F));

        root.addOrReplaceChild("leg_l2", CubeListBuilder.create()
                .texOffs(26, 23).addBox(-1.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(56, 11).addBox(2.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(32, 19).addBox(2.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(3.5F, -2.0F, 3.0F, 0.0000F, 0.4538F, 0.0000F));

        root.addOrReplaceChild("antenna_l", CubeListBuilder.create()
                .texOffs(38, 19).addBox(-0.5F, -1.0F, -2.0F, 1.0F, 1.0F, 2.0F)
                .texOffs(36, 11).addBox(0.0F, -1.2F, -4.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(56, 23).addBox(0.5F, -1.5F, -5.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(1.0F, -4.0F, -5.5F, 0.0000F, 0.3142F, 0.0000F));

        root.addOrReplaceChild("leg_r0", CubeListBuilder.create()
                .texOffs(36, 23).addBox(-3.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(60, 11).addBox(-3.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(44, 19).addBox(-3.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(-3.5F, -2.0F, -3.5F, 0.0000F, 0.4189F, 0.0000F));

        root.addOrReplaceChild("leg_r1", CubeListBuilder.create()
                .texOffs(46, 23).addBox(-3.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(0, 19).addBox(-3.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(50, 19).addBox(-3.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(-3.5F, -2.0F, 0.0F));

        root.addOrReplaceChild("leg_r2", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-3.0F, -1.5F, -0.5F, 4.0F, 1.0F, 1.0F)
                .texOffs(4, 19).addBox(-3.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(56, 19).addBox(-3.0F, 1.0F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(-3.5F, -2.0F, 3.0F, 0.0000F, -0.4538F, 0.0000F));

        root.addOrReplaceChild("antenna_r", CubeListBuilder.create()
                .texOffs(0, 23).addBox(-0.5F, -1.0F, -2.0F, 1.0F, 1.0F, 2.0F)
                .texOffs(44, 11).addBox(-1.0F, -1.2F, -4.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(60, 23).addBox(-1.5F, -1.5F, -5.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-1.0F, -4.0F, -5.5F, 0.0000F, -0.3142F, 0.0000F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}

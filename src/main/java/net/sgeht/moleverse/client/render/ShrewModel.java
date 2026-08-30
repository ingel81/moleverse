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
 * Shrew model: a scurry and a nose that never stops.
 *
 * <h2>The gait is a trot, and it has to be</h2>
 *
 * <p>Diagonal pairs: front left with hind right, front right with hind left.
 * That is one sign per leg in {@link #DIAGONAL} and the whole gait falls out of
 * it. The alternative pairings are both worse and both instantly recognisable -
 * pairing the two fronts against the two hinds is a bound, which is what the
 * weasel does and what makes a weasel look like a weasel, and pairing left
 * against right is a pace, which is a camel. A small quick mammal trots.</p>
 *
 * <h2>Why the nose is the most important line in this file</h2>
 *
 * <p>A shrew is a snout with an animal behind it. Nothing else about the model
 * says "shrew" as fast, and a snout that is merely long and still reads as a
 * beak. {@link #twitch} is the beetle's antenna function reused verbatim - a
 * sine raised to an odd power, so the sign and the seamless loop survive but the
 * cycle is spent near zero and only briefly at the extremes. A plain sine here
 * gives a snout that waves; this gives one that sniffs.</p>
 *
 * <p>It runs off {@code ageInTicks} and not off movement, which is the point: a
 * shrew standing still is still working. The ears take the same signal a quarter
 * cycle apart, and the head turns a little with it, so the three read as one
 * animal paying attention rather than as three parts on three timers.</p>
 *
 * <h2>The body</h2>
 *
 * <p>Three spine bones, siblings under {@code root}, each rotating about its own
 * pivot. They carry a small lateral wave, far smaller than the weasel's, because
 * a trotting animal's spine barely moves - what it does do is bob, and the bob
 * runs at twice the step rate because both diagonals plant once per cycle.</p>
 *
 * <p>Geometry is the Blockbench export of {@code art/shrew.bbmodel}, whose cubes
 * and UV packing come from {@code art/generators/predator_shapes.py}. Do not edit
 * it by hand.</p>
 */
public class ShrewModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("shrew"), "main");

    private static final String[] LEG_NAMES = {"leg_fl", "leg_fr", "leg_hl", "leg_hr"};

    /**
     * Which diagonal each leg belongs to, in {@link #LEG_NAMES} order.
     *
     * <p>Front left and hind right swing together; front right and hind left
     * answer them. See the class note for why the other two pairings are wrong.</p>
     */
    private static final float[] DIAGONAL = {1.0F, -1.0F, -1.0F, 1.0F};

    /** Radians of gait per unit of distance covered. */
    private static final float STEP_SPEED = 1.1F;

    /** Turns walk speed into gait amplitude, clamped at one. */
    private static final float WALK_SPEED_SCALE = 3.0F;

    /** How far a leg swings fore and aft. */
    private static final float LEG_SWING = 0.95F;

    /** How far the middle of the back dips, in model units, and how far it rolls. */
    private static final float BODY_BOB = 0.35F;
    private static final float BODY_SWAY = 0.06F;

    /** How far the tail swings, and how far behind the body it is. */
    private static final float TAIL_SWING = 0.22F;
    private static final float TAIL_LAG = 0.9F;

    /** Radians of sniffing cycle per tick. Rather more than one sniff a second. */
    private static final float SNIFF_SPEED = 0.32F;

    /** How far the snout swings and lifts, how far the ears flick, how far the head turns. */
    private static final float SNOUT_SWING = 0.30F;
    private static final float SNOUT_LIFT = 0.18F;
    private static final float EAR_FLICK = 0.28F;
    private static final float HEAD_TURN = 0.10F;

    private final ModelPart[] spine;
    private final ModelPart[] legs;
    private final ModelPart head;
    private final ModelPart snout;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart tail0;
    private final ModelPart tail1;

    public ShrewModel(ModelPart root) {
        super(root);
        ModelPart parts = root.getChild("root");
        this.spine = new ModelPart[]{
                parts.getChild("spine0"), parts.getChild("spine1"), parts.getChild("spine2"),
        };
        this.head = this.spine[0].getChild("head");
        this.snout = this.head.getChild("snout");
        this.earLeft = this.head.getChild("ear_l");
        this.earRight = this.head.getChild("ear_r");
        this.tail0 = this.spine[2].getChild("tail0");
        this.tail1 = this.tail0.getChild("tail1");

        this.legs = new ModelPart[LEG_NAMES.length];
        for (int i = 0; i < LEG_NAMES.length; i++) {
            // The front pair hangs off the chest and the hind pair off the rump,
            // so each follows the piece of spine it is actually attached to.
            ModelPart owner = i < 2 ? this.spine[0] : this.spine[2];
            this.legs[i] = owner.getChild(LEG_NAMES[i]);
        }
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);

        float gait = state.walkAnimationPos * STEP_SPEED;
        float moving = Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);

        for (int i = 0; i < this.legs.length; i++) {
            this.legs[i].xRot += moving * LEG_SWING * Mth.cos(gait) * DIAGONAL[i];
        }

        // Twice the step rate, because both diagonals plant once per cycle, and
        // on the middle segment only - the chest and the rump carry the legs and
        // a bob on them would lift the feet off the floor.
        this.spine[1].y -= moving * BODY_BOB * Mth.abs(Mth.sin(gait));
        for (int i = 0; i < this.spine.length; i++) {
            this.spine[i].yRot += moving * BODY_SWAY * Mth.sin(gait - i * 0.5F);
        }

        // The tail answers the body rather than leading it, which is the whole
        // difference between a tail and a rudder.
        this.tail0.yRot += moving * TAIL_SWING * Mth.sin(gait - TAIL_LAG);
        this.tail1.yRot += moving * TAIL_SWING * Mth.sin(gait - 2.0F * TAIL_LAG);

        float sniff = twitch(state.ageInTicks * SNIFF_SPEED);
        this.snout.yRot += SNOUT_SWING * sniff;
        // Lifts on the way through zero rather than at the extremes, so the
        // snout rises into the sniff instead of at the end of it.
        this.snout.xRot -= SNOUT_LIFT * Mth.abs(sniff);
        this.head.yRot += HEAD_TURN * sniff;

        // A quarter cycle apart and opposite, so the two never flick as a pair -
        // which is the thing that would make them read as horns.
        this.earLeft.zRot += EAR_FLICK * twitch(state.ageInTicks * SNIFF_SPEED + Mth.HALF_PI);
        this.earRight.zRot -= EAR_FLICK * twitch(state.ageInTicks * SNIFF_SPEED - Mth.HALF_PI);
    }

    /**
     * A sine that spends most of its time at rest.
     *
     * <p>Fifth power, so the sign and the seamless loop survive but the middle of
     * the range is flattened towards zero and only the peaks are reached. Lifted
     * from {@code SoilBeetleModel}, and lifted rather than reimplemented for the
     * same reason the two share a texture kit: two animals twitching on two
     * curves is two animators.</p>
     */
    private static float twitch(float phase) {
        float s = Mth.sin(phase);
        float squared = s * s;
        return s * squared * squared;
    }

    // --- Blockbench export of art/shrew.bbmodel, do not edit ---------------
    // Rebuilt from the ground up after the in-game verdict "too little
    // detail". The animal is a pointed wedge, nearly all snout: the trunk
    // widens steadily to the rump on a three unit pitch, and the face runs
    // four wide (head) through three (muzzle, on the head bone) and two
    // (snout) to one (the bare-nosed tip, on the snout bone so it sniffs
    // with it). One-texel ears, a wire of a two-bone tail, and a haunch on
    // each hind leg's bone that swings with the stride. The bones sit where
    // their names say they do in Java - the exporter mirrors X and
    // predator_shapes.py applies that flip so the two exports agree.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot()
                .addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition spine0 = root.addOrReplaceChild("spine0", CubeListBuilder.create()
                .texOffs(38, 0).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, -3.0F));

        PartDefinition spine1 = root.addOrReplaceChild("spine1", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -5.0F, -2.0F, 6.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition spine2 = root.addOrReplaceChild("spine2", CubeListBuilder.create()
                .texOffs(20, 0).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, 3.0F));

        PartDefinition head = spine0.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 9).addBox(-2.0F, -3.0F, -3.0F, 4.0F, 3.0F, 3.0F)
                .texOffs(26, 9).addBox(-1.5F, -2.0F, -4.0F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -2.0F));

        PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create()
                .texOffs(14, 9).addBox(-1.0F, -2.0F, -4.0F, 2.0F, 2.0F, 4.0F)
                .texOffs(0, 15).addBox(-0.5F, -1.5F, -5.5F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, -3.0F));

        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create()
                .texOffs(18, 15).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(1.5F, -3.0F, -1.5F));

        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create()
                .texOffs(22, 15).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(-1.5F, -3.0F, -1.5F));

        PartDefinition leg_fl = spine0.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(60, 9).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(2.5F, 0.0F, -1.0F));

        PartDefinition leg_hl = spine2.addOrReplaceChild("leg_hl", CubeListBuilder.create()
                .texOffs(6, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                .texOffs(48, 9).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
                PartPose.offset(2.5F, 0.0F, 1.0F));

        PartDefinition leg_fr = spine0.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(10, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(-2.5F, 0.0F, -1.0F));

        PartDefinition leg_hr = spine2.addOrReplaceChild("leg_hr", CubeListBuilder.create()
                .texOffs(14, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                .texOffs(54, 9).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
                PartPose.offset(-2.5F, 0.0F, 1.0F));

        PartDefinition tail0 = spine2.addOrReplaceChild("tail0", CubeListBuilder.create()
                .texOffs(54, 0).addBox(-0.5F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, -1.5F, 4.0F));

        PartDefinition tail1 = tail0.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(38, 9).addBox(-0.5F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}

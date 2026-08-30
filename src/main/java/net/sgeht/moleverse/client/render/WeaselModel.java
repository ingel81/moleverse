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
 * Weasel model: a body that travels as a wave, and a bound rather than a walk.
 *
 * <h2>The undulation, and why it is a phase offset and not five animations</h2>
 *
 * <p>Five spine bones, each yawing about its own pivot on the same sine with a
 * fixed lag between them. That is the whole of it: at {@link #WAVE_LAG} of a
 * radian per segment the peak reaches the rump about half a cycle after it left
 * the chest, so the shape that travels down the animal is a real travelling wave
 * rather than five parts wagging in step. The count went from three to five
 * when the great worm set the quality bar - a travelling wave reads by its
 * joint count, and the per-joint yaw came DOWN with the change, so the longer
 * chain is smoother, never springier. Change one number and the wave gets
 * longer or shorter; there is nothing to re-bake.</p>
 *
 * <p>The segments are siblings and not a chain, which is what makes this safe.
 * Each rotates in place, so the joints shear rather than swing - and the boxes
 * carry one and a half units of overlap at every joint precisely to hide a
 * shear of this size. It is the great worm's trick and the reason the overlap
 * exists at all.
 * A chained spine would compound the angles and turn {@link #UNDULATION} into a
 * number that means something different for each segment.</p>
 *
 * <h2>Why the legs bound instead of trotting</h2>
 *
 * <p>Front pair together, hind pair together, half a cycle apart. Mustelids do
 * not trot at speed, they gallop by folding and extending the spine, and the
 * silhouette of that - two feet, two feet, arched back between - is most of what
 * makes a weasel read as a weasel from across a room. {@code ShrewModel} uses
 * the diagonal trot and states the same comparison from the other side.</p>
 *
 * <p>The arch goes on the waist alone, lifted on the half of the cycle where
 * both pairs are gathered under the animal. Arching the chest and the rump as
 * well was tried and reads as a caterpillar: on a body this long the eye follows
 * the middle, and one segment moving is a spine while three are a concertina.</p>
 *
 * <h2>The prowl needs no code</h2>
 *
 * <p>{@code ProwlAndLungeGoal} changes the animal's speed and nothing else.
 * Every amplitude here scales with {@code walkAnimationSpeed}, so a prowling
 * weasel undulates slowly and shallowly and a lunging one snaps, and neither
 * class knows the other exists. That is the point of driving poses from numbers
 * rather than baking them: the second behaviour was free.</p>
 *
 * <p>Geometry is the Blockbench export of {@code art/weasel.bbmodel}, whose
 * cubes and UV packing come from {@code art/generators/predator_shapes.py}. Do
 * not edit it by hand.</p>
 */
public class WeaselModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Moleverse.id("weasel"), "main");

    private static final String[] LEG_NAMES = {"leg_fl", "leg_fr", "leg_hl", "leg_hr"};

    /** Which end of the animal each leg is on, in {@link #LEG_NAMES} order. */
    private static final float[] PAIR = {1.0F, 1.0F, -1.0F, -1.0F};

    /**
     * Radians of gait per unit of distance covered.
     *
     * <p>Lower than the shrew's, because this animal is twice as long and covers
     * more ground per stride. Getting this wrong is the fastest way to make a
     * large animal look like a small one being played at the wrong speed.</p>
     */
    private static final float STEP_SPEED = 0.75F;

    /** Turns walk speed into gait amplitude, clamped at one. */
    private static final float WALK_SPEED_SCALE = 2.6F;

    /** How far a leg swings fore and aft. */
    private static final float LEG_SWING = 1.0F;

    /**
     * How far each spine segment yaws, and how far behind the one in front it
     * is. The yaw dropped from 0.16 when the chain grew from three joints to
     * five, so the whole-body curvature stayed put while its resolution
     * doubled, and dropped again to 0.10 when the trunk was trimmed from
     * twenty-two units to nineteen - the same lateral reach on a shorter run
     * is a tighter bend, and the curvature is the thing being held constant.
     * The lag stays: four lags of 0.75 put the rump half a cycle behind the
     * chest, which is the travelling wave the animal has always carried.
     */
    private static final float UNDULATION = 0.10F;
    private static final float WAVE_LAG = 0.75F;

    /** How far the waist lifts on the gathered half of the bound, in model units. */
    private static final float ARCH = 0.8F;

    /**
     * How much of the chest's yaw the head takes back out.
     *
     * <p>Not all of it. A predator holds its head steady while its body moves
     * under it, which is worth having - but cancelling the yaw completely makes
     * the neck look broken, because the head is still being <em>carried</em>
     * sideways by the chest even when it is no longer being turned by it.</p>
     */
    private static final float HEAD_STEADY = 0.7F;

    /** How far the tail swings, and how far behind the rump each segment is. */
    private static final float TAIL_SWING = 0.30F;
    private static final float TAIL_LAG = 1.1F;

    /** Radians of idle cycle per tick, and how far the head casts about on it. */
    private static final float CAST_SPEED = 0.045F;
    private static final float CAST_TURN = 0.22F;

    private final ModelPart[] spine;
    private final ModelPart[] legs;
    private final ModelPart head;
    private final ModelPart tail0;
    private final ModelPart tail1;

    public WeaselModel(ModelPart root) {
        super(root);
        ModelPart parts = root.getChild("root");
        this.spine = new ModelPart[]{
                parts.getChild("spine0"), parts.getChild("spine1"), parts.getChild("spine2"),
                parts.getChild("spine3"), parts.getChild("spine4"),
        };
        this.head = this.spine[0].getChild("head");
        this.tail0 = this.spine[this.spine.length - 1].getChild("tail0");
        this.tail1 = this.tail0.getChild("tail1");

        this.legs = new ModelPart[LEG_NAMES.length];
        for (int i = 0; i < LEG_NAMES.length; i++) {
            ModelPart owner = i < 2 ? this.spine[0] : this.spine[this.spine.length - 1];
            this.legs[i] = owner.getChild(LEG_NAMES[i]);
        }
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);

        float gait = state.walkAnimationPos * STEP_SPEED;
        float moving = Math.min(state.walkAnimationSpeed * WALK_SPEED_SCALE, 1.0F);

        for (int i = 0; i < this.legs.length; i++) {
            this.legs[i].xRot += moving * LEG_SWING * Mth.cos(gait) * PAIR[i];
        }

        float chestYaw = 0.0F;
        for (int i = 0; i < this.spine.length; i++) {
            float yaw = moving * UNDULATION * Mth.sin(gait - i * WAVE_LAG);
            this.spine[i].yRot += yaw;
            if (i == 0) {
                chestYaw = yaw;
            }
        }

        // The waist only - the middle of the five - and only on the half of
        // the cycle where both pairs are gathered: `abs` folds the sine so the
        // back humps twice per stride, once per gather, which is what a bound
        // actually does.
        this.spine[this.spine.length / 2].y -= moving * ARCH * Mth.abs(Mth.sin(gait));

        // Carried by the chest, not turned by it. See HEAD_STEADY.
        this.head.yRot -= HEAD_STEADY * chestYaw;

        // The tail is the far end of the same wave, one more lag past the
        // rump, so it whips rather than follows. This is why it is two bones
        // and why the second hangs off the first.
        float tailPhase = gait - this.spine.length * WAVE_LAG;
        this.tail0.yRot += moving * TAIL_SWING * Mth.sin(tailPhase);
        this.tail1.yRot += moving * TAIL_SWING * Mth.sin(tailPhase - TAIL_LAG);

        // Standing still it casts about, slowly. A predator that freezes solid
        // between paths reads as switched off, and this animal spends most of a
        // player's encounter with it not moving very fast.
        this.head.yRot += (1.0F - moving) * CAST_TURN * Mth.sin(state.ageInTicks * CAST_SPEED);
    }

    // --- Blockbench export of art/weasel.bbmodel, do not edit --------------
    // Five spine segments on a 3.5 unit pitch (trunk nineteen units - the
    // four unit pitch came back from the game a touch too long), girth dip in
    // the heights not the widths, head with muzzle step and snout on the
    // chest, four legs each with a paw on the same bone, and a thin two piece
    // tail whose second box is the black tip. The bones sit where their names
    // say they do in Java: the exporter mirrors X and predator_shapes.py
    // applies that flip so both exports agree.
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot()
                .addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition spine0 = root.addOrReplaceChild("spine0", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -5.0F, -2.5F, 8.0F, 5.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, -7.0F));

        PartDefinition spine1 = root.addOrReplaceChild("spine1", CubeListBuilder.create()
                .texOffs(26, 0).addBox(-4.0F, -5.0F, -2.5F, 8.0F, 5.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, -3.5F));

        PartDefinition spine2 = root.addOrReplaceChild("spine2", CubeListBuilder.create()
                .texOffs(26, 10).addBox(-3.5F, -4.0F, -2.5F, 7.0F, 4.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        PartDefinition spine3 = root.addOrReplaceChild("spine3", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-3.5F, -4.0F, -2.5F, 7.0F, 4.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, 3.5F));

        PartDefinition spine4 = root.addOrReplaceChild("spine4", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-4.0F, -5.0F, -2.5F, 8.0F, 5.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, 7.0F));

        PartDefinition head = spine0.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(24, 20).addBox(-2.5F, -4.0F, -4.0F, 5.0F, 4.0F, 4.0F)
                .texOffs(0, 29).addBox(-2.0F, -3.0F, -5.0F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -2.5F));

        PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create()
                .texOffs(38, 29).addBox(-1.5F, -2.0F, -3.0F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, -4.0F));

        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create()
                .texOffs(58, 20).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
                PartPose.offset(1.5F, -4.0F, -2.0F));

        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create()
                .texOffs(58, 29).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
                PartPose.offset(-1.5F, -4.0F, -2.0F));

        PartDefinition leg_fl = spine0.addOrReplaceChild("leg_fl", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(50, 29).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(3.0F, 0.0F, -0.5F));

        PartDefinition leg_hl = spine4.addOrReplaceChild("leg_hl", CubeListBuilder.create()
                .texOffs(14, 29).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(0, 35).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(3.0F, 0.0F, 0.5F));

        PartDefinition leg_fr = spine0.addOrReplaceChild("leg_fr", CubeListBuilder.create()
                .texOffs(22, 29).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(8, 35).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(-3.0F, 0.0F, -0.5F));

        PartDefinition leg_hr = spine4.addOrReplaceChild("leg_hr", CubeListBuilder.create()
                .texOffs(30, 29).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(16, 35).addBox(-1.0F, 3.0F, -2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(-3.0F, 0.0F, 0.5F));

        PartDefinition tail0 = spine4.addOrReplaceChild("tail0", CubeListBuilder.create()
                .texOffs(42, 20).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 6.0F),
                PartPose.offset(0.0F, -1.5F, 5.5F));

        PartDefinition tail1 = tail0.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(50, 10).addBox(-1.0F, -2.0F, -2.5F, 2.0F, 2.0F, 5.0F),
                PartPose.offset(0.0F, 0.0F, 5.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}

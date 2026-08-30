package net.sgeht.moleverse.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.TravellingMole;

/**
 * Renderer for the giant mole, which is the ordinary mole and nothing else.
 *
 * <p>Same model, same texture, same walk cycle, same rearing pose. Almost
 * everything that makes the giant look different from the animal it is a picture
 * of happens outside this class.</p>
 *
 * <h2>Nothing is scaled here</h2>
 *
 * <p>The size comes from {@code Attributes.SCALE} on the entity.
 * {@code LivingEntityRenderer} reads it off the entity into the render state and
 * scales the pose stack by it before the model is posed, so a multiply in this
 * class would be the same operation done twice - and, worse, done only to the
 * picture. The attribute scales the entity's own bounding box as well, which is
 * what both the forward sweep and the corridor-width clamp measure against, so
 * the mole a player can see, the mole that can hit them and the mole that decides
 * how far it may lean towards a wall are one shape. The shadow follows without
 * asking: the radius handed to the constructor is the mole's own, and the base
 * renderer multiplies it by the same scale.</p>
 *
 * <h2>The layer is shared, not copied</h2>
 *
 * <p>{@link MoleModel#LAYER} is baked again here rather than a second layer being
 * registered for it. {@code EntityModelSet.bakeLayer} builds a fresh part tree
 * from the definition on every call, so the two renderers hold two independent
 * models of the same geometry, which is exactly what is wanted - a
 * {@code ModelPart} is posed in place and could not be shared between two
 * renderers drawing in the same frame.</p>
 *
 * <h2>Three things ride on channels the mole already had</h2>
 *
 * <p>{@link MoleModel} is not edited and does not know this entity exists. Each of
 * the three poses the traversal needs is carried by a mechanism that was already
 * there for the ordinary mole:</p>
 *
 * <ul>
 * <li><b>The body angle</b> is the dig aim - two numbers that turn the whole
 *     animal around the hip pivot, written so that one level, direction-neutral
 *     dig cycle could serve every direction a mole digs in. Aiming a mole along
 *     the slope of a corridor floor is the same question, so the measured pitch
 *     rides on the entity's own {@code xRot} and is handed straight to it.</li>
 * <li><b>The sniff</b> is the rearing pose and its keyframe channel, held at a
 *     fraction of the angle it was tuned at - see {@code TravellingMole.SNIFF_REAR}
 *     for why a full rear does not fit in a corridor.</li>
 * <li><b>The head turn</b> is the one thing with no channel of its own, and it
 *     gets the smallest subclass that will carry it. {@code MoleModel} ignores
 *     {@code renderState.yRot}, which is the standard place a head yaw relative to
 *     the body lives, so {@link LookingMoleModel} applies it to the head bone the
 *     way every vanilla quadruped does. That keeps the whole of the awareness on
 *     the ordinary head-rotation packet: nothing custom is synched for it.</li>
 * </ul>
 */
public class TravellingMoleRenderer extends MobRenderer<TravellingMole, MoleRenderState, MoleModel> {

    private static final Identifier TEXTURE = Moleverse.id("textures/entity/mole.png");

    public TravellingMoleRenderer(EntityRendererProvider.Context context) {
        // The mole's own shadow radius. The scale attribute grows it to match.
        super(context, new LookingMoleModel(context.bakeLayer(MoleModel.LAYER)), 0.35F);
    }

    @Override
    public MoleRenderState createRenderState() {
        return new MoleRenderState();
    }

    @Override
    public void extractRenderState(TravellingMole entity, MoleRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        // Always fully aimed: this animal is never level unless the ground is,
        // and the blend the ordinary mole needs between crawling and digging has
        // no counterpart here - it is doing one thing for its whole short life.
        state.digAmount = 1.0F;
        state.digPitchDegrees = state.xRot;
        state.digYawDegrees = 0.0F;

        // Clawing: at both ends of the run, and at a wall it has stopped against.
        state.digAnimationState.copyFrom(entity.digAnimationState);

        // Sniffing: half a rear, plus the head sweep and snout twitch that go
        // with it. Both are driven by the entity from the synched gait.
        state.peekAmount = entity.getPeekAmount(partialTick);
        state.peekAnimationState.copyFrom(entity.peekAnimationState);

        // The idle loop and the two one-shots are deliberately left at rest. The
        // idle is a mole standing about, and the burrow and emerge animations
        // belong to the trip this is the picture of rather than a participant in.
    }

    @Override
    public Identifier getTextureLocation(MoleRenderState state) {
        return TEXTURE;
    }

    /**
     * The mole, plus a head that turns.
     *
     * <p>The smallest possible addition rather than a fork: everything is
     * {@code MoleModel}'s, and one bone is rotated afterwards. It has to be
     * afterwards, because {@code setupAnim} resets every part to its rest pose
     * before it applies anything - a head turned first would be turned back.</p>
     *
     * <p>{@code renderState.yRot} is the head's yaw relative to the body, which
     * {@code LivingEntityRenderer} works out from the head rotation packet and the
     * body angle it derives from movement. That makes this the same one-line
     * mechanism every vanilla quadruped uses, and it costs the ordinary mole
     * nothing, since that one is drawn by {@link MoleRenderer} and never comes
     * through here.</p>
     */
    private static final class LookingMoleModel extends MoleModel {

        private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

        private final ModelPart head;

        LookingMoleModel(ModelPart root) {
            super(root);
            this.head = root.getChild("root").getChild("head");
        }

        @Override
        public void setupAnim(MoleRenderState state) {
            super.setupAnim(state);
            // Added rather than assigned: the rearing pose's keyframe channel
            // sweeps this same bone, and a sniffing mole that also happens to be
            // watching somebody should do both.
            this.head.yRot += state.yRot * DEGREES_TO_RADIANS;
        }
    }
}

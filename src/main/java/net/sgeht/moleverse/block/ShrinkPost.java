package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.dimension.BurrowTransit;
import net.sgeht.moleverse.dimension.ModDimensions;

/**
 * The fitting a player uses to go down into the burrow, and the same post
 * standing in the chamber to come back up.
 *
 * <p>One block for both directions on purpose. The way back is not a second
 * device to find and learn - it is the thing you just used, seen from the other
 * side, which is what makes the trip legible the first time somebody takes it.
 * Which of the two it does is read off the dimension it stands in rather than
 * off a state or a block entity, so a post carries no information that could
 * ever disagree with where it actually is.</p>
 *
 * <p>Above ground it is a {@link MoundAttachment} like any other and needs the
 * prepared mound underneath: shoring up a molehill is the price of an entrance,
 * and it is what keeps every heap in a meadow from being a door. Below ground
 * there are no mounds and the rule is dropped entirely - see
 * {@link #canSurvive}.</p>
 *
 * <p>All of the work is server side. A teleport the client predicted and the
 * server then refused would put a player in a room that does not exist, so the
 * client is told to do nothing and wait.</p>
 */
public class ShrinkPost extends MoundAttachment {

    public static final MapCodec<ShrinkPost> CODEC = simpleCodec(ShrinkPost::new);

    /** A post: narrow enough to walk around, tall enough to read as a fitting rather than a stump. */
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 12.0, 11.0);

    /**
     * The transition sound, played where the player comes out.
     *
     * <p>One sound for both directions, told apart by pitch: low arriving below,
     * high arriving above. A listener needs no vocabulary for that - it is the
     * same cue the size change itself would make - and it costs no second
     * asset.</p>
     */
    private static final float PITCH_DOWN = 0.6F;

    private static final float PITCH_UP = 1.4F;

    public ShrinkPost(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ShrinkPost> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * A prepared mound above ground; nothing at all below it.
     *
     * <p>The burrow has no mounds to shore up - the chamber floor is deep earth,
     * and a post there is the far end of a door rather than a fitting somebody
     * built. Inheriting the mound rule unchanged would have every arrival pop its
     * own way home off the floor the moment a block update reached it.</p>
     *
     * <p>No support rule replaces it, deliberately, and not for want of one that
     * would read well. A run dug at a lower level passes four blocks under this
     * one against a corridor height of six, so a corridor carved later can take
     * the floor out from under a post - and a post that falls leaves whoever is
     * down there sealed in a room with no way home. There is nothing to weigh
     * against that: the block is placed by the transit and by nobody else, so a
     * support rule would only ever fire as a bug.</p>
     *
     * <p>{@code ModDimensions} carries a {@link LevelReader} overload for exactly
     * this call: a block hook gets nothing better than a reader, and a reader has
     * no dimension key of its own.</p>
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return ModDimensions.isBurrow(level) || super.canSurvive(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            // No prediction: the client cannot know whether the mound at the
            // other end is still standing.
            return InteractionResult.CONSUME;
        }

        boolean inBurrow = ModDimensions.isBurrow(serverLevel);
        // The mound is the block under the fitting, which is the position the
        // whole mapping is written in terms of. Below ground the post stands on
        // the chamber floor and speaks for itself.
        boolean travelled = inBurrow
                ? BurrowTransit.leave(serverPlayer, serverLevel, pos)
                : BurrowTransit.enter(serverPlayer, serverLevel, pos.below());

        if (!travelled) {
            refuse(serverLevel, serverPlayer, pos, inBurrow);
            return InteractionResult.FAIL;
        }

        float pitch = inBurrow ? PITCH_UP : PITCH_DOWN;

        // Both ends, because by now they are different worlds and each has an
        // audience of its own. The post is left puffing for whoever stayed
        // behind; the sound follows the traveller, who would otherwise arrive
        // somewhere new having heard nothing at all.
        serverLevel.sendParticles(ParticleTypes.MYCELIUM,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                24, 0.3, 0.5, 0.3, 0.02);

        ServerLevel arrival = serverPlayer.level();
        arrival.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, pitch);
        arrival.sendParticles(ParticleTypes.MYCELIUM,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                24, 0.3, 0.5, 0.3, 0.02);

        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * What a closed door looks like.
     *
     * <p>A refusal with no explanation is the failure mode worth spending a line
     * on: from where the player stands nothing happened, and the cause - a heap
     * of earth somewhere else, gone - is not something they can see.</p>
     */
    private static void refuse(ServerLevel level, ServerPlayer player, BlockPos pos, boolean inBurrow) {
        String key = "message." + Moleverse.MOD_ID + (inBurrow ? ".burrow.closed" : ".burrow.no_way_down");
        String fallback = inBurrow
                ? "The mound above this chamber is gone. This way is closed."
                : "Nothing gives way. The burrow will not take you here.";
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback));

        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 0.6F, 0.7F);
        level.sendParticles(ParticleTypes.ASH,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.0);
    }
}

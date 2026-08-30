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
import net.minecraft.world.entity.LivingEntity;
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
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The way up out of a chamber: roots braided into a rope, hanging from the
 * ceiling to just above head height.
 *
 * <p>It replaces the {@link ShrinkPost} that used to stand in the middle of
 * every chamber. The post is still the way <em>down</em>, up on the mound, and
 * the name fits it there - you shrink, and then you are in the burrow. Below
 * ground nobody shrinks a second time, so a post there was the same fitting
 * meaning something else, and the one thing the burrow is full of that a mole
 * could plausibly have braided into a rope is roots.</p>
 *
 * <p><strong>Every segment is the whole door.</strong> Right-clicking any block
 * of the column takes the player up, because a rope is not a switch with a
 * particular block on it: whichever one is in reach is the one you grab. Only
 * the column matters to {@link BurrowTransit#leave}, which maps back through x
 * and z alone, so a segment seven blocks up answers exactly what the post on the
 * floor used to.</p>
 *
 * <p>All of the work is server side, for {@link ShrinkPost}'s reason: a teleport
 * the client predicted and the server then refused would put a player in a room
 * that does not exist.</p>
 */
public class RootLadder extends Block {

    public static final MapCodec<RootLadder> CODEC = simpleCodec(RootLadder::new);

    /**
     * A rope, not a plank: narrow, and the full height of the block.
     *
     * <p>Full height so a stack of segments highlights as one continuous thing
     * rather than as a column of separate boxes with seams between them. There is
     * no collision to go with it - see {@code ModBlocks} - so this shape is only
     * ever what the player's cursor and the outline see.</p>
     */
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    /**
     * The pitch {@link ShrinkPost} arrives on going up, repeated here on purpose.
     *
     * <p>It is the same trip through the same door, so it is the same cue. The
     * two blocks cannot share a constant without one of them depending on the
     * other for a sound, which is a worse coupling than a number that is written
     * down twice next to the reason it has to match.</p>
     */
    private static final float PITCH_UP = 1.4F;

    public RootLadder(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RootLadder> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Climbable, which the teleport does not need and the fiction does.
     *
     * <p>The exit is the right-click; nothing about getting home depends on
     * reaching the top of the rope. But a rope ladder you cannot climb is a
     * decoration, and it costs one method to let a player go up it instead - the
     * same method vines are climbed by. With no collision box the climb is by
     * holding jump rather than by walking into it, and letting go is a slow slide
     * rather than a fall, which is what a rope should do.</p>
     *
     * <p>The hook reads the block at the entity's <em>feet</em>, which is what
     * decides where the bottom of the rope may hang - see
     * {@code BurrowTransit.LADDER_FOOT}. A rope out of jumping reach is climbable
     * and unreachable, which is the same as not climbable at all.</p>
     *
     * <p>Overridden rather than done through {@code #minecraft:climbable}. The
     * tag is what this hook reads by default, so the two are the same answer, and
     * the block being able to say it on its own means a ladder placed by the
     * transit is climbable even in a world whose datapack has been rewritten.</p>
     */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    /**
     * Grab it, and you are back on the surface.
     *
     * <p>{@link BurrowTransit#leave} is handed this segment's own position. It
     * reads only x and z, both of which are the chamber's centre column whichever
     * segment was clicked, so the height the player happened to reach for cannot
     * change where they come out.</p>
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            // No prediction: the client cannot know whether the mound at the
            // other end is still standing.
            return InteractionResult.CONSUME;
        }

        // Before the trip, so the traveller hears the rope they just grabbed.
        // The chime at the far end is the arrival; this is the departure.
        serverLevel.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                ModSounds.LADDER_RUSTLE.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        if (!BurrowTransit.leave(serverPlayer, serverLevel, pos)) {
            refuse(serverLevel, serverPlayer, pos);
            return InteractionResult.FAIL;
        }

        // Both ends, because by now they are different worlds and each has an
        // audience of its own. The rope is left swinging in a puff of soil for
        // whoever stayed behind; the sound follows the traveller, who would
        // otherwise arrive somewhere new having heard nothing at all.
        serverLevel.sendParticles(ParticleTypes.MYCELIUM,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                24, 0.3, 0.5, 0.3, 0.02);

        ServerLevel arrival = serverPlayer.level();
        arrival.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, PITCH_UP);
        arrival.sendParticles(ParticleTypes.MYCELIUM,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                24, 0.3, 0.5, 0.3, 0.02);

        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * What a closed door looks like, in {@link ShrinkPost}'s words.
     *
     * <p>The same key and the same fallback, because it is the same refusal for
     * the same reason: the heap of earth this chamber hangs under is gone, and
     * that is not something the player can see from down here.</p>
     */
    private static void refuse(ServerLevel level, ServerPlayer player, BlockPos pos) {
        player.sendSystemMessage(Component.translatableWithFallback(
                "message." + Moleverse.MOD_ID + ".burrow.closed",
                "The mound above this chamber is gone. This way is closed."));

        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 0.6F, 0.7F);
        level.sendParticles(ParticleTypes.ASH,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.0);
    }
}

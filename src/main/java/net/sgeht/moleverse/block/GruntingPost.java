package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.burrow.MoundNetwork;
import net.sgeht.moleverse.registry.ModItems;
import net.sgeht.moleverse.tag.ModTags;

/**
 * A stake driven into the soil. Rasped across, it brings earthworms up.
 *
 * <p>Worm grunting is a real technique, and an old one: a stake goes into damp
 * ground, a flat iron is drawn across its head, and within a minute the worms
 * for yards around come out of the soil on their own. Why they do it is still
 * argued over - the best answer is that the vibration is close enough to a mole
 * digging that leaving is the only sensible reaction. Which makes it the one
 * piece of real-world folk knowledge that belongs in this mod without being
 * bent: the player is imitating a mole, badly, and the worms fall for it.</p>
 *
 * <p>Not a {@link MoundAttachment}. Grunting has nothing to do with a colony -
 * it works on any soil, needs no mound, and a player who has never seen a
 * molehill can still build one. It is deliberately the cheap end of the worm
 * supply, which is why it has a cooldown and the larder does not.</p>
 *
 * <p>The whole thing is server side. Whether a worm comes up depends on the
 * ground for four blocks around, and the client has no reason to have looked at
 * it - a predicted worm that the server then refused would be an item that
 * appears and vanishes.</p>
 */
public class GruntingPost extends Block {

    public static final MapCodec<GruntingPost> CODEC = simpleCodec(GruntingPost::new);

    /**
     * Whether the post has been rasped and is resting.
     *
     * <p>A state rather than a block entity, and the cooldown is a scheduled tick
     * that clears it - the same mechanism {@link ShaftLantern} uses for its glow,
     * for the same reason. What has to be remembered here is one bit; a block
     * entity would add a ticking object, a save format and a synchronisation
     * question to hold it, and a scheduled tick already survives a save because
     * the chunk writes its pending ticks out with it.</p>
     *
     * <p>The one honest limit: the delay is stored relative to the chunk, not as
     * a moment in world time, so a post in a chunk nobody is near does not count
     * down. That is how every vanilla block cooldown behaves and it errs the
     * right way - a post recovers while somebody is there to use it.</p>
     */
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");

    /**
     * How long a post rests. Ninety seconds: long enough that it is a place you
     * walk back to rather than a button, short enough that walking back is worth
     * it.
     */
    public static final int COOLDOWN_TICKS = 1800;

    /** How far out the vibration reaches. Real grunting works over about this much ground. */
    private static final int WORM_RADIUS = 4;

    private static final int MIN_WORMS = 2;

    private static final int MAX_WORMS = 4;

    /**
     * How many spots are tried before giving up on the rest of the worms.
     *
     * <p>More than the worms wanted, because a spot can fail for reasons that say
     * nothing about the next one - a path, a wall, the roof of a house. Bounded
     * so a post in a courtyard costs a fixed handful of heightmap lookups rather
     * than a search.</p>
     */
    private static final int PLACEMENT_ATTEMPTS = 16;

    /**
     * How far above or below the post a worm may surface.
     *
     * <p>Without this the heightmap happily answers with the top of the cliff the
     * post is standing against, and worms rain off a ledge four blocks up.</p>
     */
    private static final int MAX_SURFACE_STEP = 3;

    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 13.0, 10.0);

    public GruntingPost(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SPENT, false));
    }

    @Override
    public MapCodec<GruntingPost> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Soil, and nothing else. A stake in a stone floor transmits nothing. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModTags.Blocks.MOLE_DIGGABLE);
    }

    /** Comes out with the ground it was driven into, like a torch off its wall. */
    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess tickAccess,
            BlockPos pos,
            Direction direction,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random) {
        return state.canSurvive(level, pos)
                ? super.updateShape(state, level, tickAccess, pos, direction, neighbourPos, neighbourState, random)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }

        if (state.getValue(SPENT)) {
            // A refusal with nothing to see is the failure mode worth a line:
            // from where the player stands, rasping the post simply did nothing.
            tell(player, "grunting_post.spent",
                    "The ground here has not settled. Nothing more will come up yet.");
            serverLevel.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.5F, 0.7F);
            return InteractionResult.FAIL;
        }

        rasp(serverLevel, pos);

        if (surfaceWorms(serverLevel, pos, serverLevel.random) == 0) {
            // No cooldown: nothing was given, so nothing is owed. The post stands
            // on soil by its own placement rule, but one block of it in a paved
            // yard is not enough ground for anything to come out of - and a
            // player who has just built the thing needs to be told that, not left
            // to conclude it is broken.
            tell(player, "grunting_post.barren",
                    "The soil around this post is too thin. Nothing comes up.");
            return InteractionResult.SUCCESS_SERVER;
        }

        serverLevel.setBlock(pos, state.setValue(SPENT, true), Block.UPDATE_ALL);
        serverLevel.scheduleTick(pos, this, COOLDOWN_TICKS);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The cooldown running out. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPENT)) {
            level.setBlock(pos, state.setValue(SPENT, false), Block.UPDATE_ALL);
        }
    }

    /** The stake being played: the sound and the dust it shakes loose at its foot. */
    private static void rasp(ServerLevel level, BlockPos pos) {
        // The archaeology brush, pitched down. It is the one vanilla sound that is
        // a rasp rather than a knock, which is exactly what the technique is.
        level.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.9F, 0.7F);
        level.sendParticles(ParticleTypes.MYCELIUM,
                pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                12, 0.35, 0.1, 0.35, 0.01);
    }

    /**
     * Brings two to four worms out of the soil around the post.
     *
     * <p>Each one is placed on the ground where it surfaced rather than at the
     * post, because that is the thing worth watching: the worms come out of the
     * meadow, not out of the stake. Only ground a mole could dig counts, so a
     * path or a doorstep inside the radius stays empty.</p>
     *
     * @return how many actually surfaced
     */
    private static int surfaceWorms(ServerLevel level, BlockPos pos, RandomSource random) {
        int wanted = MIN_WORMS + random.nextInt(MAX_WORMS - MIN_WORMS + 1);
        int surfaced = 0;

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS && surfaced < wanted; attempt++) {
            int x = pos.getX() + random.nextInt(WORM_RADIUS * 2 + 1) - WORM_RADIUS;
            int z = pos.getZ() + random.nextInt(WORM_RADIUS * 2 + 1) - WORM_RADIUS;

            // The same "first free spot above the ground" the mounds use, so a
            // worm lands in the grass rather than on top of it.
            BlockPos surface = MoundNetwork.surfaceAt(level, x, z);
            if (Math.abs(surface.getY() - pos.getY()) > MAX_SURFACE_STEP) {
                continue;
            }
            if (!level.getBlockState(surface.below()).is(ModTags.Blocks.MOLE_DIGGABLE)) {
                continue;
            }
            if (!level.getBlockState(surface).canBeReplaced()) {
                continue;
            }

            Block.popResource(level, surface, new ItemStack(ModItems.EARTHWORM.get()));
            level.sendParticles(ParticleTypes.COMPOSTER,
                    surface.getX() + 0.5, surface.getY() + 0.1, surface.getZ() + 0.5,
                    6, 0.25, 0.0, 0.25, 0.0);
            level.playSound(null, surface, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.3F, 1.4F);
            surfaced++;
        }
        return surfaced;
    }

    /** {@code Player} has no chat method of its own; only a {@link ServerPlayer} does. */
    private static void tell(Player player, String key, String fallback) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component
                    .translatableWithFallback("message." + Moleverse.MOD_ID + "." + key, fallback)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

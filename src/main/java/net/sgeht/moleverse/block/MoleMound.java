package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.tag.ModTags;

/**
 * A heap of earth a mole pushed to the surface.
 *
 * <p>Sits on top of the ground the way a carpet does rather than replacing it:
 * turning the grass block itself into a mound would break every vanilla
 * interaction that expects grass there. It has no collision, breaks instantly
 * and drops nothing - it is displaced soil, not a resource.</p>
 *
 * <p>{@link #OPEN} distinguishes the mound with an open shaft from the closed
 * heap. The mole opens it on the way down and closes it behind itself, so a
 * field of mounds shows at a glance which one it is currently inside. A mound
 * placed by a player is always closed.</p>
 */
public class MoleMound extends Block {

    public static final MapCodec<MoleMound> CODEC = simpleCodec(MoleMound::new);

    /** True while the shaft is open, which is to say while a mole is down it. */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    /**
     * True on the one mound at the middle of a colony: the fortress mound.
     *
     * <p>Real moles raise a single oversized heap over the nest, and in wet ground
     * it is the one part of a burrow you can find without a spade. Down below, the
     * colony's core already marks the nest - {@code NestCarver} puts the room there
     * - and this is the same fact said on the surface, so that a player standing in
     * a meadow can tell which of a dozen heaps is worth going down.</p>
     *
     * <p><strong>It is a bigger model on one block and nothing else.</strong> Two
     * shapes that would have read as "2-3 blocks" were both tried on paper and both
     * break something load-bearing. A <em>stack</em> of mounds walks into
     * {@link MoundAttachment#moundUnder}, which counts exactly two blocks down from
     * the heightmap to find the mound under a fitting - and the core is the mound a
     * player is most likely to put the way home on, so the collision would be with
     * the one case that matters. A <em>cluster</em> of extra mounds around the core
     * is worse: every mound is a point of interest, so a skirt of them would count
     * against {@code BurrowConstants.MAX_MOUNDS_IN_RADIUS} and stop moles digging
     * anywhere near their own colony's middle. A property changes the model and
     * touches neither.</p>
     *
     * <p>It also costs nothing anywhere else: both mound blocks are
     * {@code noCollision} and raise no heightmap, the point of interest is built
     * from {@code getPossibleStates}, and the tag is by block. So the fortress is
     * still a mound to every question anything asks about one.</p>
     */
    public static final BooleanProperty FORTRESS = BooleanProperty.create("fortress");

    /**
     * Outline only - the block has no collision, so this is what the player
     * highlights and breaks, not what they walk into. One box covers all three
     * models; their silhouettes differ by a pixel or two and a separate shape
     * per variant would be precision nobody can see.
     */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0);

    /** Chance per tick that an entity moving through the mound kicks up soil. */
    private static final float DUST_CHANCE = 0.35F;

    public MoleMound(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(OPEN, false)
                .setValue(FORTRESS, false));
    }

    // --- what the burrowing mechanic needs from the block ---------------------

    /**
     * Whether a mound could go into this position right now.
     *
     * <p>The cover test is {@link BlockState#canBeReplaced()}, not "is it air".
     * Short grass, ferns and flowers are not air, and rejecting them would reject
     * nearly every meadow a mole lives in - the mole would simply never dig. The
     * fluid is asked separately because water is replaceable too and a mound
     * under water is not a mound.</p>
     */
    public static boolean canPlaceAt(LevelReader level, BlockPos pos) {
        BlockState cover = level.getBlockState(pos);
        return cover.canBeReplaced()
                && cover.getFluidState().isEmpty()
                && level.getBlockState(pos.below()).is(ModTags.Blocks.MOLE_MOUND_PLACEABLE);
    }

    /** True when this position already holds a mound, whatever its shaft is doing. */
    public static boolean isMound(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(ModTags.Blocks.MOLE_MOUNDS);
    }

    /**
     * Puts a mound in, swallowing whatever plant was growing there. No drop: the
     * mole pushed earth over it, it was not harvested.
     *
     * @return false when the site does not take one, in which case nothing changed
     */
    public static boolean tryPlace(ServerLevel level, BlockPos pos, boolean open) {
        if (!canPlaceAt(level, pos)) {
            return false;
        }
        BlockState mound = ModBlocks.MOLE_MOUND.get().defaultBlockState().setValue(OPEN, open);
        return level.setBlock(pos, mound, Block.UPDATE_ALL);
    }

    /**
     * Opens or closes the shaft of an existing mound. Does nothing when the mound
     * is gone - breaking the one a mole went down is allowed and must not strand
     * the caller.
     */
    public static void setOpen(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModTags.Blocks.MOLE_MOUNDS) && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        }
    }

    /**
     * Raises or flattens the heap on an existing mound.
     *
     * <p>Guarded on {@link ModBlocks#MOLE_MOUND} itself rather than on the
     * {@code MOLE_MOUNDS} tag, which is the one thing about this method that is
     * easy to get wrong: {@link PreparedMoleMound} is in that tag and does
     * <em>not</em> carry {@link #FORTRESS}, so a tag-wide guard would read a
     * property off a state that has none and throw. {@link #setOpen} can use the
     * tag because both blocks carry {@link #OPEN}.</p>
     *
     * <p>Shoring a fortress mound up therefore flattens it: the prepared block has
     * no heap to inherit. That is left as it is rather than mirrored onto the
     * second block, and it reads honestly - a heap somebody has shored up is not a
     * heap any more, and the player who did it is the player who has already found
     * the nest and no longer needs the landmark.</p>
     *
     * <p>Does nothing when the mound is gone. A player levelling the core is
     * allowed to, and what is left is an ordinary site that takes an ordinary mound
     * the next time a mole comes up through it.</p>
     */
    public static void setFortress(ServerLevel level, BlockPos pos, boolean fortress) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.MOLE_MOUND.get()) || state.getValue(FORTRESS) == fortress) {
            return;
        }
        level.setBlock(pos, state.setValue(FORTRESS, fortress), Block.UPDATE_ALL);
    }

    @Override
    public MapCodec<? extends MoleMound> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FORTRESS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A mound only holds on soft ground - never on stone, planks or glass. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModTags.Blocks.MOLE_MOUND_PLACEABLE);
    }

    /** Falls away with its support, like a carpet. */
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

    /**
     * Walking through a mound kicks up loose soil.
     *
     * <p>Spawned from the server, not the client. This hook does run on both
     * sides, but only for entities the client simulates itself - which is the
     * local player and nothing else. Mobs are moved by position updates rather
     * than by {@code move()}, so a mole trotting through its own mound would
     * raise no dust at all if this were client side.</p>
     */
    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean intersects) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Standing in a mound should not smoke; only movement disturbs it.
        boolean moving = entity.xOld != entity.getX() || entity.zOld != entity.getZ();
        if (!moving || serverLevel.getRandom().nextFloat() > DUST_CHANCE) {
            return;
        }

        // The three-argument option lets the particle pick its texture from the
        // model at that position, which matters for the open variant: its shaft
        // floor carries a second, darker texture.
        serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state, pos),
                entity.getX(),
                pos.getY() + 0.15,
                entity.getZ(),
                3,
                0.2,
                0.02,
                0.2,
                0.02);
    }
}

package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
        this.registerDefaultState(this.defaultBlockState().setValue(OPEN, false));
    }

    @Override
    public MapCodec<MoleMound> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
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
     * <p>Client side only: this hook runs on both sides, and spawning the
     * particles on each would double every puff.</p>
     */
    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean flag) {
        if (!level.isClientSide()) {
            return;
        }

        // Standing in a mound should not smoke; only movement disturbs it.
        boolean moving = entity.xOld != entity.getX() || entity.zOld != entity.getZ();
        if (!moving || level.getRandom().nextFloat() > DUST_CHANCE) {
            return;
        }

        RandomSource random = level.getRandom();
        level.addParticle(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                entity.getX() + (random.nextDouble() - 0.5) * 0.4,
                pos.getY() + 0.15,
                entity.getZ() + (random.nextDouble() - 0.5) * 0.4,
                (random.nextDouble() - 0.5) * 0.08,
                0.05,
                (random.nextDouble() - 0.5) * 0.08);
    }
}

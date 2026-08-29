package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.entity.Mole;

/**
 * A lamp on a prepared mound that lights up when a mole comes out of it.
 *
 * <p>The first fitting, and deliberately the smallest one that proves the socket
 * works: it needs the prepared mound underneath, it pops off when that mound
 * goes, and its whole behaviour is the one hook every later attachment will use.
 * No block entity, no inventory, nothing to get wrong twice.</p>
 *
 * <p>It is also the answer to a real problem. A network is invisible: mounds sit
 * quiet in a meadow and the moles are underground, so there is nothing to watch.
 * A row of lanterns turns a colony into something that can be seen working from
 * a distance, at night, without a debug overlay - which is the same instinct as
 * the tuning panel, applied to the world rather than to a number.</p>
 *
 * <p>The glow times out through a scheduled tick rather than a block entity.
 * That is what a block entity would be for, and this needs one number that only
 * matters for a few seconds.</p>
 */
public class ShaftLantern extends MoundAttachment {

    public static final MapCodec<ShaftLantern> CODEC = simpleCodec(ShaftLantern::new);

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /** How long the glow lasts after a mole has passed. */
    public static final int GLOW_TICKS = 100;

    /** Light while lit. Bright enough to read a network by, dim enough not to be a torch. */
    public static final int LIGHT_WHEN_LIT = 10;

    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 9.0, 11.0);

    public ShaftLantern(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
    }

    @Override
    public MapCodec<ShaftLantern> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void moleSurfaced(ServerLevel level, BlockPos pos, BlockState state, Mole mole) {
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
        // Re-scheduled on every visit, so a busy mound stays lit rather than
        // flickering out between two moles.
        level.scheduleTick(pos, this, GLOW_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }
}

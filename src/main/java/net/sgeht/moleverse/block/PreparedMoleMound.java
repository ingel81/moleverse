package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A molehill somebody has shored up: a rim of packed earth around the shaft,
 * level enough on top for something to stand on.
 *
 * <p>It is a mound in every way the moles care about - the same open flag, the
 * same point-of-interest type, the same target for a run - and a socket in the
 * one way they do not. A plain molehill is a pixel tall at its highest element,
 * so anything set on the block above it would hang in the air. This is what an
 * attachment rests on.</p>
 *
 * <p>Still {@code noCollision}, however solid the rim looks, and the reason is
 * not cosmetic: a surfacing mole is snapped <em>into</em> the mound's own block
 * by {@code MoleBurrowGoal}. A collision box here - even a ring around the open
 * middle - would squeeze a 0.7 block wide animal out of the very hole it came
 * up. What it does have is an outline the full height of the block, so a player
 * can see and hit it.</p>
 */
public class PreparedMoleMound extends MoleMound {

    public static final MapCodec<PreparedMoleMound> CODEC = simpleCodec(PreparedMoleMound::new);

    /** Outline only, matching the model: a full block with the shaft cut out on top. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public PreparedMoleMound(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends MoleMound> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

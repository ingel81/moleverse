package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.registry.ModBlocks;

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

    /**
     * Breaking the shoring leaves the molehill it was built on.
     *
     * <p>Undoes exactly what {@link net.sgeht.moleverse.item.PreparedMoundItem}
     * did rather than taking the mound with it. The colony's runs are keyed by
     * coordinate, so a mound removed here is a mound removed from every route
     * that ends at it - which is a great deal to lose for taking a fitting's
     * socket back.</p>
     *
     * <p>This hook rather than {@code playerWillDestroy}, because it replaces the
     * removal instead of merely preceding it: NeoForge calls it for the creative
     * path and the survival path alike, and returning true means "handled" so
     * nothing sets air afterwards. The drop is unaffected - {@code playerDestroy}
     * runs on the state that was here, so the shoring comes back as an item.</p>
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            ItemStack toolStack, boolean willHarvest, FluidState fluid) {
        BlockState mound = ModBlocks.MOLE_MOUND.get().defaultBlockState()
                .setValue(OPEN, state.getValue(OPEN));
        return level.setBlock(pos, mound, level.isClientSide() ? 11 : Block.UPDATE_ALL);
    }
}

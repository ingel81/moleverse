package net.sgeht.moleverse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Base for everything a player can fit onto a prepared mound.
 *
 * <p>The socket the exchange chest, the trap and the way into the burrow below
 * will all share. What every attachment gets from here is the same three things:
 * it needs a prepared mound underneath, it falls away when that mound goes, and
 * it hears about a mole surfacing at the mound it sits on.</p>
 *
 * <p>A plain molehill is deliberately not enough. It is one pixel tall, so a
 * fitting on the block above would hang in the air - and shoring one up is the
 * cost that keeps every molehill in a meadow from being a trading post.</p>
 */
public abstract class MoundAttachment extends Block {

    protected MoundAttachment(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * A mole has come up out of the mound under this attachment.
     *
     * <p>Called from the burrow goal, which is at that position anyway - it opens
     * the shaft on the way down and places the mound on the way up - so this
     * costs a lookup rather than a search. Server side only.</p>
     */
    protected void moleSurfaced(ServerLevel level, BlockPos pos, BlockState state, Mole mole) {
    }

    /**
     * Tells whatever sits on this mound that a mole has surfaced in it.
     *
     * <p>The one place that knows the mound-to-fitting relationship, so callers
     * never have to reach for {@code above()} themselves.</p>
     */
    public static void notifySurfaced(ServerLevel level, BlockPos mound, Mole mole) {
        BlockPos above = mound.above();
        BlockState state = level.getBlockState(above);
        if (state.getBlock() instanceof MoundAttachment attachment) {
            attachment.moleSurfaced(level, above, state, mole);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModBlocks.PREPARED_MOLE_MOUND.get());
    }

    /** Falls away with its mound, like a torch with its wall. */
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
}

package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity;
import net.sgeht.moleverse.entity.Mole;

import org.jetbrains.annotations.Nullable;

/**
 * A crate fitted over a prepared mound, where a player trades worms for finds.
 *
 * <p>The first fitting that does something a player has to come back for. A
 * {@link ShaftLantern} reports that the colony is alive; this one is the reason
 * to care. Worms go into the input, and every mole that surfaces through the
 * mound underneath takes one and leaves whatever it happened to dig up in the
 * output.</p>
 *
 * <p>The trade is not an interaction the player performs. Filling the crate is,
 * and after that it is the colony's business - which is what turns a network of
 * mounds from scenery into a thing worth siting deliberately. A station on a
 * mound no mole visits pays nothing, and that is the whole mechanic.</p>
 *
 * <p>What comes back is a placeholder table, see
 * {@link ExchangeStationBlockEntity}.</p>
 */
public class ExchangeStation extends MoundAttachment implements EntityBlock {

    public static final MapCodec<ExchangeStation> CODEC = simpleCodec(ExchangeStation::new);

    /**
     * A crate that sits into the mound's crater rather than on top of it.
     *
     * <p>The prepared mound is a rim with a hole in the middle, so a fitting
     * that started at the full width would look bolted on. Starting one pixel
     * in, and stopping a pixel short of the top, reads as something lowered
     * into the shaft.</p>
     */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

    public ExchangeStation(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ExchangeStation> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExchangeStationBlockEntity(pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MenuProvider provider ? provider : null;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel
                && level.getBlockEntity(pos) instanceof ExchangeStationBlockEntity station) {
            player.openMenu(station);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A mole has come up through the mound. It takes a worm and leaves a find.
     *
     * <p>The station is the only thing that knows whether it can pay, so the
     * decision stays in the block entity - see
     * {@link ExchangeStationBlockEntity#exchange}. Nothing at all happens if it
     * cannot: a full station must not swallow worms it has no room to answer
     * for.</p>
     */
    @Override
    protected void moleSurfaced(ServerLevel level, BlockPos pos, BlockState state, Mole mole) {
        if (level.getBlockEntity(pos) instanceof ExchangeStationBlockEntity station) {
            station.exchange(level);
        }
    }
}

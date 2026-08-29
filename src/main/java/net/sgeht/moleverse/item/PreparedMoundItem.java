package net.sgeht.moleverse.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * Shoring a molehill up, as an item rather than as a right-click on the heap.
 *
 * <p>There used to be two ways in: craft this block and put it down anywhere, or
 * right-click an existing molehill with loose soil. Two routes to one state is
 * one too many, and the loose-soil route was the worse of them - it read as
 * "use an item on a block" while doing something closer to a build, and it left
 * the crafted block able to stand on ground no mole had ever touched.</p>
 *
 * <p>So there is one route now, and it goes through an existing mound. The block
 * replaces the molehill where it stands, which is what keeps every run recorded
 * to that position - a colony's links are keyed by coordinate, and a mound
 * shored up somewhere else would be a new one with no history.</p>
 *
 * <p>{@code useOn} rather than {@code place}: a molehill is not a replaceable
 * block, so ordinary placement would put this on top of it instead of in it.</p>
 */
public class PreparedMoundItem extends BlockItem {

    private static final String HINT_KEY = "item." + Moleverse.MOD_ID + ".prepared_mole_mound.hint";
    private static final String REFUSED_KEY = "message." + Moleverse.MOD_ID + ".prepared_mole_mound.refused";

    public PreparedMoundItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        // The plain mound only. Clicking one that is already shored up does
        // nothing rather than consuming the block for no change.
        if (!state.is(ModBlocks.MOLE_MOUND.get())) {
            if (level.isClientSide() && player != null) {
                player.displayClientMessage(
                        Component.translatable(REFUSED_KEY).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            // The open flag carries over. A mound can be shored up while a mole
            // is down its shaft, and losing that would leave the animal
            // underground with no way marked out.
            BlockState prepared = ModBlocks.PREPARED_MOLE_MOUND.get().defaultBlockState()
                    .setValue(MoleMound.OPEN, state.getValue(MoleMound.OPEN));
            serverLevel.setBlock(pos, prepared, Block.UPDATE_ALL);
            serverLevel.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            context.getItemInHand().consume(1, player);
        }

        return InteractionResult.SUCCESS;
    }

    /** Nothing may place this the ordinary way, or the one route above becomes two again. */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, adder, flag);
        adder.accept(Component.translatable(HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }
}

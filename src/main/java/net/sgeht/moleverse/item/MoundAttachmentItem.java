package net.sgeht.moleverse.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoundAttachment;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The item form of anything that fits onto a prepared mound, which says so.
 *
 * <p>{@link MoundAttachment#canSurvive} refuses every position that has no
 * prepared mound below it, and refusal in vanilla is silence: {@code place}
 * returns {@code FAIL}, no block appears, no sound plays, nothing is said. From
 * the outside that is indistinguishable from a broken item, and it was read as
 * one - the first person to try the colony board reported it as unplaceable.</p>
 *
 * <p>So two things, and the tooltip is the more important of the two: a player
 * who reads it never reaches the failure. The message is only there for the one
 * who did not.</p>
 */
public class MoundAttachmentItem extends BlockItem {

    private static final String HINT_KEY = "item." + Moleverse.MOD_ID + ".mound_attachment.hint";
    private static final String REFUSED_KEY = "message." + Moleverse.MOD_ID + ".mound_attachment.refused";

    public MoundAttachmentItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Says why nothing happened, on the client only.
     *
     * <p>Both sides run the placement and both fail the same way, so the client
     * already knows without being told - and telling it from the server would
     * cost a packet to say the same thing a tick later. The check is on the block
     * below rather than on the result alone, because {@code FAIL} also covers
     * cases this hint would be wrong about, such as somewhere already occupied.</p>
     */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);

        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (result instanceof InteractionResult.Fail && level.isClientSide() && player != null) {
            BlockPos below = context.getClickedPos().below();
            if (!level.getBlockState(below).is(ModBlocks.PREPARED_MOLE_MOUND.get())) {
                player.displayClientMessage(
                        Component.translatable(REFUSED_KEY).withStyle(ChatFormatting.GRAY), true);
            }
        }

        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, adder, flag);
        adder.accept(Component.translatable(HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }
}

package net.sgeht.moleverse.menu;

import java.util.Objects;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModMenus;

/**
 * The screen's server half: the station's two inventories plus the player's.
 *
 * <p>The slot layout is the dispenser's three-by-three grid, which the
 * background texture already draws - top row for the worms going in, the two
 * rows under it for the finds coming out. That the two halves are one grid is
 * the point: the station is a hole in the ground with a crate over it, not a
 * machine with a process to watch.</p>
 *
 * <p>Built twice for every open screen. On the server the handlers are the
 * block entity's own; on the client they are throwaway ones of the same size,
 * filled by the slot synchronisation like a vanilla container. That is why the
 * menu never reaches for the block entity again after construction.</p>
 */
public class ExchangeStationMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOTS = ExchangeStationBlockEntity.INPUT_SLOTS;

    private static final int OUTPUT_SLOTS = ExchangeStationBlockEntity.OUTPUT_SLOTS;

    private static final int STATION_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

    /** Twenty-seven bags plus nine on the hotbar, in that order - see {@code addStandardInventorySlots}. */
    private static final int BAG_SLOTS = 27;

    private static final int HOTBAR_START = STATION_SLOTS + BAG_SLOTS;

    private static final int SLOT_END = HOTBAR_START + 9;

    // The dispenser background's grid: top-left slot, and one slot every 18 px.
    private static final int GRID_X = 62;
    private static final int GRID_Y = 17;
    private static final int GRID_STEP = 18;
    private static final int GRID_COLUMNS = 3;

    /** Where the player's own inventory sits on a 176x166 background. */
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;

    private final ContainerLevelAccess access;

    /** Client-side constructor. The handlers are empty stand-ins that the slot sync fills. */
    public ExchangeStationMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL,
                new ItemStacksResourceHandler(INPUT_SLOTS),
                new ItemStacksResourceHandler(OUTPUT_SLOTS));
    }

    /** Server-side constructor, working directly on the station's inventories. */
    public ExchangeStationMenu(int containerId, Inventory playerInventory, ExchangeStationBlockEntity station) {
        this(containerId, playerInventory,
                ContainerLevelAccess.create(Objects.requireNonNull(station.getLevel()), station.getBlockPos()),
                station.getInput(), station.getOutput());
    }

    private ExchangeStationMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            ItemStacksResourceHandler input,
            ItemStacksResourceHandler output) {
        super(ModMenus.EXCHANGE_STATION.get(), containerId);
        this.access = access;

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            this.addSlot(new ResourceHandlerSlot(input, input::set, slot,
                    GRID_X + slot * GRID_STEP, GRID_Y));
        }

        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            this.addSlot(new FindSlot(output, output::set, slot,
                    GRID_X + slot % GRID_COLUMNS * GRID_STEP,
                    GRID_Y + (1 + slot / GRID_COLUMNS) * GRID_STEP));
        }

        this.addStandardInventorySlots(playerInventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.EXCHANGE_STATION.get());
    }

    /**
     * Shift-click. Out of the station goes to the player; in from the player
     * goes to the input if it is a worm, and between bags and hotbar otherwise.
     *
     * <p>Nothing here checks for earthworms by hand:
     * {@code moveItemStackTo} asks each slot, and the input slots already refuse
     * anything else.</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack inSlot = slot.getItem();
        ItemStack before = inSlot.copy();

        if (index < STATION_SLOTS) {
            // Hotbar first, the way every vanilla container empties into a player.
            if (!this.moveItemStackTo(inSlot, STATION_SLOTS, SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(inSlot, 0, INPUT_SLOTS, false)) {
            if (index < HOTBAR_START) {
                if (!this.moveItemStackTo(inSlot, HOTBAR_START, SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(inSlot, STATION_SLOTS, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (inSlot.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, inSlot);
        return before;
    }

    /**
     * An output slot: things can be taken out of it, never put into it.
     *
     * <p>The handler itself has no filter, because the station has to be able to
     * put a find there. The refusal belongs to the slot, exactly as it does for
     * a furnace's result.</p>
     */
    private static final class FindSlot extends ResourceHandlerSlot {

        private FindSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier,
                int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}

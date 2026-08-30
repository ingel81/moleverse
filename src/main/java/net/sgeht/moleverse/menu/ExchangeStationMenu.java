package net.sgeht.moleverse.menu;

import java.util.Objects;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity.Payment;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity.Tier;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModMenus;

import org.jetbrains.annotations.Nullable;

/**
 * The screen's server half: the station's two inventories plus the player's.
 *
 * <p>Three groups rather than one grid, laid out left to right in the order the
 * thing actually works: the feed goes in on the left, the shaft is in the
 * middle, and what came back is on the right. The station is a crate over a
 * hole, and this is the crate seen from above - which is why the slots are not
 * a block of nine any more. They were, while the background was the dispenser's
 * and the input took one item; a graded input has a left-hand side to belong
 * to.</p>
 *
 * <p>Built twice for every open screen. On the server the handlers are the
 * block entity's own; on the client they are throwaway ones of the same size,
 * filled by the slot synchronisation like a vanilla container. That is why the
 * menu never reaches for the block entity again after construction - it keeps
 * the input handler instead, whichever of the two it was handed, and asks it
 * the grading question directly.</p>
 */
public class ExchangeStationMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOTS = ExchangeStationBlockEntity.INPUT_SLOTS;

    private static final int OUTPUT_SLOTS = ExchangeStationBlockEntity.OUTPUT_SLOTS;

    private static final int STATION_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

    /** Twenty-seven bags plus nine on the hotbar, in that order - see {@code addStandardInventorySlots}. */
    private static final int BAG_SLOTS = 27;

    private static final int HOTBAR_START = STATION_SLOTS + BAG_SLOTS;

    private static final int SLOT_END = HOTBAR_START + 9;

    /**
     * Where the feed slots sit, then where the finds sit.
     *
     * <p>Copies of {@code LAYOUT} in {@code art/generators/exchange_gui.py},
     * which is where the background's holes are cut and therefore the authority
     * on both. {@code python art/generators/exchange_gui.py --layout} prints
     * them, so a slot that has drifted off its well can be spotted without
     * launching the game.</p>
     */
    private static final int[][] FEED_SLOTS = {{16, 17}, {34, 17}, {52, 17}};

    private static final int[][] FIND_SLOTS = {
        {107, 26}, {125, 26}, {143, 26},
        {107, 44}, {125, 44}, {143, 44}};

    /** Where the player's own inventory sits on a 176x166 background. */
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;

    /** The one number the screen needs that the slots do not carry: see {@link #trades()}. */
    private static final int TRADE_COUNT = 0;

    private final ContainerLevelAccess access;

    /**
     * The input, whichever side this menu was built on.
     *
     * <p>Kept so the screen can ask {@code bestPayment} what the contents would
     * be paid at. The client's copy is filled by the ordinary slot sync, so the
     * answer is the same on both sides without a packet of its own - the grade
     * is not state, it is a function of the slots.</p>
     */
    private final ResourceHandler<ItemResource> input;

    private final ContainerData data;

    /** Client-side constructor. The handlers are empty stand-ins that the slot sync fills. */
    public ExchangeStationMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL,
                feedHandler(),
                new ItemStacksResourceHandler(OUTPUT_SLOTS),
                new SimpleContainerData(1));
    }

    /** Server-side constructor, working directly on the station's inventories. */
    public ExchangeStationMenu(int containerId, Inventory playerInventory, ExchangeStationBlockEntity station) {
        this(containerId, playerInventory,
                ContainerLevelAccess.create(Objects.requireNonNull(station.getLevel()), station.getBlockPos()),
                station.getInput(), station.getOutput(), new StationData(station));
    }

    private ExchangeStationMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            ItemStacksResourceHandler input,
            ItemStacksResourceHandler output,
            ContainerData data) {
        super(ModMenus.EXCHANGE_STATION.get(), containerId);
        this.access = access;
        this.input = input;
        this.data = data;

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            this.addSlot(new ResourceHandlerSlot(input, input::set, slot,
                    FEED_SLOTS[slot][0], FEED_SLOTS[slot][1]));
        }

        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            this.addSlot(new FindSlot(output, output::set, slot,
                    FIND_SLOTS[slot][0], FIND_SLOTS[slot][1]));
        }

        this.addStandardInventorySlots(playerInventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
        this.addDataSlots(data);
    }

    /**
     * The client's stand-in for the input, carrying the station's own filter.
     *
     * <p>A plain handler here would let every input slot light up for anything a
     * player drags over it, and the server would then quietly refuse. The
     * predicate is the block entity's, so the two sides cannot drift apart.</p>
     */
    private static ItemStacksResourceHandler feedHandler() {
        return new ItemStacksResourceHandler(INPUT_SLOTS) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return ExchangeStationBlockEntity.accepts(resource);
            }
        };
    }

    /**
     * What the input would be paid at right now, or {@code null} if there is
     * nothing in it a mole would take - which includes a single root nodule,
     * since one is half a payment.
     */
    public @Nullable Tier grade() {
        Payment payment = ExchangeStationBlockEntity.bestPayment(this.input);
        return payment == null ? null : payment.tier();
    }

    /**
     * The station's trade counter, cycling.
     *
     * <p>The screen keeps the last value it saw and animates the shaft when this
     * one differs. It is the only part of a trade a player could otherwise not
     * witness: the mole is underground, the crate is shut, and the slots would
     * have changed the same way if somebody had simply taken something out.</p>
     */
    public int trades() {
        return this.data.get(TRADE_COUNT);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.EXCHANGE_STATION.get());
    }

    /**
     * Shift-click. Out of the station goes to the player; in from the player
     * goes to the input if the station takes it, and between bags and hotbar
     * otherwise.
     *
     * <p>Nothing here checks the feed by hand: {@code moveItemStackTo} asks each
     * slot, and the input slots already refuse anything the station will not
     * take.</p>
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

    /**
     * The server side of the one synced number, read straight off the station.
     *
     * <p>{@code set} does nothing on purpose. A data slot is a one-way street
     * here - the client is being told what the colony did, and there is nothing
     * it could tell back.</p>
     */
    private record StationData(ExchangeStationBlockEntity station) implements ContainerData {

        @Override
        public int get(int index) {
            return this.station.trades();
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}

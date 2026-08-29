package net.sgeht.moleverse.block.entity;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.sgeht.moleverse.menu.ExchangeStationMenu;
import net.sgeht.moleverse.registry.ModBlockEntities;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

import org.jetbrains.annotations.Nullable;

/**
 * The two inventories behind an {@link net.sgeht.moleverse.block.ExchangeStation}
 * and the trade itself.
 *
 * <p>Two handlers rather than one, because the two halves obey different rules:
 * the input takes earthworms and nothing else, the output takes whatever comes
 * back. Splitting them is also what lets the screen and the automation view say
 * "worms go in here, finds come out there" without a slot-index convention
 * nobody can see.</p>
 *
 * <p>Everything here runs server side. The client gets its copy of the contents
 * through the menu's slots like any other container.</p>
 */
public class ExchangeStationBlockEntity extends BlockEntity implements MenuProvider {

    /** Three worms is a few visits' worth without turning the station into a silo. */
    public static final int INPUT_SLOTS = 3;

    /** Twice the input, because the finds are eight different things and do not stack together. */
    public static final int OUTPUT_SLOTS = 6;

    private static final String INPUT_KEY = "input";

    private static final String OUTPUT_KEY = "output";

    /** Quiet enough to be a mole's doing rather than a machine's. */
    private static final float TRADE_VOLUME = 0.7F;

    private static final float TRADE_PITCH = 1.0F;

    /**
     * What a mole brings up, and how often.
     *
     * <p><strong>Placeholder.</strong> Everything in it already exists elsewhere
     * in the mod or in vanilla, and the weights are a first guess at a curve
     * rather than a balanced table. The real version is not a longer list: it is
     * one table per worm tier, so that what a player feeds the colony decides
     * what the colony goes looking for. Until those tiers exist there is nothing
     * to key a second table off, so there is one table and it lives here.</p>
     *
     * <p>The echo shard is the exception worth keeping whatever replaces this.
     * It is the only entry that says the tunnels go somewhere - one in a hundred
     * trades, from a hole in a meadow.</p>
     */
    private static final List<Find> FIND_TABLE = List.of(
            // The worm comes back now and then: a mole that turned up more than
            // it was paid. This is what keeps a fed station from starving.
            new Find(24, ModItems.EARTHWORM),
            new Find(22, ModBlocks.LOOSE_SOIL),
            new Find(16, () -> Items.CLAY_BALL),
            new Find(13, () -> Items.BONE),
            new Find(13, () -> Items.FLINT),
            new Find(6, ModItems.MOLE_PELT),
            new Find(5, () -> Items.AMETHYST_SHARD),
            new Find(1, () -> Items.ECHO_SHARD));

    private static final int TOTAL_WEIGHT = FIND_TABLE.stream().mapToInt(Find::weight).sum();

    /** Earthworms only, and a change in here marks the block entity dirty. */
    private final ItemStacksResourceHandler input = new ItemStacksResourceHandler(INPUT_SLOTS) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return resource.is(ModItems.EARTHWORM.get());
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    /** Whatever the colony turns up. No filter: the find table decides what lands here. */
    private final ItemStacksResourceHandler output = new ItemStacksResourceHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    /**
     * What a hopper or a pipe sees: one handler over both halves, with the two
     * directions each pinned to the half they belong to.
     *
     * <p>Without this a machine could pull the worms straight back out of the
     * input, or stuff junk into the output. The screen does not need it because
     * a slot can refuse a click on its own; automation asks the capability, and
     * the capability has to be able to say no by itself.</p>
     */
    private final ResourceHandler<ItemResource> automationView =
            new CombinedResourceHandler<>(new InsertOnly(this.input), new ExtractOnly(this.output));

    public ExchangeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXCHANGE_STATION.get(), pos, state);
    }

    public ItemStacksResourceHandler getInput() {
        return this.input;
    }

    public ItemStacksResourceHandler getOutput() {
        return this.output;
    }

    /** The view registered for {@code Capabilities.Item.BLOCK}. */
    public ResourceHandler<ItemResource> getAutomationView() {
        return this.automationView;
    }

    /**
     * One worm out of the input, one find into the output.
     *
     * <p>Both halves happen inside a single transaction, which is the whole
     * reason to use one: an output with no room rolls the extraction back, so a
     * full station cannot eat a worm and give nothing for it. Rolling the find
     * before opening the transaction is deliberate - the room needed depends on
     * what came up, and a station with one free slot holding flint has room for
     * more flint and none for a bone.</p>
     *
     * <p>Silent when nothing happened. A sound on a refused trade would be the
     * one cue a player learns to ignore.</p>
     */
    public void exchange(ServerLevel level) {
        ItemStack find = rollFind(level.getRandom());
        boolean traded = false;

        try (Transaction transaction = Transaction.openRoot()) {
            int taken = this.input.extract(ItemResource.of(ModItems.EARTHWORM.get()), 1, transaction);
            int given = taken == 1
                    ? this.output.insert(ItemResource.of(find), find.getCount(), transaction)
                    : 0;
            if (given == find.getCount()) {
                transaction.commit();
                traded = true;
            }
        }

        if (traded) {
            level.playSound(null, this.worldPosition, SoundEvents.DECORATED_POT_INSERT,
                    SoundSource.BLOCKS, TRADE_VOLUME, TRADE_PITCH);
        }
    }

    /** One weighted draw from {@link #FIND_TABLE}. */
    private static ItemStack rollFind(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Find find : FIND_TABLE) {
            roll -= find.weight();
            if (roll < 0) {
                return new ItemStack(find.item().get());
            }
        }
        // Unreachable while the weights are positive; the table is data, so say
        // something harmless rather than trust that it always will be.
        return new ItemStack(ModBlocks.LOOSE_SOIL.get());
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        this.input.deserialize(data.childOrEmpty(INPUT_KEY));
        this.output.deserialize(data.childOrEmpty(OUTPUT_KEY));
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        this.input.serialize(data.child(INPUT_KEY));
        this.output.serialize(data.child(OUTPUT_KEY));
    }

    /**
     * Gives the contents back when the station goes.
     *
     * <p>This covers more than a pickaxe: the mound underneath can be broken
     * instead, and the attachment then falls away through
     * {@code MoundAttachment.updateShape}. Both paths end in the same block
     * change, and this hook is what that change calls.</p>
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.input.copyToList());
            Containers.dropContents(this.level, pos, this.output.copyToList());
        }
    }

    @Override
    public Component getDisplayName() {
        return ModBlocks.EXCHANGE_STATION.get().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ExchangeStationMenu(containerId, playerInventory, this);
    }

    /** One row of {@link #FIND_TABLE}. The item is a supplier because the table is built before registration finishes. */
    private record Find(int weight, Supplier<? extends ItemLike> item) {
    }

    /** The input, as automation may touch it: worms in, nothing out. */
    private static final class InsertOnly extends DelegatingResourceHandler<ItemResource> {

        private InsertOnly(ResourceHandler<ItemResource> delegate) {
            super(delegate);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /** The output, as automation may touch it: finds out, nothing in. */
    private static final class ExtractOnly extends DelegatingResourceHandler<ItemResource> {

        private ExtractOnly(ResourceHandler<ItemResource> delegate) {
            super(delegate);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}

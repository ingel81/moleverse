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
 * the input takes feed and grades it, the output takes whatever comes back.
 * Splitting them is also what lets the screen and the automation view say "worms
 * go in here, finds come out there" without a slot-index convention nobody can
 * see.</p>
 *
 * <p>What a station pays depends on what it was fed - see {@link Tier}. That is
 * the whole of the trade: one mole, one trip, one armful, and the size and the
 * quality of that armful are the feed's doing.</p>
 *
 * <p>Everything here runs server side. The client gets its copy of the contents
 * through the menu's slots like any other container.</p>
 */
public class ExchangeStationBlockEntity extends BlockEntity implements MenuProvider {

    /** Three slots of feed is a few visits' worth without turning the station into a silo. */
    public static final int INPUT_SLOTS = 3;

    /** Twice the input, because the finds are nine different things and do not stack together. */
    public static final int OUTPUT_SLOTS = 6;

    private static final String INPUT_KEY = "input";

    private static final String OUTPUT_KEY = "output";

    /** Quiet enough to be a mole's doing rather than a machine's. */
    private static final float TRADE_VOLUME = 0.7F;

    /**
     * How far the trade counter runs before it starts again.
     *
     * <p>A container's data slots go over the wire as <strong>shorts</strong>
     * - {@code ClientboundContainerSetDataPacket} writes one - so a counter
     * that only ever goes up would wrap into negative numbers on its own
     * terms. It is not a total anybody reads; the screen compares it with the
     * last one it saw and plays its animation when the two differ, and any
     * cycle long enough not to repeat within one frame does that job.</p>
     */
    private static final int TRADE_CYCLE = 1000;

    /** Whatever a station will take, and a change in here marks the block entity dirty. */
    private final ItemStacksResourceHandler input = new ItemStacksResourceHandler(INPUT_SLOTS) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return accepts(resource);
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

    /**
     * Trades so far, modulo {@link #TRADE_CYCLE}, for the screen to notice.
     *
     * <p>Not saved, and it must not be: it is not state the station has, it is
     * a nudge to anybody watching. A station that comes back from disk at zero
     * has lost nothing, and the screen treats the first value it sees as the
     * starting point rather than as a trade.</p>
     */
    private int trades;

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
     * Whether the input will take this at all.
     *
     * <p>Public because the menu builds a second, empty input handler on the
     * client and has to give it the same filter. A slot that refuses a click on
     * one side and takes it on the other is the worst of both.</p>
     */
    public static boolean accepts(ItemResource resource) {
        return Tier.of(resource) != null;
    }

    /**
     * Trades so far, cycling. See {@link #trades} for why it is not a total.
     */
    public int trades() {
        return this.trades;
    }

    /**
     * One payment out of the input, one armful of finds into the output.
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
        Payment payment = bestPayment(this.input);
        if (payment == null) {
            return;
        }

        Tier tier = payment.tier();
        ItemStack find = tier.roll(level.getRandom());
        boolean traded = false;

        try (Transaction transaction = Transaction.openRoot()) {
            int taken = this.input.extract(payment.resource(), tier.price(), transaction);
            int given = taken == tier.price()
                    ? this.output.insert(ItemResource.of(find), find.getCount(), transaction)
                    : 0;
            if (given == find.getCount()) {
                transaction.commit();
                traded = true;
            }
        }

        if (traded) {
            this.trades = (this.trades + 1) % TRADE_CYCLE;
            level.playSound(null, this.worldPosition, SoundEvents.DECORATED_POT_INSERT,
                    SoundSource.BLOCKS, TRADE_VOLUME, tier.pitch());
        }
    }

    /**
     * The best thing in the input the station can actually afford to take.
     *
     * <p>Best first, because that is what the animal would do: a mole offered a
     * fat worm and an earthworm takes the fat worm. It also keeps the cheap
     * lanes out of the way - a bag of root nodules left in the station does not
     * eat the trips a worm was meant to pay for, it waits until the worms are
     * gone.</p>
     *
     * <p>Amounts are summed across slots, so a payment may be spread over
     * several of them, and the exact resource of the first slot found is what
     * gets counted and later extracted. Matching on the item alone would count a
     * renamed worm towards a payment that {@code extract} then cannot collect,
     * and the station would go quiet with no visible reason.</p>
     *
     * <p>Static, and over any handler rather than over this station's own,
     * because the screen has to ask the same question of the copy the menu
     * keeps on the client. Two implementations of "what would this be paid at"
     * would disagree the first time a tier moved, and the one the player can
     * see is the one that would be wrong.</p>
     */
    public static @Nullable Payment bestPayment(ResourceHandler<ItemResource> input) {
        ItemResource[] offered = new ItemResource[Tier.COUNT];
        int[] amounts = new int[Tier.COUNT];

        for (int slot = 0; slot < input.size(); slot++) {
            ItemResource resource = input.getResource(slot);
            Tier tier = Tier.of(resource);
            if (tier == null) {
                continue;
            }
            int index = tier.ordinal();
            if (offered[index] == null) {
                offered[index] = resource;
            }
            if (offered[index].equals(resource)) {
                amounts[index] += input.getAmountAsInt(slot);
            }
        }

        // Backwards: the ladder is declared poorest first, so the richest tier
        // that has enough in the box wins.
        for (int index = Tier.COUNT - 1; index >= 0; index--) {
            Tier tier = Tier.VALUES[index];
            if (offered[index] != null && amounts[index] >= tier.price()) {
                return new Payment(tier, offered[index]);
            }
        }
        return null;
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

    /** One row of a find table. The item is a supplier because the tables are built before registration finishes. */
    private record Find(int weight, Supplier<? extends ItemLike> item) {
    }

    /** A tier the input can pay with, together with the exact resource it was found as. */
    public record Payment(Tier tier, ItemResource resource) {
    }

    /**
     * What a station takes, what it costs, and what it pays for it.
     *
     * <p>The block entity used to hold one find table and a note saying the real
     * version was one table per worm tier, so that what a player feeds the
     * colony decides what the colony goes looking for. This is that: the tier
     * decides how many finds come back <em>and</em> which table they are drawn
     * from, and a better worm buys both. Neither alone would do - more of the
     * same gravel is not a reward, and a better table handed out one item at a
     * time is a difference nobody notices.</p>
     *
     * <p>One roll per trade, and the whole armful is that one find. A mole comes
     * up from one trip through one kind of ground; six different things out of a
     * single hole would read as a shop. It also keeps the room the output needs
     * down to a slot, so a well-stocked station never has to refuse the best
     * worm in it for want of space.</p>
     *
     * <p>Declared poorest first. The order is the ladder - {@link #pitch} reads
     * it, the scan in {@code bestPayment} reads it, and the screen lights the
     * gauge socket at {@code ordinal()} - so a new tier goes in at its value,
     * not at the end.</p>
     *
     * <p>Public, unlike everything else in here, because the screen shows the
     * ladder: the feed's own icon, what one trip costs and what it pays. The
     * table itself stays private - what comes out of a trade is the colony's
     * business and the screen has no need to know it.</p>
     *
     * <p>The prices, and the reason for each:</p>
     * <ul>
     * <li><strong>Root nodules, two for one poor find.</strong> Moles dig
     * through nodules all day, so a colony will certainly take them, and a
     * player who has been cutting corridor walls has more than they can eat. It
     * is bulk feed and it is priced like bulk feed: two for one trip, out of a
     * table with no worm in it and nothing rare. That last part is the whole
     * point - a nodule lane that paid worms would turn a spade into a worm farm
     * and make the worm box pointless.</li>
     * <li><strong>Earthworms, one for one.</strong> The base rate everything
     * else is measured against, and its table is unchanged from the one this
     * station shipped with.</li>
     * <li><strong>Fat worms, one for three.</strong> Roughly four earthworms
     * once the better table is counted. A worm box gives up at most one per
     * harvest and only on rich feed, so this is what that chance is for -
     * {@code ModItems.FAT_WORM} has said so since it was registered.</li>
     * <li><strong>Glow worms, one for six.</strong> Nearer nine or ten
     * earthworms with the table, which sounds steep until you count what one
     * costs: glow mycelium grows nowhere but the burrow, so every glow worm in
     * the input is a descent. The colony digs deeper for it, which is what its
     * table says.</li>
     * </ul>
     */
    public enum Tier {

        /**
         * Bulk feed. Loose soil, a stone, the odd bone - what a mole scrapes
         * past on a short trip and cannot be bothered to carry far.
         */
        ROOT_NODULE(ModItems.ROOT_NODULE, 2, 1, List.of(
                new Find(45, ModBlocks.LOOSE_SOIL),
                new Find(25, () -> Items.CLAY_BALL),
                new Find(20, () -> Items.FLINT),
                new Find(10, () -> Items.BONE))),

        /**
         * The base table, unchanged.
         *
         * <p>The worm comes back now and then: a mole that turned up more than
         * it was paid, and what keeps a fed station from starving. The echo
         * shard is the other entry worth keeping - it is the only one that says
         * the tunnels go somewhere, one in a hundred trades, from a hole in a
         * meadow.</p>
         */
        EARTHWORM(ModItems.EARTHWORM, 1, 1, List.of(
                new Find(24, ModItems.EARTHWORM),
                new Find(22, ModBlocks.LOOSE_SOIL),
                new Find(16, () -> Items.CLAY_BALL),
                new Find(13, () -> Items.BONE),
                new Find(13, () -> Items.FLINT),
                new Find(6, ModItems.MOLE_PELT),
                new Find(5, () -> Items.AMETHYST_SHARD),
                new Find(1, () -> Items.ECHO_SHARD))),

        /**
         * The same ground, dug with more appetite: less of the spoil, more of
         * what was in it. The iron nugget is the first entry that is not simply
         * lying in topsoil.
         */
        FAT_WORM(ModItems.FAT_WORM, 1, 3, List.of(
                new Find(22, ModItems.EARTHWORM),
                new Find(12, ModBlocks.LOOSE_SOIL),
                new Find(12, () -> Items.CLAY_BALL),
                new Find(13, () -> Items.BONE),
                new Find(13, () -> Items.FLINT),
                new Find(10, ModItems.MOLE_PELT),
                new Find(10, () -> Items.IRON_NUGGET),
                new Find(6, () -> Items.AMETHYST_SHARD),
                new Find(2, () -> Items.ECHO_SHARD))),

        /**
         * A colony that has eaten from the burrow digs towards it. Barely any
         * spoil left, amethyst as often as flint, and the echo shard at four in
         * a hundred instead of one.
         */
        GLOW_WORM(ModItems.GLOW_WORM, 1, 6, List.of(
                new Find(18, ModItems.EARTHWORM),
                new Find(8, ModBlocks.LOOSE_SOIL),
                new Find(8, () -> Items.CLAY_BALL),
                new Find(10, () -> Items.BONE),
                new Find(10, () -> Items.FLINT),
                new Find(12, ModItems.MOLE_PELT),
                new Find(12, () -> Items.IRON_NUGGET),
                new Find(18, () -> Items.AMETHYST_SHARD),
                new Find(4, () -> Items.ECHO_SHARD)));

        /** Cached, because {@code values()} hands out a fresh array every call and this runs per mole. */
        private static final Tier[] VALUES = values();

        private static final int COUNT = VALUES.length;

        private final Supplier<? extends ItemLike> feed;

        private final int price;

        private final int payout;

        private final List<Find> table;

        private final int totalWeight;

        Tier(Supplier<? extends ItemLike> feed, int price, int payout, List<Find> table) {
            this.feed = feed;
            this.price = price;
            this.payout = payout;
            this.table = table;
            this.totalWeight = table.stream().mapToInt(Find::weight).sum();
        }

        /** The tier this resource pays at, or {@code null} if the station will not take it. */
        public static @Nullable Tier of(ItemResource resource) {
            for (Tier tier : VALUES) {
                if (resource.is(tier.feed.get())) {
                    return tier;
                }
            }
            return null;
        }

        /** How many of the feed item one trade costs. */
        public int price() {
            return this.price;
        }

        /** How many finds one trade hands back. */
        public int payout() {
            return this.payout;
        }

        /** The item this tier is paid in. Resolved on call: the tables are built before registration. */
        public ItemLike feed() {
            return this.feed.get();
        }

        /**
         * One weighted draw, in the size this tier pays.
         *
         * <p>The count is on the stack rather than on a second roll: what a mole
         * brings up is what it dug through, and it brings up more of it when it
         * has been fed properly.</p>
         */
        private ItemStack roll(RandomSource random) {
            int roll = random.nextInt(this.totalWeight);
            for (Find find : this.table) {
                roll -= find.weight();
                if (roll < 0) {
                    return new ItemStack(find.item().get(), this.payout);
                }
            }
            // Unreachable while the weights are positive; the tables are data, so
            // say something harmless rather than trust that they always will be.
            return new ItemStack(ModBlocks.LOOSE_SOIL.get(), this.payout);
        }

        /**
         * The trade sound, a step higher per tier.
         *
         * <p>The only thing that tells a player from earshot that the grading
         * exists at all. Everything else about a trade looks the same from
         * outside: the crate is shut and the mole is underground.</p>
         */
        private float pitch() {
            return 0.9F + this.ordinal() * 0.1F;
        }
    }

    /** The input, as automation may touch it: feed in, nothing out. */
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

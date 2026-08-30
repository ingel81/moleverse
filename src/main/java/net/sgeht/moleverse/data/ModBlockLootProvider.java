package net.sgeht.moleverse.data;

import java.util.List;
import java.util.Set;

import net.minecraft.advancements.criterion.LightPredicate;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Loot tables for this mod's blocks.
 *
 * <p>{@link #getKnownBlocks()} is narrowed to our own blocks so the validation
 * pass does not demand tables for the entire vanilla block registry.</p>
 */
public final class ModBlockLootProvider extends BlockLootSubProvider {

    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    /** Roughly every fifth mound has a worm in it. */
    private static final float EARTHWORM_CHANCE = 0.2F;

    /**
     * How often a larder gives up a fat worm on top of the ordinary ones.
     *
     * <p>One in seven, which is about one per alcove: a larder room is a wall of
     * these blocks, so a chance that reads as generous per block comes out as a
     * heap per room. The fat worm is the reason to clear a larder rather than take
     * two blocks out of it, and one per room is exactly the size of that
     * reason.</p>
     */
    private static final float FAT_WORM_CHANCE = 0.15F;

    /**
     * How often a larder in a lit room gives up a glow worm as well.
     *
     * <p>Deliberately lower than the fat worm's, because unlike the fat worm this
     * one can be farmed: a player who lights an alcove has turned every block in it
     * into a glow-worm roll. Lighting a larder is meant to be worth doing, not
     * worth doing to the exclusion of everything else - and there is no silk touch
     * shortcut, so the blocks do not come back.</p>
     */
    private static final float GLOW_WORM_CHANCE = 0.1F;

    /**
     * How bright a larder's surroundings have to be for the glow worms to be in
     * it.
     *
     * <p>Eight, which is vanilla's own line between lit and dark and the number
     * every other light rule a player has met is written against.</p>
     *
     * <p><strong>Work out what that reaches before moving it.</strong> Block light
     * falls by one per block, so a source of level {@code L} clears eight out to
     * {@code L - 8} blocks and no further. This mod's own lights are dim: glow
     * mycelium is 9 and so only counts from the very next block, a lit shaft
     * lantern is 10 and counts from two, and a shrink post at 5 never counts at
     * all. A vanilla torch is 14 and lights a whole alcove out to six.</p>
     *
     * <p>That is the rule doing its job rather than failing at it. Corridors are
     * dressed with mycelium everywhere, so a threshold the ambient glow could reach
     * would hand out glow worms for standing in a corridor, and the upkeep in
     * BURROW_LIFE's "light is upkeep" would cost nobody anything. Eight means
     * somebody brought a light and put it near the larder - which is the whole
     * transaction.</p>
     */
    private static final int LARDER_LIGHT = 8;

    /**
     * How often a root nodule has a worm curled up in it.
     *
     * <p>Below the mound's own {@link #EARTHWORM_CHANCE}, because a nodule is
     * already worth digging for on its own account. Worms live where roots are, so
     * some rate above zero is the honest answer; this one keeps the nodule a root
     * that sometimes has a worm rather than a worm dispenser with a root on
     * it.</p>
     */
    private static final float NODULE_WORM_CHANCE = 0.15F;

    @Override
    protected void generate() {
        dropSelf(ModBlocks.LOOSE_SOIL.get());

        // Unlike the heap it was made from, this gives itself back: the work put
        // into it is the point, and losing it to a misplaced click would make
        // anybody stop building them.
        dropSelf(ModBlocks.PREPARED_MOLE_MOUND.get());
        dropSelf(ModBlocks.SHAFT_LANTERN.get());
        dropSelf(ModBlocks.SHRINK_POST.get());
        dropSelf(ModBlocks.ROOT_BEAM.get());
        dropSelf(ModBlocks.EXCHANGE_STATION.get());
        dropSelf(ModBlocks.GRUNTING_POST.get());
        dropSelf(ModBlocks.COLONY_BOARD.get());
        dropSelf(ModBlocks.WORM_BOX.get());
        dropSelf(ModBlocks.MOLE_TRAP.get());

        // The point of a larder is what is in it, and the block itself does not
        // come back - it is a hole in a wall, not a crate.
        //
        // One or two worms rather than the two to four it used to be. The number
        // was set when a larder was a single block of furniture in a chamber;
        // ChamberFurnisher now studs a whole alcove with them, so the same rate
        // per block is a stack per room. What a room is worth is the number that
        // matters, and it is the count of blocks that went up, not this.
        add(ModBlocks.WORM_LARDER.get(), block -> LootTable.lootTable()
                .withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(UniformGenerator.between(1.0F, 2.0F))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get()))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(FAT_WORM_CHANCE))
                        .add(LootItem.lootTableItem(ModItems.FAT_WORM.get())))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(GLOW_WORM_CHANCE))
                        .add(litOnAnySide(ModItems.GLOW_WORM.get()))));
        dropSelf(ModBlocks.GLOW_MYCELIUM.get());

        // A pocket in the lining, and the one thing worth putting a spade into a
        // wall for. It gives the item and never the block: the pocket is where the
        // ground grew one, not a thing a player carries around and plants.
        add(ModBlocks.ROOT_NODULE.get(), block -> LootTable.lootTable()
                .withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.ROOT_NODULE.get())
                                .apply(SetItemCountFunction.setCount(
                                        UniformGenerator.between(1.0F, 2.0F))))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(NODULE_WORM_CHANCE))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get()))));
        // Unbreakable in play; the table exists so the generator does not complain
        // about a block it knows nothing about.
        dropSelf(ModBlocks.DEEP_EARTH.get());

        // A mound gives back the earth a mole pushed up, and now and then what
        // the mole was after in the first place. That second pool is the reason
        // to dig a mound open rather than walk past it.
        add(ModBlocks.MOLE_MOUND.get(), block -> LootTable.lootTable()
                .withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModBlocks.LOOSE_SOIL.get()))))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(EARTHWORM_CHANCE))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get()))));
    }

    /**
     * One item, dropped only when the room the block sits in is lit.
     *
     * <p>Six alternatives, one per face, and the first that matches wins - so the
     * pool gives at most one item however many sides are bright. A larder is packed
     * into a wall on five sides and open on the sixth, and which sixth is
     * {@code ChamberFurnisher}'s business rather than this file's, so the question
     * is asked of all of them instead of guessing.</p>
     *
     * <p><strong>The neighbours and never the block itself.</strong> A block's own
     * position is the obvious place to measure and it is the wrong one twice over.
     * A larder is opaque, so while it stands the light inside it is nothing; and
     * loot is rolled the instant the block is removed, before the light engine has
     * run - it queues the update and the server flushes it later in the tick. So
     * the reading at the block is zero whether the room is lit or not, and the
     * condition would simply never fire. The neighbouring air was air all along and
     * its light is already correct, which makes this a stable question with a
     * stable answer.</p>
     */
    private static LootPoolEntryContainer.Builder<?> litOnAnySide(ItemLike item) {
        return AlternativesEntry.alternatives(List.of(Direction.values()),
                face -> LootItem.lootTableItem(item).when(litAt(face)));
    }

    /** The room next to this face is at least {@link #LARDER_LIGHT} bright. */
    private static LootItemCondition.Builder litAt(Direction face) {
        return LocationCheck.checkLocation(
                LocationPredicate.Builder.location()
                        .setLight(LightPredicate.Builder.light()
                                .setComposite(MinMaxBounds.Ints.atLeast(LARDER_LIGHT))),
                BlockPos.ZERO.relative(face));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.REGISTER.getEntries().stream()
                .map(holder -> (Block) holder.value())
                .toList();
    }
}

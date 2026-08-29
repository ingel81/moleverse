package net.sgeht.moleverse.data;

import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
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

        // The point of a larder is what is in it. Two to four worms, and the
        // block itself does not come back - it is a hole in a wall, not a crate.
        add(ModBlocks.WORM_LARDER.get(), block -> LootTable.lootTable()
                .withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(UniformGenerator.between(2.0F, 4.0F))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get())))));
        dropSelf(ModBlocks.GLOW_MYCELIUM.get());
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

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.REGISTER.getEntries().stream()
                .map(holder -> (Block) holder.value())
                .toList();
    }
}

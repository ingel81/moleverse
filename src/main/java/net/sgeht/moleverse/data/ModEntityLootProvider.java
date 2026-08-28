package net.sgeht.moleverse.data;

import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Loot tables for this mod's entities.
 *
 * <p>{@link #getKnownEntityTypes()} is narrowed to our own types, otherwise the
 * validation pass demands a table for every entity in the game.</p>
 */
public final class ModEntityLootProvider extends EntityLootSubProvider {

    public ModEntityLootProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        // One mole, one pelt. A uniform 0..1 count would round down half the
        // time, which reads as a broken drop rather than as rarity.
        add(ModEntities.MOLE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.MOLE_PELT.get())
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return ModEntities.REGISTER.getEntries().stream().map(holder -> (EntityType<?>) holder.value());
    }
}

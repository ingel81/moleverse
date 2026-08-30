package net.sgeht.moleverse.data;

import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
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

        // A great worm is an earthworm at the burrow's scale, so what it leaves
        // is earthworms - several, because there is a great deal of it.
        add(ModEntities.GREAT_WORM.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 4.0F))))));

        // A small worm is a worm. Constant 1, not uniform 0..1 - that rounds
        // down half the time and reads as a broken drop rather than as rarity.
        add(ModEntities.EARTHWORM.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get())
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))));

        add(ModEntities.SOIL_BEETLE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.CHITIN_FLAKE.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 1.0F))))));

        add(ModEntities.SHREW.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.EARTHWORM.get())
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
                                .when(LootItemRandomChanceCondition.randomChance(0.3F)))));

        // An incursion is an event every few evenings, and whoever ends one
        // gets paid for it: 1-2 pelts guaranteed. No chitin - chitin comes off
        // armoured animals, and a weasel wears fur. The pelt is the punchline:
        // it is what the animal ate.
        add(ModEntities.WEASEL.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.MOLE_PELT.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F))))));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return ModEntities.REGISTER.getEntries().stream().map(holder -> (EntityType<?>) holder.value());
    }
}

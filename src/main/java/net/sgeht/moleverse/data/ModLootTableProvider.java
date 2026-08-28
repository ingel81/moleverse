package net.sgeht.moleverse.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Bundles every loot sub provider of this mod. */
public final class ModLootTableProvider extends LootTableProvider {

    public ModLootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output,
                Set.of(),
                List.of(new SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                registries);
    }
}

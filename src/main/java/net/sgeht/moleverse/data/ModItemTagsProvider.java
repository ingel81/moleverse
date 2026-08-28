package net.sgeht.moleverse.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModItems;
import net.sgeht.moleverse.tag.ModTags;

/** Item tags of this mod. */
public final class ModItemTagsProvider extends ItemTagsProvider {

    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Moleverse.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(ModTags.Items.MOLE_MATERIALS)
                .add(ModItems.MOLE_PELT.get());
    }
}

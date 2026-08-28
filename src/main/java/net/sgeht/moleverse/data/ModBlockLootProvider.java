package net.sgeht.moleverse.data;

import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.sgeht.moleverse.registry.ModBlocks;

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

    @Override
    protected void generate() {
        dropSelf(ModBlocks.LOOSE_SOIL.get());

        // Displaced earth, not a resource. Breaking a mound gives nothing.
        add(ModBlocks.MOLE_MOUND.get(), noDrop());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.REGISTER.getEntries().stream()
                .map(holder -> (Block) holder.value())
                .toList();
    }
}

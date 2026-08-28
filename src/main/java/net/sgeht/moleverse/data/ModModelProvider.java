package net.sgeht.moleverse.data;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Generates blockstates, block models and item models.
 *
 * <p>The provider validates its own output: every block and item registered in
 * this mod's namespace must be covered here, otherwise {@code runData} fails.
 * That check is the reason not to hand-write these files any more.</p>
 */
public final class ModModelProvider extends ModelProvider {

    public ModModelProvider(PackOutput output) {
        super(output, Moleverse.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Cube with the same texture on all faces; the block item reuses the block model.
        blockModels.createTrivialCube(ModBlocks.LOOSE_SOIL.get());

        itemModels.generateFlatItem(ModItems.MOLE_PELT.get(), ModelTemplates.FLAT_ITEM);

        // Spawn eggs carry their own texture in this version rather than the
        // old two-layer tinted template, so a flat item model is all it needs.
        itemModels.generateFlatItem(ModItems.MOLE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
    }
}

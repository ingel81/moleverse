package net.sgeht.moleverse.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.tag.ModTags;

/** Block tags of this mod, plus our additions to vanilla tags. */
public final class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Moleverse.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(ModTags.Blocks.MOLE_DIGGABLE)
                .add(ModBlocks.LOOSE_SOIL.get())
                .add(Blocks.DIRT)
                .add(Blocks.GRASS_BLOCK)
                .add(Blocks.COARSE_DIRT)
                .add(Blocks.ROOTED_DIRT)
                .add(Blocks.PODZOL)
                .add(Blocks.MYCELIUM)
                .add(Blocks.SAND)
                .add(Blocks.GRAVEL)
                .add(Blocks.MUD)
                .add(Blocks.CLAY);

        // Same list as above for now, plus farmland: a mound in the wheat is
        // exactly what real gardeners curse about, and it only blocks the one
        // planting spot until it is knocked away.
        tag(ModTags.Blocks.MOLE_MOUND_PLACEABLE)
                .add(ModBlocks.LOOSE_SOIL.get())
                .add(Blocks.DIRT)
                .add(Blocks.GRASS_BLOCK)
                .add(Blocks.COARSE_DIRT)
                .add(Blocks.ROOTED_DIRT)
                .add(Blocks.PODZOL)
                .add(Blocks.MYCELIUM)
                .add(Blocks.SAND)
                .add(Blocks.GRAVEL)
                .add(Blocks.MUD)
                .add(Blocks.CLAY)
                .add(Blocks.FARMLAND);

        tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(ModBlocks.LOOSE_SOIL.get())
                .add(ModBlocks.MOLE_MOUND.get());
    }
}

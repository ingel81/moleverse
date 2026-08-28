package net.sgeht.moleverse.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.KeyTagProvider;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.tag.ModTags;

/**
 * Biome tags of this mod.
 *
 * <p>Spawning goes through a tag rather than a list of biomes in the biome
 * modifier, so that where moles live can be changed by a data pack without
 * touching the mod - and so that the list has one home instead of being spread
 * across generated JSON.</p>
 */
public final class ModBiomeTagsProvider extends KeyTagProvider<Biome> {

    public ModBiomeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.BIOME, lookupProvider, Moleverse.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Grassland and woodland with soft ground. Deliberately not the whole
        // overworld: bare rock, sand and snow give a mole nothing to dig.
        tag(ModTags.Biomes.SPAWNS_MOLES)
                .add(Biomes.PLAINS)
                .add(Biomes.SUNFLOWER_PLAINS)
                .add(Biomes.MEADOW)
                .add(Biomes.FOREST)
                .add(Biomes.FLOWER_FOREST)
                .add(Biomes.BIRCH_FOREST)
                .add(Biomes.OLD_GROWTH_BIRCH_FOREST)
                .add(Biomes.DARK_FOREST)
                .add(Biomes.TAIGA)
                .add(Biomes.OLD_GROWTH_PINE_TAIGA)
                .add(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
    }
}

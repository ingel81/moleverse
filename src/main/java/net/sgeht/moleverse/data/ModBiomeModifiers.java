package net.sgeht.moleverse.data;

import java.util.List;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.tag.ModTags;

/**
 * Where moles turn up on their own.
 *
 * <p>Written into the datapack registry, so it lands as
 * {@code data/moleverse/neoforge/biome_modifier/spawn_moles.json} and can be
 * overridden by a data pack.</p>
 */
public final class ModBiomeModifiers {

    public static final ResourceKey<BiomeModifier> SPAWN_MOLES = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS, Moleverse.id("spawn_moles"));

    /**
     * Low next to vanilla animals - a pig is 10, a rabbit 4. A mole is meant to
     * be a find rather than scenery, and it leaves mounds behind, so a common
     * one would pave a meadow before a player ever saw it dig.
     */
    private static final int WEIGHT = 3;

    private static final int MIN_GROUP = 1;
    private static final int MAX_GROUP = 2;

    private ModBiomeModifiers() {
    }

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        Weighted<MobSpawnSettings.SpawnerData> mole = new Weighted<>(
                new MobSpawnSettings.SpawnerData(ModEntities.MOLE.get(), MIN_GROUP, MAX_GROUP),
                WEIGHT);

        context.register(SPAWN_MOLES, new BiomeModifiers.AddSpawnsBiomeModifier(
                biomes.getOrThrow(ModTags.Biomes.SPAWNS_MOLES),
                WeightedList.of(List.of(mole))));
    }
}

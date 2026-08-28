package net.sgeht.moleverse.data;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.sgeht.moleverse.Moleverse;

/**
 * Entry point for data generation. Only runs under {@code ./gradlew runData}.
 *
 * <p>Everything registered here writes to {@code src/generated/resources},
 * which is part of the shipped jar. Anything a generator produces must not also
 * exist by hand under {@code src/main/resources} — the two would collide.</p>
 *
 * <p>Bound to {@link Dist#CLIENT} because model generation lives in client-only
 * classes and {@code runData} launches a client data run.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID, value = Dist.CLIENT)
public final class MoleverseDataGenerators {

    private MoleverseDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        var generator = event.getGenerator();
        var lookup = event.getLookupProvider();
        var pack = generator.getVanillaPack(true);

        // Blockstates, block models and item models.
        pack.addProvider(ModModelProvider::new);

        // Loot tables.
        pack.addProvider(output -> new ModLootTableProvider(output, lookup));

        // Tags. Item tags may need the block tag contents later, so keep the handle.
        var blockTags = pack.addProvider(output -> new ModBlockTagsProvider(output, lookup));
        pack.addProvider(output -> new ModItemTagsProvider(output, lookup));

        // Base localisation. Other locales stay hand-written under src/main/resources.
        pack.addProvider(output -> new ModLanguageProvider(output, "en_us"));
    }
}

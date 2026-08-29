package net.sgeht.moleverse.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Base localisation (en_us).
 *
 * <p>Only the source locale is generated. Translations live as hand-written
 * files under {@code src/main/resources/assets/moleverse/lang}.</p>
 */
public final class ModLanguageProvider extends LanguageProvider {

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, Moleverse.MOD_ID, locale);
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + Moleverse.MOD_ID + ".main", "Moleverse");

        addBlock(ModBlocks.LOOSE_SOIL, "Loose Soil");
        addBlock(ModBlocks.MOLE_MOUND, "Mole Mound");
        addBlock(ModBlocks.PREPARED_MOLE_MOUND, "Prepared Mole Mound");
        addBlock(ModBlocks.SHAFT_LANTERN, "Shaft Lantern");
        addBlock(ModBlocks.SHRINK_POST, "Shrink Post");
        addBlock(ModBlocks.WORM_LARDER, "Worm Larder");
        addBlock(ModBlocks.EXCHANGE_STATION, "Exchange Station");
        addBlock(ModBlocks.GRUNTING_POST, "Grunting Post");
        addBlock(ModBlocks.COLONY_BOARD, "Colony Board");
        addBlock(ModBlocks.ROOT_BEAM, "Root Beam");
        addBlock(ModBlocks.GLOW_MYCELIUM, "Glow Mycelium");
        addBlock(ModBlocks.DEEP_EARTH, "Deep Earth");
        addItem(ModItems.MOLE_PELT, "Mole Pelt");
        addItem(ModItems.EARTHWORM, "Earthworm");
        addItem(ModItems.MOLE_SPAWN_EGG, "Mole Spawn Egg");
        addItem(ModItems.GREAT_WORM_SPAWN_EGG, "Great Worm Spawn Egg");
        addEntityType(ModEntities.MOLE, "Mole");
        addEntityType(ModEntities.GREAT_WORM, "Great Worm");

        add("message." + Moleverse.MOD_ID + ".greeting", "Hello, mole. The tunnels are waiting.");

        // Subtitles. Without these, players with subtitles enabled see nothing.
        add("subtitles." + Moleverse.MOD_ID + ".entity.mole.dig", "Mole digs");
        add("subtitles." + Moleverse.MOD_ID + ".entity.mole.sniff", "Mole sniffs");
        add("subtitles." + Moleverse.MOD_ID + ".entity.mole.surface", "Mole surfaces");

        add(Moleverse.MOD_ID + ".configuration.title", "Moleverse Configuration");
        add(Moleverse.MOD_ID + ".configuration.debugLogging", "Debug logging");
        add(Moleverse.MOD_ID + ".configuration.debugLogging.tooltip",
                "Write additional Moleverse debug output to the log.");
        add(Moleverse.MOD_ID + ".configuration.greetPlayer", "Greet player");
        add(Moleverse.MOD_ID + ".configuration.greetPlayer.tooltip",
                "Greet the player with a Moleverse message when they join a world.");
    }
}

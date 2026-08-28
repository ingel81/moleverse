package net.sgeht.moleverse.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModBlocks;
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
        addItem(ModItems.MOLE_PELT, "Mole Pelt");

        add("message." + Moleverse.MOD_ID + ".greeting", "Hello, mole. The tunnels are waiting.");

        add(Moleverse.MOD_ID + ".configuration.title", "Moleverse Configuration");
        add(Moleverse.MOD_ID + ".configuration.debugLogging", "Debug logging");
        add(Moleverse.MOD_ID + ".configuration.debugLogging.tooltip",
                "Write additional Moleverse debug output to the log.");
        add(Moleverse.MOD_ID + ".configuration.greetPlayer", "Greet player");
        add(Moleverse.MOD_ID + ".configuration.greetPlayer.tooltip",
                "Greet the player with a Moleverse message when they join a world.");
    }
}

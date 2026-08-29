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

    /** Title and description of one advancement, under the keys the tree asks for. */
    private void advancement(String name, String title, String description) {
        add("advancements." + Moleverse.MOD_ID + "." + name + ".title", title);
        add("advancements." + Moleverse.MOD_ID + "." + name + ".description", description);
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
        addItem(ModItems.FAT_WORM, "Fat Worm");
        addItem(ModItems.GLOW_WORM, "Glow Worm");
        addBlock(ModBlocks.WORM_BOX, "Worm Box");
        addItem(ModItems.MOLE_SPAWN_EGG, "Mole Spawn Egg");
        addItem(ModItems.GREAT_WORM_SPAWN_EGG, "Great Worm Spawn Egg");
        addEntityType(ModEntities.MOLE, "Mole");
        addEntityType(ModEntities.GREAT_WORM, "Great Worm");

        add("message." + Moleverse.MOD_ID + ".greeting", "Hello, mole. The tunnels are waiting.");

        // The advancement tree doubles as a checklist: one that never fires is a
        // feature that never ran.
        advancement("root", "Something Has Been Digging",
                "A heap of earth where there was none, and whatever left it is not here now.");
        advancement("own_mound", "A Mound of Your Own",
                "Place one yourself. The moles will treat it as theirs, which is the point.");
        advancement("shored_up", "Shored Up",
                "A molehill is a heap. Give it a rim and it can carry something.");
        advancement("fitting", "Fitted",
                "Every fitting answers the same question: has a mole just come up here?");
        advancement("down_there", "Down There",
                "The run a mole dug through one block of soil, at the size it is to a mole.");
        advancement("scale_of_it", "The Scale of It",
                "The same earthworm you farm, as tall as you are.");
        advancement("a_find", "A Find",
                "Worms in, something else out. What exactly is not settled yet.");
        advancement("deeper", "Deeper",
                "Some things only exist below, and this is one of them.");

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

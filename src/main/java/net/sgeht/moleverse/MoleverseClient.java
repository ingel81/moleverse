package net.sgeht.moleverse;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-seitiger Einstiegspunkt. Wird auf dedizierten Servern nicht geladen,
 * daher darf hier direkt auf Client-Klassen zugegriffen werden.
 */
@Mod(value = Moleverse.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Moleverse.MOD_ID, value = Dist.CLIENT)
public final class MoleverseClient {

    public MoleverseClient(ModContainer container) {
        // Generischer Config-Screen unter Mods -> Moleverse -> Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Moleverse.LOGGER.debug("Client setup abgeschlossen.");
    }
}

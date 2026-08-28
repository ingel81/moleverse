package net.sgeht.moleverse;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.sgeht.moleverse.client.debug.MoleDebugCommand;
import net.sgeht.moleverse.client.render.MoleModel;
import net.sgeht.moleverse.client.render.MoleRenderer;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * Client-side entry point. Not loaded on dedicated servers, so client classes
 * may be referenced directly from here.
 */
@Mod(value = Moleverse.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Moleverse.MOD_ID, value = Dist.CLIENT)
public final class MoleverseClient {

    public MoleverseClient(ModContainer container) {
        // Generic config screen under Mods -> Moleverse -> Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Moleverse.LOGGER.debug("Client setup finished.");
    }

    /** Bakes the geometry produced by {@link MoleModel#createBodyLayer()}. */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MoleModel.LAYER, MoleModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOLE.get(), MoleRenderer::new);
    }

    /** Development aid, see {@link MoleDebugCommand}. Runs entirely on the client. */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        MoleDebugCommand.register(event.getDispatcher());
    }
}

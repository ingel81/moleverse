package net.sgeht.moleverse;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.sgeht.moleverse.client.render.TravellingMoleRenderer;
import net.sgeht.moleverse.client.render.ShrewModel;
import net.sgeht.moleverse.client.render.ShrewRenderer;
import net.sgeht.moleverse.client.render.WeaselModel;
import net.sgeht.moleverse.client.render.WeaselRenderer;
import net.sgeht.moleverse.client.render.EarthwormModel;
import net.sgeht.moleverse.client.render.EarthwormRenderer;
import net.sgeht.moleverse.client.render.GrubModel;
import net.sgeht.moleverse.client.render.GrubRenderer;
import net.sgeht.moleverse.client.render.SoilBeetleModel;
import net.sgeht.moleverse.client.render.SoilBeetleRenderer;
import net.sgeht.moleverse.client.BurrowAmbience;
import net.sgeht.moleverse.client.screen.ExchangeStationScreen;
import net.sgeht.moleverse.registry.ModMenus;
import net.sgeht.moleverse.client.debug.BurrowTuneCommand;
import net.sgeht.moleverse.client.debug.MoleDebugCommand;
import net.sgeht.moleverse.client.debug.MoleNetworkOverlay;
import net.sgeht.moleverse.client.render.GreatWormModel;
import net.sgeht.moleverse.client.render.GreatWormRenderer;
import net.sgeht.moleverse.client.render.MoleModel;
import net.sgeht.moleverse.client.render.MoleRenderer;
import net.sgeht.moleverse.network.ModPayloads;
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
        // The payload is registered in common code, which must not name a client
        // class - so the handler is a slot and this is where it gets filled.
        ModPayloads.setLinkSink(MoleNetworkOverlay::acceptLinks);
        Moleverse.LOGGER.debug("Client setup finished.");
    }

    /**
     * Binds the station's screen to its menu.
     *
     * <p>Through NeoForge's event rather than {@code MenuScreens.register},
     * which is private in this version - the map it writes into is handed out
     * here and nowhere else.</p>
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.EXCHANGE_STATION.get(), ExchangeStationScreen::new);
    }

    /** Bakes the geometry produced by {@link MoleModel#createBodyLayer()}. */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MoleModel.LAYER, MoleModel::createBodyLayer);
        event.registerLayerDefinition(GreatWormModel.LAYER, GreatWormModel::createBodyLayer);
        event.registerLayerDefinition(EarthwormModel.LAYER, EarthwormModel::createBodyLayer);
        event.registerLayerDefinition(SoilBeetleModel.LAYER, SoilBeetleModel::createBodyLayer);
        event.registerLayerDefinition(GrubModel.LAYER, GrubModel::createBodyLayer);
        event.registerLayerDefinition(ShrewModel.LAYER, ShrewModel::createBodyLayer);
        event.registerLayerDefinition(WeaselModel.LAYER, WeaselModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOLE.get(), MoleRenderer::new);
        event.registerEntityRenderer(ModEntities.GREAT_WORM.get(), GreatWormRenderer::new);
        event.registerEntityRenderer(ModEntities.EARTHWORM.get(), EarthwormRenderer::new);
        event.registerEntityRenderer(ModEntities.SOIL_BEETLE.get(), SoilBeetleRenderer::new);
        event.registerEntityRenderer(ModEntities.GRUB.get(), GrubRenderer::new);
        event.registerEntityRenderer(ModEntities.SHREW.get(), ShrewRenderer::new);
        event.registerEntityRenderer(ModEntities.WEASEL.get(), WeaselRenderer::new);
        event.registerEntityRenderer(ModEntities.TRAVELLING_MOLE.get(), TravellingMoleRenderer::new);
    }

    /** Development aid, see {@link MoleDebugCommand}. Runs entirely on the client. */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        MoleDebugCommand.register(event.getDispatcher());
        BurrowTuneCommand.register(event.getDispatcher());
    }

    /**
     * Feeds the mound overlay. A client tick rather than a render event on
     * purpose: {@code Minecraft.tick} is wrapped in a gizmo collector, so
     * {@code Gizmos.line} works from here and the drawing needs no
     * {@code PoseStack} and no buffer handling of its own.
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        MoleNetworkOverlay.tick();
        BurrowAmbience.tick();
    }
}

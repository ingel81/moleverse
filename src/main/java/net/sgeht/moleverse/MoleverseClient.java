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
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.sgeht.moleverse.client.BurrowAmbience;
import net.sgeht.moleverse.client.debug.MoleDebugCommand;
import net.sgeht.moleverse.client.debug.MoleNetworkOverlay;
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

    /**
     * The burrow's own fog. Both hooks return immediately anywhere else, so this
     * costs one dimension check per frame in the overworld.
     */
    @SubscribeEvent
    static void onComputeFogColour(ViewportEvent.ComputeFogColor event) {
        BurrowAmbience.onComputeFogColour(event);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        BurrowAmbience.onRenderFog(event);
    }
}

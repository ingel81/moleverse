package net.sgeht.moleverse.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.debug.DevGate;

/**
 * Opens a single-player development world to LAN, with cheats, as soon as it is
 * entered.
 *
 * <p>Only in the development client: the run configuration sets
 * {@code moleverse.devPublish}, and nothing outside {@code runClient} ever does.
 * A player who installs the mod is unaffected, and a dedicated server never
 * reaches this class at all - it is client-only code.</p>
 *
 * <p>Both things come from one call. {@code publishServer} is what the
 * "Open to LAN" screen uses, and its middle argument is the cheats flag, so
 * enabling commands does not need the world to have been created with them.
 * That matters here: every {@code runClient} tends to produce a fresh world, and
 * ticking the box by hand each time is exactly the sort of chore that gets
 * skipped until it is needed.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID, value = Dist.CLIENT)
public final class DevWorldPublisher {

    /** Port to publish on, or 0 to let Minecraft pick a free one. */
    private static final String PORT_PROPERTY = "moleverse.devPublishPort";

    private DevWorldPublisher() {
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        // The property this class gave its name to now lives in DevGate, which
        // every instrument asks. One copy of the string, so that the day the
        // release switch is flipped there is one place to flip it.
        if (!DevGate.isDevelopmentRun()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || server.isPublished()) {
            // Not a single-player world, or already open - joining a dedicated
            // server from the dev client is a perfectly normal thing to do.
            return;
        }

        // Deferred: at login the server is still finishing its own start-up, and
        // publishing needs the connection to be settled.
        minecraft.execute(() -> publish(minecraft, server));
    }

    private static void publish(Minecraft minecraft, IntegratedServer server) {
        if (server.isPublished()) {
            return;
        }

        int port = Integer.getInteger(PORT_PROPERTY, 0);
        // Null game type keeps whatever mode the player is already in.
        boolean opened = server.publishServer(null, true, port);

        if (minecraft.player == null) {
            return;
        }
        minecraft.player.displayClientMessage(Component.literal(opened
                ? "Moleverse dev: world open on port " + server.getPort() + ", cheats on"
                : "Moleverse dev: could not open the world to LAN"), false);
    }
}

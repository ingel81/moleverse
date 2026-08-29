package net.sgeht.moleverse.network;

import java.util.function.Consumer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.sgeht.moleverse.Moleverse;

/**
 * Registration for this mod's packets.
 *
 * <p>The handler is deliberately a slot rather than a method reference. Drawing
 * belongs to the overlay, the overlay is client code, and common code must not
 * name a client class - so the client fills the slot during its own setup and
 * this class never learns what is on the other end. On a dedicated server the
 * slot stays empty and a payload that somehow arrived would be dropped, which is
 * the right outcome for a debug packet.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class ModPayloads {

    /**
     * The protocol version. Bumped when a payload's shape changes, which makes a
     * client of the old shape fail the handshake instead of misreading a packet.
     */
    private static final String VERSION = "1";

    private static volatile Consumer<BurrowLinksPayload> linkSink = payload -> {
    };

    private ModPayloads() {
    }

    /** Called from client setup. */
    public static void setLinkSink(Consumer<BurrowLinksPayload> sink) {
        linkSink = sink;
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                // Optional: an older or plain vanilla client may connect, it simply
                // never sees the debug view.
                .optional()
                .playToClient(BurrowLinksPayload.TYPE, BurrowLinksPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> linkSink.accept(payload)));
    }
}

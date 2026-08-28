package net.sgeht.moleverse.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.config.MoleverseConfig;

/**
 * Handlers on the NeoForge game bus, meaning runtime events rather than the
 * mod lifecycle.
 *
 * <p>{@link EventBusSubscriber} registers every static
 * {@code @SubscribeEvent} method automatically.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class MoleverseGameEvents {

    private MoleverseGameEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Moleverse.LOGGER.info("Moleverse is active - the underground is waiting.");
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!MoleverseConfig.GREET_PLAYER.get()) {
            return;
        }
        // The event also fires for the client player; only the server player sends chat.
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(
                    Component.translatable("message." + Moleverse.MOD_ID + ".greeting")
                            .withStyle(ChatFormatting.GOLD));
        }
    }
}

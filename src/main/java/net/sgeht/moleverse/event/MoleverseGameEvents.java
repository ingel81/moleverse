package net.sgeht.moleverse.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.config.MoleverseConfig;
import net.sgeht.moleverse.debug.MoleServerCommand;

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

    /**
     * The game bus, not the mod bus: {@code RegisterCommandsEvent} fires whenever
     * the reloadable server resources are rebuilt, which is server start and
     * every {@code /reload}.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MoleServerCommand.register(event.getDispatcher());
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

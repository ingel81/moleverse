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
 * Handler am NeoForge-Game-Bus (Laufzeit-Events, nicht Mod-Lifecycle).
 *
 * <p>{@link EventBusSubscriber} registriert alle statischen
 * {@code @SubscribeEvent}-Methoden automatisch.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class MoleverseGameEvents {

    private MoleverseGameEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Moleverse.LOGGER.info("Moleverse ist aktiv - der Untergrund wartet.");
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!MoleverseConfig.GREET_PLAYER.get()) {
            return;
        }
        // Das Event feuert auch fuer den ClientPlayer; nur der ServerPlayer verschickt Chat.
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(
                    Component.translatable("message." + Moleverse.MOD_ID + ".greeting")
                            .withStyle(ChatFormatting.GOLD));
        }
    }
}

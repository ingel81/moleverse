package net.sgeht.moleverse;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.sgeht.moleverse.config.MoleverseConfig;
import net.sgeht.moleverse.registry.ModRegistries;
import org.slf4j.Logger;

/**
 * Einstiegspunkt der Mod. Laeuft auf Client und dedizierter Server-Seite.
 *
 * <p>Diese Klasse haelt bewusst keine Inhalte, sondern verdrahtet nur:
 * Registries, Config und Lifecycle-Listener. Alles Fachliche liegt in den
 * Unterpaketen.</p>
 */
@Mod(Moleverse.MOD_ID)
public final class Moleverse {

    /** Mod-ID. Muss identisch zu {@code mod_id} in gradle.properties sein. */
    public static final String MOD_ID = "moleverse";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Moleverse(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);

        modBus.addListener(this::onCommonSetup);

        container.registerConfig(ModConfig.Type.COMMON, MoleverseConfig.COMMON_SPEC);

        LOGGER.info("Moleverse konstruiert.");
    }

    /**
     * Baut eine {@link Identifier} im Namespace dieser Mod.
     *
     * @param path Pfad ohne Namespace, z.B. {@code "loose_soil"}
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Thread-unsicherer Setup-Code gehoert in event.enqueueWork(...).
        event.enqueueWork(() -> LOGGER.debug("Common setup abgeschlossen."));
    }
}

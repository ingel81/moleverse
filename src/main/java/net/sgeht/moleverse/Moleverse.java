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
 * Mod entry point. Runs on both the client and the dedicated server.
 *
 * <p>This class deliberately holds no content. It only wires things up:
 * registries, configuration and lifecycle listeners. Everything domain
 * specific lives in the sub-packages.</p>
 */
@Mod(Moleverse.MOD_ID)
public final class Moleverse {

    /** Mod id. Must be identical to {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "moleverse";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Moleverse(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);

        modBus.addListener(this::onCommonSetup);

        container.registerConfig(ModConfig.Type.COMMON, MoleverseConfig.COMMON_SPEC);

        LOGGER.info("Moleverse constructed.");
    }

    /**
     * Builds an {@link Identifier} in this mod's namespace.
     *
     * @param path path without namespace, for example {@code "loose_soil"}
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Thread-unsafe setup work belongs inside event.enqueueWork(...).
        event.enqueueWork(() -> LOGGER.debug("Common setup finished."));
    }
}

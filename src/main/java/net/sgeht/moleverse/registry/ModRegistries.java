package net.sgeht.moleverse.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Single attachment point for every {@code DeferredRegister} of this mod.
 *
 * <p>New registry classes are registered here and nowhere else, so that there
 * is exactly one place where the registration order is visible.</p>
 */
public final class ModRegistries {

    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        // Blocks before items, so that block items can resolve their blocks.
        // Entity types before items as well, so spawn eggs can resolve their type.
        ModBlocks.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
        // After blocks: a point of interest names the blockstates it matches.
        ModPoi.REGISTER.register(modBus);
        // After blocks, for the same reason: a block entity type names the
        // blocks it may sit in.
        ModBlockEntities.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);
    }
}

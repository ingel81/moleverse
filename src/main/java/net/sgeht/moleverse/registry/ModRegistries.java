package net.sgeht.moleverse.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Zentraler Einstiegspunkt fuer alle {@code DeferredRegister} dieser Mod.
 *
 * <p>Neue Registry-Klassen werden ausschliesslich hier angemeldet, damit es
 * genau eine Stelle gibt, an der die Registrierungsreihenfolge sichtbar ist.</p>
 */
public final class ModRegistries {

    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        // Reihenfolge: Bloecke vor Items, damit BlockItems ihre Bloecke aufloesen koennen.
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
    }
}

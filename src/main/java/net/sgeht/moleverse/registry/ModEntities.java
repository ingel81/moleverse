package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Entity-Typen der Mod.
 *
 * <p>Noch leer. Sobald der Maulwurf-Mob existiert, kommen hier
 * {@code EntityType.Builder}-Registrierungen hin; Attribute werden ueber
 * {@code EntityAttributeCreationEvent} nachgereicht, Renderer im Client-Paket.</p>
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, Moleverse.MOD_ID);

    private ModEntities() {
    }
}

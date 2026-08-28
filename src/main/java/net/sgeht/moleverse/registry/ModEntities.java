package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Entity types of this mod.
 *
 * <p>Still empty. Once the mole mob exists, {@code EntityType.Builder}
 * registrations go here; attributes are supplied through
 * {@code EntityAttributeCreationEvent} and renderers live in the client package.</p>
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, Moleverse.MOD_ID);

    private ModEntities() {
    }
}

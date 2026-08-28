package net.sgeht.moleverse.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * Handlers on the mod event bus: lifecycle and registration.
 *
 * <p>Not to be confused with {@link MoleverseGameEvents}, which listens on the
 * game bus for runtime events. Mixing the two up is the most common source of
 * handlers that silently never fire.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class MoleverseModEvents {

    private MoleverseModEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MOLE.get(), Mole.createAttributes().build());
    }
}

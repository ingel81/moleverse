package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.Mole;

/** Entity types of this mod. */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, Moleverse.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Mole>> MOLE = REGISTER.register(
            "mole",
            key -> EntityType.Builder.of(Mole::new, MobCategory.CREATURE)
                    // Roughly one block long and low to the ground.
                    .sized(0.7F, 0.45F)
                    .eyeHeight(0.35F)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    private ModEntities() {
    }
}

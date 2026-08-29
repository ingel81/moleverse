package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.GreatWorm;
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

    /**
     * The animal a player meets in the burrow.
     *
     * <p>It is the same earthworm that is farmed above ground, seen at the scale
     * the burrow is built to - which is the whole point of the dimension in one
     * creature. Roughly four blocks long, so it fills a good part of a corridor
     * without blocking it.</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GreatWorm>> GREAT_WORM = REGISTER.register(
            "great_worm",
            key -> EntityType.Builder.of(GreatWorm::new, MobCategory.CREATURE)
                    .sized(1.4F, 1.0F)
                    .eyeHeight(0.7F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    private ModEntities() {
    }
}

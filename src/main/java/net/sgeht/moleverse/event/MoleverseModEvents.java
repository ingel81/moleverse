package net.sgeht.moleverse.event;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.sgeht.moleverse.entity.TravellingMole;
import net.sgeht.moleverse.entity.predator.BurrowPredator;
import net.sgeht.moleverse.entity.predator.Shrew;
import net.sgeht.moleverse.entity.predator.Weasel;
import net.sgeht.moleverse.entity.critter.BurrowCritter;
import net.sgeht.moleverse.entity.critter.Earthworm;
import net.sgeht.moleverse.entity.critter.Grub;
import net.sgeht.moleverse.entity.critter.SoilBeetle;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.GreatWorm;
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
        event.put(ModEntities.GREAT_WORM.get(), GreatWorm.createAttributes().build());
        event.put(ModEntities.EARTHWORM.get(), Earthworm.createAttributes().build());
        event.put(ModEntities.SOIL_BEETLE.get(), SoilBeetle.createAttributes().build());
        event.put(ModEntities.GRUB.get(), Grub.createAttributes().build());
        event.put(ModEntities.SHREW.get(), Shrew.createAttributes().build());
        event.put(ModEntities.WEASEL.get(), Weasel.createAttributes().build());
        event.put(ModEntities.TRAVELLING_MOLE.get(), TravellingMole.createAttributes().build());
    }

    /**
     * Where a mole is allowed to appear once a biome has decided to spawn one.
     *
     * <p>The same triple every vanilla land animal uses. Which biomes spawn
     * moles at all is a separate question, answered by the biome modifier in
     * {@code ModBiomeModifiers}.</p>
     */
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.MOLE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Animal::checkAnimalSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);

        event.register(
                ModEntities.EARTHWORM.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BurrowCritter::checkBurrowSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);

        event.register(
                ModEntities.SOIL_BEETLE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BurrowCritter::checkBurrowSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);

        event.register(
                ModEntities.GRUB.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BurrowCritter::checkBurrowSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);

        event.register(
                ModEntities.SHREW.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BurrowPredator::checkBurrowMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);

        event.register(
                ModEntities.WEASEL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BurrowPredator::checkBurrowMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.AND);
    }
}

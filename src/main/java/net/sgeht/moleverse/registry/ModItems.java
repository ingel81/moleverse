package net.sgeht.moleverse.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Every item of this mod, including the block items from {@link ModBlocks}. */
public final class ModItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Moleverse.MOD_ID);

    /** Mole pelt: the first drop material, later a crafting base. */
    public static final DeferredItem<Item> MOLE_PELT = REGISTER.registerSimpleItem("mole_pelt");

    /**
     * Earthworm: what a mole is actually after, and the reason to dig a mound
     * open rather than knock it flat. Moles will follow and breed for one.
     */
    public static final DeferredItem<Item> EARTHWORM = REGISTER.registerSimpleItem("earthworm");

    // --- Block items ------------------------------------------------------
    public static final DeferredItem<BlockItem> LOOSE_SOIL = REGISTER.registerSimpleBlockItem(ModBlocks.LOOSE_SOIL);

    /** Mounds drop nothing, but the item exists so they can be placed by hand. */
    public static final DeferredItem<BlockItem> MOLE_MOUND = REGISTER.registerSimpleBlockItem(ModBlocks.MOLE_MOUND);

    public static final DeferredItem<BlockItem> PREPARED_MOLE_MOUND =
            REGISTER.registerSimpleBlockItem(ModBlocks.PREPARED_MOLE_MOUND);

    // --- Spawn eggs -------------------------------------------------------
    // The properties operator runs during item registration, not at class init.
    // Entity types are registered before items, so the type resolves by then.
    public static final DeferredItem<SpawnEggItem> MOLE_SPAWN_EGG = REGISTER.registerItem(
            "mole_spawn_egg",
            SpawnEggItem::new,
            props -> props.spawnEgg(ModEntities.MOLE.get()));

    private ModItems() {
    }
}

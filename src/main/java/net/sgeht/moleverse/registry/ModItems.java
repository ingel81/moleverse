package net.sgeht.moleverse.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Every item of this mod, including the block items from {@link ModBlocks}. */
public final class ModItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Moleverse.MOD_ID);

    /** Mole pelt: the first drop material, later a crafting base. */
    public static final DeferredItem<Item> MOLE_PELT = REGISTER.registerSimpleItem("mole_pelt");

    // --- Block items ------------------------------------------------------
    public static final DeferredItem<BlockItem> LOOSE_SOIL = REGISTER.registerSimpleBlockItem(ModBlocks.LOOSE_SOIL);

    private ModItems() {
    }
}

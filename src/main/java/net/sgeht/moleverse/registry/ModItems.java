package net.sgeht.moleverse.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Alle Items der Mod, inklusive der BlockItems aus {@link ModBlocks}. */
public final class ModItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Moleverse.MOD_ID);

    /** Maulwurfsfell: erstes Drop-Material, spaeter Craftingbasis. */
    public static final DeferredItem<Item> MOLE_PELT = REGISTER.registerSimpleItem("mole_pelt");

    // --- BlockItems -------------------------------------------------------
    public static final DeferredItem<BlockItem> LOOSE_SOIL = REGISTER.registerSimpleBlockItem(ModBlocks.LOOSE_SOIL);

    private ModItems() {
    }
}

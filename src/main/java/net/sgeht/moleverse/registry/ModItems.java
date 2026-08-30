package net.sgeht.moleverse.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.item.MoleInSack;
import net.sgeht.moleverse.item.MoundAttachmentItem;
import net.sgeht.moleverse.item.PreparedMoundItem;

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

    /** Goes onto an existing molehill and nowhere else - see {@link PreparedMoundItem}. */
    public static final DeferredItem<PreparedMoundItem> PREPARED_MOLE_MOUND = REGISTER.registerItem(
            "prepared_mole_mound",
            props -> new PreparedMoundItem(ModBlocks.PREPARED_MOLE_MOUND.get(), props),
            (java.util.function.UnaryOperator<Item.Properties>) Item.Properties::useBlockDescriptionPrefix);

    public static final DeferredItem<MoundAttachmentItem> SHAFT_LANTERN =
            attachment(ModBlocks.SHAFT_LANTERN);

    public static final DeferredItem<MoundAttachmentItem> SHRINK_POST =
            attachment(ModBlocks.SHRINK_POST);

    public static final DeferredItem<BlockItem> WORM_LARDER =
            REGISTER.registerSimpleBlockItem(ModBlocks.WORM_LARDER);

    /**
     * The second worm. Better mole food, and what buys the better finds once the
     * exchange station grades its input.
     */
    public static final DeferredItem<Item> FAT_WORM = REGISTER.registerSimpleItem("fat_worm");

    /**
     * The third, and the bridge to what lights the burrow: the same fungal glow
     * that grows on a corridor ceiling.
     */
    public static final DeferredItem<Item> GLOW_WORM = REGISTER.registerSimpleItem("glow_worm");

    /**
     * A caught mole, carried. The animal itself lives in the stack's components,
     * so a reload does not lose it - and releasing it is how a colony gets
     * founded somewhere on purpose.
     */
    public static final DeferredItem<MoleInSack> MOLE_IN_SACK = REGISTER.registerItem(
            "mole_in_sack",
            MoleInSack::new,
            props -> props.stacksTo(1));

    public static final DeferredItem<MoundAttachmentItem> MOLE_TRAP =
            attachment(ModBlocks.MOLE_TRAP);

    public static final DeferredItem<BlockItem> WORM_BOX =
            REGISTER.registerSimpleBlockItem(ModBlocks.WORM_BOX);

    public static final DeferredItem<MoundAttachmentItem> EXCHANGE_STATION =
            attachment(ModBlocks.EXCHANGE_STATION);

    public static final DeferredItem<BlockItem> GRUNTING_POST =
            REGISTER.registerSimpleBlockItem(ModBlocks.GRUNTING_POST);

    public static final DeferredItem<MoundAttachmentItem> COLONY_BOARD =
            attachment(ModBlocks.COLONY_BOARD);

    public static final DeferredItem<BlockItem> ROOT_BEAM =
            REGISTER.registerSimpleBlockItem(ModBlocks.ROOT_BEAM);

    public static final DeferredItem<BlockItem> GLOW_MYCELIUM =
            REGISTER.registerSimpleBlockItem(ModBlocks.GLOW_MYCELIUM);

    /**
     * The fill of the burrow below. It cannot be broken, so this only exists to
     * put one somewhere by hand while the dimension is being built.
     */
    public static final DeferredItem<BlockItem> DEEP_EARTH =
            REGISTER.registerSimpleBlockItem(ModBlocks.DEEP_EARTH);

    // --- Spawn eggs -------------------------------------------------------
    // The properties operator runs during item registration, not at class init.
    // Entity types are registered before items, so the type resolves by then.
    public static final DeferredItem<SpawnEggItem> MOLE_SPAWN_EGG = REGISTER.registerItem(
            "mole_spawn_egg",
            SpawnEggItem::new,
            props -> props.spawnEgg(ModEntities.MOLE.get()));

    public static final DeferredItem<SpawnEggItem> GREAT_WORM_SPAWN_EGG = REGISTER.registerItem(
            "great_worm_spawn_egg",
            SpawnEggItem::new,
            props -> props.spawnEgg(ModEntities.GREAT_WORM.get()));

    /**
     * What a wall pocket gives up: a knot of root with the swellings still on
     * it. Food on the spot, and the base a later brewing line hangs off.
     *
     * <p>Sweet-berry sized on purpose. It is raw, it is dug out of a wall a few
     * paces from where the player is standing, and there is no cooking down
     * there - so it is the food that keeps a descent going, not the food that
     * makes one unnecessary.</p>
     */
    public static final DeferredItem<Item> ROOT_NODULE = REGISTER.registerItem(
            "root_nodule",
            Item::new,
            props -> props.food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationModifier(0.1F)
                    .build()));

    /** What a soil beetle leaves behind: the crafting material of the deep. */
    public static final DeferredItem<Item> CHITIN_FLAKE = REGISTER.registerSimpleItem("chitin_flake");

    public static final DeferredItem<SpawnEggItem> EARTHWORM_SPAWN_EGG = REGISTER.registerItem(
            "earthworm_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.EARTHWORM.get()));

    public static final DeferredItem<SpawnEggItem> SOIL_BEETLE_SPAWN_EGG = REGISTER.registerItem(
            "soil_beetle_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.SOIL_BEETLE.get()));

    public static final DeferredItem<SpawnEggItem> GRUB_SPAWN_EGG = REGISTER.registerItem(
            "grub_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.GRUB.get()));

    public static final DeferredItem<SpawnEggItem> SHREW_SPAWN_EGG = REGISTER.registerItem(
            "shrew_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.SHREW.get()));

    public static final DeferredItem<SpawnEggItem> WEASEL_SPAWN_EGG = REGISTER.registerItem(
            "weasel_spawn_egg", SpawnEggItem::new, props -> props.spawnEgg(ModEntities.WEASEL.get()));

    private ModItems() {
    }

    /**
     * A fitting for a prepared mound, with the item that says so.
     *
     * <p>Mirrors {@code registerSimpleBlockItem}, which is
     * {@code registerItem(name, BlockItem::new, props.useBlockDescriptionPrefix())}
     * - the description prefix is what makes the item take the block's name, and
     * leaving it off renames every fitting to {@code item.moleverse.*}.</p>
     */
    private static DeferredItem<MoundAttachmentItem> attachment(DeferredBlock<? extends Block> block) {
        return REGISTER.registerItem(block.getId().getPath(),
                props -> new MoundAttachmentItem(block.get(), props),
                (java.util.function.UnaryOperator<Item.Properties>) Item.Properties::useBlockDescriptionPrefix);
    }
}

package net.sgeht.moleverse.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.sgeht.moleverse.Moleverse;

/**
 * Tag-Keys der Mod. Die zugehoerigen JSON-Dateien liegen unter
 * {@code data/moleverse/tags/...} bzw. werden per Datagen erzeugt.
 */
public final class ModTags {

    private ModTags() {
    }

    public static final class Blocks {
        /** Bloecke, durch die ein Maulwurf graben kann. */
        public static final TagKey<Block> MOLE_DIGGABLE = create("mole_diggable");

        private Blocks() {
        }

        private static TagKey<Block> create(String path) {
            return TagKey.create(Registries.BLOCK, Moleverse.id(path));
        }
    }

    public static final class Items {
        /** Materialien aus Maulwurfsfell. */
        public static final TagKey<Item> MOLE_MATERIALS = create("mole_materials");

        private Items() {
        }

        private static TagKey<Item> create(String path) {
            return TagKey.create(Registries.ITEM, Moleverse.id(path));
        }
    }
}

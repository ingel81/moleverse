package net.sgeht.moleverse.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.sgeht.moleverse.Moleverse;

/**
 * Tag keys of this mod. The matching JSON files live under
 * {@code data/moleverse/tags/...} or are produced by the data generators.
 */
public final class ModTags {

    private ModTags() {
    }

    public static final class Blocks {
        /**
         * Every block that counts as a molehill.
         *
         * <p>Two of them now: the heap a mole leaves and the shored-up version a
         * player makes of it. The point-of-interest index, the network, the
         * routes and the shaft all have to see both, and a tag is the one place
         * that list can live without every caller naming the blocks itself.</p>
         */
        public static final TagKey<Block> MOLE_MOUNDS = create("mole_mounds");

        /** Blocks a mole is able to dig through. */
        public static final TagKey<Block> MOLE_DIGGABLE = create("mole_diggable");

        /**
         * Blocks a mole mound may rest on. Deliberately separate from
         * {@link #MOLE_DIGGABLE}: what a mound can sit on and what a mole can
         * tunnel through are different questions, and they diverge as soon as
         * stone joins the digging tag.
         */
        public static final TagKey<Block> MOLE_MOUND_PLACEABLE = create("mole_mound_placeable");

        private Blocks() {
        }

        private static TagKey<Block> create(String path) {
            return TagKey.create(Registries.BLOCK, Moleverse.id(path));
        }
    }

    public static final class Biomes {
        /** Biomes moles spawn in naturally. */
        public static final TagKey<Biome> SPAWNS_MOLES = create("spawns_moles");

        private Biomes() {
        }

        private static TagKey<Biome> create(String path) {
            return TagKey.create(Registries.BIOME, Moleverse.id(path));
        }
    }

    public static final class Items {
        /** Materials made from mole pelt. */
        public static final TagKey<Item> MOLE_MATERIALS = create("mole_materials");

        private Items() {
        }

        private static TagKey<Item> create(String path) {
            return TagKey.create(Registries.ITEM, Moleverse.id(path));
        }
    }
}

package net.sgeht.moleverse.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Every block of this mod. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Moleverse.MOD_ID);

    /**
     * Loose soil, the base material of mole tunnels. Breaks faster than vanilla
     * dirt and will later carry the tunnelling mechanic.
     */
    public static final DeferredBlock<Block> LOOSE_SOIL = REGISTER.registerSimpleBlock(
            "loose_soil",
            props -> props.mapColor(MapColor.DIRT)
                    .strength(0.35F)
                    .sound(SoundType.GRAVEL));

    private ModBlocks() {
    }
}

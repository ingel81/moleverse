package net.sgeht.moleverse.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/** Alle Bloecke der Mod. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Moleverse.MOD_ID);

    /**
     * Lockere Erde: das Grundmaterial der Maulwurfsgaenge. Schneller abbaubar als
     * Vanilla-Erde und spaeter Traeger der Tunnel-Mechanik.
     */
    public static final DeferredBlock<Block> LOOSE_SOIL = REGISTER.registerSimpleBlock(
            "loose_soil",
            props -> props.mapColor(MapColor.DIRT)
                    .strength(0.35F)
                    .sound(SoundType.GRAVEL));

    private ModBlocks() {
    }
}

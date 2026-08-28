package net.sgeht.moleverse.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;

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

    /**
     * The heap of earth a mole leaves behind. Walked through rather than over,
     * broken with a touch, and worth nothing when it is.
     */
    public static final DeferredBlock<MoleMound> MOLE_MOUND = REGISTER.registerBlock(
            "mole_mound",
            MoleMound::new,
            props -> props.mapColor(MapColor.DIRT)
                    .instabreak()
                    .sound(SoundType.GRAVEL)
                    // noCollision clears occlusion along with the collision box.
                    .noCollision()
                    // A piston crumbles a mound rather than pushing it, which
                    // is what keeps them out of redstone contraptions.
                    .pushReaction(PushReaction.DESTROY));

    private ModBlocks() {
    }
}

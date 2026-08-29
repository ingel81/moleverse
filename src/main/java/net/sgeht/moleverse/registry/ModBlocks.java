package net.sgeht.moleverse.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.block.PreparedMoleMound;
import net.sgeht.moleverse.block.ShaftLantern;

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

    /**
     * A mound a player has shored up, so that something can sit on it.
     *
     * <p>Not {@code instabreak} like the heap it came from: the heap is displaced
     * soil and this took work. Still {@code noCollision}, for the reason spelled
     * out in {@link PreparedMoleMound}.</p>
     */
    public static final DeferredBlock<PreparedMoleMound> PREPARED_MOLE_MOUND = REGISTER.registerBlock(
            "prepared_mole_mound",
            PreparedMoleMound::new,
            props -> props.mapColor(MapColor.DIRT)
                    .strength(0.6F)
                    .sound(SoundType.ROOTED_DIRT)
                    .noCollision()
                    .pushReaction(PushReaction.DESTROY));

    /**
     * The first fitting for a prepared mound: a lamp that lights up when a mole
     * comes out of the shaft under it.
     */
    public static final DeferredBlock<ShaftLantern> SHAFT_LANTERN = REGISTER.registerBlock(
            "shaft_lantern",
            ShaftLantern::new,
            props -> props.mapColor(MapColor.PODZOL)
                    .strength(0.4F)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ShaftLantern.LIT) ? ShaftLantern.LIGHT_WHEN_LIT : 0));

    private ModBlocks() {
    }
}

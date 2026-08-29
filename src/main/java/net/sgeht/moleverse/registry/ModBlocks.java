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
import net.sgeht.moleverse.block.ShrinkPost;
import net.sgeht.moleverse.block.WormLarder;

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

    /**
     * What the burrow below is made of, everywhere a corridor has not been dug.
     *
     * <p>Unbreakable on purpose, and it is not a difficulty setting: outside a
     * run there is no room, so there is nothing to mine. It is the physical layer
     * of the limits in {@code IDEAS.md} - leaving the corridors is impossible
     * rather than forbidden, which is a rule that needs no policing.</p>
     */
    public static final DeferredBlock<Block> DEEP_EARTH = REGISTER.registerSimpleBlock(
            "deep_earth",
            props -> props.mapColor(MapColor.DIRT)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.ROOTED_DIRT));

    /** A root grown across a corridor. Down there they are beams. */
    public static final DeferredBlock<Block> ROOT_BEAM = REGISTER.registerSimpleBlock(
            "root_beam",
            props -> props.mapColor(MapColor.PODZOL)
                    .strength(1.2F)
                    .sound(SoundType.WOOD));

    /** Fungal threads on a corridor ceiling. Where the light down there comes from. */
    public static final DeferredBlock<Block> GLOW_MYCELIUM = REGISTER.registerSimpleBlock(
            "glow_mycelium",
            props -> props.mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.3F)
                    .sound(SoundType.SLIME_BLOCK)
                    .lightLevel(state -> 9));

    /**
     * The fitting that takes a player into the burrow below, and the way back
     * out of it.
     */
    public static final DeferredBlock<ShrinkPost> SHRINK_POST = REGISTER.registerBlock(
            "shrink_post",
            ShrinkPost::new,
            props -> props.mapColor(MapColor.PODZOL)
                    .strength(0.8F)
                    .sound(SoundType.ROOTED_DIRT)
                    .noOcclusion()
                    .lightLevel(state -> 5));

    /**
     * A colony's cache of worms, in the wall of a chamber.
     *
     * <p>Moles really do this: a bite paralyses an earthworm without killing it,
     * and a larder of them keeps through a winter. At the scale of the burrow
     * that is not a detail, it is furniture - and breaking one gives the worms
     * back, which is the only thing down there worth carrying home so far.</p>
     */
    public static final DeferredBlock<WormLarder> WORM_LARDER = REGISTER.registerBlock(
            "worm_larder",
            WormLarder::new,
            props -> props.mapColor(MapColor.DIRT)
                    .strength(0.6F)
                    .sound(SoundType.SLIME_BLOCK));

    private ModBlocks() {
    }
}

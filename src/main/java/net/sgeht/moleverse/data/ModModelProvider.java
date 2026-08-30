package net.sgeht.moleverse.data;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Quadrant;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;
import net.sgeht.moleverse.block.MoleTrap;
import net.sgeht.moleverse.block.GruntingPost;
import net.sgeht.moleverse.block.ShaftLantern;
import net.sgeht.moleverse.block.WormBox;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Generates blockstates, block models and item models.
 *
 * <p>The provider validates its own output: every block and item registered in
 * this mod's namespace must be covered here, otherwise {@code runData} fails.
 * That check is the reason not to hand-write these files any more.</p>
 */
public final class ModModelProvider extends ModelProvider {

    public ModModelProvider(PackOutput output) {
        super(output, Moleverse.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Cube with the same texture on all faces; the block item reuses the block model.
        blockModels.createTrivialCube(ModBlocks.LOOSE_SOIL.get());
        blockModels.createTrivialCube(ModBlocks.ROOT_NODULE.get());

        // Blockstate only: there is no block item, and registerSimpleItemModel would
        // file the model under Items.AIR. handModelled without its second line.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.ROOT_LADDER.get(),
                BlockModelGenerators.variant(new Variant(Moleverse.id("block/root_ladder")))));

        registerMoleMound(blockModels);
        registerPreparedMoleMound(blockModels);
        registerShaftLantern(blockModels);

        // The burrow's own blocks. Their models are hand-managed like the
        // mound's, because they point at vanilla textures for now - a cube
        // template would insist on a texture of our own that does not exist yet.
        handModelled(blockModels, ModBlocks.DEEP_EARTH.get(), "deep_earth");
        handModelled(blockModels, ModBlocks.ROOT_BEAM.get(), "root_beam");
        handModelled(blockModels, ModBlocks.WORM_LARDER.get(), "worm_larder");
        handModelled(blockModels, ModBlocks.EXCHANGE_STATION.get(), "exchange_station");
        handModelled(blockModels, ModBlocks.COLONY_BOARD.get(), "colony_board");

        // Both states share one model for now: spent shows in the chat line and
        // in the sound, not yet in the wood.
        MultiVariant post = BlockModelGenerators.variant(new Variant(Moleverse.id("block/grunting_post")));
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.GRUNTING_POST.get())
                        .with(PropertyDispatch.initial(GruntingPost.SPENT)
                                .select(false, post)
                                .select(true, post)));
        blockModels.registerSimpleItemModel(ModBlocks.GRUNTING_POST.get(),
                Moleverse.id("block/grunting_post"));

        // Nine fill levels, one model. What the box holds shows in the sound and
        // in what it gives back, not yet in the lid.
        MultiVariant box = BlockModelGenerators.variant(new Variant(Moleverse.id("block/worm_box")));
        PropertyDispatch.C1<MultiVariant, Integer> fill = PropertyDispatch.initial(WormBox.FILL);
        for (int level = 0; level <= 8; level++) {
            fill = fill.select(level, box);
        }
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.WORM_BOX.get()).with(fill));
        blockModels.registerSimpleItemModel(ModBlocks.WORM_BOX.get(), Moleverse.id("block/worm_box"));

        // Three states, one model for now: whether the trap is set, sprung or
        // empty shows in the chat and the sound rather than in the wood.
        // One model per state: whether a trap is set, sprung or empty is the one
        // thing a player must be able to read from across a meadow.
        PropertyDispatch.C1<MultiVariant, MoleTrap.State> traps = PropertyDispatch.initial(MoleTrap.STATE)
                .select(MoleTrap.State.EMPTY,
                        BlockModelGenerators.variant(new Variant(Moleverse.id("block/mole_trap"))))
                .select(MoleTrap.State.BAITED,
                        BlockModelGenerators.variant(new Variant(Moleverse.id("block/mole_trap_baited"))))
                .select(MoleTrap.State.FULL,
                        BlockModelGenerators.variant(new Variant(Moleverse.id("block/mole_trap_full"))));
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.MOLE_TRAP.get()).with(traps));
        blockModels.registerSimpleItemModel(ModBlocks.MOLE_TRAP.get(), Moleverse.id("block/mole_trap"));
        handModelled(blockModels, ModBlocks.GLOW_MYCELIUM.get(), "glow_mycelium");
        handModelled(blockModels, ModBlocks.SHRINK_POST.get(), "shrink_post");

        itemModels.generateFlatItem(ModItems.MOLE_PELT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EARTHWORM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FAT_WORM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GLOW_WORM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ROOT_NODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MOLE_IN_SACK.get(), ModelTemplates.FLAT_ITEM);

        // Spawn eggs carry their own texture in this version rather than the
        // old two-layer tinted template, so a flat item model is all it needs.
        itemModels.generateFlatItem(ModItems.MOLE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GREAT_WORM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EARTHWORM_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOIL_BEETLE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GRUB_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SHREW_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WEASEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CHITIN_FLAKE.get(), ModelTemplates.FLAT_ITEM);
    }

    /**
     * Twelve variants for the mound: two closed shapes and one open crater,
     * each in four rotations.
     *
     * <p>The models themselves are hand-managed under
     * {@code assets/moleverse/models/block/} - a stepped heap of earth cannot be
     * expressed by substituting textures into a template, which is all the model
     * templates can do. Only the blockstate and the item model are generated.</p>
     *
     * <p>Two closed shapes rather than one because a field of identical mounds
     * reads as stamped, and the rotations do not hide that on their own.</p>
     */
    private void registerMoleMound(BlockModelGenerators blockModels) {
        Variant domed = new Variant(Moleverse.id("block/mole_mound_a"));
        Variant flat = new Variant(Moleverse.id("block/mole_mound_b"));
        Variant open = new Variant(Moleverse.id("block/mole_mound_open"));

        Variant fortress = new Variant(Moleverse.id("block/mole_mound_fortress"));

        // The fortress keeps the ordinary open model: an opened shaft is a hole,
        // and the heap around a hole is whatever the digging left of it.
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.MOLE_MOUND.get())
                        .with(PropertyDispatch.initial(MoleMound.OPEN, MoleMound.FORTRESS)
                                .select(false, false, rotations(domed, flat))
                                .select(false, true, rotations(fortress))
                                .select(true, false, rotations(open))
                                .select(true, true, rotations(open))));

        // The item shows the domed shape; the flat one reads as a smear in hand.
        blockModels.registerSimpleItemModel(ModBlocks.MOLE_MOUND.get(), Moleverse.id("block/mole_mound_a"));
    }

    /**
     * One shape in four rotations, for both states of the shaft.
     *
     * <p>The open flag changes nothing to look at here: the rim leaves the shaft
     * exposed either way, and a mole down a prepared mound is hidden by the earth
     * rather than by a lid. Both values are still listed, because a blockstate
     * that omits one crashes the moment the property takes it.</p>
     */
    private void registerPreparedMoleMound(BlockModelGenerators blockModels) {
        MultiVariant shape = rotations(new Variant(Moleverse.id("block/prepared_mole_mound")));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.PREPARED_MOLE_MOUND.get())
                        .with(PropertyDispatch.initial(MoleMound.OPEN)
                                .select(false, shape)
                                .select(true, shape)));

        blockModels.registerSimpleItemModel(ModBlocks.PREPARED_MOLE_MOUND.get(),
                Moleverse.id("block/prepared_mole_mound"));
    }

    /**
     * One shape for both states of the lamp.
     *
     * <p>Lit and unlit share a model on purpose for now: what changes is the
     * light level, which comes from the block properties rather than from the
     * model. A second texture can be added later without touching anything
     * else.</p>
     */
    private void registerShaftLantern(BlockModelGenerators blockModels) {
        MultiVariant shape = BlockModelGenerators.variants(
                new Variant(Moleverse.id("block/shaft_lantern")));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.SHAFT_LANTERN.get())
                        .with(PropertyDispatch.initial(ShaftLantern.LIT)
                                .select(false, shape)
                                .select(true, shape)));

        blockModels.registerSimpleItemModel(ModBlocks.SHAFT_LANTERN.get(),
                Moleverse.id("block/shaft_lantern"));
    }

    /**
     * A block with one hand-written model and no state to dispatch on: the
     * blockstate and the item model are generated, the shape is not.
     */
    private void handModelled(BlockModelGenerators blockModels, Block block, String model) {
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                block, BlockModelGenerators.variant(new Variant(Moleverse.id("block/" + model)))));
        blockModels.registerSimpleItemModel(block, Moleverse.id("block/" + model));
    }

    /** Every given shape in all four Y rotations, picked at random in world. */
    private static MultiVariant rotations(Variant... shapes) {
        List<Variant> all = new ArrayList<>(shapes.length * 4);
        for (Variant shape : shapes) {
            all.add(shape);
            all.add(shape.withYRot(Quadrant.R90));
            all.add(shape.withYRot(Quadrant.R180));
            all.add(shape.withYRot(Quadrant.R270));
        }
        return BlockModelGenerators.variants(all.toArray(new Variant[0]));
    }
}

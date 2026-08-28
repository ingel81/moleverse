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
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.MoleMound;
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

        registerMoleMound(blockModels);

        itemModels.generateFlatItem(ModItems.MOLE_PELT.get(), ModelTemplates.FLAT_ITEM);

        // Spawn eggs carry their own texture in this version rather than the
        // old two-layer tinted template, so a flat item model is all it needs.
        itemModels.generateFlatItem(ModItems.MOLE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
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

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.MOLE_MOUND.get())
                        .with(PropertyDispatch.initial(MoleMound.OPEN)
                                .select(false, rotations(domed, flat))
                                .select(true, rotations(open))));

        // The item shows the domed shape; the flat one reads as a smear in hand.
        blockModels.registerSimpleItemModel(ModBlocks.MOLE_MOUND.get(), Moleverse.id("block/mole_mound_a"));
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

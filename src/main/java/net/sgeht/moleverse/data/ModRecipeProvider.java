package net.sgeht.moleverse.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModItems;

/**
 * Crafting recipes for this mod.
 *
 * <p>The economy behind them: vanilla material is the cheap half of every
 * recipe and this mod's own material is the expensive half. Sticks, dirt and
 * planks gate nothing — worms and pelts do. Nothing here asks for an ore, a
 * cave or a nether trip, because the intended cost of the mod's own machines is
 * time spent with the animals.</p>
 *
 * <p>Every builder needs at least one {@code unlockedBy} criterion. Without one
 * the recipe advancement cannot be built and {@code runData} aborts with
 * "No way of obtaining recipe".</p>
 */
public final class ModRecipeProvider extends RecipeProvider {

    /**
     * Recipe book group for the fittings that sit on a prepared mound. They are
     * one family and collapse into a single entry in the book. All of them use
     * {@link RecipeCategory#DECORATIONS}, which maps to the same crafting book
     * category, so the grouping actually takes effect.
     */
    private static final String ATTACHMENT_GROUP = "mound_attachment";

    /** Group for the two directions of the dirt/loose soil conversion. */
    private static final String LOOSE_SOIL_GROUP = "loose_soil";

    private ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        soil();
        mound();
        attachments();
    }

    // --- Soil -------------------------------------------------------------

    /**
     * Loose soil is displaced earth and nothing more, so it converts both ways
     * at one to one. Anything less generous would make a player hoard the stuff
     * that every other recipe here is built on.
     */
    private void soil() {
        this.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.LOOSE_SOIL.get())
                .requires(Items.DIRT)
                .group(LOOSE_SOIL_GROUP)
                .unlockedBy(getHasName(Items.DIRT), this.has(Items.DIRT))
                .save(this.output);

        // Needs an explicit key: the default id is taken from the result item,
        // which would write this into data/minecraft/recipe/dirt.json.
        this.shapeless(RecipeCategory.BUILDING_BLOCKS, Items.DIRT)
                .requires(ModBlocks.LOOSE_SOIL.get())
                .group(LOOSE_SOIL_GROUP)
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output, key("dirt_from_loose_soil"));
    }

    // --- The socket -------------------------------------------------------

    /**
     * The in-world way to get a prepared mound is one loose soil on a molehill a
     * mole already built, and that stays the better deal on purpose: the mound
     * is free, so the whole thing costs a single block. This is the fallback for
     * a player who has no molehill within reach, and it is priced accordingly —
     * three soil for the rim and two stakes to hold it.
     */
    private void mound() {
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.PREPARED_MOLE_MOUND.get())
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .define('R', Items.STICK)
                .pattern("SSS")
                .pattern("R R")
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output);
    }

    // --- Fittings ---------------------------------------------------------

    private void attachments() {
        // A lamp on a stand. Cheap: a colony is only worth watching if a row of
        // these costs nothing much.
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.SHAFT_LANTERN.get())
                .define('R', Items.STICK)
                .define('T', Items.TORCH)
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .pattern(" R ")
                .pattern("RTR")
                .pattern(" S ")
                .group(ATTACHMENT_GROUP)
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output);

        // A root is scenery, so it comes two at a time. Not an attachment, so
        // it stays out of the group.
        this.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ROOT_BEAM.get(), 2)
                .define('R', Items.STICK)
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .pattern(" R ")
                .pattern("RSR")
                .pattern(" R ")
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output);

        // A stake driven into a bed of soil - the shape is the recipe. It is the
        // first worm source that does not involve breaking molehills, so it has
        // to be affordable before a player owns anything else of this mod's.
        //
        // Outside the group on purpose: GruntingPost is a plain Block, not a
        // MoundAttachment. It needs no mound under it and does not belong in a
        // recipe book entry that says otherwise.
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.GRUNTING_POST.get())
                .define('R', Items.STICK)
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .pattern(" R ")
                .pattern(" R ")
                .pattern("SSS")
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output);

        // A sign in all but name, and priced like one: the soil in the middle is
        // what ties the board to the ground it reads.
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.COLONY_BOARD.get())
                .define('P', ItemTags.PLANKS)
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .define('R', Items.STICK)
                .pattern("PPP")
                .pattern("PSP")
                .pattern(" R ")
                .group(ATTACHMENT_GROUP)
                .unlockedBy(getHasName(ModBlocks.LOOSE_SOIL.get()), this.has(ModBlocks.LOOSE_SOIL.get()))
                .save(this.output);

        // The door into the burrow, and the most expensive thing in the mod.
        // Two pelts and five worms is two moles and a stocked larder's worth of
        // time with the animals - which is the gate the roadmap asks for, handed
        // over by the animals themselves rather than dug out of a cave.
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.SHRINK_POST.get())
                .define('P', ModItems.MOLE_PELT.get())
                .define('W', ModItems.EARTHWORM.get())
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .define('R', Items.STICK)
                .pattern("PWP")
                .pattern("WSW")
                .pattern("WRW")
                .group(ATTACHMENT_GROUP)
                .unlockedBy(getHasName(ModItems.MOLE_PELT.get()), this.has(ModItems.MOLE_PELT.get()))
                .unlockedBy(getHasName(ModItems.EARTHWORM.get()), this.has(ModItems.EARTHWORM.get()))
                .save(this.output);

        // The other expensive one. The chest is the cheap half - two inventories
        // have to come from somewhere - and the pelts and worms are the gate. A
        // worm short of the shrink post, because the station is what a player
        // builds first and the way down is the deeper of the two gates.
        this.shaped(RecipeCategory.DECORATIONS, ModBlocks.EXCHANGE_STATION.get())
                .define('P', ModItems.MOLE_PELT.get())
                .define('W', ModItems.EARTHWORM.get())
                .define('C', Items.CHEST)
                .define('S', ModBlocks.LOOSE_SOIL.get())
                .pattern("PWP")
                .pattern("WCW")
                .pattern("SSS")
                .group(ATTACHMENT_GROUP)
                .unlockedBy(getHasName(ModItems.MOLE_PELT.get()), this.has(ModItems.MOLE_PELT.get()))
                .unlockedBy(getHasName(ModItems.EARTHWORM.get()), this.has(ModItems.EARTHWORM.get()))
                .save(this.output);
    }

    // Deliberately without a recipe:
    //
    // * mole_mound - a molehill is an animal's doing. Crafting one would let a
    //   player fabricate the whole network by hand, which is the one thing the
    //   mod is about.
    // * worm_larder - it breaks for two to four worms and does not give itself
    //   back. Any recipe costing fewer than five worms is a duplication loop,
    //   and one costing more is a recipe nobody would ever craft. There is no
    //   price in between, so it stays a thing found in the burrow. That also
    //   keeps it honest: a larder is a colony's cache, not player furniture.
    // * glow_mycelium - likewise found below, and it is the burrow's own light.
    //   Nothing in the overworld it could plausibly be made of, and making it
    //   craftable would let a player light the corridors from a workbench
    //   instead of from what grows down there.
    // * deep_earth - unbreakable dimension fill.
    // * mole_pelt - nothing to smelt or cook it into. It is not food, and vanilla
    //   has no precedent for cooking a hide. Left alone rather than invented.
    // * the spawn eggs - creative items.

    /** Recipe id in this mod's namespace. */
    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, Moleverse.id(path));
    }

    /**
     * The {@link net.minecraft.data.DataProvider} half.
     *
     * <p>Since 1.21.9 the recipe provider is split in two: {@code RecipeProvider}
     * holds the builders and has a no-argument {@code buildRecipes()}, while this
     * inner {@code Runner} is what the pack actually registers. The old
     * {@code buildRecipes(Consumer)} that every tutorial shows does not exist.</p>
     */
    public static final class Runner extends RecipeProvider.Runner {

        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Moleverse Recipes";
        }
    }
}

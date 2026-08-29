package net.sgeht.moleverse.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.ChangeDimensionTrigger;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.criterion.KilledTrigger;
import net.minecraft.advancements.criterion.PlayerPredicate;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.registry.ModItems;

/**
 * The mod's advancement tree.
 *
 * <p>It has two jobs. It tells a new player what the mod is for, in the order
 * the mod expects to be played: find a mound, place one, prepare it, fit
 * something to it, go under. And it is a test surface — an advancement that
 * never fires in play is a feature that never ran, which is the one failure
 * mode that leaves no trace in the log.</p>
 *
 * <p>The API moved in the 1.21 line. There is no longer a NeoForge wrapper
 * around {@link AdvancementProvider}; the vanilla class takes the sub providers
 * directly and writes to {@code data/moleverse/advancement/}. Every criterion
 * here is a vanilla trigger — the mod defines none of its own, so anything the
 * tree cannot observe is noted at the node that wanted it.</p>
 */
public final class ModAdvancementProvider extends AdvancementProvider {

    /**
     * Background for the root tab. This is a {@code ClientAsset} id, not a
     * texture path: the loader appends {@code textures/} and {@code .png}, and
     * the tab blits it as 16x16 tiles. Any 16x16 vanilla block texture works,
     * so the tree gets rooted dirt rather than one of the five stock
     * backgrounds.
     */
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("block/rooted_dirt");

    public ModAdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, List.of(new MoleverseAdvancements()));
    }

    private static final class MoleverseAdvancements implements AdvancementSubProvider {

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> writer) {
            HolderGetter<EntityType<?>> entityTypes = registries.lookupOrThrow(Registries.ENTITY_TYPE);

            // 1. The mod announces itself the moment either of its two starting
            //    materials reaches the inventory. A molehill dug up by hand and
            //    a worm dug out of the ground are the same discovery.
            AdvancementHolder root = Advancement.Builder.advancement()
                    .display(
                            ModItems.EARTHWORM.get(),
                            title("root"),
                            description("root"),
                            BACKGROUND,
                            AdvancementType.TASK,
                            false,
                            false,
                            false)
                    .addCriterion("mole_mound", InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.MOLE_MOUND.get()))
                    .addCriterion("earthworm", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.EARTHWORM.get()))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("root"));

            // 2. Placing one is the first thing the player does rather than
            //    finds. Every colony in the mod starts at a mound.
            AdvancementHolder ownMound = Advancement.Builder.advancement()
                    .parent(root)
                    .display(
                            ModBlocks.MOLE_MOUND.get(),
                            title("own_mound"),
                            description("own_mound"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("place_mound", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.MOLE_MOUND.get()))
                    .save(writer, Moleverse.id("own_mound"));

            // 3. The gate to everything that sits on a mound. Preparing is a
            //    right-click with loose soil, which the placed-block trigger
            //    does not see — it fires on block placement, not on a block
            //    replaced in world by an interaction. Obtaining the prepared
            //    mound (creative tab, or breaking one) is therefore the second
            //    criterion, and in practice the one that fires.
            AdvancementHolder shoredUp = Advancement.Builder.advancement()
                    .parent(ownMound)
                    .display(
                            ModBlocks.PREPARED_MOLE_MOUND.get(),
                            title("shored_up"),
                            description("shored_up"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("have_prepared_mound",
                            InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.PREPARED_MOLE_MOUND.get()))
                    .addCriterion("place_prepared_mound",
                            ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.PREPARED_MOLE_MOUND.get()))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("shored_up"));

            // 4. Any one fitting proves the socket works. Four criteria under
            //    OR rather than four advancements, because the point is that
            //    the mound accepts attachments at all.
            Advancement.Builder.advancement()
                    .parent(shoredUp)
                    .display(
                            ModBlocks.SHAFT_LANTERN.get(),
                            title("fitting"),
                            description("fitting"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("shaft_lantern", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.SHAFT_LANTERN.get()))
                    .addCriterion("colony_board", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.COLONY_BOARD.get()))
                    .addCriterion("exchange_station", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.EXCHANGE_STATION.get()))
                    .addCriterion("grunting_post", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.GRUNTING_POST.get()))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("fitting"));

            // 5. The burrow. The trigger fires on any arrival in the dimension,
            //    including the debug command, which is deliberate: the point is
            //    to know that the dimension can be entered at all.
            AdvancementHolder downThere = Advancement.Builder.advancement()
                    .parent(shoredUp)
                    .display(
                            ModBlocks.SHRINK_POST.get(),
                            title("down_there"),
                            description("down_there"),
                            null,
                            AdvancementType.GOAL,
                            true,
                            true,
                            false)
                    .addCriterion("enter_burrow", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModDimensions.BURROW))
                    .save(writer, Moleverse.id("down_there"));

            // 6. The great worm is harmless and slow, so killing it is a poor
            //    thing to ask for. There is no vanilla "was near an entity"
            //    trigger, but the location trigger runs every 20 ticks and
            //    PlayerPredicate carries a looking_at clause backed by a ray
            //    cast with a line-of-sight check — so simply setting eyes on
            //    one counts. The kill stays as the second alternative for
            //    anyone who takes that route anyway.
            Advancement.Builder.advancement()
                    .parent(downThere)
                    .display(
                            ModItems.GREAT_WORM_SPAWN_EGG.get(),
                            title("scale_of_it"),
                            description("scale_of_it"),
                            null,
                            AdvancementType.GOAL,
                            true,
                            true,
                            false)
                    .addCriterion("look_at_great_worm", PlayerTrigger.TriggerInstance.located(
                            EntityPredicate.Builder.entity().subPredicate(
                                    PlayerPredicate.Builder.player()
                                            .setLookingAt(EntityPredicate.Builder.entity().of(entityTypes, ModEntities.GREAT_WORM.get()))
                                            .build())))
                    .addCriterion("kill_great_worm", KilledTrigger.TriggerInstance.playerKilledEntity(
                            EntityPredicate.Builder.entity().of(entityTypes, ModEntities.GREAT_WORM.get())))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("scale_of_it"));

            // 7. Placeholder, exactly like the exchange station's loot table:
            //    the burrow has no treasure of its own yet, so this asks for
            //    two vanilla shards that read as "found underground". Replace
            //    both criteria once the burrow has something worth carrying up.
            Advancement.Builder.advancement()
                    .parent(shoredUp)
                    .display(
                            Items.AMETHYST_SHARD,
                            title("a_find"),
                            description("a_find"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("echo_shard", InventoryChangeTrigger.TriggerInstance.hasItems(Items.ECHO_SHARD))
                    .addCriterion("amethyst_shard", InventoryChangeTrigger.TriggerInstance.hasItems(Items.AMETHYST_SHARD))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("a_find"));

            // 8. Neither block exists above ground, so holding one is proof
            //    that a corridor was walked and something was broken down
            //    there — the cheapest check that decoration and the larders
            //    actually generated.
            Advancement.Builder.advancement()
                    .parent(downThere)
                    .display(
                            ModBlocks.GLOW_MYCELIUM.get(),
                            title("deeper"),
                            description("deeper"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("glow_mycelium", InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.GLOW_MYCELIUM.get()))
                    .addCriterion("worm_larder", InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.WORM_LARDER.get()))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(writer, Moleverse.id("deeper"));
        }

        private static Component title(String name) {
            return Component.translatable("advancements." + Moleverse.MOD_ID + "." + name + ".title");
        }

        private static Component description(String name) {
            return Component.translatable("advancements." + Moleverse.MOD_ID + "." + name + ".description");
        }
    }
}

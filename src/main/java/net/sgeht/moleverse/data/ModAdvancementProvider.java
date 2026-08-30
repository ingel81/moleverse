package net.sgeht.moleverse.data;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.ChangeDimensionTrigger;
import net.minecraft.advancements.criterion.ConsumeItemTrigger;
import net.minecraft.advancements.criterion.DamagePredicate;
import net.minecraft.advancements.criterion.EntityHurtPlayerTrigger;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.criterion.KilledTrigger;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.advancements.criterion.PlayerPredicate;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * something to it, go under - and then, below, dig a wall, find a larder, find
 * the nest, and meet what lives there. And it is a test surface — an
 * advancement that never fires in play is a feature that never ran, which is
 * the one failure mode that leaves no trace in the log.</p>
 *
 * <p>The burrow half of the tree is written under that second job before the
 * first. Nothing down there has a position anyone can name, so a location
 * trigger cannot ask for a room; what it can ask for is the material a room is
 * made of, and the generator gives every room one material it uses nowhere
 * else. Each node below records what it actually observes, which is rarely
 * quite what its title claims.</p>
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
            HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
            HolderGetter<Item> items = registries.lookupOrThrow(Registries.ITEM);

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

            // 7. The exchange station grades what it is fed now, and this is
            //    the node that says so: a worm worth more than an earthworm,
            //    however it was come by. It replaces two vanilla shards that
            //    were here only because nothing of the mod's own was ready.
            //
            //    Not either of the two burrow items the title invites. Both are
            //    spoken for - the root nodule carries dig_in and the glow worm
            //    carries warm_hollow - and a criterion that fires at the same
            //    instant as another node's tests nothing the tree did not
            //    already test, which is half of what this tree is for. Both are
            //    also burrow-only, and this node hangs off shored_up on the
            //    surface branch: asking for either would put the one reward a
            //    mound-and-station player can reach behind a descent, and leave
            //    that branch ending at a fitting nobody is paid for.
            //
            //    What it actually observes is the worm economy getting past its
            //    first rung - a worm box on rich feed, or a larder below. That
            //    a station paid one out is not observable at all: a find is put
            //    into a container and never touches the player, so no vanilla
            //    trigger ever sees it. This is the nearest honest thing, and it
            //    is the item the station's whole tier ladder is built around.
            Advancement.Builder.advancement()
                    .parent(shoredUp)
                    .display(
                            ModItems.FAT_WORM.get(),
                            title("a_find"),
                            description("a_find"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("fat_worm", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.FAT_WORM.get()))
                    .save(writer, Moleverse.id("a_find"));

            // 8. Neither block exists above ground, so holding one is proof
            //    that a corridor was walked and something was broken down
            //    there — the cheapest check that decoration and the larders
            //    actually generated.
            //
            //    The worm_larder criterion is the weaker half and knowingly so:
            //    a larder gives up worms and never itself, so outside creative
            //    it cannot fire. It stays because it costs nothing and because
            //    the day a larder starts dropping itself is a day worth
            //    noticing; glow mycelium is what actually carries this node.
            AdvancementHolder deeper = Advancement.Builder.advancement()
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

            // 9. The lining is loose soil, so a wall is something to put a
            //    spade into rather than to walk past, and a hashed share of it
            //    comes out as root nodule. The item exists in no other place in
            //    the game, so holding one is three things at once: the shell
            //    generated soft, the pockets were seeded, and the pocket's own
            //    loot table fired. It is the cheapest check the digging layer
            //    has.
            AdvancementHolder digIn = Advancement.Builder.advancement()
                    .parent(deeper)
                    .display(
                            ModBlocks.LOOSE_SOIL.get(),
                            title("dig_in"),
                            description("dig_in"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("root_nodule", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.ROOT_NODULE.get()))
                    .save(writer, Moleverse.id("dig_in"));

            // 10. Holding a nodule and eating one are different tests. Food
            //     properties are attached at registration and nothing above
            //     complains if they are missing - an item without them simply
            //     cannot be eaten, silently, and the only way to find that out
            //     is to put one in a mouth. This node is that mouth.
            Advancement.Builder.advancement()
                    .parent(digIn)
                    .display(
                            ModItems.ROOT_NODULE.get(),
                            title("field_rations"),
                            description("field_rations"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("eat_root_nodule", ConsumeItemTrigger.TriggerInstance.usedItem(items, ModItems.ROOT_NODULE.get()))
                    .save(writer, Moleverse.id("field_rations"));

            // 11. A larder alcove floors itself in vanilla mud, and AlcoveCarver
            //     is the only thing in this dimension that lays any. So "standing
            //     on mud, below" is "standing in a larder", and that is what the
            //     node tests: the alcove budded off a deep run, the room was cut,
            //     and it was floored. It says nothing about the larder blocks
            //     themselves - those are the next node's business - and a player
            //     who carries mud down and stands on it has cheated a checklist
            //     item, which is a trade worth making for a trigger that needs no
            //     coordinates.
            //
            //     steppingOn is the block underfoot and requires onGround, so it
            //     is genuinely "walked in", not "flew over".
            AdvancementHolder larder = Advancement.Builder.advancement()
                    .parent(downThere)
                    .display(
                            ModBlocks.WORM_LARDER.get(),
                            title("larder"),
                            description("larder"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("stand_in_alcove", PlayerTrigger.TriggerInstance.located(
                            EntityPredicate.Builder.entity()
                                    .located(LocationPredicate.Builder.inDimension(ModDimensions.BURROW))
                                    .steppingOn(LocationPredicate.Builder.location()
                                            .setBlock(BlockPredicate.Builder.block().of(blocks, Blocks.MUD)))))
                    .save(writer, Moleverse.id("larder"));

            // 12. The same trick, and a cleaner one: the nest bed is hay, and
            //     NestCarver's own javadoc records that hay is used nowhere else
            //     in this dimension. Standing on it is therefore proof of the one
            //     thing no location trigger could be written for directly - the
            //     nest was placed at the colony core and its spurs actually
            //     joined it to a corridor, because an unconnected room is a room
            //     nobody stands in.
            //
            //     A location trigger cannot ask for a position the generator
            //     chose, so this is the substitute: ask for the material instead
            //     of the place, and pick a material that only that place has.
            AdvancementHolder nest = Advancement.Builder.advancement()
                    .parent(larder)
                    .display(
                            Blocks.HAY_BLOCK,
                            title("the_nest"),
                            description("the_nest"),
                            null,
                            AdvancementType.GOAL,
                            true,
                            true,
                            false)
                    .addCriterion("stand_in_nest", PlayerTrigger.TriggerInstance.located(
                            EntityPredicate.Builder.entity()
                                    .located(LocationPredicate.Builder.inDimension(ModDimensions.BURROW))
                                    .steppingOn(LocationPredicate.Builder.location()
                                            .setBlock(BlockPredicate.Builder.block().of(blocks, Blocks.HAY_BLOCK)))))
                    .save(writer, Moleverse.id("the_nest"));

            // 13. The trove under the moss lid, and the only node in the tree
            //     that tests arithmetic rather than a block. A larder gives up a
            //     glow worm only if a neighbouring block reads light eight; block
            //     light falls one per block and mycelium is nine, so the glow
            //     frame ringing the lid is the single place in a colony where
            //     that holds. A glow worm in the inventory means the frame was
            //     laid, the lid came off, and the light survived the break.
            //
            //     Not exclusive, and the honest version of that: a worm box fed
            //     glow mycelium also makes them. That route still needs mycelium,
            //     which only grows below, so the node cannot be reached without a
            //     descent - it can be reached without finding the hollow. Worth
            //     it for a criterion that needs no new trigger.
            Advancement.Builder.advancement()
                    .parent(nest)
                    .display(
                            ModItems.GLOW_WORM.get(),
                            title("warm_hollow"),
                            description("warm_hollow"),
                            null,
                            AdvancementType.GOAL,
                            true,
                            true,
                            false)
                    .addCriterion("glow_worm", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.GLOW_WORM.get()))
                    .save(writer, Moleverse.id("warm_hollow"));

            // 14. The first of the three creature nodes, in the order
            //     docs/BURROW_LIFE.md builds them: beetle, shrew, weasel. A
            //     chitin flake comes off a soil beetle and off nothing else, so
            //     this is the spawn list and the entity loot table in one
            //     criterion. The drop is nought or one, so it takes a few
            //     beetles - which is the intended read of a common ambient
            //     animal, not a fault.
            AdvancementHolder chitin = Advancement.Builder.advancement()
                    .parent(downThere)
                    .display(
                            ModItems.CHITIN_FLAKE.get(),
                            title("chitin"),
                            description("chitin"),
                            null,
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion("chitin_flake", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.CHITIN_FLAKE.get()))
                    .save(writer, Moleverse.id("chitin"));

            predators(writer, entityTypes, items, chitin);
        }

        /**
         * The two hunters, written against their ids rather than against their
         * classes.
         *
         * <p>They are built in a wave of their own, so this file must not depend
         * on {@code ModEntities} carrying them yet: a field reference would not
         * compile and a {@code getOrThrow} would abort {@code runData} for
         * everybody. {@link #burrowEntity} looks the id up and hands back nothing
         * when it is not registered, so each node appears by itself on the first
         * generator run after its animal lands, and until then the tree is simply
         * one node shorter.</p>
         *
         * <p>The icons go the same way. Every animal in this mod has a spawn egg
         * named after it, so the egg is asked for by id too and a vanilla
         * stand-in fills the gap - an advancement is allowed to look wrong for a
         * wave; it is not allowed to break the run that generates it.</p>
         */
        private static void predators(Consumer<AdvancementHolder> writer, HolderGetter<EntityType<?>> entityTypes,
                HolderGetter<Item> items, AdvancementHolder chitin) {
            AdvancementHolder parent = chitin;

            // 15. A shrew is fast, weak and comes in twos and threes, and it
            //     wants the same worms the player does. Two ways in, because the
            //     two test different halves: being bitten proves the spawn list
            //     put one in a corridor and that its AI picked the player, and
            //     killing one proves it can be fought at all. Whichever happens
            //     first is the one that mattered.
            Optional<EntityType<?>> shrew = burrowEntity(entityTypes, "shrew");
            if (shrew.isPresent()) {
                parent = Advancement.Builder.advancement()
                        .parent(chitin)
                        .display(
                                icon(items, "shrew_spawn_egg", Items.BONE),
                                title("nipped"),
                                description("nipped"),
                                null,
                                AdvancementType.TASK,
                                true,
                                true,
                                false)
                        .addCriterion("hurt_by_shrew", EntityHurtPlayerTrigger.TriggerInstance.entityHurtPlayer(
                                DamagePredicate.Builder.damageInstance().sourceEntity(
                                        EntityPredicate.Builder.entity().of(entityTypes, shrew.get()).build())))
                        .addCriterion("kill_shrew", KilledTrigger.TriggerInstance.playerKilledEntity(
                                EntityPredicate.Builder.entity().of(entityTypes, shrew.get())))
                        .requirements(AdvancementRequirements.Strategy.OR)
                        .save(writer, Moleverse.id("nipped"));
            }

            // 16. The weasel is the rare one and the end of the branch, so it
            //     asks for the kill and nothing softer. Being hurt by one is not
            //     an achievement, it is the normal course of meeting one; walking
            //     away from the corridor with the weasel not in it is the event
            //     the wave exists to produce.
            Optional<EntityType<?>> weasel = burrowEntity(entityTypes, "weasel");
            if (weasel.isPresent()) {
                Advancement.Builder.advancement()
                        .parent(parent)
                        .display(
                                icon(items, "weasel_spawn_egg", Items.RABBIT_HIDE),
                                title("not_today"),
                                description("not_today"),
                                null,
                                AdvancementType.CHALLENGE,
                                true,
                                true,
                                false)
                        .addCriterion("kill_weasel", KilledTrigger.TriggerInstance.playerKilledEntity(
                                EntityPredicate.Builder.entity().of(entityTypes, weasel.get())))
                        .save(writer, Moleverse.id("not_today"));
            }
        }

        /** An entity type of this mod by id, or nothing if its wave has not landed. */
        private static Optional<EntityType<?>> burrowEntity(HolderGetter<EntityType<?>> entityTypes, String path) {
            return entityTypes.get(ResourceKey.create(Registries.ENTITY_TYPE, Moleverse.id(path)))
                    .map(holder -> (EntityType<?>) holder.value());
        }

        /** An item of this mod by id, with a vanilla stand-in while it does not exist. */
        private static ItemLike icon(HolderGetter<Item> items, String path, ItemLike fallback) {
            return items.get(ResourceKey.create(Registries.ITEM, Moleverse.id(path)))
                    .<ItemLike>map(Holder.Reference::value)
                    .orElse(fallback);
        }

        private static Component title(String name) {
            return Component.translatable("advancements." + Moleverse.MOD_ID + "." + name + ".title");
        }

        private static Component description(String name) {
            return Component.translatable("advancements." + Moleverse.MOD_ID + "." + name + ".description");
        }
    }
}

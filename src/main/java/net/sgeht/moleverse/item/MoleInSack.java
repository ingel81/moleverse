package net.sgeht.moleverse.item;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.registry.ModEntities;
import net.sgeht.moleverse.registry.ModItems;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * A mole, carried.
 *
 * <p>What a {@link net.sgeht.moleverse.block.MoleTrap} hands over, and the only
 * way to put a colony where a player wants one instead of where a wandering mole
 * happened to stop. Right-clicking the ground lets the animal out; the goal it
 * runs from then on is what founds the colony, not this item - see
 * {@code ColonyStore.found}, which is reached the first time the released mole
 * digs.</p>
 *
 * <p>The mole travels in the stack's components, not in a scheme of this mod's
 * own. In this version the entity is written into {@code DataComponents.ENTITY_DATA},
 * which is a {@link TypedEntityData} of an {@code EntityType} and a tag - not the
 * {@code CustomData} that older code and every tutorial reaches for. That
 * distinction is the whole reason this class is short: {@code TypedEntityData}
 * carries the type, checks it against the entity it is applied to, and merges the
 * tag over a freshly spawned mole through {@code loadInto}, so age, health and
 * everything else a mole saves come back without a field-by-field copy.</p>
 *
 * <p>The tag comes from {@code saveWithoutId} and is therefore the mole's whole
 * save state, which is more than should be carried - see {@link #NOT_CARRIED}.</p>
 */
public class MoleInSack extends Item {

    /**
     * Keys stripped out of the saved mole before it goes into the sack.
     *
     * <p>Three different reasons, all of them bugs if they are left in.</p>
     *
     * <p>The position keys describe where the mole was caught. {@code ENTITY_DATA}
     * is applied <em>after</em> {@code EntityType.create} has already placed the
     * new entity, so a stored {@code Pos} would silently teleport every released
     * mole back to the trap it came out of.</p>
     *
     * <p>{@code UUID} is dropped because the sack is not the mole - it is a
     * record of one. {@code TypedEntityData.loadInto} restores the fresh entity's
     * own id afterwards anyway, so keeping it would only make two identical moles
     * carry the same name plate in a debug view.</p>
     *
     * <p>{@code CustomName} moves to the stack's own {@code CUSTOM_NAME}
     * component instead, which is what makes a named mole readable in the
     * inventory and renameable in an anvil. It is applied back to the entity by
     * {@code EntityType.appendComponentsConfig} on release, so the name survives
     * either way - but only one copy may exist, or the anvil would silently lose
     * against the tag.</p>
     *
     * <p>{@code OpenShaft} is the mound the mole left standing open. The trap
     * discards the mole immediately after taking this copy, and discarding runs
     * the mole's own cleanup, which shuts that shaft. Carrying the position
     * forward would mean a mole released an hour later closing a mound that
     * somebody else's mole is currently down.</p>
     */
    private static final List<String> NOT_CARRIED = List.of(
            "Pos", "Motion", "Rotation", "OnGround", "fall_distance",
            "UUID", "CustomName", "OpenShaft");

    public MoleInSack(Item.Properties properties) {
        super(properties);
    }

    /**
     * Packs a mole into a fresh sack. Does <em>not</em> remove it from the world -
     * that is the caller's job, and the order matters where a shaft is involved.
     */
    public static ItemStack holding(Mole mole) {
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector problems =
                new ProblemReporter.ScopedCollector(mole.problemPath(), Moleverse.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(problems, mole.registryAccess());
            mole.saveWithoutId(output);
            tag = output.buildResult();
        }

        for (String key : NOT_CARRIED) {
            tag.remove(key);
        }

        ItemStack stack = new ItemStack(ModItems.MOLE_IN_SACK.get());
        stack.set(DataComponents.ENTITY_DATA, TypedEntityData.<EntityType<?>>of(ModEntities.MOLE.get(), tag));
        stack.copyFrom(DataComponents.CUSTOM_NAME, mole);
        return stack;
    }

    /**
     * Letting the mole out.
     *
     * <p>The placement is the spawn egg's, deliberately: click the top of a
     * grass block and the animal stands on the grass, click its side and it
     * stands beside it. Reinventing that is how a released mole ends up inside
     * the wall it was let out against.</p>
     *
     * <p>Server side only. The mole is rebuilt from the stack's components there
     * and reaches the client as an ordinary entity spawn, so there is nothing
     * worth predicting - and a predicted mole that the server then refused would
     * be an animal that appears and vanishes.</p>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos spot = state.getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(face);

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // spawn() applies the stack's components and then ENTITY_DATA, in that
        // order, so the mole is finalised as a fresh spawn first and then
        // overwritten with what it actually was. That is why a caught baby is
        // still a baby: finalizeSpawn may roll an age, and the stored one lands
        // on top of it.
        Mole mole = ModEntities.MOLE.get().spawn(serverLevel, stack, player, spot,
                EntitySpawnReason.BUCKET, true, !clicked.equals(spot) && face == Direction.UP);
        if (mole == null) {
            return InteractionResult.FAIL;
        }

        serverLevel.playSound(null, mole.getX(), mole.getY(), mole.getZ(),
                ModSounds.MOLE_SURFACE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        stack.consume(1, player);
        serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, spot);
        return InteractionResult.SUCCESS;
    }
}

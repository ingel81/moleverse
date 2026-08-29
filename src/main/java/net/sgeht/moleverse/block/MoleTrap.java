package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sgeht.moleverse.block.entity.MoleTrapBlockEntity;
import net.sgeht.moleverse.entity.Mole;
import net.sgeht.moleverse.item.MoleInSack;
import net.sgeht.moleverse.registry.ModItems;

import org.jetbrains.annotations.Nullable;

/**
 * A baited box on a prepared mound. The mole that comes up it is caught alive.
 *
 * <p>Every other way to acquire a mole ends with a dead one. This is the
 * alternative, and it is deliberately not a fight: the trap does nothing on its
 * own, the player never sees the catch happen, and what comes out is the same
 * animal that went in - the same age, the same name, the same health. Releasing
 * it somewhere else is the only deliberate way to start a colony where a player
 * wants one, rather than where a wandering mole happened to stop.</p>
 *
 * <p><strong>A trap blocks the mound while it is armed or full.</strong> That is
 * the cost, and it is the point. The mound stays in the colony's network and
 * moles keep routing to it, but while the trap is set every one that surfaces
 * there is taken out of the world, and while it is full it is a fitting that
 * does nothing until somebody empties it. Catching therefore does not cost a
 * worm - it costs a working exit, for as long as the trap stands. A trap left on
 * a busy mound quietly drains the colony that feeds it.</p>
 *
 * <p>An empty hand takes the bait back out again. A trap that could only be
 * unset by breaking it would make a misplaced worm cost the block as well.</p>
 *
 * <p>The mole itself lives in a {@link MoleTrapBlockEntity} while it is held.
 * Unlike the lantern's glow and the grunting post's cooldown, which are single
 * bits and get by on a scheduled tick, this is a whole animal: an age, a name, a
 * health value. There is nowhere in a block state to put that, and losing it
 * would mean a trap that returns some other mole than the one it caught.</p>
 */
public class MoleTrap extends MoundAttachment implements EntityBlock {

    public static final MapCodec<MoleTrap> CODEC = simpleCodec(MoleTrap::new);

    /**
     * What the box is doing.
     *
     * <p>Three named values rather than two booleans. Armed and full are
     * mutually exclusive - the mole that springs the trap is the one that ate
     * the bait - and a pair of flags would leave a fourth combination that means
     * nothing and still has to be modelled.</p>
     */
    public static final EnumProperty<State> STATE = EnumProperty.create("state", State.class);

    /**
     * A crate sunk into the mound's rim, the way the exchange station is. The
     * outline is the same in all three states: what changes is the front of the
     * box, not its footprint, so nothing a player has walked around moves under
     * them when the trap springs.
     */
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 9.0, 14.0);

    public MoleTrap(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(STATE, State.EMPTY));
    }

    @Override
    public MapCodec<MoleTrap> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoleTrapBlockEntity(pos, state);
    }

    // --- the player's half ----------------------------------------------------

    /**
     * Baiting. One earthworm, and only into an empty box.
     *
     * <p>Everything else falls through to the empty-hand path on purpose. A
     * player holding a worm in front of a full trap is reaching for the mole, not
     * trying to feed a box that is already shut.</p>
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(STATE) != State.EMPTY || !stack.is(ModItems.EARTHWORM.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(pos, state.setValue(STATE, State.BAITED), Block.UPDATE_ALL);
            serverLevel.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.4F, 1.4F);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            // What the trap holds lives in the block entity and is never sent
            // anywhere, so the client has nothing to predict with.
            return InteractionResult.CONSUME;
        }

        return switch (state.getValue(STATE)) {
            case FULL -> takeCatch(serverLevel, pos, state, player);
            case BAITED -> takeBait(serverLevel, pos, state, player);
            case EMPTY -> InteractionResult.CONSUME;
        };
    }

    /** Hands the caught mole over as a {@link MoleInSack} and opens the box again. */
    private static InteractionResult takeCatch(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        ItemStack sack = level.getBlockEntity(pos) instanceof MoleTrapBlockEntity trap
                ? trap.take()
                : ItemStack.EMPTY;

        if (sack.isEmpty()) {
            // The state said full and the store was not - a hand-placed block, or
            // a block entity that lost its data. Reset rather than leave a trap
            // that can never be emptied and never catches anything again.
            level.setBlock(pos, state.setValue(STATE, State.EMPTY), Block.UPDATE_ALL);
            return InteractionResult.CONSUME;
        }

        give(player, sack);
        level.setBlock(pos, state.setValue(STATE, State.EMPTY), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Unsetting the trap. The worm comes back whole - it was never eaten. */
    private static InteractionResult takeBait(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        give(player, new ItemStack(ModItems.EARTHWORM.get()));
        level.setBlock(pos, state.setValue(STATE, State.EMPTY), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.3F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Into the inventory, or onto the ground when there is no room for it. */
    private static void give(Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    // --- the colony's half ----------------------------------------------------

    /**
     * A mole has come up through the mound. If the box is baited, that is the
     * catch.
     *
     * <p>Runs from the burrow goal at the end of the emerge animation, so the
     * mole is standing in the mound below and is about to go back to wandering.
     * Removing it here is the whole mechanic: from above ground a trap simply
     * shuts, and the animal it took is in the box.</p>
     */
    @Override
    protected void moleSurfaced(ServerLevel level, BlockPos pos, BlockState state, Mole mole) {
        if (state.getValue(STATE) != State.BAITED) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MoleTrapBlockEntity trap)) {
            // Nowhere to put him. A mole that walks away from a broken trap is a
            // bug somebody can see; one that is deleted because the store was
            // missing is a mole that is simply gone.
            return;
        }

        trap.hold(MoleInSack.holding(mole));
        level.setBlock(pos, state.setValue(STATE, State.FULL), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 0.9F);
        level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                6, 0.2, 0.1, 0.2, 0.0);

        // Last, and the order matters. Discarding runs the mole's own removal
        // hook, which shuts the shaft it left standing open at the mound it went
        // down - so a catch never leaves a crater open for the rest of the
        // world's life. The copy in the sack was taken one line earlier, while
        // that record still existed, and MoleInSack drops it again on the way in:
        // by the time anybody releases this mole the shaft is long closed, and a
        // stale position would only shut somebody else's.
        mole.discard();
    }

    /** Empty box, set box, shut box. */
    public enum State implements StringRepresentable {

        EMPTY("empty"),
        BAITED("baited"),
        FULL("full");

        private final String name;

        State(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}

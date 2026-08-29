package net.sgeht.moleverse.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.sgeht.moleverse.registry.ModBlockEntities;

/**
 * The one mole a {@link net.sgeht.moleverse.block.MoleTrap} is holding.
 *
 * <p>Stored as the finished {@code MOLE_IN_SACK} stack rather than as a loose
 * entity tag. The trap has to be able to hand the same thing to a player who
 * right-clicks it and to the ground when the block is broken, and building the
 * item twice from a tag is two chances to build it differently.</p>
 *
 * <p>Server side only, and never synchronised: nothing about the catch is
 * visible from outside the box. What the block shows is the {@code full} value
 * of its own block state, which travels with the block like any other.</p>
 */
public class MoleTrapBlockEntity extends BlockEntity {

    private static final String CATCH_KEY = "catch";

    /** The sack, or empty. Never more than one - a trap springs once. */
    private ItemStack held = ItemStack.EMPTY;

    public MoleTrapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLE_TRAP.get(), pos, state);
    }

    public void hold(ItemStack sack) {
        this.held = sack;
        this.setChanged();
    }

    /** Takes the catch out and leaves the box empty. Returns an empty stack when there was none. */
    public ItemStack take() {
        ItemStack taken = this.held;
        this.held = ItemStack.EMPTY;
        this.setChanged();
        return taken;
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        this.held = data.read(CATCH_KEY, ItemStack.SINGLE_ITEM_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        if (!this.held.isEmpty()) {
            data.store(CATCH_KEY, ItemStack.SINGLE_ITEM_CODEC, this.held);
        }
    }

    /**
     * Gives the mole back when the trap goes.
     *
     * <p>Covers both ways it can go: a player breaking the box, and the prepared
     * mound underneath being broken instead, which drops the fitting through
     * {@code MoundAttachment.updateShape}. Both end in the same block change and
     * this hook is what that change calls - which is what keeps a caught mole
     * from being deleted by a stray pickaxe.</p>
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel && !this.held.isEmpty()) {
            Block.popResource(this.level, pos, this.take());
        }
    }
}

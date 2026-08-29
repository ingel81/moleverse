package net.sgeht.moleverse.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.block.entity.ExchangeStationBlockEntity;

/**
 * Block entity types of this mod.
 *
 * <p>A type names the blocks it is valid for, so this register has to be
 * attached after {@link ModBlocks} - see {@code ModRegistries.register}.</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Moleverse.MOD_ID);

    /**
     * The exchange station's two inventories.
     *
     * <p>The block supplier is resolved inside the {@code register} lambda, not
     * at class initialisation: the type is built during registration, by which
     * point the block itself is there.</p>
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExchangeStationBlockEntity>>
            EXCHANGE_STATION = REGISTER.register(
                    "exchange_station",
                    () -> new BlockEntityType<ExchangeStationBlockEntity>(
                            ExchangeStationBlockEntity::new,
                            ModBlocks.EXCHANGE_STATION.get()));

    private ModBlockEntities() {
    }
}

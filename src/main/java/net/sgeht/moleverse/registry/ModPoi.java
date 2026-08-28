package net.sgeht.moleverse.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sgeht.moleverse.Moleverse;

/**
 * Points of interest of this mod.
 *
 * <p>Mole mounds are indexed as points of interest so that a mole can ask
 * "which mounds are within sixteen blocks" without reading the world block by
 * block. That question is asked before every dig, and the answer feeds a chained
 * search that repeats it around each mound it finds - as a raw scan it would run
 * into hundreds of thousands of block reads for a single decision. Vanilla
 * indexes beds and workstations exactly this way.</p>
 *
 * <p>NeoForge maps every matching blockstate to its type automatically when the
 * type is registered, so nothing else is needed here. Blocks are registered
 * before points of interest, which is what lets the state set resolve.</p>
 */
public final class ModPoi {

    public static final DeferredRegister<PoiType> REGISTER =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Moleverse.MOD_ID);

    /**
     * How many moles may hold a ticket on one mound. Moles query with
     * {@code Occupancy.ANY} and never take a ticket, so this only matters if
     * something later wants exclusive use of a shaft.
     */
    private static final int MAX_TICKETS = 1;

    /** Distance a mole may be from the mound and still count as arrived. */
    private static final int VALID_RANGE = 32;

    public static final DeferredHolder<PoiType, PoiType> MOLE_MOUND = REGISTER.register(
            "mole_mound",
            () -> new PoiType(
                    Set.copyOf(ModBlocks.MOLE_MOUND.get().getStateDefinition().getPossibleStates()),
                    MAX_TICKETS,
                    VALID_RANGE));

    private ModPoi() {
    }
}

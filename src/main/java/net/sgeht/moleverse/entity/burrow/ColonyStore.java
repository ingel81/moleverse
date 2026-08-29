package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every colony of one level, written to disk with the world.
 *
 * <p>{@link SavedData} rather than chunk data: a colony spans many chunks by
 * definition, and so does a trip between two of its mounds. The registration is
 * the codec-based form this version uses - a {@link SavedDataType} carrying the
 * file id, a constructor for the empty case and the codec - not the
 * {@code load}/{@code save} pair that every tutorial still shows.</p>
 *
 * <p>The mounds themselves stay in the point-of-interest index. This holds only
 * what the index cannot: which ground belongs to whom, and since when.</p>
 */
public class ColonyStore extends SavedData {

    private static final Codec<ColonyStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Colony.CODEC.listOf().optionalFieldOf("colonies", List.of()).forGetter(store -> store.colonies),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(store -> store.nextId))
            .apply(instance, ColonyStore::new));

    /**
     * The data fixer type is required and none of them describes mod data. The
     * level type is the harmless choice: its fixers key on vanilla structures
     * that never appear inside this file.
     */
    public static final SavedDataType<ColonyStore> TYPE = new SavedDataType<>(
            "moleverse_colonies", ColonyStore::new, CODEC, DataFixTypes.LEVEL);

    private final List<Colony> colonies = new ArrayList<>();
    private int nextId = 1;

    public ColonyStore() {
    }

    private ColonyStore(List<Colony> colonies, int nextId) {
        this.colonies.addAll(colonies);
        this.nextId = nextId;
    }

    public static ColonyStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The colony this ground belongs to, or null where none does. */
    public @Nullable Colony at(BlockPos pos) {
        for (Colony colony : this.colonies) {
            if (colony.contains(pos)) {
                return colony;
            }
        }
        return null;
    }

    /**
     * Starts a colony centred here.
     *
     * <p>Refused when another core is closer than
     * {@link BurrowConstants#COLONY_MIN_SEPARATION}. That leaves a band of
     * unclaimed ground around every colony which belongs to nobody: a mole
     * standing there is told to walk on, and walking on is what spreads a
     * territory instead of stacking it.</p>
     *
     * @return the new colony, or null when this ground is too close to one that
     *         already exists
     */
    public @Nullable Colony found(BlockPos core, long gameTime) {
        for (Colony colony : this.colonies) {
            if (Colony.separation(colony.core(), core) < BurrowConstants.COLONY_MIN_SEPARATION) {
                return null;
            }
        }

        Colony colony = new Colony(this.nextId++, core, gameTime);
        this.colonies.add(colony);
        this.setDirty();
        return colony;
    }

    public List<Colony> all() {
        return List.copyOf(this.colonies);
    }
}

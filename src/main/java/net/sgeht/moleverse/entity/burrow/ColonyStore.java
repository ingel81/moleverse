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
import net.sgeht.moleverse.block.MoleMound;

/**
 * Every colony of one level and every run its moles have travelled, written to
 * disk with the world.
 *
 * <p>{@link SavedData} rather than chunk data: a colony spans many chunks by
 * definition, and so does a link between two of its mounds. The registration is
 * the codec-based form this version uses - a {@link SavedDataType} carrying the
 * file id, a constructor for the empty case and the codec - not the
 * {@code load}/{@code save} pair that every tutorial still shows.</p>
 *
 * <p>The mounds themselves stay in the point-of-interest index. This holds only
 * what the index cannot: which ground belongs to whom, since when, and which
 * pairs of mounds are joined by a run that was really dug.</p>
 */
public class ColonyStore extends SavedData {

    private static final Codec<ColonyStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Colony.CODEC.listOf().optionalFieldOf("colonies", List.of()).forGetter(store -> store.colonies),
            BurrowLink.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(store -> store.links),
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
    private final List<BurrowLink> links = new ArrayList<>();
    private int nextId = 1;

    public ColonyStore() {
    }

    private ColonyStore(List<Colony> colonies, List<BurrowLink> links, int nextId) {
        this.colonies.addAll(colonies);
        this.links.addAll(links);
        this.nextId = nextId;
    }

    public static ColonyStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- colonies -------------------------------------------------------------

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

    /**
     * Whether a colony could be founded here - which is also what an emigrating
     * mole is walking towards.
     *
     * <p>Only the separation is asked. It is larger than
     * {@link BurrowConstants#COLONY_EXTENT}, so a position inside somebody's box
     * fails it anyway.</p>
     */
    public boolean isFreeGround(BlockPos pos) {
        for (Colony colony : this.colonies) {
            if (Colony.separation(colony.core(), pos) < BurrowConstants.COLONY_MIN_SEPARATION) {
                return false;
            }
        }
        return true;
    }

    public List<Colony> all() {
        return List.copyOf(this.colonies);
    }

    // --- links ----------------------------------------------------------------

    /** The run between these two mounds, whichever way round, or null. */
    public @Nullable BurrowLink linkBetween(BlockPos a, BlockPos b) {
        for (BurrowLink link : this.links) {
            if (link.joins(a, b)) {
                return link;
            }
        }
        return null;
    }

    /**
     * The depth a run between these two mounds is dug at.
     *
     * <p>An established run keeps the level it was dug at: the pair is asked
     * before a trip is planned, and only a pair that has never been travelled
     * rolls a new one. Letting a link change level would put two corridors in
     * the burrow below where the colony has one run.</p>
     */
    public @Nullable RunLevel levelBetween(BlockPos a, BlockPos b) {
        BurrowLink link = this.linkBetween(a, b);
        return link == null ? null : link.level();
    }

    /**
     * Writes down a run that was just completed, or counts one more use of a run
     * that was already known.
     */
    public void record(ServerLevel level, int colony, BlockPos a, BlockPos b, RunLevel run,
            List<Integer> depths) {
        long now = level.getGameTime();
        for (int i = 0; i < this.links.size(); i++) {
            BurrowLink existing = this.links.get(i);
            if (existing.joins(a, b)) {
                this.links.set(i, existing.reshaped(depths, now));
                this.setDirty();
                return;
            }
        }

        this.links.add(new BurrowLink(colony, a, b, run, List.copyOf(depths), 1, now));
        this.setDirty();
    }

    public List<BurrowLink> linksOf(int colony) {
        return this.links.stream().filter(link -> link.colony() == colony).toList();
    }

    /** Every run with an end within {@code radius} of this position. */
    public List<BurrowLink> linksNear(BlockPos pos, int radius) {
        int radiusSqr = radius * radius;
        return this.links.stream()
                .filter(link -> link.a().distSqr(pos) <= radiusSqr || link.b().distSqr(pos) <= radiusSqr)
                .toList();
    }

    public int linkCount() {
        return this.links.size();
    }

    /**
     * Drops runs whose ends are no longer mounds.
     *
     * <p>Only where the ground can actually be looked at: a link into an
     * unloaded chunk is left alone, because "no mound there" and "nothing loaded
     * there" are indistinguishable from here, and the second must never delete
     * anything. Pruning is therefore something that happens when somebody asks,
     * not a background sweep - a mound broken in a corner of the world nobody
     * has visited since stays recorded until the world gets round to it.</p>
     *
     * @return how many links were removed
     */
    public int prune(ServerLevel level) {
        int before = this.links.size();
        this.links.removeIf(link -> gone(level, link.a()) || gone(level, link.b()));

        int removed = before - this.links.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }

    private static boolean gone(ServerLevel level, BlockPos mound) {
        return level.isLoaded(mound) && !MoleMound.isMound(level, mound);
    }
}

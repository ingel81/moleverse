package net.sgeht.moleverse.dimension.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.AlcoveCarver;
import net.sgeht.moleverse.dimension.BoltHoles;
import net.sgeht.moleverse.dimension.Junctions;
import net.sgeht.moleverse.dimension.LevelShafts;
import net.sgeht.moleverse.dimension.NestCarver;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;

/**
 * Everything the burrow contains, worked out from the colonies above it.
 *
 * <p>This is the seam the whole of {@code docs/BURROW_WORLDGEN.md} turns on.
 * Vanilla generation is a pure function of the seed, which is what lets a chunk
 * answer its own question without looking at any neighbour; the burrow's shape is
 * the history of a colony instead, and {@code ColonyStore} plays the seed's part.
 * The store in, a list of features out, and nothing in between reads a block,
 * takes a lock or knows which thread it is on.</p>
 *
 * <p><strong>Deterministic, and that is a requirement rather than a nicety.</strong>
 * The same store gives the same list in the same order with the same hashes, on
 * every call, in every world that ever loads that save. A chunk's ledger records
 * what it applied by key and hash, so a list that reordered itself or hashed
 * differently would have the whole burrow carved a second time - harmless, and
 * enough work to be felt. Every ordering below is therefore sorted rather than
 * inherited from the store: colonies by id, mounds by position, crossings by the
 * finders' own sort, larders and bolt-holes by their keys - see
 * {@link #sortedByKey}, which is where a per-run finder's output stops being the
 * store's order and starts being the colony's.</p>
 *
 * <h2>The order is the carve order</h2>
 *
 * <p>Corridors, then chambers, then shafts, then junctions, then the rooms of the
 * colony's anatomy - the nest, the larders and the bolt-holes - and
 * {@link #intersecting} keeps that order, so a chunk that reconciles the list top
 * to bottom gets it for free. The middle step is the one that matters most: a shaft
 * and a junction are both a widening of corridors that already exist, and neither
 * will touch a crossing where the two runs are not open. Cutting them after the
 * runs means a chunk finishes what it started rather than leaving it for the next
 * reconcile.</p>
 *
 * <p>The anatomy comes last for a softer version of the same reason. A larder buds
 * off the side of a run and a bolt-hole climbs out of one, so both are cut into
 * ground a corridor has usually already opened; unlike a shaft neither <em>needs</em>
 * that to have happened - they overlap the corridor's own bore by construction, so
 * one cut first is a volume the run opens into rather than a room sealed beside it.
 * Corridors first anyway, because the alternative is a corridor carving through a
 * larder's worms on the pass after they were placed.</p>
 *
 * <h2>And carving is not the last pass</h2>
 *
 * <p>{@link BurrowFeature#carveWithin} moves earth;
 * {@link BurrowFeature#decorateWithin} dresses what was moved, and it cannot run
 * in the same breath. Dressing <em>measures</em> - the tunnel pass probes for the
 * floor, the ceiling and the walls, a chamber's larders probe for a wall that a
 * corridor may still be about to open - and a probe run against a neighbour that
 * is still solid earth reads the chunk border as the end of the world and dresses
 * it as one. So the reconciler carves a chunk's neighbourhood first and decorates
 * afterwards, in this same list order.</p>
 */
public final class BurrowPlan {

    private BurrowPlan() {
    }

    /**
     * Every feature of these colonies' networks.
     *
     * <p>Links are matched to colonies by id, and a link whose colony is not in
     * the list is skipped rather than planned on its own. That is what lets a
     * caller plan one colony by handing over one colony, and it is also the honest
     * reading: a chamber is the mounds of a colony and a crossing is a pair of its
     * runs, so neither question has an answer for a run belonging to nobody.</p>
     */
    public static List<BurrowFeature> featuresOf(List<Colony> colonies, List<BurrowLink> links) {
        List<Colony> byId = new ArrayList<>(colonies);
        byId.sort(Comparator.comparingInt(Colony::id));

        List<BurrowFeature> corridors = new ArrayList<>();
        List<BurrowFeature> chambers = new ArrayList<>();
        List<BurrowFeature> shafts = new ArrayList<>();
        List<BurrowFeature> junctions = new ArrayList<>();
        List<BurrowFeature> nests = new ArrayList<>();
        List<BurrowFeature> larders = new ArrayList<>();
        List<BurrowFeature> boltHoles = new ArrayList<>();

        for (Colony colony : byId) {
            List<BurrowLink> runs = links.stream()
                    .filter(link -> link.colony() == colony.id() && link.pointCount() > 0)
                    .toList();
            if (runs.isEmpty()) {
                continue;
            }

            for (BurrowLink run : runs) {
                // A single waypoint is a run with no length: there is nothing to
                // interpolate along and the carver returns without writing. It
                // still counts towards the chamber below, which only wants the
                // height the run leaves at.
                if (run.pointCount() >= 2) {
                    corridors.add(new CorridorFeature(run));
                }
            }

            for (BlockPos mound : moundsOf(runs)) {
                chambers.add(ChamberFeature.of(mound, ChamberFeature.runsAt(runs, mound)));
            }

            for (LevelShafts.Crossing crossing : LevelShafts.crossingsOf(runs)) {
                shafts.add(new ShaftFeature(crossing));
            }

            for (Junctions.Crossing crossing : Junctions.crossingsOf(runs)) {
                junctions.add(new JunctionFeature(crossing));
            }

            // One per colony, at its core, whatever the colony has dug. The finder
            // takes all of the runs because the spurs are aimed at the two nearest
            // corridors and "nearest" is a question about the whole network.
            nests.add(new NestFeature(NestCarver.nestOf(colony, runs)));

            larders.addAll(sortedByKey(lardersOf(runs)));
            boltHoles.addAll(sortedByKey(boltHolesOf(runs)));
        }

        List<BurrowFeature> all = new ArrayList<>(
                corridors.size() + chambers.size() + shafts.size() + junctions.size()
                        + nests.size() + larders.size() + boltHoles.size());
        all.addAll(corridors);
        all.addAll(chambers);
        all.addAll(shafts);
        all.addAll(junctions);
        all.addAll(nests);
        all.addAll(larders);
        all.addAll(boltHoles);
        return List.copyOf(all);
    }

    /** Every larder budding off this colony's deep runs, in whatever order the runs came in. */
    private static List<BurrowFeature> lardersOf(List<BurrowLink> runs) {
        List<BurrowFeature> found = new ArrayList<>();
        for (BurrowLink run : runs) {
            for (AlcoveCarver.Larder larder : AlcoveCarver.lardersOf(run)) {
                found.add(new LarderFeature(larder));
            }
        }
        return found;
    }

    /** Every bolt-hole this colony's runs have, in whatever order the runs came in. */
    private static List<BurrowFeature> boltHolesOf(List<BurrowLink> runs) {
        List<BurrowFeature> found = new ArrayList<>();
        for (BurrowLink run : runs) {
            BoltHoles.Stub stub = BoltHoles.on(run);
            if (stub != null) {
                found.add(new BoltHoleFeature(stub));
            }
        }
        return found;
    }

    /**
     * The same features, in an order that belongs to the colony rather than to the
     * store.
     *
     * <p>Both finders above walk the run list as it was handed over, and that order
     * is the order {@code ColonyStore} last wrote its links in - which is not a
     * property of the colony at all. A list that reordered itself between two loads
     * would not be wrong, because a chunk's ledger records what it applied by key;
     * it would be a burrow that carved itself in a different sequence every time a
     * link was rewritten, and this whole class exists to make that impossible. The
     * key is the sort because it is built from positions and is unique by
     * construction.</p>
     */
    private static List<BurrowFeature> sortedByKey(List<BurrowFeature> features) {
        features.sort(Comparator.comparing(BurrowFeature::key));
        return features;
    }

    /**
     * The subset whose bounds intersect this burrow chunk, in the order they were
     * given.
     *
     * <p>Only the horizontal is asked, because a chunk is the whole column - a
     * feature that overlaps its sixteen by sixteen footprint at all is a feature
     * this chunk has a share of.</p>
     *
     * <p>A walk over the whole list, which is a dozen or two features per colony
     * and a handful of colonies. If a world ever grows enough of them for this to
     * show in a profile, the seam for a spatial index is exactly here and nothing
     * outside this method needs to know.</p>
     */
    public static List<BurrowFeature> intersecting(List<BurrowFeature> all, ChunkPos chunk) {
        List<BurrowFeature> here = new ArrayList<>();
        for (BurrowFeature feature : all) {
            if (feature.bounds().intersects(
                    chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ())) {
                here.add(feature);
            }
        }
        return List.copyOf(here);
    }

    /**
     * The box a chunk is allowed to write in: its own footprint, floor to sky.
     *
     * <p>This is the clamp every {@link BurrowFeature#carveWithin} gets, and it is
     * what replaces the loaded-chunk check as the <em>normal</em> way a carve is
     * bounded. The vertical comes from the level rather than from the burrow's own
     * range, because a feature at the very top or bottom of that range still
     * reaches a few blocks past it and there is nothing to be gained by clipping
     * it twice.</p>
     */
    public static BoundingBox clampFor(ChunkPos chunk, LevelHeightAccessor level) {
        return new BoundingBox(
                chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
    }

    /**
     * Every mound this colony's runs end at, once each and in a fixed order.
     *
     * <p>Sorted by packed position rather than left in the order the runs were
     * walked: a set has no order at all and the run list's order belongs to the
     * store, and either would put the colony's chambers into the feature list
     * differently from one load to the next.</p>
     */
    private static List<BlockPos> moundsOf(List<BurrowLink> runs) {
        Set<BlockPos> mounds = new HashSet<>();
        for (BurrowLink run : runs) {
            mounds.add(run.a());
            mounds.add(run.b());
        }

        List<BlockPos> ordered = new ArrayList<>(mounds);
        ordered.sort(Comparator.comparingLong(BlockPos::asLong));
        return ordered;
    }
}

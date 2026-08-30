package net.sgeht.moleverse.dimension.plan;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.ChamberFurnisher;
import net.sgeht.moleverse.dimension.CorridorCarver;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;

/**
 * The room under one mound, and what a colony puts in it.
 *
 * <p>One per mound that has runs ending at it. A mound with no run has nothing to
 * open into and gets no chamber - the room exists because corridors meet there,
 * not because there is a heap of earth above.</p>
 *
 * <p>Where the room goes is not where the mound maps to, and the arithmetic that
 * decides it is the interesting part of this class rather than the carving. It
 * lived in {@code BurrowTransit} while entering the burrow was the only thing
 * that ever carved one; it is here now because a chunk has to be able to answer
 * the same question with nobody standing anywhere near the mound. See
 * {@link #floorAt} for the reasoning, which is the same reasoning it always
 * was.</p>
 *
 * <p><strong>The way out and the worms are not here.</strong> A shrink post is
 * placed for a player who is about to arrive and a great worm is an entity, which
 * is to say neither is idempotent and neither belongs to a chunk that may be
 * reconciled a dozen times. Both stay with whoever owns the arrival.</p>
 */
public record ChamberFeature(BlockPos mound, BlockPos centre, List<Integer> mouthLayers)
        implements BurrowFeature {

    /**
     * Blocks of air a gallery needs above its floor.
     *
     * <p>{@code CorridorCarver}'s own figure, which is private there and is copied
     * here for one purpose only: the carve cuts the ledge zone this far past the
     * highest mouth, so the bounds have to reach at least as high or the top
     * gallery would be clipped at a chunk border. Two, because that is how tall a
     * player is.</p>
     */
    private static final int GALLERY_HEADROOM = 2;

    /**
     * How far past the room the bounds reach.
     *
     * <p>The furnishing pass probes one block past the chamber's own height
     * looking for a ceiling to light, and cuts its larders outside the wall - that
     * part is {@link ChamberFurnisher#REACH}'s business and is already in the box.
     * Two blocks of slack covers the probe and leaves room for the pass to grow
     * one, on the same argument the corridors make: a generous box costs a walk,
     * a tight one leaves earth nobody comes back for.</p>
     */
    private static final int FURNISHING_MARGIN = 2;

    public ChamberFeature {
        mouthLayers = List.copyOf(mouthLayers);
    }

    /** The chamber this mound opens into, given the runs that end at it. */
    public static ChamberFeature of(BlockPos mound, List<BurrowLink> runs) {
        int floor = floorAt(mound, runs);
        List<Integer> layers = Arrays.stream(mouthLayers(mound, runs, floor)).boxed().toList();
        return new ChamberFeature(mound, BurrowGeometry.toBurrow(mound).atY(floor), layers);
    }

    /**
     * The runs that end at this mound.
     *
     * <p>{@code touches} decides and nothing else, so hand it whatever list is
     * cheap to get at - a colony's runs, or the whole store's. A run with no
     * waypoints is dropped: there is no height to read off it, so it can say
     * nothing about where the floor goes.</p>
     */
    public static List<BurrowLink> runsAt(List<BurrowLink> links, BlockPos mound) {
        return links.stream()
                .filter(run -> run.touches(mound) && run.pointCount() > 0)
                .toList();
    }

    /** Where a player arriving at this mound lands, in burrow space. */
    public static BlockPos centreOf(BlockPos mound, List<BurrowLink> runs) {
        return BurrowGeometry.toBurrow(mound).atY(floorAt(mound, runs));
    }

    /**
     * The walking surface of the chamber this mound opens into, in burrow space.
     *
     * <p>Not the mapped mound position, which is the obvious answer and the wrong
     * one. A mound sits on the surface; the runs leaving it were dug two to six
     * blocks under it, and {@link BurrowGeometry#VERTICAL_SCALE} doubles that gap.
     * A chamber carved at the mound's own height would hang four to twelve blocks
     * above every corridor that is supposed to meet in it - a sealed room, and one
     * that would look correct in every screenshot of it.</p>
     *
     * <p>So the chamber goes at the <em>deepest</em> run that ends here. The three
     * run levels lie two blocks apart, which is eight down below, and a chamber is
     * {@link BurrowGeometry#CHAMBER_HEIGHT} nine high - so a floor at the deepest
     * run reaches the mouth of the shallowest one and every corridor between. The
     * shallowest as the reference would leave the deep ones under the floor.</p>
     *
     * <p>A mound whose colony has dug nothing yet has no run to measure, and the
     * feeding level is the fallback because it is the level a first trip uses. If
     * a deeper run appears later the chamber's floor drops, which changes this
     * feature's fingerprint and has it carved again - the old floor stays up in
     * the ceiling, harmless, and still maps back to this same mound.</p>
     *
     * <p>The minimum is taken after mapping, not before. {@code burrowY} clamps to
     * the dimension's own range, and while the clamp is monotonic - so it cannot
     * reorder two runs - doing the arithmetic in the space the answer is used in
     * means {@link #mouthLayers} can never come out negative, whatever the clamp
     * does to a colony on a mountain or in a superflat world.</p>
     */
    public static int floorAt(BlockPos mound, List<BurrowLink> runs) {
        int floor = BurrowGeometry.burrowY(mound.getY() - BurrowConstants.DEPTH_FEEDING);
        for (BurrowLink run : runs) {
            floor = Math.min(floor, BurrowGeometry.burrowY(runEndY(run, mound)));
        }
        return floor;
    }

    /**
     * How far above the chamber floor each run leaves, which is what the carver
     * puts a gallery at.
     *
     * <p>A mouth has no floor under it - the room cleared that - so it can only be
     * entered from the wall, at its own height, on its own bearing. Without these
     * the chamber is carved bare and a player standing on the floor can see the
     * shallower corridors and not reach them.</p>
     *
     * <p>Sorted and deduplicated, which the carver does again for itself and does
     * not need. It matters here for a different reason: these numbers go into the
     * fingerprint, and a fingerprint that changed because two runs were stored in
     * a different order would have every chamber in the world carved again for
     * nothing.</p>
     *
     * <p>A layer past the top of the chamber is dropped by the carver, and it is
     * right to drop it - a gallery clamped down to the ceiling would only put the
     * player four blocks closer to a mouth still out of reach. What the world ends
     * up with is a real tunnel above the chamber with no way up to it. It is kept
     * in this list rather than filtered out so that the fact survives to whoever
     * looks at the feature; it takes a changed surface to happen at all, because
     * the depth of a run is sampled when that run is recorded, so two runs at one
     * mound can be measured against two different ground heights if somebody
     * raised the ground between the two recordings. The place to notice it is
     * where the link is written, not here - this is asked once per chunk load and
     * anything it says would be said thousands of times.</p>
     */
    public static int[] mouthLayers(BlockPos mound, List<BurrowLink> runs, int floor) {
        return runs.stream()
                .mapToInt(run -> BurrowGeometry.burrowY(runEndY(run, mound)) - floor)
                .distinct()
                .sorted()
                .toArray();
    }

    @Override
    public String key() {
        return "chamber:" + BurrowFeature.tag(this.mound);
    }

    /**
     * The floor and the heights the runs leave at.
     *
     * <p>Which is the whole of what the carve reads. The mound is in it too, so
     * that a chamber can never inherit a ledger entry from a different one through
     * a key collision that is not supposed to be possible anyway.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.mound);
        hash = BurrowFeature.fold(hash, this.centre);
        for (int layer : this.mouthLayers) {
            hash = BurrowFeature.fold(hash, layer);
        }
        return hash;
    }

    /**
     * The room, its galleries, and the larders cut outside its wall.
     *
     * <p>The height is the larger of the chamber's own and whatever the topmost
     * gallery needs: a mouth near the ceiling has the carve cut the last layer or
     * two into the roof, out at the ledge, and that has to be inside the box or
     * the top gallery loses whichever quarter fell in another chunk.</p>
     */
    @Override
    public BoundingBox bounds() {
        int reach = ChamberFurnisher.REACH;
        int height = Math.max(BurrowGeometry.CHAMBER_HEIGHT, highestGallery() + GALLERY_HEADROOM);

        return new BoundingBox(
                this.centre.getX() - reach, this.centre.getY() - 1, this.centre.getZ() - reach,
                this.centre.getX() + reach, this.centre.getY() + height, this.centre.getZ() + reach)
                .inflatedBy(FURNISHING_MARGIN);
    }

    /**
     * Cuts the room, and nothing else.
     *
     * <p>Always true: a chamber is cut out of whatever earth is in front of it and
     * has no precondition that a later visit could satisfy.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        CorridorCarver.carveChamber(burrow, this.centre, layers(), chunkClamp);
        return true;
    }

    /**
     * Furnishes the room, once there is a room to measure.
     *
     * <p>This is the half that has to wait, and the larders are why. An alcove is
     * cut into a wall, the furnisher probes to find out which parts of the room's
     * edge <em>are</em> wall, and the answer depends on where the corridors come
     * in. Run while a neighbouring chunk is still solid, the probe reads that
     * chunk border as wall and cuts a larder into the mouth of a corridor that had
     * not arrived yet - and a larder is not deep earth, so nothing afterwards
     * takes it away again.</p>
     *
     * <p>It furnishes whenever the clamp overlaps the room at all, not only from
     * the chunk that holds the middle. Every decision comes from the block
     * position about to be written to, so a room dressed in quarters by four
     * chunks in any order comes out the same room - which is exactly what
     * {@code ChamberFurnisher} says it was built for. The random source is handed
     * over because the signature wants one and is deliberately unused; it is
     * seeded from the mound so that the day something does draw from it, it draws
     * the same numbers on every visit.</p>
     */
    @Override
    public void decorateWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        ChamberFurnisher.furnish(burrow, this.centre, RandomSource.create(this.mound.asLong()), chunkClamp);
    }

    /** The topmost mouth, or zero for a chamber whose runs all leave at the floor. */
    private int highestGallery() {
        int highest = 0;
        for (int layer : this.mouthLayers) {
            highest = Math.max(highest, layer);
        }
        return highest;
    }

    private int[] layers() {
        int[] layers = new int[this.mouthLayers.size()];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = this.mouthLayers.get(i);
        }
        return layers;
    }

    /**
     * The height of a run where it meets this mound.
     *
     * <p>Which end of the link that is has to be asked: ends are stored in the
     * order they were dug, so the mound may be either of them.</p>
     */
    private static int runEndY(BurrowLink run, BlockPos mound) {
        int index = run.a().equals(mound) ? 0 : run.pointCount() - 1;
        return Mth.floor(run.pointAt(index).y);
    }
}

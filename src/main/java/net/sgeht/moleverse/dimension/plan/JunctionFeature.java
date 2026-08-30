package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.Junctions;

/**
 * The widening where two runs of one level cross.
 *
 * <p>A wrapper and nothing more: {@link Junctions} already knows where a junction
 * belongs and how to cut one, and this only puts the questions a chunk asks in
 * front of it. Which pair of runs crosses here is what names it - two straight
 * lines meet once or not at all, so a pair is a name and needs no number after
 * it.</p>
 *
 * <p>Nothing is left for the decoration pass. The crown of threads over the
 * crossing goes in with the widening, because it is the point of the widening
 * rather than dressing on it, and it is grown into a layer this class has just
 * cleared rather than found by probing.</p>
 */
public record JunctionFeature(Junctions.Crossing crossing) implements BurrowFeature {

    @Override
    public String key() {
        return "junction:" + BurrowFeature.tag(this.crossing.one(), this.crossing.other());
    }

    /**
     * Where the widening goes and how large it comes out.
     *
     * <p>The two runs are not folded in - the key already names them, and the
     * geometry is what the cut reads. The radius and the height are in it as well
     * as the position, because both follow from the runs' level and their floor
     * step: a crossing that stayed put but was re-dug as a main run is a wider
     * junction and wants cutting again.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.crossing.x());
        hash = BurrowFeature.fold(hash, this.crossing.z());
        hash = BurrowFeature.fold(hash, this.crossing.walkY());
        hash = BurrowFeature.fold(hash, this.crossing.step());
        hash = BurrowFeature.fold(hash, this.crossing.radius());
        hash = BurrowFeature.fold(hash, this.crossing.height());
        return hash;
    }

    @Override
    public BoundingBox bounds() {
        return this.crossing.bounds();
    }

    /**
     * False where the two corridors are not open here yet.
     *
     * <p>A junction is a widening of something. Cut into ground the carver has not
     * reached, it would be a sealed room beside a corridor that has not arrived -
     * so it waits, and the wait is temporary by nature: the corridors come with
     * their own chunks. A reconciler that settled the ledger on the refusal would
     * leave the crossing a plain overlap for good.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        return Junctions.cut(burrow, this.crossing, chunkClamp);
    }
}

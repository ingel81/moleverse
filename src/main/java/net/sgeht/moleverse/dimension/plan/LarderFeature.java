package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.AlcoveCarver;

/**
 * A store room budding off one of a colony's deep runs.
 *
 * <p>A wrapper and nothing more: {@link AlcoveCarver} already knows where a larder
 * belongs, how to cut the alcove and how to stock it, and this only puts the
 * questions a chunk asks in front of it.</p>
 *
 * <p>The run and an index along it are what name it. The index rather than the
 * position, and the difference matters: a run's length is fixed by the two mounds it
 * joins, so the number of larders on it and the order they come in cannot change,
 * while their depth follows whatever ground the run was last measured against. A key
 * built from the position would rename every larder on a re-dug run and have them
 * cut a second time beside themselves; a key built from the index names the same
 * larder before and after, and the fingerprint is what notices it has moved.</p>
 */
public record LarderFeature(AlcoveCarver.Larder larder) implements BurrowFeature {

    @Override
    public String key() {
        return "larder:" + this.larder.run().colony() + ":"
                + BurrowFeature.tag(this.larder.run()) + ":" + this.larder.index();
    }

    /**
     * Where the alcove goes.
     *
     * <p>Which is the whole of what the cut reads - the room's size is a constant and
     * everything else about it follows from this one position. The run is not folded
     * in beyond its colony: the key already names it, and a run that was merely
     * travelled again leaves its larders exactly where they were.</p>
     *
     * <p>The level is in it because it decides whether this larder exists at all. A
     * run cannot change level today - {@code BurrowLink.reshaped} keeps it on purpose
     * - and folding it in is what makes that a fact the ledger would notice rather
     * than one it would take on trust.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.larder.run().colony());
        hash = BurrowFeature.fold(hash, this.larder.run().level().ordinal());
        hash = BurrowFeature.fold(hash, this.larder.index());
        hash = BurrowFeature.fold(hash, this.larder.x());
        hash = BurrowFeature.fold(hash, this.larder.walkY());
        hash = BurrowFeature.fold(hash, this.larder.z());
        return hash;
    }

    @Override
    public BoundingBox bounds() {
        return this.larder.bounds();
    }

    /**
     * Cuts the alcove.
     *
     * <p>Always true, and unlike a shaft or a junction it needs no precondition: the
     * alcove overlaps the corridor's own bore by a block, so one cut before its run
     * arrives is a pod the run opens straight into rather than a sealed room beside
     * it. See {@code AlcoveCarver} for why that overlap is the whole doorway.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        AlcoveCarver.cut(burrow, this.larder, chunkClamp);
        return true;
    }

    /**
     * Packs the walls with worms and muds the floor.
     *
     * <p>A second pass rather than the end of the first, and here that is the
     * reconciler's discipline rather than the probes': nothing in the stocking pass
     * measures anything. What the split buys is that a re-cut alcove is re-stocked -
     * the reconciler drops a feature's decoration entry whenever it carves it again,
     * so a larder whose run was re-dug through changed ground gets its worms back in
     * the new shape instead of keeping the old one's.</p>
     */
    @Override
    public void decorateWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        AlcoveCarver.stock(burrow, this.larder, chunkClamp);
    }
}

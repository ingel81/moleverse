package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.LevelShafts;

/**
 * The way up where a feeding run passes under a main run.
 *
 * <p>A wrapper and nothing more: {@link LevelShafts} already knows where a shaft
 * belongs and how to build one, and this only puts the questions a chunk asks in
 * front of it. Which pair of runs crosses here is what names it, because that is
 * the one thing about a shaft that does not change when the ground above
 * does.</p>
 *
 * <p>Nothing is left for the decoration pass. A shaft arrives with its own
 * furniture - the helix of root and the deck - because those are what it is, not
 * dressing on it, and none of it is placed by probing the room around it.</p>
 */
public record ShaftFeature(LevelShafts.Crossing crossing) implements BurrowFeature {

    @Override
    public String key() {
        return "shaft:" + BurrowFeature.tag(this.crossing.one(), this.crossing.other());
    }

    /**
     * Where the well goes and how far it climbs.
     *
     * <p>The two runs are not folded in - the key already names them, and the
     * geometry is what the build reads. A run that was re-dug somewhere else along
     * its length moves this crossing or removes it; a run re-dug to the same shape
     * leaves it alone, and there is nothing to build again.</p>
     *
     * <p>The bearing of the upper run is in it because the deck follows it: a
     * crossing whose upper run swung round lays its deck across different ground,
     * even where the well has not moved a block.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.crossing.x());
        hash = BurrowFeature.fold(hash, this.crossing.z());
        hash = BurrowFeature.fold(hash, this.crossing.low());
        hash = BurrowFeature.fold(hash, this.crossing.rise());
        hash = BurrowFeature.fold(hash, Double.hashCode(this.crossing.alongX()));
        hash = BurrowFeature.fold(hash, Double.hashCode(this.crossing.alongZ()));
        return hash;
    }

    @Override
    public BoundingBox bounds() {
        return this.crossing.bounds();
    }

    /**
     * False where the two corridors are not open here yet.
     *
     * <p>This is the feature the answer was added for. A shaft joins two things,
     * and until both of them exist it refuses - so a reconciler that took silence
     * for success would settle the ledger on a shaft that was never built, and
     * nothing would ever ask again. The refusal is temporary by nature: the
     * corridors arrive with their own chunks.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        return LevelShafts.connect(burrow, this.crossing, chunkClamp);
    }
}

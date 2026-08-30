package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.BoltHoles;

/**
 * The escape shaft climbing off one run, where a run has one.
 *
 * <p>A wrapper and nothing more: {@link BoltHoles} already knows which runs get one,
 * where it leaves from and how to cut it, and this only puts the questions a chunk
 * asks in front of it. The run is what names it - a run has at most one bolt-hole,
 * so there is nothing to number.</p>
 *
 * <p><strong>It does not reach the overworld, and the plan that asked for it was
 * wrong about that.</strong> {@code docs/BURROW_LIFE.md} describes a bolt-hole as
 * ending a few blocks under the overworld surface so that a player could dig the
 * last metre and get out. There is no such surface: the burrow is its own closed 256
 * block box and the overworld is a different level, reachable only through a
 * chamber's way home. What this actually cuts is a stub that climbs well above the
 * corridor and ends in a plug of loose soil with a patch of light grown into it -
 * worth having on its own terms, and not an exit. {@link BoltHoles} carries the full
 * argument and the cap that keeps the climb inside
 * {@code BurrowGeometry.MAX_BURROW_Y}.</p>
 *
 * <p>Nothing is left for the decoration pass, and that is deliberate rather than
 * unfinished: this is a hole something dug in a hurry, and the one thing that would
 * make it read otherwise is furniture. The light in the plug goes in with the cut,
 * because it is what the shaft is for rather than dressing on it, and it is grown
 * into a layer the cut has just lined rather than found by probing.</p>
 */
public record BoltHoleFeature(BoltHoles.Stub stub) implements BurrowFeature {

    @Override
    public String key() {
        return "bolthole:" + this.stub.run().colony() + ":" + BurrowFeature.tag(this.stub.run());
    }

    /**
     * Where the shaft leaves from, which way it goes and how far it climbs.
     *
     * <p>Which is the whole of what the cut reads. The run is not folded in beyond
     * its colony - the key already names it, and a run that was merely travelled
     * again leaves its bolt-hole exactly where it was.</p>
     *
     * <p>The rise is in it as well as the position, and it is the term that earns its
     * place: the climb is capped against the top of the dimension, so a run that was
     * re-dug nearer the surface has a shorter shaft even where the shaft has not
     * moved a block.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.stub.run().colony());
        hash = BurrowFeature.fold(hash, this.stub.baseX());
        hash = BurrowFeature.fold(hash, this.stub.baseZ());
        hash = BurrowFeature.fold(hash, this.stub.walkY());
        hash = BurrowFeature.fold(hash, this.stub.rise());
        hash = BurrowFeature.fold(hash, this.stub.stepX());
        hash = BurrowFeature.fold(hash, this.stub.stepZ());
        return hash;
    }

    @Override
    public BoundingBox bounds() {
        return this.stub.bounds();
    }

    /**
     * Cuts the shaft and lights its plug.
     *
     * <p>Always true. The first slice stands on the corridor's own centre line and
     * clears whatever is there, so a stub cut before its run arrives is a shaft the
     * run opens into rather than a hole beside it - no precondition, and nothing a
     * later visit could satisfy that this one cannot.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        BoltHoles.dig(burrow, this.stub, chunkClamp);
        return true;
    }
}

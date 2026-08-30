package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.CorridorCarver;
import net.sgeht.moleverse.dimension.CorridorProfile;
import net.sgeht.moleverse.entity.burrow.BurrowLink;

/**
 * The corridor one run leaves in the burrow.
 *
 * <p>One per {@link BurrowLink}, which is the unit for the reason that record
 * gives: a link is an edge that gets walked again and again, and it is edges the
 * burrow mirrors. Everything about the shape is already in the link, so this adds
 * nothing but the questions a chunk asks of it.</p>
 */
public record CorridorFeature(BurrowLink link) implements BurrowFeature {

    /**
     * How far past the carved tube the bounds reach.
     *
     * <p>The carve is not the last thing that touches a corridor.
     * {@code TunnelDecorator} lays a floor under the walking surface, grows
     * threads into the ceiling above the section, speckles the wall a block
     * outside it, and sweeps several blocks along the run either side of every
     * segment centre it is handed. None of that is worth tracking exactly: the
     * bounds only decide which chunks are asked about this corridor and how far
     * their clamps let it write, so being generous costs a chunk a walk over a
     * feature it barely touches, while being tight leaves a stripe of undecorated
     * earth at a chunk border that nothing ever comes back for.</p>
     *
     * <p>Eight blocks, which is comfortably past every reach the dressing pass has
     * today and leaves room for it to grow one.</p>
     */
    private static final int DRESSING_MARGIN = 8;

    @Override
    public String key() {
        return "corridor:" + this.link.colony() + ":" + BurrowFeature.tag(this.link);
    }

    /**
     * The depth profile, the level and the two ends.
     *
     * <p>Which is to say: everything a carve reads, and nothing else. A run that
     * was merely travelled again keeps its hash, so counting a use does not
     * re-carve a colony. A run a mole re-dug through changed ground has a new
     * profile and a new hash, and is carved again where the chunks are loaded to
     * take it.</p>
     *
     * <p>The section is folded in as well as the level it comes from - both the
     * plain one and the envelope the wander and the swell reach to. The two say
     * the same thing about a given run today, and the day somebody retunes
     * {@link CorridorProfile} is the day every corridor in every world ought to be
     * cut to the new size rather than staying the old one until a mole happens to
     * walk it.</p>
     *
     * <p>It reaches as far as numbers and no further: retuning the noise the swell
     * is drawn from changes the shape without changing anything folded in here,
     * and there is no honest way for a fingerprint to see that. Re-carving a world
     * after that kind of change is a job for somebody with a command, not for this
     * method to guess at.</p>
     */
    @Override
    public int contentHash() {
        CorridorProfile profile = CorridorProfile.of(this.link.level());
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.link.colony());
        hash = BurrowFeature.fold(hash, this.link.level().ordinal());
        hash = BurrowFeature.fold(hash, profile.width());
        hash = BurrowFeature.fold(hash, profile.height());
        hash = BurrowFeature.fold(hash, profile.outerRadius());
        hash = BurrowFeature.fold(hash, profile.outerHeight());
        hash = BurrowFeature.fold(hash, this.link.a());
        hash = BurrowFeature.fold(hash, this.link.b());
        for (int depth : this.link.depths()) {
            hash = BurrowFeature.fold(hash, depth);
        }
        return hash;
    }

    /**
     * The box around the whole polyline.
     *
     * <p>The waypoints are enough to bound it: the carve steps between two of them
     * in single blocks along the straight line joining them, so no segment ever
     * leaves the box its own two ends make. What has to be added is the section -
     * and the section that matters here is
     * {@link CorridorProfile#outerRadius}, not {@link CorridorProfile#radius}. A
     * run wanders off the straight line and swells as it goes, so a box drawn to
     * the average width would have the carve writing outside it, which under a
     * clamp is silent. Below the walking surface one block is enough: the carve
     * leaves the floor standing and only the dressing pass writes into it.</p>
     */
    @Override
    public BoundingBox bounds() {
        CorridorProfile profile = CorridorProfile.of(this.link.level());
        int reach = profile.outerRadius();
        BlockPos first = burrowPoint(this.link, 0);
        int minX = first.getX();
        int maxX = minX;
        int minY = first.getY();
        int maxY = minY;
        int minZ = first.getZ();
        int maxZ = minZ;

        for (int i = 1; i < this.link.pointCount(); i++) {
            BlockPos point = burrowPoint(this.link, i);
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }

        return new BoundingBox(
                minX - reach, minY - 1, minZ - reach,
                maxX + reach, maxY + profile.outerHeight() - 1, maxZ + reach)
                .inflatedBy(DRESSING_MARGIN);
    }

    /**
     * Always true: a corridor is cut out of whatever earth is in front of it and
     * has no precondition that a later visit could satisfy. A carve that cleared
     * nothing found the run already open, which is the work being done rather
     * than the work being refused.
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        CorridorCarver.carve(burrow, this.link, chunkClamp);
        return true;
    }

    @Override
    public void decorateWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        CorridorCarver.decorateRun(burrow, this.link, chunkClamp);
    }

    /**
     * Waypoint {@code index} of the run, in burrow space.
     *
     * <p>The same mapping {@code CorridorCarver.burrowPoint} carves with, written
     * out again because that one is package visible and this package is a
     * different one. It is two calls and the two must not drift: a bounding box
     * measured with a different mapping than the carve uses is a box the carve
     * writes outside of, and under a clamp that is silent.</p>
     */
    private static BlockPos burrowPoint(BurrowLink link, int index) {
        return BurrowGeometry.toBurrow(BlockPos.containing(link.pointAt(index)));
    }
}

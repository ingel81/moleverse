package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.entity.burrow.BurrowLink;

/**
 * One thing the burrow has because the colony above it dug something.
 *
 * <p>A corridor, a chamber, a shaft between two levels, a junction where two runs
 * of one level cross. Between them they are everything the burrow contains, and
 * the point of naming them is that a chunk can then ask which of them pass
 * through it and carve exactly those - which is the contract vanilla generation
 * has and the one the burrow was missing. See {@code docs/BURROW_WORLDGEN.md} for
 * the argument.</p>
 *
 * <p>Deriving a feature touches no level. That is the whole discipline of this
 * package: {@link BurrowPlan#featuresOf} is arithmetic on the colony's links, so
 * the answer exists before the ground does, is the same on every thread and in
 * every order, and can be tested without a world. Only {@link #carveWithin} is
 * allowed to know a {@code ServerLevel} exists.</p>
 *
 * <h2>What the three questions are for</h2>
 *
 * <p>{@link #key} identifies the feature across a save. It is what a chunk's
 * ledger writes down, so it has to name the same corridor after a reload, after
 * the world was carried to another machine, and after the store was written in a
 * different order. It is built from positions and never from an object.</p>
 *
 * <p>{@link #contentHash} is the fingerprint of everything that, if it changed,
 * means "carve me again". A run that a mole re-dug at a new depth has the same
 * key and a different hash; a run that was merely walked once more has the same
 * both, and is therefore not re-carved. Same rule as the key: from the data, never
 * from identity, and stable across a save.</p>
 *
 * <p>{@link #bounds} is what decides which chunks are asked to carve this at all,
 * and it must cover every block the carve can touch - the carved envelope plus
 * whatever the decoration pass reaches past it. Too large costs a chunk a walk
 * over a feature it has no part of; too small leaves a stripe of earth nobody
 * ever comes back for. So err large.</p>
 */
public interface BurrowFeature {

    /**
     * Where a fingerprint starts. Any odd number would do; the base spells the
     * mod, which makes a stray hash recognisable in a log.
     *
     * <p>The added generation counter is bumped whenever the <em>carve
     * output</em> changes shape without any feature's own data changing - the
     * soil lining and the rounder sections were the first such change. Every
     * hash in every chunk ledger goes stale at once, and every loaded chunk
     * re-carves its features on the next reconcile, which is exactly the
     * migration an existing world needs. Bump it for shape changes, never for
     * refactors.</p>
     *
     * <p>At two since the anatomy wave: rooms are now cut into corridors that
     * already exist - a larder alcove buds off the side of a deep run and a
     * bolt-hole climbs out of one - and the lining a run is cut with gained
     * pockets of its own. Neither changes any feature's own data, which is
     * exactly the case this counter is for.</p>
     */
    int HASH_SEED = 0x4D4F4C45 + 2;

    /** Stable identity, unique among all features of every colony. */
    String key();

    /** Fingerprint of everything that, if changed, means this must be carved again. */
    int contentHash();

    /** Everything the carve can touch, in burrow space, carve radius included. */
    BoundingBox bounds();

    /**
     * Carves this feature, writing only inside {@code chunkClamp}.
     *
     * <p>Idempotent, and it has to be: a feature that crosses four chunks is
     * carved four times, one clamped quarter per chunk, and a player who breaks
     * ground in it has it carved again on the next reconcile. Every write in the
     * burrow either clears deep earth or fills air, so a second pass finds its
     * own work done.</p>
     *
     * <p>Null is the unbounded case and carves the whole feature. It is what a
     * debug command wants; the chunk path always has a box.</p>
     *
     * <p><strong>The answer is what settles a ledger entry.</strong> True means
     * this feature has had its say for this clamp - it wrote, or it found nothing
     * left to write because the ground was already carved. False means it was
     * turned away by something that can be different later, and a shaft is the
     * case that matters: it refuses to build over corridors that have not been
     * cut yet. A reconciler that recorded the hash on a false would leave that
     * shaft unbuilt for the life of the world, because nothing would ever ask
     * again.</p>
     *
     * @return whether this feature applied itself, as opposed to being skipped
     *         for a reason that may not hold next time
     */
    boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp);

    /**
     * Dresses what {@link #carveWithin} cut, writing only inside
     * {@code chunkClamp}.
     *
     * <p>A second pass rather than the end of the first, and the reason is that
     * decoration <em>measures</em> where carving only writes. The tunnel dressing
     * probes for the floor, the ceiling and both walls before it puts anything
     * down, and a chamber's larders probe for a wall that a corridor may still be
     * about to open. Run against a chunk whose neighbours are earth, those probes
     * read the chunk border as the end of the corridor and dress it as one - a
     * seam of wall speckle and roots across open air, which no later pass removes
     * because it is not deep earth any more.</p>
     *
     * <p>So the reconciler holds this back until the ground the probes will walk
     * over exists: carve a chunk and its neighbours, then decorate. Nothing here
     * may assume it runs in the same tick as the carve, or at all.</p>
     *
     * <p>Empty by default. A shaft and a junction bring their own furniture with
     * them - a helix of root, a crown of threads - and have nothing to add once
     * the earth is out.</p>
     */
    default void decorateWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
    }

    /**
     * Folds one number into a fingerprint.
     *
     * <p>Written out rather than left to {@code Objects.hash} and
     * {@code BlockPos.hashCode}, because a fingerprint that survives a save is
     * also a fingerprint that has to survive a Minecraft update: the day vanilla
     * changes how a {@code Vec3i} hashes, every ledger in every world would read
     * as stale and the whole burrow would be carved a second time. Harmless, and
     * still not something to leave to somebody else's arithmetic.</p>
     */
    static int fold(int hash, int value) {
        return hash * 31 + value;
    }

    /** The same, for a position. */
    static int fold(int hash, BlockPos pos) {
        return fold(fold(fold(hash, pos.getX()), pos.getY()), pos.getZ());
    }

    /** A position as it appears in a key. Terse, and it survives a round trip through a log. */
    static String tag(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * A run as it appears in a key.
     *
     * <p>The two ends are sorted rather than written in the order they were dug.
     * A link has no direction of its own - {@code BurrowLink.joins} makes the same
     * point, and {@code CorridorCarver}'s seed is built the same way - so a key
     * that flipped with the storage order would name the same corridor two
     * different things and the ledger would carve it twice.</p>
     */
    static String tag(BurrowLink link) {
        BlockPos a = link.a();
        BlockPos b = link.b();
        boolean inOrder = a.asLong() <= b.asLong();
        return tag(inOrder ? a : b) + ">" + tag(inOrder ? b : a);
    }

    /**
     * The pair of runs that cross somewhere, as it appears in a key.
     *
     * <p>Sorted, for {@link #tag(BurrowLink)}'s reason one level up. A crossing is
     * found by a pair of nested loops over the colony's links, so which of the two
     * runs is called the first follows from the order the store was last written
     * in - and a shaft that changed its name when the store was rewritten would be
     * built a second time beside itself.</p>
     */
    static String tag(BurrowLink one, BurrowLink other) {
        String first = tag(one);
        String second = tag(other);
        return first.compareTo(second) <= 0 ? first + "x" + second : second + "x" + first;
    }
}

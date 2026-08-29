package net.sgeht.moleverse.entity.burrow;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A run that was actually travelled, between two mounds of one colony.
 *
 * <p>The unit here is the link, not the trip. A trip is an event; a link is an
 * edge that gets walked again and again, and it is edges that the burrow below
 * mirrors. What comes out of a colony is therefore a <em>historical</em> graph -
 * the runs moles have dug - rather than the proximity graph {@link MoundNetwork}
 * derives from whichever mounds happen to lie near one another.</p>
 *
 * <p>Only the depths are stored. {@link BurrowRoute#between} interpolates x and z
 * linearly between the two mounds and chooses freely only in the vertical, so
 * the two ends plus one depth per waypoint reconstruct the whole run - see
 * {@link #pointAt}. Biomes are not stored either: they are a lookup from x and z
 * at the moment somebody asks, and a stored copy would be stale as soon as
 * anything changed them.</p>
 */
public record BurrowLink(int colony, BlockPos a, BlockPos b, RunLevel level, List<Integer> depths,
        int uses, long lastUsed) {

    /**
     * Heights as an int array rather than a list of numbers.
     *
     * <p>{@code Codec.INT_STREAM} writes a TAG_Int_Array through {@code NbtOps},
     * which is one tag for the whole profile instead of one per waypoint. A
     * colony carries dozens of runs of sixteen points each, so the difference is
     * the shape of the file rather than a rounding error.</p>
     */
    private static final Codec<List<Integer>> DEPTHS_CODEC = Codec.INT_STREAM.xmap(
            stream -> stream.boxed().toList(),
            depths -> depths.stream().mapToInt(Integer::intValue));

    public static final Codec<BurrowLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("colony").forGetter(BurrowLink::colony),
            BlockPos.CODEC.fieldOf("a").forGetter(BurrowLink::a),
            BlockPos.CODEC.fieldOf("b").forGetter(BurrowLink::b),
            RunLevel.CODEC.fieldOf("level").forGetter(BurrowLink::level),
            DEPTHS_CODEC.fieldOf("depths").forGetter(BurrowLink::depths),
            Codec.INT.optionalFieldOf("uses", 1).forGetter(BurrowLink::uses),
            Codec.LONG.optionalFieldOf("last_used", 0L).forGetter(BurrowLink::lastUsed))
            .apply(instance, BurrowLink::new));

    /**
     * Whether this link joins these two mounds, in either direction.
     *
     * <p>Ends are stored in the order they were dug rather than sorted, because
     * which end a mole started from is worth keeping. Matching therefore has to
     * try both ways round, which is cheaper than it looks and honest about the
     * fact that a run has no direction of its own.</p>
     */
    public boolean joins(BlockPos one, BlockPos other) {
        return (this.a.equals(one) && this.b.equals(other))
                || (this.a.equals(other) && this.b.equals(one));
    }

    public boolean touches(BlockPos mound) {
        return this.a.equals(mound) || this.b.equals(mound);
    }

    /**
     * Waypoint {@code index} of the run.
     *
     * <p>x and z come from the straight line between the two ends, exactly as
     * {@code BurrowRoute} laid them out; only the height was stored. The half
     * block offsets put the point in the middle of its block, which is where the
     * mole is.</p>
     */
    public Vec3 pointAt(int index) {
        return pointAt(this.a, this.b, this.depths, index);
    }

    /**
     * The same reconstruction without a link to hold it, so the client can draw
     * a run it was sent rather than one it stores.
     */
    public static Vec3 pointAt(BlockPos a, BlockPos b, List<Integer> depths, int index) {
        int last = depths.size() - 1;
        double t = last <= 0 ? 0.0 : (double) index / last;
        Vec3 from = a.getCenter();
        Vec3 to = b.getCenter();
        return new Vec3(
                from.x + (to.x - from.x) * t,
                depths.get(index) + 0.5,
                from.z + (to.z - from.z) * t);
    }

    public int pointCount() {
        return this.depths.size();
    }

    /** The same link, walked once more. */
    public BurrowLink travelledAgain(long gameTime) {
        return new BurrowLink(this.colony, this.a, this.b, this.level, this.depths,
                this.uses + 1, gameTime);
    }

    /**
     * The same link with a freshly measured profile.
     *
     * <p>The ground above a run can change - a player digs a pond, a tree grows -
     * and the next mole to travel it takes the new shape. The level is kept: a
     * run that was dug as a main run stays one, or the burrow below would end up
     * with two corridors where the colony has a single run.</p>
     */
    public BurrowLink reshaped(List<Integer> depths, long gameTime) {
        return new BurrowLink(this.colony, this.a, this.b, this.level, List.copyOf(depths),
                this.uses + 1, gameTime);
    }
}

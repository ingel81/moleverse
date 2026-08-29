package net.sgeht.moleverse.entity.burrow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * One colony: a fixed centre and the ground that belongs to it.
 *
 * <p>Before this existed a network was whatever mounds happened to chain
 * together near the mole asking, which is not a place. Every new mound moved the
 * frontier outward by up to {@link BurrowConstants#NEW_TRAVEL_MAX} blocks and
 * nothing at the back ever dropped off, so a colony crept across the world for
 * as long as the world ran. An hour of play was already enough to see it: two
 * dozen mounds, and one lone link trailing off to a mound far outside the rest.</p>
 *
 * <p>The core is the mound a colony started from and is never recomputed. A
 * centre that moves with its members is exactly the drift this is here to stop:
 * every step outwards would carry the middle along with it.</p>
 *
 * <p>Membership is not stored. A mound belongs to the colony whose box contains
 * it, which needs no bookkeeping when a player knocks one away and cannot go
 * stale. {@link BurrowConstants#COLONY_MIN_SEPARATION} keeps the boxes apart, so
 * at most one colony ever contains a given position.</p>
 */
public record Colony(int id, BlockPos core, long founded) {

    public static final Codec<Colony> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(Colony::id),
            BlockPos.CODEC.fieldOf("core").forGetter(Colony::core),
            Codec.LONG.fieldOf("founded").forGetter(Colony::founded))
            .apply(instance, Colony::new));

    /**
     * Whether this ground belongs to the colony.
     *
     * <p>A square rather than a circle, and height is ignored on purpose. What
     * this bounds is territory on the surface: mounds sit on the ground, and a
     * colony on a hillside is one colony, not two.</p>
     */
    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - this.core.getX()) <= BurrowConstants.COLONY_EXTENT
                && Math.abs(pos.getZ() - this.core.getZ()) <= BurrowConstants.COLONY_EXTENT;
    }

    public int minX() {
        return this.core.getX() - BurrowConstants.COLONY_EXTENT;
    }

    public int maxX() {
        return this.core.getX() + BurrowConstants.COLONY_EXTENT;
    }

    public int minZ() {
        return this.core.getZ() - BurrowConstants.COLONY_EXTENT;
    }

    public int maxZ() {
        return this.core.getZ() + BurrowConstants.COLONY_EXTENT;
    }

    /**
     * Chebyshev distance between two centres - the measure that matches square
     * boxes. Two colonies whose cores are this far apart have boxes that do not
     * touch when the distance is at least twice the extent.
     */
    public static int separation(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }
}

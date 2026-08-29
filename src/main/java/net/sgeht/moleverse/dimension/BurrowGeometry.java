package net.sgeht.moleverse.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * How the burrow below relates to the world above it.
 *
 * <p>One rule, in both directions: the burrow is the same network at the scale a
 * mole experiences it. A run a mole dug through one block of soil is a corridor
 * you can walk down there, because you are a quarter of your size - which is the
 * fiction, and {@link #SCALE} is what it costs in blocks.</p>
 *
 * <p>The vertical is stretched less than the horizontal on purpose. Runs follow
 * the ground and the ground has slopes; at the full scale a three block dip
 * becomes a twelve block drop and every hillside colony turns into a staircase.
 * Nobody in the world can measure the ratio between the two, so the two numbers
 * are free to differ.</p>
 *
 * <p>The datum is what keeps the burrow inside its own small height range: an
 * overworld position at sea level lands in the middle of it, and everything else
 * is measured from there.</p>
 */
public final class BurrowGeometry {

    /** Horizontal stretch. One overworld block becomes this many down below. */
    public static final int SCALE = 4;

    /** Vertical stretch. Deliberately smaller, so slopes stay walkable. */
    public static final int VERTICAL_SCALE = 2;

    /** Overworld height that maps onto {@link #BURROW_DATUM}. Sea level. */
    public static final int OVERWORLD_DATUM = 64;

    /** The burrow height sea level maps to. Middle of the dimension's range. */
    public static final int BURROW_DATUM = 128;

    /**
     * Width of a corridor, in burrow blocks. Odd, so a corridor has a centre
     * line to lay a floor and hang a lamp on.
     *
     * <p>Somewhere between four and eight is the range worth walking; this is a
     * starting point to be judged with the sample command, not a settled number.</p>
     */
    public static final int CORRIDOR_WIDTH = 5;

    /** Height of a corridor. A little more than the width reads as a burrow rather than a pipe. */
    public static final int CORRIDOR_HEIGHT = 6;

    /** A chamber at a mound: wider than a corridor, and where the way out is. */
    public static final int CHAMBER_RADIUS = 6;

    public static final int CHAMBER_HEIGHT = 9;

    private BurrowGeometry() {
    }

    /** Where an overworld position lands in the burrow. */
    public static BlockPos toBurrow(BlockPos overworld) {
        return new BlockPos(
                overworld.getX() * SCALE,
                burrowY(overworld.getY()),
                overworld.getZ() * SCALE);
    }

    public static Vec3 toBurrow(Vec3 overworld) {
        return new Vec3(
                overworld.x * SCALE,
                BURROW_DATUM + (overworld.y - OVERWORLD_DATUM) * VERTICAL_SCALE,
                overworld.z * SCALE);
    }

    /** And back. Used to ask whether the mound a chamber belongs to still stands. */
    public static BlockPos toOverworld(BlockPos burrow) {
        return new BlockPos(
                Math.floorDiv(burrow.getX(), SCALE),
                overworldY(burrow.getY()),
                Math.floorDiv(burrow.getZ(), SCALE));
    }

    public static int burrowY(int overworldY) {
        return BURROW_DATUM + (overworldY - OVERWORLD_DATUM) * VERTICAL_SCALE;
    }

    public static int overworldY(int burrowY) {
        return OVERWORLD_DATUM + Math.floorDiv(burrowY - BURROW_DATUM, VERTICAL_SCALE);
    }
}

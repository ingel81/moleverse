package net.sgeht.moleverse.entity.burrow;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * How deep a run lies under the ground above it.
 *
 * <p>Three levels, two blocks apart, all of them measured from the local surface
 * rather than from an absolute height - a colony on a hillside has runs that
 * follow the hillside. Real moles are built the same way: the whole spread stays
 * inside the topsoil and nothing digs towards bedrock.</p>
 *
 * <p>Two blocks of separation sounds like nothing. It is not, for two reasons.
 * Above ground it is what keeps two crossing runs from being the same hole. And
 * the burrow below multiplies it - at a scale of four, two levels lie eight
 * blocks apart, which is the difference between an overpass and a plaza.</p>
 */
public enum RunLevel implements StringRepresentable {

    /** The everyday run, just under the turf. What every trip used before there were levels. */
    FEEDING("feeding", BurrowConstants.DEPTH_FEEDING),

    /** The backbone a colony keeps and reuses. */
    MAIN("main", BurrowConstants.DEPTH_MAIN),

    /**
     * The main burrow and its chambers.
     *
     * <p>Nothing chooses this yet. It exists so that the level is a closed set
     * from the first stored link onwards, rather than a field that grows a case
     * later and invalidates everything written before it.</p>
     */
    CHAMBER("chamber", BurrowConstants.DEPTH_CHAMBER);

    public static final Codec<RunLevel> CODEC = StringRepresentable.fromEnum(RunLevel::values);

    private final String name;
    private final int depth;

    RunLevel(String name, int depth) {
        this.name = name;
        this.depth = depth;
    }

    private static final RunLevel[] BY_INDEX = values();

    /**
     * By position in the enum, for the wire. Out of range falls back to the
     * feeding level rather than throwing: a debug packet from a mismatched build
     * should draw something slightly wrong, not disconnect anybody.
     */
    public static RunLevel byIndex(int index) {
        return index >= 0 && index < BY_INDEX.length ? BY_INDEX[index] : FEEDING;
    }

    /** Blocks between the topmost solid block of a column and the run under it. */
    public int depth() {
        return this.depth;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

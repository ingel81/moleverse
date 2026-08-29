package net.sgeht.moleverse.dimension;

import net.sgeht.moleverse.entity.burrow.RunLevel;

/**
 * How large a corridor is cut, per {@link RunLevel}.
 *
 * <p>Every run used to come out the same tube, and a burrow of identical tubes is
 * a maze rather than a network. A colony's backbone is the run it keeps and
 * reuses; down here it should be the thing you follow when you are lost, and it
 * can only be that if you can tell it from a feeding run by standing in it. Not
 * by a sign, not by a different block - by the walls being further away and the
 * ceiling being higher, which is the one difference a player reads without being
 * told to look.</p>
 *
 * <p>{@link BurrowGeometry#CORRIDOR_WIDTH} and
 * {@link BurrowGeometry#CORRIDOR_HEIGHT} stay what they were and are the feeding
 * run's own section: the level every first trip digs at, and the size everything
 * else is judged against. Widening the common case would have changed the whole
 * burrow to make one run legible.</p>
 *
 * <h2>Why the backbone stops at seven</h2>
 *
 * <p><strong>A wide corridor stops being a corridor.</strong> Somewhere around
 * nine blocks you no longer see both walls at once, and a passage you cannot see
 * the sides of reads as a room you are crossing rather than as a way to
 * somewhere. Seven puts a wall three blocks either side of the centre line -
 * enough that a root beam can stand off centre and still be walked past, not so
 * much that the corridor loses its direction.</p>
 *
 * <p><strong>The chamber is the harder cap, and it bites first.</strong> A run
 * leaves its mound through the chamber's centre column, so its cross-section is
 * also the size of the hole it tears in the galleries on its way out.
 * {@code CorridorCarver.LEDGE_INNER_RADIUS} puts the innermost gallery lane one
 * block outside the widest run for exactly this reason. At a radius of three the
 * mouth takes about a quarter of the ring and you walk round the other way; at
 * four every block of that inner lane on the side the run leaves by is within
 * the corridor's own width, so the lane would simply be gone. Nine wide is
 * therefore not a matter of taste down here - it is a gallery the colony can no
 * longer walk.</p>
 *
 * <h2>Why the backbone is only one block taller</h2>
 *
 * <p>Two would be better and two is not available. {@code TunnelDecorator}
 * measures a corridor rather than being told its size, and its ceiling probe
 * searches exactly {@link BurrowGeometry#CORRIDOR_HEIGHT} + 1 blocks above the
 * walking surface. A corridor whose ceiling is out of that reach answers "no
 * ceiling" and loses its light, its roots and its wall speckle - and an unlit
 * corridor is the one failure this dimension cannot absorb, because it is the
 * one you cannot navigate out of. Seven puts the ceiling on the last block the
 * probe looks at, with nothing to spare. The moment that bound reads a profile
 * instead of a constant, the backbone should go to eight, which also restores
 * the proportion {@code BurrowGeometry} argues for - a little taller than wide,
 * so it reads as a burrow rather than as a pipe.</p>
 */
public record CorridorProfile(int width, int height) {

    /**
     * The everyday run: the geometry constants, unchanged.
     *
     * <p>Most of a colony is this, so this is the size that has to stay right.
     * Against a backbone two blocks wider it reads as a side passage without ever
     * being cramped, which is what a feeding run is.</p>
     */
    private static final CorridorProfile FEEDING_RUN =
            new CorridorProfile(BurrowGeometry.CORRIDOR_WIDTH, BurrowGeometry.CORRIDOR_HEIGHT);

    /**
     * The backbone: two blocks wider, one taller.
     *
     * <p>Shared by {@link RunLevel#MAIN} and {@link RunLevel#CHAMBER} rather than
     * given a third size of its own. Nothing digs at the chamber level yet, and a
     * distinction invented for a level that does not exist is a number nobody can
     * check against anything. When something does dig there it wants a section of
     * its own; until then the deepest tier is the backbone at a different
     * depth.</p>
     */
    private static final CorridorProfile BACKBONE = new CorridorProfile(7, 7);

    /**
     * Sideways reach of the widest run any level digs.
     *
     * <p>Derived rather than written down, because the chamber measures its
     * galleries against it: a gallery that started inside the widest mouth would
     * be earth left standing in a doorway. Adding a level with a wider section
     * moves the galleries out on its own instead of leaving a second number to
     * remember.</p>
     */
    public static final int WIDEST_RADIUS = widestRadius();

    /**
     * A corridor with an even width has no centre line, and the centre line is
     * what the floor, the light and the guarantee that a run is walkable all hang
     * off. Cheaper to fail at class load than to find out by walking.
     */
    public CorridorProfile {
        if (width % 2 == 0) {
            throw new IllegalArgumentException("corridor width must be odd, so a run has a centre line: " + width);
        }
    }

    /** The section a run at this level is cut to. */
    public static CorridorProfile of(RunLevel level) {
        return switch (level) {
            case FEEDING -> FEEDING_RUN;
            case MAIN, CHAMBER -> BACKBONE;
        };
    }

    /**
     * Sideways reach from the centre line. An odd width has a centre block, and
     * this is what is left either side of it.
     */
    public int radius() {
        return (this.width - 1) / 2;
    }

    private static int widestRadius() {
        int widest = 0;
        for (RunLevel level : RunLevel.values()) {
            widest = Math.max(widest, of(level).radius());
        }
        return widest;
    }
}

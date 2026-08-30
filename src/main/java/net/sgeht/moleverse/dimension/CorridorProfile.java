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
 * probe looks at, with nothing to spare; {@link #MAX_LIT_HEIGHT} is that bound
 * written down, and it is what the carver's own bulges are capped against. The
 * moment the decorator reads a profile instead of a constant, the backbone
 * should go to eight, which also restores the proportion
 * {@code BurrowGeometry} argues for - a little taller than wide, so it reads as
 * a burrow rather than as a pipe.</p>
 *
 * <h2>The section is a maximum, not a measurement</h2>
 *
 * <p>A run is not cut to one size along its whole length. {@code CorridorCarver}
 * lets the section breathe - a block wider here, a block lower there, the centre
 * line wandering a block off the straight - so that a corridor reads as dug
 * rather than drilled. The three swings below are that licence, expressed here
 * rather than in the carver because everything that has to keep out of a
 * corridor's way asks this record how large one can get, and a second copy of
 * the number is a second thing to keep in step.</p>
 *
 * <p>All three are one block. That is not a round number picked for looks: at
 * two the backbone's slice would be eleven across, past the widest span
 * {@code TunnelDecorator} still recognises as a corridor, and the run would go
 * dark for the same reason a ceiling out of probe reach does.</p>
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
     * How far the cut radius may swing either side of the section's own.
     *
     * <p>One block, and the cap is the decorator rather than taste: it stops
     * dressing a slice wider than {@code CORRIDOR_WIDTH + 4}, and the backbone at
     * seven is already only two off that. Two blocks of swing would put every
     * bulge in a backbone past the bound and leave those stretches unlit.</p>
     */
    public static final int RADIUS_SWING = 1;

    /**
     * How far the cut height may swing, subject to {@link #MAX_LIT_HEIGHT}.
     *
     * <p>The cap bites asymmetrically and that is intended: a backbone is already
     * as tall as the decorator's probe reaches, so its swing is a pinch only,
     * while a feeding run may bulge as well. A corridor that sometimes ducks is a
     * corridor that was dug; one that sometimes goes dark is a bug.</p>
     */
    public static final int HEIGHT_SWING = 1;

    /**
     * How far the cut centre line may wander off the straight, across the run.
     *
     * <p>One block, per axis. A run below this reads as laser-cut over any long
     * straight; a run above it stops being a line between two mounds and starts
     * being a search.</p>
     */
    public static final int WANDER = 1;

    /**
     * The narrowest a pinch may leave a section.
     *
     * <p>One, so the slice is still three across and the walking centre line has
     * a block either side of it. This is a guard on the arithmetic rather than a
     * shape anything aims for - no level's section is narrow enough for a single
     * block of swing to reach it - and it is here so that a future level with a
     * three block section cannot be pinched shut without somebody changing this
     * line on purpose.</p>
     */
    public static final int NARROWEST_RADIUS = 1;

    /**
     * The lowest a pinch may leave a section, and the height the carver
     * guarantees over the centre line whatever else it does.
     *
     * <p>Three: two blocks is a player and the third is the difference between
     * walking and shuffling along with your head in the ceiling. Every other
     * number here is a matter of how the place reads; this one is whether it can
     * be walked at all.</p>
     */
    public static final int LOWEST_SECTION = 3;

    /**
     * The tallest section anything may be cut to.
     *
     * <p>{@code TunnelDecorator}'s ceiling probe searches exactly this far above
     * the walking surface - see the discussion above. A ceiling past it measures
     * as no ceiling, and a corridor with no ceiling gets no light.</p>
     */
    public static final int MAX_LIT_HEIGHT = BurrowGeometry.CORRIDOR_HEIGHT + 1;

    /**
     * Sideways reach of the widest run any level digs, at its <em>mouth</em>.
     *
     * <p>Derived rather than written down, because the chamber measures its
     * galleries against it: a gallery that started inside the widest mouth would
     * be earth left standing in a doorway. Adding a level with a wider section
     * moves the galleries out on its own instead of leaving a second number to
     * remember.</p>
     *
     * <p>Deliberately free of {@link #RADIUS_SWING} and {@link #WANDER}, which
     * would push the innermost gallery lane out to the chamber wall and leave no
     * ring at all. The carver keeps the claim true from the other side: it fades
     * every swing to nothing over the last blocks of a run, so a mouth is always
     * cut to the section itself. {@link #outerRadius} is the number for anything
     * asking about the middle of a run instead.</p>
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

    /**
     * The furthest from the straight line between two waypoints this section is
     * ever cut - the widest bulge, standing at the far end of the widest wander.
     *
     * <p>The envelope, in other words - the swell and the wander at full stretch
     * in the same place, which is a wider reach than any single slice is. Anything
     * that wants to know whether a run can touch a block, or that asserts a run
     * has not eaten one, wants this rather than {@link #radius}: the far wall of a
     * five wide corridor is not always five blocks away.</p>
     */
    public int outerRadius() {
        return radius() + RADIUS_SWING + WANDER;
    }

    /** The tallest this section is ever cut, bulge included and {@link #MAX_LIT_HEIGHT} applied. */
    public int outerHeight() {
        return Math.min(this.height + HEIGHT_SWING, MAX_LIT_HEIGHT);
    }

    /**
     * This section's radius, swung by {@code blocks}.
     *
     * <p>Clamped at both ends rather than trusted: the swing arrives from a noise
     * function, and a guarantee that reads "as long as nobody retunes the noise"
     * is not one.</p>
     *
     * <p><strong>Fractional, and that is the whole point of the type.</strong> A
     * swing rounded to a whole block gives a wall that steps out and back once
     * every wavelength, and one block at this scale is a notch you can see from
     * twenty blocks away - which is the opposite of what the modulation is for.
     * The carver's disc test takes a real radius, so 2.4 is a genuine shape
     * between 2 and 3: the diagonals come out first and the cardinals follow, and
     * a swell arrives over four or five slices instead of on one. The height
     * cannot do the same - a layer is a layer - so the roof still steps and the
     * walls no longer do.</p>
     */
    public double radiusSwungBy(double blocks) {
        return Math.clamp(radius() + blocks, NARROWEST_RADIUS, radius() + (double) RADIUS_SWING);
    }

    /** This section's height, swung by {@code blocks}. Same clamp, and the lit ceiling is the upper end. */
    public int heightSwungBy(int blocks) {
        return Math.clamp(this.height + blocks, LOWEST_SECTION, outerHeight());
    }

    private static int widestRadius() {
        int widest = 0;
        for (RunLevel level : RunLevel.values()) {
            widest = Math.max(widest, of(level).radius());
        }
        return widest;
    }
}

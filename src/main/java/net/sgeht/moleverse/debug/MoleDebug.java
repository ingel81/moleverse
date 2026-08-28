package net.sgeht.moleverse.debug;

/**
 * Live tuning values for the mole's rearing and digging poses.
 *
 * <p>Not persisted, not synchronised. Adjusted in game through
 * {@code /moleverse peek ...} and {@code /moleverse dig ...} so a value can be
 * changed while looking straight at the mole. Once a number looks right it is
 * baked into {@link #DEFAULT_PITCH} and friends, and the command becomes a way
 * to check rather than to search.</p>
 *
 * <p>Why this exists at all: the body angle passes through several coordinate
 * conversions between Blockbench and the game, and reasoning about them from the
 * outside proved unreliable. Measuring beats deriving here.</p>
 *
 * <p>Deliberately not client-only: {@code forcePeek} also has to stop the mob
 * from wandering off while a pose is being judged, and that decision is made
 * server side. In single player both run in the same process, which is the only
 * setting this is meant for. On a dedicated server the flag simply stays off.</p>
 */
public final class MoleDebug {

    // --- rearing pose ------------------------------------------------------

    // Tuned in game with the slider panel. The corrections are small because the
    // root pivot sits at the hips; with the pivot in the middle of the body they
    // had to be an order of magnitude larger to hide the same error.

    /** Degrees the mole tips back when fully reared up. Negative lifts the nose. */
    public static final float DEFAULT_PITCH = -55.0F;

    /** Vertical correction in model units. Positive moves the mole down. */
    public static final float DEFAULT_OFFSET_Y = 1.5F;

    /** Depth correction in model units. Positive moves the mole backwards. */
    public static final float DEFAULT_OFFSET_Z = -5.3F;

    public static float peekPitchDegrees = DEFAULT_PITCH;
    public static float peekOffsetY = DEFAULT_OFFSET_Y;
    public static float peekOffsetZ = DEFAULT_OFFSET_Z;

    /** Holds every mole in the reared pose, so tuning needs no waiting. */
    public static boolean forcePeek;

    // --- dig aim -----------------------------------------------------------

    // The dig cycle is authored level and direction-neutral; these two angles
    // are what points it somewhere. They are the tuning half of that decision -
    // phase 3 computes them per mole from the route it is following.

    /**
     * Degrees the digging mole tips forward. Positive lowers the nose, the
     * opposite sign to {@link #DEFAULT_PITCH}, which lifts it.
     *
     * <p>90 is straight down. That is the extreme the direction-neutral cycle
     * has to survive and therefore the case worth seeing first; every shallower
     * angle is a slider away and none of them is harder to read.</p>
     */
    public static final float DEFAULT_DIG_PITCH = 90.0F;

    /**
     * Degrees the dig direction deviates from the body's facing. Zero because a
     * mole normally digs where it is already pointing; the value exists for the
     * case where phase 3 aims it somewhere else without turning the body first.
     */
    public static final float DEFAULT_DIG_YAW = 0.0F;

    public static float digPitchDegrees = DEFAULT_DIG_PITCH;
    public static float digYawDegrees = DEFAULT_DIG_YAW;

    /** Holds every mole in the aimed digging pose, so tuning needs no waiting. */
    public static boolean forceDig;

    /**
     * Play counters for the one-shot animations. Counters rather than flags:
     * every mole in the world reads them in the same tick and compares against
     * the value it last saw, so none of them can consume the request for the
     * others.
     */
    public static int burrowRequest;
    public static int emergeRequest;

    private MoleDebug() {
    }

    /** Plays {@code mole_burrow} once on every mole. */
    public static void playBurrow() {
        burrowRequest++;
    }

    /** Plays {@code mole_emerge} once on every mole. */
    public static void playEmerge() {
        emergeRequest++;
    }

    public static void reset() {
        peekPitchDegrees = DEFAULT_PITCH;
        peekOffsetY = DEFAULT_OFFSET_Y;
        peekOffsetZ = DEFAULT_OFFSET_Z;
        forcePeek = false;
        digPitchDegrees = DEFAULT_DIG_PITCH;
        digYawDegrees = DEFAULT_DIG_YAW;
        forceDig = false;
        // The play counters stay where they are. Winding them back is a change
        // like any other and would fire both animations on every mole.
    }

    public static String describe() {
        return String.format(
                "peek: pitch=%.1f deg, offsetY=%.2f, offsetZ=%.2f, force=%s | "
                        + "dig: pitch=%.1f deg, yaw=%.1f deg, force=%s  (16 units = 1 block)",
                peekPitchDegrees, peekOffsetY, peekOffsetZ, forcePeek,
                digPitchDegrees, digYawDegrees, forceDig);
    }
}

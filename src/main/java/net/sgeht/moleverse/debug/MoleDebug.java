package net.sgeht.moleverse.debug;

/**
 * Live tuning values for the mole's rearing pose.
 *
 * <p>Not persisted, not synchronised. Adjusted in game through
 * {@code /moleverse peek ...} so a value can be changed while looking straight
 * at the mole. Once a number looks right it is baked into {@link #DEFAULT_PITCH}
 * and friends, and the command becomes a way to check rather than to search.</p>
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

    private MoleDebug() {
    }

    public static void reset() {
        peekPitchDegrees = DEFAULT_PITCH;
        peekOffsetY = DEFAULT_OFFSET_Y;
        peekOffsetZ = DEFAULT_OFFSET_Z;
        forcePeek = false;
    }

    public static String describe() {
        return String.format(
                "pitch=%.1f deg, offsetY=%.2f, offsetZ=%.2f, force=%s  (16 units = 1 block)",
                peekPitchDegrees, peekOffsetY, peekOffsetZ, forcePeek);
    }
}

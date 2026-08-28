package net.sgeht.moleverse.entity.burrow;

/**
 * Every tuning number the burrowing mechanic uses.
 *
 * <p>They live together because they are read from four different classes and
 * because they are all expected to move once there is something to watch. A
 * value hidden in the class that happens to use it is a value nobody finds
 * again.</p>
 *
 * <p>The first block is quoted from {@code docs/MOLEHILL.md}; the second block
 * is what building the mechanic turned out to need on top of it.</p>
 */
public final class BurrowConstants {

    private static final int TICKS_PER_SECOND = 20;

    // --- from the plan ------------------------------------------------------

    /** How far a mole looks for existing mounds, and the radius the density cap counts over. */
    public static final int SEARCH_RADIUS = 16;

    /** Above this many mounds within {@link #SEARCH_RADIUS} of a site, no new mound is created. */
    public static final int MAX_MOUNDS_IN_RADIUS = 4;

    /** Shortest distance of a freshly dug trip. */
    public static final int NEW_TRAVEL_MIN = 8;

    /** Longest distance of a freshly dug trip. */
    public static final int NEW_TRAVEL_MAX = 16;

    /** An exit closer to the entry than this is not worth the trip. */
    public static final int MIN_EXIT_DISTANCE = 8;

    /** Two mounds count as connected up to this gap, and the chain continues from there. */
    public static final int NETWORK_LINK_MAX = 16;

    /** Hard bound on how far from the entry a chain is followed. */
    public static final int NETWORK_SCAN_MAX = 64;

    /** Travel speed below the surface: 3 blocks per second. */
    public static final double UNDERGROUND_SPEED_PER_TICK = 3.0 / TICKS_PER_SECOND;

    /** Earliest a mole wants to dig again after a trip. */
    public static final int BURROW_COOLDOWN = 90 * TICKS_PER_SECOND;

    /** Standing still this long counts as bored. */
    public static final int BURROW_IDLE_DELAY = 6 * TICKS_PER_SECOND;

    /** Give up walking to an entry mound after this long and dig where he stands. */
    public static final int APPROACH_TIMEOUT = 5 * TICKS_PER_SECOND;

    // --- decided while building ---------------------------------------------

    /**
     * Length of {@code mole_burrow} in ticks, and therefore how long the mole
     * stands in {@code BURROWING}. Matches the exported 1.2 s.
     */
    public static final int BURROW_TICKS = 24;

    /** Length of {@code mole_emerge} in ticks. Matches the exported 0.8 s. */
    public static final int EMERGE_TICKS = 16;

    /**
     * How far below the topmost solid block the route runs. Two blocks is deep
     * enough that a one-block dip in the terrain does not surface him and
     * shallow enough that the dust trail lands where he is.
     */
    public static final int ROUTE_DEPTH = 2;

    /**
     * Distance between two waypoints. The route follows the terrain by sampling
     * the heightmap at each one, so this is how finely it does that.
     */
    public static final int WAYPOINT_SPACING = 2;

    /**
     * Bound on the chained network. {@link #NETWORK_SCAN_MAX} already limits the
     * reach; this limits the cost when a player has carpeted an area in mounds.
     */
    public static final int NETWORK_MAX_MEMBERS = 32;

    /** How long after being hurt a mole still counts as fleeing. */
    public static final int FLEE_MEMORY = 5 * TICKS_PER_SECOND;

    /** Distance at which the mole counts as having reached the entry mound. */
    public static final double ENTRY_REACH_DISTANCE = 1.5;

    /** Squared horizontal speed below which a mole counts as standing still. */
    public static final double STILL_THRESHOLD = 1.0E-5;

    /** Walking pace to an entry mound when he is merely bored. */
    public static final double APPROACH_SPEED = 1.0;

    /** And when something is after him. The same figure {@code PanicGoal} uses. */
    public static final double FLEE_APPROACH_SPEED = 1.6;

    /**
     * Wait this long after a refusal before working out a trip again. Without it
     * a mole standing in a crowded meadow re-runs the whole search every other
     * tick for as long as it is bored, which is permanently.
     */
    public static final int REFUSAL_RETRY_DELAY = 3 * TICKS_PER_SECOND;

    /** Random surface points tried before a fresh dig is given up on. */
    public static final int FRESH_SITE_ATTEMPTS = 8;

    /** Ticks between two puffs of dust on the surface above a travelling mole. */
    public static final int DUST_INTERVAL = 3;

    /** Ticks between two scoops of the digging sound. */
    public static final int DIG_SOUND_INTERVAL = 10;

    /** Quiet and low: the mole is two blocks of soil away from the listener. */
    public static final float DIG_SOUND_VOLUME = 0.35F;

    public static final float DIG_SOUND_PITCH = 0.7F;

    private BurrowConstants() {
    }
}

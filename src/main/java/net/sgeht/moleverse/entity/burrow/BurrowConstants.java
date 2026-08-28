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

    /**
     * Earliest a mole digs a <em>new</em> hole after a trip.
     *
     * <p>Was ninety seconds, on the assumption that time was what had to ration
     * new mounds. It is not - the density cap does that, at the site, where it
     * can actually judge whether another hole belongs there. All the long timer
     * achieved was a mole standing on its own molehill for a minute and a half,
     * which is the opposite of an animal that lives underground.</p>
     */
    public static final int BURROW_COOLDOWN = 12 * TICKS_PER_SECOND;

    /**
     * Earliest a mole goes back down a hole that already exists.
     *
     * <p>Short, because using the network costs the world nothing - no new mound
     * appears. A mole with a network of its own should spend most of its life in
     * it rather than trotting about on the surface; above ground it only surfaces,
     * looks around and goes back under.</p>
     */
    public static final int NETWORK_TRIP_COOLDOWN = 2 * TICKS_PER_SECOND;

    /**
     * How long a mole stays above ground before it wants to be under it again.
     *
     * <p>Counted from the moment it surfaced, not from when it last stood still:
     * a mole with a wandering goal is almost never still, so a stillness timer
     * left it strolling about indefinitely. Above ground is where it looks
     * around and gets its bearings; the network is where it lives.</p>
     */
    public static final int SURFACE_DWELL = 2 * TICKS_PER_SECOND;

    /**
     * Chance that a mole digs somewhere new even though its network offers a
     * perfectly good exit.
     *
     * <p>High, because extending the network is what a mole is <em>for</em>.
     * Preferring existing mounds would freeze a territory at two holes; the
     * density cap is what stops this from filling a meadow, and it does that
     * job at the site rather than here. What is left of the network preference
     * is a mole that still uses its tunnels when there is nowhere new to go.</p>
     */
    public static final float EXPLORE_CHANCE = 0.6F;

    /**
     * The same while fleeing, and far higher.
     *
     * <p>Running to a hole the pursuer is already standing next to is no escape.
     * Breaking new ground away from the threat is, which makes flight the one
     * moment a mole should be most willing to dig somewhere new - the opposite
     * of what this code first assumed.</p>
     */
    public static final float FLEE_EXPLORE_CHANCE = 0.75F;

    /**
     * How close a player may come before a mole dives for cover.
     *
     * <p>This is the mole's whole relationship with the player: seen from a
     * distance, gone up close. It needs no damage first.</p>
     */
    public static final double PLAYER_SCARE_DISTANCE = 8.0;

    /**
     * The same for a player who is sneaking, as a fraction of the above.
     *
     * <p>Sneaking is the way to get near a mole at all, so the difference has to
     * be worth the walk: a third of the distance means someone crouching gets
     * close enough to watch one, and someone strolling never does.</p>
     */
    public static final double SNEAK_SCARE_FACTOR = 1.0 / 3.0;

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

    /**
     * How close a juvenile has to be when an adult dives to be taken along.
     *
     * <p>Small on purpose: a baby that is off across the meadow has no business
     * being teleported into a tunnel it never walked to. It stays above ground
     * and waits, which is the documented fallback anyway.</p>
     */
    public static final double BABY_FOLLOW_RADIUS = 4.0;

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

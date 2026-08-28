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


    /** Longest distance of a freshly dug trip. */
    public static final int NEW_TRAVEL_MAX = 16;

    /**
     * An exit closer to the entry than this is not worth the trip.
     *
     * <p>Deliberately larger than {@link #PLAYER_SCARE_DISTANCE}: the shortest
     * permitted escape has to actually leave the danger behind. At equal values
     * a fleeing mole could surface inside the radius it just fled, and would
     * dive again on arrival, for as long as the player stood there.</p>
     */
    public static final int MIN_EXIT_DISTANCE = 12;

    /**
     * Shortest distance of a freshly dug trip.
     *
     * <p>Tied to {@link #MIN_EXIT_DISTANCE}, not chosen independently: a site
     * nearer than that is rejected after the fact, so any shorter roll here is
     * an attempt thrown away before it is made.</p>
     */
    public static final int NEW_TRAVEL_MIN = MIN_EXIT_DISTANCE;

    /** Two mounds count as connected up to this gap, and the chain continues from there. */
    public static final int NETWORK_LINK_MAX = 16;

    /** Hard bound on how far from the entry a chain is followed. */
    public static final int NETWORK_SCAN_MAX = 64;

    /** Travel speed below the surface: 3 blocks per second. */
    public static final double UNDERGROUND_SPEED_PER_TICK = 3.0 / TICKS_PER_SECOND;

    /**
     * Earliest a mole breaks ground for a <em>new</em> hole.
     *
     * <p>Rations digging, and nothing else. It used to gate every trip, which
     * meant a mole that had just extended its network sat on the new molehill
     * for the whole minute - the tunnels it had just dug standing unused. Running
     * the network costs the world nothing and is what a mole does all day; only
     * adding to it is worth slowing down.</p>
     */
    public static final int NEW_HOLE_COOLDOWN = 60 * TICKS_PER_SECOND;


    /**
     * How long a mole stays above ground before going back down, at the least.
     *
     * <p>Counted from the moment it surfaced, not from when it last stood still:
     * a mole with a wandering goal is almost never still, so a stillness timer
     * left it strolling about indefinitely. Above ground is where it looks
     * around and gets its bearings; the network is where it lives.</p>
     */
    public static final int SURFACE_DWELL_MIN = 2 * TICKS_PER_SECOND;

    /**
     * And at the most. A fresh figure is drawn every time it comes up.
     *
     * <p>Randomised because a fixed interval reads as clockwork: a mole popping
     * up and vanishing on the same beat every time looks like a machine, and the
     * spread is what turns the same behaviour into an animal going about its
     * business. It also spaces out a colony that would otherwise surface in
     * lockstep.</p>
     */
    public static final int SURFACE_DWELL_MAX = 8 * TICKS_PER_SECOND;

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
     * How far a mole notices an offered earthworm.
     *
     * <p>Vanilla's tempt range, which is what actually makes it walk over. The
     * calm it brings has to reach at least as far, or it would set off towards
     * the worm and dig itself away halfway there.</p>
     */
    public static final double FOOD_NOTICE_DISTANCE = 10.0;

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

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
    public static final int DEPTH_FEEDING = 2;

    /**
     * The backbone of a colony, four blocks down.
     *
     * <p>Two blocks below a feeding run, which is what keeps two crossing runs
     * from being one hole - and the burrow below multiplies that gap by its own
     * scale, so what is a hand's breadth up here is a storey down there.</p>
     */
    public static final int DEPTH_MAIN = 4;

    /**
     * The main burrow and its chambers, six blocks down. Nothing digs at this
     * level yet; it is here so the set of levels is closed before the first link
     * is written to disk.
     */
    public static final int DEPTH_CHAMBER = 6;

    /**
     * How often a trip between two mounds that have never been joined digs a
     * main run rather than a feeding run.
     *
     * <p>A guess to be judged in play. Too high and a colony is all backbone,
     * with nothing left near the surface; too low and the second level barely
     * exists. Only a pair with no run yet rolls at all - an established run keeps
     * its depth.</p>
     */
    public static final float MAIN_RUN_CHANCE = 0.35F;

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

    /**
     * Half the width of a colony's ground, so 128 by 128 blocks around its core.
     *
     * <p>The figure is already in this file as {@link #NETWORK_SCAN_MAX}, which
     * bounds how far the chain search reaches. What it lacked was a fixed point
     * to measure from - measured from whichever mole happens to ask, a limit on
     * reach is not a limit on where the colony ends up.</p>
     */
    public static final int COLONY_EXTENT = 64;

    /**
     * How far apart two colony cores have to be, measured as a square.
     *
     * <p>It has to exceed twice {@link #COLONY_EXTENT}, so that boxes never
     * touch and a position belongs to at most one colony. The surplus is a band
     * around every colony where no new one may start: 224 minus 128 leaves
     * ninety-six blocks of ground that belongs to nobody.</p>
     *
     * <p><strong>This was 144, and the reason it was that low no longer
     * holds.</strong> The old note here argued against a wider band because a
     * mole standing in one refused every trip and did nothing but pace - which
     * was true, and measured: a soak run caught one mole managing a hundred and
     * fourteen refusals over seven and a half minutes. That was a missing exit,
     * not an argument for a narrow band. Since {@link MoleEmigrateGoal} covers
     * the band as well as a full colony, a mole that lands in one leaves after a
     * single refusal, and the width costs a walk instead of a stall.</p>
     *
     * <p>Widened on purpose: at 144 two colonies could sit with sixteen blocks
     * between their territories, which reads as one colony with a seam rather
     * than as two.</p>
     */
    public static final int COLONY_MIN_SEPARATION = 224;

    /**
     * How far past {@link #COLONY_MIN_SEPARATION} an emigrating mole aims.
     *
     * <p><strong>A walking target, not a guarantee about where colonies end
     * up.</strong> An earlier note here claimed the margin stopped colonies from
     * ringing each other at the minimum distance. It does not, and two soak runs
     * measured the gap exactly: aiming at 272, founding at 224, short by 48 -
     * this constant - every time.</p>
     *
     * <p>The reason is structural rather than a slip. Arrival is not what
     * decides where a colony starts; {@link ColonyStore#found} does, it accepts
     * at {@link #COLONY_MIN_SEPARATION}, and {@link MoleBurrowGoal} asks it every
     * {@link #REFUSAL_RETRY_DELAY} while the mole is still walking. The lower
     * threshold is checked far more often than the higher one, so it always wins.
     * Colony spacing is therefore set by {@link #COLONY_MIN_SEPARATION} alone -
     * which is the constant to change when spacing is what is wanted.</p>
     *
     * <p>What the margin still buys is the walk itself: a target beyond the line
     * keeps the mole moving outward instead of stopping on it. See
     * {@link ColonyStore#hasSettlingRoom} for why stopping on the line was worse
     * than walking past it.</p>
     */
    public static final int EMIGRATION_MARGIN = 48;

    /**
     * One leg of an emigration walk.
     *
     * <p>Vanilla pathfinding will not plan a hundred and fifty blocks in one
     * call - it gives up and the mole stands still, which looks exactly like a
     * bug. The limit is {@code max(FOLLOW_RANGE, 16)} and the follow range
     * defaults to 32, so sixteen sits comfortably inside it with room for the
     * detours a path takes around terrain. Short legs always succeed, and the
     * bearing keeps them pointing the same way.</p>
     */
    public static final int EMIGRATION_HOP = 16;

    /** How close counts as having reached the current leg. */
    public static final int EMIGRATION_HOP_REACHED_SQR = 9;

    /** After this long an emigration is abandoned, wherever the mole got to. */
    public static final int EMIGRATION_TIMEOUT = 3 * 60 * TICKS_PER_SECOND;

    /** And this long before it may try again. */
    public static final int EMIGRATION_RETRY_DELAY = 60 * TICKS_PER_SECOND;

    /** Random surface points tried before a fresh dig is given up on. */
    public static final int FRESH_SITE_ATTEMPTS = 8;

    /** Ticks between two puffs of dust on the surface above a travelling mole. */
    public static final int DUST_INTERVAL = 3;

    /** Ticks between two scoops of the digging sound. */
    public static final int DIG_SOUND_INTERVAL = 10;

    /** Quiet and low: the mole is two blocks of soil away from the listener. */
    public static final float DIG_SOUND_VOLUME = 0.35F;

    public static final float DIG_SOUND_PITCH = 0.7F;

    /**
     * How long a mole may stand on ground it cannot dig before it is carried
     * back to soil.
     *
     * <p>Walking off is the usual answer to that refusal and it works nearly
     * every time. It is no answer where walking cannot reach soil - the roof of
     * a village house, a platform, any ledge the pathfinder will not drop off -
     * and there the refusal repeats every {@link #REFUSAL_RETRY_DELAY} for the
     * rest of the world's life. Long enough that an ordinary detour across a
     * path block never reaches it.</p>
     */
    public static final int STRANDED_RESCUE_DELAY = 30 * TICKS_PER_SECOND;

    /**
     * How far around a stranded mole soil is looked for. A village house is
     * about seven blocks across, so this reaches the meadow beside one from
     * its ridge.
     */
    public static final int STRANDED_RESCUE_RADIUS = 8;

    private BurrowConstants() {
    }
}

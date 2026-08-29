package net.sgeht.moleverse.entity.burrow;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.config.MoleverseConfig;

/**
 * The burrowing mechanic's running commentary, gated behind
 * {@code MoleverseConfig.debugLogging} so a normal game stays quiet.
 *
 * <p>Without it the only evidence of a wrong decision is a mole standing around,
 * which says nothing about <em>why</em>. The refusal and recovery lines are the
 * point of the whole class: a mole that does not dig is the failure mode this
 * mechanic will spend its time in, and every refusal has a namable cause.</p>
 *
 * <p>Every line carries the entity id and position so several moles in the same
 * meadow can be told apart in the log.</p>
 */
public final class BurrowLog {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.mole");

    /**
     * On from the first tick in a development run, off in a shipped game.
     *
     * <p>Set only by the Gradle run configurations, the same way
     * {@code moleverse.devPublish} is - a played game never sees the property.
     * The reason it exists: the colony mechanic's failure mode is a mole that
     * refuses silently, and a colony is founded within seconds of a world being
     * entered. Having to type {@code /moleverse mole log on} first means the
     * founding is already over by the time anything is being recorded, and that
     * is the one line worth having.</p>
     */
    private static final boolean DEV_DEFAULT = Boolean.getBoolean("moleverse.devLogging");

    /** What {@code /moleverse mole log} last said, or null while nobody has said anything. */
    private static @Nullable Boolean override;

    private BurrowLog() {
    }

    /**
     * The command's answer, which outranks both the dev default and the config.
     *
     * <p>Without this the dev property would make {@code log off} do nothing, and
     * turning the commentary off for a moment is exactly what it is for.</p>
     */
    public static void setOverride(boolean on) {
        override = on;
    }

    private static boolean off() {
        Boolean chosen = override;
        if (chosen != null) {
            return !chosen;
        }
        // Not the config in a dev run: this is read on every line, and the
        // property answers before a config has even been loaded.
        return DEV_DEFAULT ? false : !MoleverseConfig.DEBUG_LOGGING.get();
    }

    /** {@code [#42 @-118,64,301]} - enough to follow one mole through the log. */
    private static String who(Entity mole) {
        return String.format("[#%d @%d,%d,%d]",
                mole.getId(),
                mole.getBlockX(), mole.getBlockY(), mole.getBlockZ());
    }

    private static String where(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** The registry name, not the translation key - a log wants {@code minecraft:grass_block}. */
    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static void stateChange(Entity mole, BurrowState from, BurrowState to, String reason) {
        if (off()) {
            return;
        }
        LOG.info("{} state {} -> {} ({})", who(mole), from, to, reason);
    }

    public static void wanted(Entity mole, String trigger, BlockState ground, boolean diggable) {
        if (off()) {
            return;
        }
        LOG.info("{} wants to burrow: trigger={}, ground={}, diggable={}",
                who(mole), trigger, name(ground), diggable);
    }

    public static void scanFinished(Entity mole, int moundsInRadius, boolean densityCapReached) {
        if (off()) {
            return;
        }
        LOG.info("{} scan: {} mound(s) within {}, density cap {}",
                who(mole), moundsInRadius, BurrowConstants.SEARCH_RADIUS,
                densityCapReached ? "reached" : "clear");
    }

    public static void networkBuilt(Entity mole, int members, int chainDepth, double farthest) {
        if (off()) {
            return;
        }
        LOG.info("{} network: {} member(s), chain depth {}, farthest {} blocks",
                who(mole), members, chainDepth, String.format("%.1f", farthest));
    }

    public static void targetChosen(Entity mole, BlockPos entry, boolean entryIsNew,
            BlockPos exit, boolean exitIsNew, double routeLength, int waypoints) {
        if (off()) {
            return;
        }
        LOG.info("{} target: entry={} ({}), exit={} ({}), route {} blocks over {} waypoint(s)",
                who(mole),
                where(entry), entryIsNew ? "new" : "reused",
                where(exit), exitIsNew ? "new" : "reused",
                String.format("%.1f", routeLength), waypoints);
    }

    /** A run was written down. The count is waypoints, which is the shape of the stored profile. */
    public static void linkRecorded(Entity mole, BlockPos a, BlockPos b, RunLevel run, int points) {
        if (off()) {
            return;
        }
        LOG.info("{} run recorded: {} to {}, {} at depth {}, {} point(s)",
                who(mole), where(a), where(b), run.getSerializedName(), run.depth(), points);
    }

    /** A mole gave up on a full colony and set off. */
    /**
     * @param leavingOwnColony true when a full colony is being left, false for the
     *                         unclaimed band - where the mole is a member of
     *                         nothing and "leaving a colony" would be a lie. The
     *                         band is the commoner of the two.
     */
    public static void emigrating(Entity mole, BlockPos target, boolean leavingOwnColony) {
        if (off()) {
            return;
        }
        LOG.info("{} {}, heading for {}", who(mole),
                leavingOwnColony ? "leaving a full colony" : "walking out of the unclaimed band",
                where(target));
    }

    /** And arrived somewhere a colony may be founded. */
    public static void settled(Entity mole, BlockPos where) {
        if (off()) {
            return;
        }
        LOG.info("{} reached free ground at {}", who(mole), where(where));
    }

    /** A colony came into being. Rare enough that every one of them is worth a line. */
    public static void colonyFounded(Entity mole, int id, BlockPos core) {
        if (off()) {
            return;
        }
        LOG.info("{} founded colony #{} at {}", who(mole), id, where(core));
    }

    /** The most important line in the table. Every refusal names its cause. */
    public static void refused(Entity mole, String why) {
        if (off()) {
            return;
        }
        LOG.info("{} refused: {}", who(mole), why);
    }

    public static void moundPlaced(Entity mole, BlockPos pos, BlockState support, BlockState replaced) {
        if (off()) {
            return;
        }
        LOG.info("{} mound placed at {}, on {}, replacing {}",
                who(mole), where(pos), name(support), name(replaced));
    }

    public static void travelFinished(Entity mole, int ticksTaken, double distance, int estimatedTicks) {
        if (off()) {
            return;
        }
        LOG.info("{} travel done: {} tick(s) for {} blocks, estimate was {} ({}{})",
                who(mole), ticksTaken, String.format("%.1f", distance), estimatedTicks,
                ticksTaken - estimatedTicks >= 0 ? "+" : "", ticksTaken - estimatedTicks);
    }

    /** Every way a trip can end early, and the load-time fix-up. */
    public static void recovered(Entity mole, String what) {
        if (off()) {
            return;
        }
        LOG.info("{} recovered: {}", who(mole), what);
    }
}

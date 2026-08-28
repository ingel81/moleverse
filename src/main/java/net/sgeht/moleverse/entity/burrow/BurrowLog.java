package net.sgeht.moleverse.entity.burrow;

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

    private BurrowLog() {
    }

    private static boolean off() {
        return !MoleverseConfig.DEBUG_LOGGING.get();
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

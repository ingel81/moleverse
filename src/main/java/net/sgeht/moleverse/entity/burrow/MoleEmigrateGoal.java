package net.sgeht.moleverse.entity.burrow;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.sgeht.moleverse.entity.Mole;

/**
 * Walking to ground where a colony may be founded, from the two places where
 * staying means refusing forever.
 *
 * <p>The first is a <strong>full colony</strong>, which stops at
 * {@link BurrowConstants#NETWORK_MAX_MEMBERS} mounds. Before this goal existed,
 * what happened next was nothing: the burrow goal refused every trip, the mole
 * wandered its own ground for the rest of the world's life, and the territory
 * never grew again. Refusing forever is the failure mode this mechanic is most
 * prone to, and it has no visible cause at all.</p>
 *
 * <p>The second is the <strong>unclaimed band</strong> between
 * {@link BurrowConstants#COLONY_EXTENT} and
 * {@link BurrowConstants#COLONY_MIN_SEPARATION} around a core - ground where
 * {@link ColonyStore#at} finds no colony to join and {@link ColonyStore#found}
 * still refuses to start one. That band is eighty blocks wide. A mole was
 * supposed to walk out of it, but walking out was a random stroll: in the first
 * play session one mole managed a hundred and fourteen refusals over seven and a
 * half minutes before chance carried it clear. The two cases differ in exactly
 * one respect - a band mole belongs to no colony, so leaving cannot empty one and
 * {@link #hasCompany} does not apply to it.</p>
 *
 * <p>Emigration is a walk, not a trip. A run is bounded by
 * {@link BurrowConstants#NEW_TRAVEL_MAX} and by the entity-ticking area, so
 * burrowing cannot carry a mole the hundred and fifty blocks a new core needs to
 * be away. It walks instead, in hops, on a bearing away from its core, and stops
 * when it stands on ground where a colony may be founded. Then it is an ordinary
 * mole again and the next hole it digs founds one, by the rule that already
 * exists in {@link ColonyStore#found}.</p>
 *
 * <p>Two things it must not do. It must not leave a colony empty, so it asks
 * whether another grown mole is at home first. And it must not send everybody at
 * once, which the cooldown after each attempt covers.</p>
 *
 * <p>The full-colony case still fires rarely: a colony usually has one animal in
 * it, moles only breed when a player feeds them, and the company check then
 * refuses. The band case is the common one, because every mole that spawns near
 * an established colony lands in it.</p>
 */
public class MoleEmigrateGoal extends Goal {

    private final Mole mole;
    private final double speed;

    /** Where it is headed: far enough out that a colony may be founded there. */
    private @Nullable BlockPos target;

    /** The current leg. Vanilla pathfinding will not plan the whole way in one go. */
    private @Nullable BlockPos hop;

    /** Which of the two cases sent it walking, so the log can say which. */
    private boolean leavingOwnColony;

    private int giveUpAt;
    private int nextAttemptTick;

    public MoleEmigrateGoal(Mole mole, double speed) {
        this.mole = mole;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(this.mole.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.mole.isBaby() || this.mole.isLeashed() || this.mole.isPassenger() || this.mole.isVehicle()) {
            return false;
        }
        if (this.mole.tickCount < this.nextAttemptTick) {
            return false;
        }

        MoleBurrowGoal burrowing = this.mole.getBurrowGoal();
        if (burrowing == null || !burrowing.wantsToLeave()) {
            return false;
        }

        ColonyStore store = ColonyStore.get(level);
        BlockPos here = this.mole.blockPosition();

        // Two ways to want out, and they differ in one thing only: whether the
        // mole leaving would empty a colony.
        Colony colony = store.at(here);
        this.leavingOwnColony = colony != null;
        if (colony != null) {
            if (!hasCompany(level, colony)) {
                BurrowLog.refused(this.mole, "colony is full, but leaving would empty it");
                this.nextAttemptTick = this.mole.tickCount + BurrowConstants.EMIGRATION_RETRY_DELAY;
                return false;
            }
        } else {
            // The unclaimed band. A mole here is nobody's member, so walking on
            // empties nothing and no company check applies - and it must walk on,
            // because this is the one place where staying means refusing forever.
            colony = store.nearestCrowding(here);
            if (colony == null) {
                // Already on free ground - nothing to leave.
                burrowing.clearLeaveWish();
                return false;
            }
        }

        this.target = pickTarget(level, colony, here);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        MoleBurrowGoal burrowing = this.mole.getBurrowGoal();
        return this.target != null
                && burrowing != null && burrowing.wantsToLeave()
                && this.mole.tickCount < this.giveUpAt;
    }

    @Override
    public void start() {
        this.giveUpAt = this.mole.tickCount + BurrowConstants.EMIGRATION_TIMEOUT;
        this.hop = null;
        BurrowLog.emigrating(this.mole, this.target, this.leavingOwnColony);
    }

    @Override
    public void tick() {
        if (!(this.mole.level() instanceof ServerLevel level) || this.target == null) {
            return;
        }

        BlockPos here = this.mole.blockPosition();
        if (ColonyStore.get(level).hasSettlingRoom(here)) {
            // Far enough from every core that the next hole founds a colony, and
            // far enough past that line that an ordinary stroll does not undo it.
            MoleBurrowGoal burrowing = this.mole.getBurrowGoal();
            if (burrowing != null) {
                burrowing.clearLeaveWish();
            }
            BurrowLog.settled(this.mole, here);
            this.target = null;
            return;
        }

        // One leg at a time. Pathfinding refuses distances like this one in a
        // single call, and a mole that is told to walk somewhere unreachable
        // stands still rather than setting off.
        if (this.hop == null || this.mole.getNavigation().isDone()
                || here.distSqr(this.hop) <= BurrowConstants.EMIGRATION_HOP_REACHED_SQR) {
            this.hop = nextHop(level, here, this.target);
            this.mole.getNavigation().moveTo(
                    this.hop.getX() + 0.5, this.hop.getY(), this.hop.getZ() + 0.5, this.speed);
        }
    }

    @Override
    public void stop() {
        this.mole.getNavigation().stop();
        this.target = null;
        this.hop = null;
        this.nextAttemptTick = this.mole.tickCount + BurrowConstants.EMIGRATION_RETRY_DELAY;

        // Whether it arrived or ran out of time, the wish is spent. A mole that
        // is still hemmed in will be refused again and ask again, which is the
        // same path a first attempt takes and needs no state of its own.
        MoleBurrowGoal burrowing = this.mole.getBurrowGoal();
        if (burrowing != null) {
            burrowing.clearLeaveWish();
        }
    }

    /**
     * A point beyond the unclaimed band, on the bearing that leads away from the
     * colony's core - which is the direction with the most room in it.
     */
    private static @Nullable BlockPos pickTarget(ServerLevel level, Colony colony, BlockPos from) {
        double dx = from.getX() - colony.core().getX();
        double dz = from.getZ() - colony.core().getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        double bearing = length < 1.0
                ? level.getRandom().nextDouble() * Math.PI * 2.0
                : Math.atan2(dz, dx);

        int distance = BurrowConstants.COLONY_MIN_SEPARATION + BurrowConstants.EMIGRATION_MARGIN;
        int x = colony.core().getX() + Mth.floor(Math.cos(bearing) * distance);
        int z = colony.core().getZ() + Mth.floor(Math.sin(bearing) * distance);
        return new BlockPos(x, level.getSeaLevel(), z);
    }

    /** The next leg towards the target, at most one hop long. */
    private static BlockPos nextHop(ServerLevel level, BlockPos from, BlockPos target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= BurrowConstants.EMIGRATION_HOP) {
            return MoundNetwork.surfaceAt(level, target.getX(), target.getZ());
        }

        double scale = BurrowConstants.EMIGRATION_HOP / length;
        int x = from.getX() + Mth.floor(dx * scale);
        int z = from.getZ() + Mth.floor(dz * scale);
        return MoundNetwork.surfaceAt(level, x, z);
    }

    /**
     * Whether a grown mole other than this one is on the colony's ground.
     *
     * <p>An entity query over the whole colony is not cheap, which is why it sits
     * behind the wish, the cooldown and the saturation check rather than in front
     * of them: it runs when a mole has already been told there is nowhere left to
     * dig, and at most once per {@link BurrowConstants#EMIGRATION_RETRY_DELAY}.</p>
     */
    private boolean hasCompany(ServerLevel level, Colony colony) {
        AABB ground = new AABB(
                colony.minX(), level.getMinY(), colony.minZ(),
                colony.maxX() + 1.0, level.getMinY() + level.getHeight(), colony.maxZ() + 1.0);

        List<Mole> others = level.getEntitiesOfClass(Mole.class, ground,
                other -> other != this.mole && !other.isBaby() && other.isAlive());
        return !others.isEmpty();
    }
}

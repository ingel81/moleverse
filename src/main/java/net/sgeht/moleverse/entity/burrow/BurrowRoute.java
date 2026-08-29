package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The path a mole takes between two mounds, and the checks that keep him from
 * getting lost on it.
 *
 * <p>Nothing is excavated - he simply moves through solid ground. Keeping the
 * path as a list of waypoints rather than jumping straight from entry to exit is
 * the one concession to 0.3, which will carve exactly these lines for real. The
 * waypoints follow the terrain: each one sits
 * {@link BurrowConstants#ROUTE_DEPTH} blocks under the surface at its own
 * coordinates, so a route over a hill goes over it rather than through it.</p>
 *
 * <p>The mole is moved by hand through a world that changes underneath him, and
 * {@link #advance} is where that is caught. Every failure it can report ends the
 * trip at the last valid point, which is always a better outcome than a mole
 * stuck in a wall or frozen in an unloaded chunk.</p>
 */
public final class BurrowRoute {

    /** What one tick of travel did. Everything but {@link #TRAVELLING} ends the trip. */
    public enum Progress {
        TRAVELLING,
        ARRIVED,
        /**
         * The next stretch is outside the entity-ticking area. Checking merely
         * "loaded" is the wrong predicate, and a single check at departure is
         * stale on arrival anyway: a mole that walks into a border chunk stops
         * ticking and stays there invisible, immune and immobile forever.
         */
        NOT_ENTITY_TICKING,
        /**
         * Open air ahead. A ravine, a cave mouth or a player's cellar drops the
         * ground away and the invisible mole would cross it in mid air; a roof
         * lifts the heightmap and the route runs through someone's living room.
         */
        NOT_SOLID,
        /** Water or lava. Impassable - the trip is cut short rather than routed through. */
        LIQUID
    }

    private final List<Vec3> waypoints;

    /** Distance from the start to each waypoint, so a position can be found by length. */
    private final double[] lengthAt;

    private final double totalLength;

    private double travelled;
    private Vec3 position;

    private BurrowRoute(List<Vec3> waypoints, double[] lengthAt) {
        this.waypoints = waypoints;
        this.lengthAt = lengthAt;
        this.totalLength = lengthAt[lengthAt.length - 1];
        this.position = waypoints.get(0);
    }

    /**
     * How far the route may rise or fall between two waypoints. One block per
     * two horizontal blocks is a slope a burrow can plausibly hold, and it is
     * what keeps the line between waypoints inside the ground.
     */
    private static final double MAX_DEPTH_STEP = 1.0;

    /**
     * Samples the straight line between the two mounds every
     * {@link BurrowConstants#WAYPOINT_SPACING} blocks and drops each sample to
     * its own local depth, following the terrain rather than whatever is
     * standing on it.
     */
    public static BurrowRoute between(LevelReader level, BlockPos entry, BlockPos exit) {
        Vec3 from = entry.getCenter();
        Vec3 to = exit.getCenter();
        double horizontal = Math.hypot(to.x - from.x, to.z - from.z);
        int steps = Math.max(1, (int) Math.ceil(horizontal / BurrowConstants.WAYPOINT_SPACING));

        List<Vec3> points = new ArrayList<>(steps + 1);
        double previous = Double.NaN;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double z = from.z + (to.z - from.z) * t;

            double y = depthAt(level, x, z);
            if (!Double.isNaN(previous)) {
                // A tunnel follows the ground, not what is standing on it. The
                // heightmap counts a tree trunk, a wall or a stack of crates as
                // surface, so an unclamped route jumps several blocks up in that
                // column and the straight line between the two waypoints then
                // passes through open air - the mole surfaces after a stride. In
                // forest and around any building that is the normal case.
                //
                // Upwards only. A downward step needs no limit: it puts the
                // route deeper into rock, where it stays valid. Limiting it
                // instead leaves the route hanging above a falling slope, which
                // breaks ordinary hillsides that worked before this clamp.
                y = Math.min(y, previous + MAX_DEPTH_STEP);
            }
            previous = y;
            points.add(new Vec3(x, y, z));
        }

        double[] lengthAt = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            lengthAt[i] = lengthAt[i - 1] + points.get(i).distanceTo(points.get(i - 1));
        }
        return new BurrowRoute(points, lengthAt);
    }

    /** Centre of the block {@link BurrowConstants#ROUTE_DEPTH} below the ground surface. */
    private static double depthAt(LevelReader level, double x, double z) {
        BlockPos surface = MoundNetwork.surfaceAt(level, (int) Math.floor(x), (int) Math.floor(z));
        // getHeight returns the first free spot, so the topmost solid block is
        // one below it and the route runs ROUTE_DEPTH blocks under that.
        int y = surface.getY() - 1 - BurrowConstants.ROUTE_DEPTH;
        return Math.max(y, level.getMinY() + 1) + 0.5;
    }

    /** Where the mole is right now. Valid before the first {@link #advance}. */
    public Vec3 position() {
        return this.position;
    }

    public double travelled() {
        return this.travelled;
    }

    public double length() {
        return this.totalLength;
    }

    /**
     * The route as a chain of points. Read by the debug overlay, which builds
     * the same route on the client to draw where a tunnel actually runs - the
     * heightmap it needs is the same one on both sides.
     */
    public List<Vec3> waypoints() {
        return Collections.unmodifiableList(this.waypoints);
    }

    public int waypointCount() {
        return this.waypoints.size();
    }

    /** How long the trip should take. The log compares this against the truth. */
    public int estimatedTicks() {
        return (int) Math.ceil(this.totalLength / BurrowConstants.UNDERGROUND_SPEED_PER_TICK);
    }

    /**
     * Moves one tick's worth along the route, but only after the ground it moves
     * into has been checked.
     *
     * <p>Two positions are tested: the one the mole would occupy this tick, and
     * the next waypoint. The first is what decides whether he ends up inside a
     * wall; the second sees a blocked stretch a couple of blocks early, which is
     * the difference between surfacing beside a ravine and surfacing in it.</p>
     *
     * <p>On any failure the position is left where it was, so the caller can
     * surface him at the last valid point.</p>
     */
    public Progress advance(ServerLevel level) {
        double next = Math.min(this.travelled + BurrowConstants.UNDERGROUND_SPEED_PER_TICK, this.totalLength);
        Vec3 candidate = positionAt(next);

        Progress here = validate(level, BlockPos.containing(candidate));
        if (here != Progress.TRAVELLING) {
            return here;
        }

        BlockPos ahead = waypointAfter(next);
        if (ahead != null) {
            Progress upcoming = validate(level, ahead);
            if (upcoming != Progress.TRAVELLING) {
                return upcoming;
            }
        }

        this.travelled = next;
        this.position = candidate;
        return this.travelled >= this.totalLength ? Progress.ARRIVED : Progress.TRAVELLING;
    }

    private static Progress validate(ServerLevel level, BlockPos pos) {
        if (!level.isPositionEntityTicking(pos)) {
            return Progress.NOT_ENTITY_TICKING;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return Progress.LIQUID;
        }
        return state.isSolid() ? Progress.TRAVELLING : Progress.NOT_SOLID;
    }

    private Vec3 positionAt(double distance) {
        for (int i = 1; i < this.lengthAt.length; i++) {
            if (distance <= this.lengthAt[i]) {
                double segment = this.lengthAt[i] - this.lengthAt[i - 1];
                double t = segment <= 1.0E-6 ? 1.0 : (distance - this.lengthAt[i - 1]) / segment;
                Vec3 a = this.waypoints.get(i - 1);
                Vec3 b = this.waypoints.get(i);
                return a.add(b.subtract(a).scale(t));
            }
        }
        return this.waypoints.get(this.waypoints.size() - 1);
    }

    private @Nullable BlockPos waypointAfter(double distance) {
        for (int i = 0; i < this.lengthAt.length; i++) {
            if (this.lengthAt[i] > distance) {
                return BlockPos.containing(this.waypoints.get(i));
            }
        }
        return null;
    }
}

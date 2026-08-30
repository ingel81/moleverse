package net.sgeht.moleverse.entity.burrow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.dimension.BurrowGeometry;

/**
 * Walking an animal down a carved run without letting it decide where to go.
 *
 * <p>Two creatures use this and they have almost nothing else in common: the
 * giant mole is an apparition of a trip happening in another dimension, and the
 * great worm is a real animal that lives down here and occasionally goes
 * somewhere. What they share is the movement problem, and it is a peculiar one.
 * A feeding run is five blocks wide, its walls wander and swell, and the animals
 * in it are two and nearly five blocks across - the giant mole fills such a run
 * almost exactly. Vanilla ground pathfinding stalls on that - a wide hitbox in a
 * narrow tube spends its time recalculating against corners - and a position
 * interpolated along the run's centre line does not stall but is a tram. Neither
 * is an animal.</p>
 *
 * <p>So: <strong>the polyline is a guide, not a rail.</strong> It answers where to
 * go and nothing else. Everything about <em>how</em> is worked out here against
 * the world as it actually is, every tick:</p>
 *
 * <ul>
 * <li><b>The weave.</b> A smooth drift across the corridor, clamped by the free
 *     width probed at this slice minus the animal's own half width. In a wide
 *     stretch it wanders nearly to a wall; where the run pinches - or where the
 *     animal is simply as wide as the run, which is the giant mole's normal state
 *     in a feeding run - the clamp collapses to zero and it centres itself on the
 *     line {@code CorridorCarver.walkway} promises is open. That reads as
 *     squeezing through, and it is also the reason nothing here can clip into
 *     earth: both bounds are floored at zero, so the worst the clamp can do is
 *     put the animal exactly where the carver guarantees air.</li>
 * <li><b>The ground.</b> The height comes from probing the floor under the
 *     animal, with the run's depth profile only seeding the search, so it steps up
 *     over a boulder and down into a hollow. The body pitch is measured from that
 *     floor a stride either side.</li>
 * <li><b>The two ends.</b> Entering and leaving are a slide through the floor,
 *     eased, nose up on the way out and nose down on the way in.</li>
 * </ul>
 *
 * <h2>Why the floor and not the wall</h2>
 *
 * <p>The obvious transition is through a side wall, and it does not work. A
 * corridor's walls wander a block off the straight and swell along the run, so
 * "the wall" is not at a fixed distance from the centre line and a transition
 * aimed at it sometimes ends in open air. The block below the walking surface is
 * different: the carve never cuts it, and lines the layers under it as soil.
 * Every point on every run has real earth exactly one block beneath it, which
 * makes the floor the one surface a transition can be aimed at from anywhere.</p>
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p>Speed, and when to stop. The mole's pace is a rubber band tied to a trip in
 * the overworld and it has beats and stops woven through it; the worm simply
 * flows. Both are policy about a single number - {@link #progress()} - and the two
 * have nothing to say to each other, so each animal advances its own and this
 * class only ever asks where it has got to.</p>
 */
public final class TunnelWalk {

    /**
     * How one animal moves, as distinct from where.
     *
     * @param driftAmplitude how far off the centre line it would drift if the
     *                       corridor let it. A wish and never a distance: what it
     *                       gets is whatever the probe says is there, and asking
     *                       for more than a corridor can give is what makes an
     *                       animal lean on the wall it is nearest instead of
     *                       tracing a tidy sine down the middle
     * @param driftWaves     how many swells of that drift fit in a whole run
     * @param driftMargin    daylight left between the body and the wall
     * @param driftFollow    how quickly the drift follows its target. Low, so a
     *                       pinch closes on it smoothly instead of snapping
     * @param transitionLead how far along the run a transition carries it, so it
     *                       is already moving when it breaks the surface rather
     *                       than rising on the spot like a lift
     * @param transitionSink how far under the walking surface a transition starts
     *                       and ends. Wants to be more than the animal is tall, or
     *                       both ends of the journey are half a body hanging out
     *                       of the floor
     * @param enterPitch     nose angle at the start of the rise, positive down
     * @param exitPitch      nose angle at the end of the dive, positive down
     * @param yawFollow      how fast the heading swings round to where it is
     *                       actually going
     */
    public record Style(
            double driftAmplitude,
            double driftWaves,
            double driftMargin,
            float driftFollow,
            double transitionLead,
            double transitionSink,
            float enterPitch,
            float exitPitch,
            float yawFollow) {
    }

    /** Which part of the journey a {@link #place} call is drawing. */
    public enum Stage {
        /** Rising out of the floor onto the run. */
        ENTERING,
        /** On the run. */
        TRAVELLING,
        /** Sinking back into the floor. */
        LEAVING
    }

    // --- probing --------------------------------------------------------------

    /** How far out the width probe walks, and in what steps. */
    private static final double PROBE_REACH = 4.5;
    private static final double PROBE_STEP = 0.5;

    /**
     * The two heights the width is probed at, above the walking surface.
     *
     * <p>Two, because a corridor is an arch: {@code CorridorCarver} pulls the top
     * layers in along a quarter ellipse, so the widest point is at the floor and
     * the space a back needs is narrower than the space feet need. One probe at
     * ankle height would drift an animal into the curve of the roof.</p>
     */
    private static final double PROBE_LOW = 0.5;
    private static final double PROBE_HIGH = 1.5;

    /** How far above and below the guide's own height the floor is looked for. */
    private static final int GROUND_REACH_UP = 2;
    private static final int GROUND_REACH_DOWN = 3;

    /**
     * How quickly it settles onto the height it measured.
     *
     * <p>A filter rather than a snap. The ground under a corridor is dressed with
     * gravel patches and moss, so the measured height steps by a whole block from
     * one column to the next, and an animal that took each step literally would
     * stutter up and down several times a second.</p>
     */
    private static final float GROUND_FOLLOW = 0.3F;

    /** How far ahead and behind the floor is sampled to measure the slope it is on. */
    private static final double SLOPE_SPAN = 2.0;

    private static final float PITCH_FOLLOW = 0.25F;

    // --- state ----------------------------------------------------------------

    private final Path path;
    private final Style style;

    /** The two phases of the drift, drawn per walk so no two animals weave alike. */
    private final double phaseA;
    private final double phaseB;

    private double progress;

    /** Smoothed drift across the corridor, smoothed ground height, smoothed body pitch. */
    private double drift;
    private double groundY;
    private float bodyPitch;

    /**
     * Where across the corridor the weave is being aimed, or NaN while it is free
     * to follow its own noise. Clamped like any other target, so an aim the
     * corridor cannot honour resolves to as far as it can.
     */
    private double lean = Double.NaN;

    /** Blocks covered by the last {@link #place}, and the sideways unit vector it used. */
    private double lastStep;
    private Vec3 lastAcross = new Vec3(1.0, 0.0, 0.0);

    /**
     * The two edges the commentary is written on, so a state that holds for
     * hundreds of ticks costs one line rather than hundreds.
     */
    private boolean wasPinched;
    private boolean wasFloorless;

    private TunnelWalk(Path path, Style style, RandomSource random) {
        this.path = path;
        this.style = style;
        this.phaseA = random.nextDouble() * Math.PI * 2.0;
        this.phaseB = random.nextDouble() * Math.PI * 2.0;
        this.groundY = path.at(0.0).y;
    }

    public static TunnelWalk along(Path path, Style style, RandomSource random) {
        return new TunnelWalk(path, style, random);
    }

    public Path path() {
        return this.path;
    }

    public double progress() {
        return this.progress;
    }

    public double length() {
        return this.path.length();
    }

    /** Whether the far end has been reached. */
    public boolean finished() {
        return this.progress >= 1.0;
    }

    public void setProgress(double fraction) {
        this.progress = Mth.clamp(fraction, 0.0, 1.0);
        this.groundY = this.path.at(this.progress).y;
    }

    /** Moves along by this fraction of the run. Never backwards, never past the end. */
    public void advanceBy(double fraction) {
        this.progress = Mth.clamp(this.progress + Math.max(0.0, fraction), 0.0, 1.0);
    }

    /**
     * Aims the weave at one offset until it is let go again.
     *
     * <p>In blocks left of the run's own direction, negative for the right. For an
     * animal that has stopped to do something to a wall, or one swerving at
     * something it means to bite. The value is the caller's to remember: picking
     * a side afresh every tick would be an animal changing its mind rather than
     * one that had found something.</p>
     */
    public void leanTo(double lateral) {
        this.lean = lateral;
    }

    /** Hands the weave back to its own noise. */
    public void weaveFreely() {
        this.lean = Double.NaN;
    }

    /**
     * Far enough across that the clamp resolves it to whichever wall there is.
     *
     * <p>For an animal that wants to be <em>against</em> something rather than at
     * a measured offset from the centre. Passing the clamp's own limit would need
     * the caller to know it, and it changes every tick.</p>
     */
    public static final double TO_THE_WALL = 64.0;

    /** Blocks the last {@link #place} moved the animal. For counting footfalls. */
    public double blocksMoved() {
        return this.lastStep;
    }

    /** The sideways unit vector of the last {@link #place}, pointing left of the run. */
    public Vec3 across() {
        return this.lastAcross;
    }

    /**
     * How far off the centre line the weave currently sits, positive to the left.
     *
     * <p>Read by an animal that wants to know which wall it is nearest before it
     * asks {@link #pinTo} to hold it there.</p>
     */
    public double drift() {
        return this.drift;
    }

    // --- placing --------------------------------------------------------------

    /**
     * Puts the animal where the guide, the corridor and the ground between them
     * agree it should be.
     *
     * <p>{@code setPos} and not {@code snapTo}: the second rewrites the previous
     * position and rotation as well, which is what makes it a teleport rather than
     * a move, and the one moment that is wanted is {@link #placeAtStart}.</p>
     *
     * @param blend how far through the transition, 0 to 1, already eased. Ignored
     *              while {@link Stage#TRAVELLING}
     */
    public void place(Mob mob, ServerLevel burrow, Stage stage, float blend) {
        Vec3 on = this.path.at(this.progress);
        Vec3 flat = flatten(this.path.headingAt(this.progress));
        Vec3 across = new Vec3(-flat.z, 0.0, flat.x);
        this.lastAcross = across;

        // Probed from the centre line at the height the animal is actually
        // standing at, which is last tick's settled floor. The guide's own height
        // is a block out wherever the ground has been dressed or dug.
        this.drift = Mth.lerp(this.style.driftFollow(), this.drift,
                this.driftTarget(mob, burrow, new Vec3(on.x, this.groundY, on.z), across));

        double along = 0.0;
        double sink = 0.0;
        if (stage == Stage.ENTERING) {
            along = -(1.0F - blend) * this.style.transitionLead();
            sink = (1.0F - blend) * this.style.transitionSink();
        } else if (stage == Stage.LEAVING) {
            along = blend * this.style.transitionLead();
            sink = blend * this.style.transitionSink();
        }

        double x = on.x + flat.x * along + across.x * this.drift;
        double z = on.z + flat.z * along + across.z * this.drift;

        // The guide's height only seeds the search. What it stands on is whatever
        // is under this column, which is the difference between walking the
        // corridor and hovering down the middle of it.
        double measured = floorAt(burrow, x, on.y, z);
        // A miss means nothing solid in the whole window under this column - a
        // shaft, a hole somebody dug, or ground the reconciler has not carved yet
        // - and the guide's own height is being used instead. Rare and worth
        // seeing: a run of these is an animal hovering rather than walking.
        boolean lost = measured == on.y;
        if (lost != this.wasFloorless) {
            this.wasFloorless = lost;
            say("#{}: floor {} at {}% (column {},{})", mob.getId(),
                    lost ? "not found - falling back on the guide's height" : "found again",
                    Math.round(this.progress * 100.0), Mth.floor(x), Mth.floor(z));
        }

        this.groundY = Mth.lerp(GROUND_FOLLOW, this.groundY, measured);
        this.bodyPitch = Mth.lerp(PITCH_FOLLOW, this.bodyPitch, this.slopeAt(burrow, x, z, flat));

        float pitch = this.bodyPitch;
        if (stage == Stage.ENTERING) {
            pitch = Mth.lerp(blend, this.style.enterPitch(), pitch);
        } else if (stage == Stage.LEAVING) {
            pitch = Mth.lerp(blend, pitch, this.style.exitPitch());
        }

        Vec3 at = new Vec3(x, this.groundY - sink, z);
        Vec3 moved = at.subtract(mob.position());
        this.lastStep = stage == Stage.TRAVELLING ? moved.length() : 0.0;

        // Aimed at where it is going rather than at where the guide points, so an
        // animal crossing the corridor is angled across it. Followed rather than
        // set: the weave changes direction slowly and a heading that snapped to
        // every tick's displacement would shiver. Skipped through the two
        // transitions, whose offset is a stride backwards or forwards along the
        // run and would read as the animal turning round to go into the floor.
        float yaw = mob.getYRot();
        if (stage == Stage.TRAVELLING && moved.horizontalDistanceSqr() > 1.0E-4) {
            yaw += Mth.wrapDegrees(headingYaw(moved) - yaw) * this.style.yawFollow();
        }

        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(pitch);
        mob.setPos(at.x, at.y, at.z);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * The first placement, aimed along the run and with no previous position to
     * interpolate away from.
     *
     * <p>For an animal being spawned onto a run this is not a nicety: the packet
     * that introduces an entity to a client carries the position it had when it
     * was added, so one added on the centre line and only then buried by its own
     * first tick flickers in at full size and drops through the floor. For an
     * animal already in the world it is what stops the join reading as a
     * teleport.</p>
     */
    public void placeAtStart(Mob mob, ServerLevel burrow) {
        Vec3 on = this.path.at(this.progress);
        this.groundY = on.y;
        this.bodyPitch = 0.0F;
        mob.snapTo(on.x, on.y, on.z, headingYaw(flatten(this.path.headingAt(this.progress))), 0.0F);
        mob.yBodyRot = mob.getYRot();
        mob.setYHeadRot(mob.getYRot());

        this.place(mob, burrow, Stage.ENTERING, 0.0F);
        mob.setOldPosAndRot();
    }

    // --- the weave ------------------------------------------------------------

    /**
     * How far across the corridor it would like to be, and how far it is allowed.
     *
     * <p>The clamp is the whole safety argument for letting anything leave the
     * centre line. It is measured, not assumed: the corridor is probed either side
     * at this slice, the animal's own half width and a margin are taken off, and
     * what is left is the play. Where the run swells the play is a block or more
     * either way; where a junction or a swing of the carver's noise pinches it, the
     * play goes to zero and the animal is on the centre line, which is exactly what
     * squeezing through should look like.</p>
     */
    private double driftTarget(Mob mob, ServerLevel burrow, Vec3 on, Vec3 across) {
        double half = mob.getBbWidth() * 0.5 + this.style.driftMargin();
        double left = openReach(burrow, on, across);
        double right = openReach(burrow, on, across.reverse());
        double maxLeft = Math.max(0.0, left - half);
        double maxRight = Math.max(0.0, right - half);

        // Both clamps at zero is the animal wearing the corridor. Worth a line
        // because it is invisible from inside the game and it is the difference
        // between "the weave is badly tuned" and "there is no room to weave" -
        // and at the giant's width the second is the normal state of a feeding
        // run. On the edge only: while it holds it holds for hundreds of ticks.
        boolean pinched = maxLeft <= 0.0 && maxRight <= 0.0;
        if (pinched != this.wasPinched) {
            this.wasPinched = pinched;
            say("#{}: weave {} at {}% (body {}, open {} left / {} right)",
                    mob.getId(), pinched ? "pinched to the centre" : "has room again",
                    Math.round(this.progress * 100.0), round(mob.getBbWidth()),
                    round(left), round(right));
        }

        double wanted = Double.isNaN(this.lean)
                ? this.style.driftAmplitude() * this.driftNoise()
                : this.lean;

        // Never inverted: both bounds are floored at zero, so the low bound is
        // never above the high one and Mth.clamp cannot be handed a broken range.
        // At the giant's width in a feeding run both are zero and this resolves
        // to the centre line, which is the one place the carver promises is open.
        return Mth.clamp(wanted, -maxRight, maxLeft);
    }

    /**
     * A smooth number in about {@code [-1, 1]} that changes as it goes.
     *
     * <p>Two sines rather than the value noise the carver hashes out of a seed.
     * That noise exists to be reproducible: a corridor is carved in chunk-sized
     * pieces at different times and the pieces have to agree. Nothing here is
     * produced twice - one animal, one journey, one server - so the only property
     * a drift actually needs is smoothness, and two waves that do not share a
     * period give it for two multiplications and no further copy of a hash.</p>
     */
    private double driftNoise() {
        double s = this.progress * this.style.driftWaves() * Math.PI * 2.0;
        return 0.62 * Math.sin(s + this.phaseA) + 0.38 * Math.sin(s * 1.73 + this.phaseB);
    }

    /**
     * How far the corridor is open in this direction, from this slice.
     *
     * <p>Walked outwards in half blocks, in the probing style the decorator uses,
     * and stopped by the first thing that would block a body. An unloaded position
     * counts as closed - it is the one answer that cannot be wrong, since nothing
     * may drift into ground that has not been carved yet.</p>
     */
    private static double openReach(ServerLevel burrow, Vec3 on, Vec3 direction) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double reach = 0.0;

        for (double step = PROBE_STEP; step <= PROBE_REACH; step += PROBE_STEP) {
            int x = Mth.floor(on.x + direction.x * step);
            int z = Mth.floor(on.z + direction.z * step);
            if (blocked(burrow, cursor.set(x, Mth.floor(on.y + PROBE_LOW), z))
                    || blocked(burrow, cursor.set(x, Mth.floor(on.y + PROBE_HIGH), z))) {
                return reach;
            }
            reach = step;
        }
        return reach;
    }

    /**
     * Whether this position is earth rather than corridor.
     *
     * <p>A full collision cube, which is the predicate both probes want and the
     * one the corridor's own furniture answers correctly: gravel and loose soil
     * are cubes and stop the weave, a moss carpet and an amethyst bud are not and
     * do not - so a dressed stretch is exactly as wide as a bare one, and the
     * floor under a carpet is still the floor.</p>
     */
    private static boolean blocked(ServerLevel burrow, BlockPos pos) {
        return !burrow.isLoaded(pos) || burrow.getBlockState(pos).isCollisionShapeFullBlock(burrow, pos);
    }

    // --- the ground -----------------------------------------------------------

    /**
     * The walking surface in this column.
     *
     * <p>Searched around the guide's own height rather than from the world's
     * ceiling, which is what makes it cheap and what makes it right: the run was
     * cut at that height, so the floor is within a block or two of it, and a
     * search that started anywhere else would find the roof of the corridor
     * below.</p>
     *
     * <p>The highest blocking block inside the window, plus one. A gravel patch
     * the decorator laid is therefore stepped over rather than waded through, and
     * a hollow somebody has dug is stepped down into.</p>
     */
    private static double floorAt(ServerLevel burrow, double x, double seedY, double z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int from = Mth.floor(seedY) + GROUND_REACH_UP;
        int to = from - GROUND_REACH_UP - GROUND_REACH_DOWN;

        for (int y = from; y >= to; y--) {
            cursor.set(bx, y, bz);
            if (burrow.isLoaded(cursor) && burrow.getBlockState(cursor).isCollisionShapeFullBlock(burrow, cursor)) {
                return y + 1.0;
            }
        }
        // Nothing underfoot at all - a shaft, or a hole somebody dug. The guide's
        // own height is the last thing that still describes a corridor.
        return seedY;
    }

    /**
     * The angle of the ground it is on, measured a stride either side.
     *
     * <p>From the floor rather than from the guide, so the animal tips over the lip
     * of a hollow and levels out again on the other side. Positive lowers the nose,
     * which is the entity's convention and the mole model's alike.</p>
     */
    private float slopeAt(ServerLevel burrow, double x, double z, Vec3 flat) {
        double ahead = floorAt(burrow, x + flat.x * SLOPE_SPAN, this.groundY, z + flat.z * SLOPE_SPAN);
        double behind = floorAt(burrow, x - flat.x * SLOPE_SPAN, this.groundY, z - flat.z * SLOPE_SPAN);
        return (float) Math.toDegrees(Math.atan2(behind - ahead, SLOPE_SPAN * 2.0));
    }

    // --- shared arithmetic ----------------------------------------------------

    /**
     * A puff of whatever that block is, thrown by something moving through it.
     *
     * <p>Here rather than on either animal because both of them do it and neither
     * owns it: an animal digging through the floor of a corridor throws the floor
     * of that corridor, so a run lined with loose soil throws soil and one cut
     * into deep earth throws that. Silent when the block is air, which is what
     * makes it safe to call on every tick of a transition without asking first
     * whether this particular tick is still underground.</p>
     */
    public static void castSoil(ServerLevel burrow, BlockPos from, double x, double y, double z,
            int count, double spread, double lift) {
        BlockState state = burrow.getBlockState(from);
        if (state.isAir()) {
            return;
        }
        burrow.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state, from),
                x, y, z, count, spread, lift, spread, 0.05);
    }

    /**
     * Slows both ends of a transition, so an animal leans out of the ground and
     * settles into the corridor rather than sliding out on rails.
     */
    public static float eased(int ticks, int span) {
        float t = Mth.clamp((float) ticks / span, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * On from the first tick of a development run, off in a shipped game.
     *
     * <p>The same property and logger the rest of the burrow uses. Two things here
     * are worth watching and neither is visible from inside the game: how much room
     * the weave actually has - which decides whether the animal reads as squeezing
     * through or as gliding - and whether the floor probe is finding ground at all.
     * Both are written on the edge rather than per tick.</p>
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
    }

    /** One decimal, which is as much as any of these numbers deserves in a log. */
    private static String round(double value) {
        return String.format("%.1f", value);
    }

    /**
     * The horizontal part of a direction, as a unit vector.
     *
     * <p>{@code Vec3.horizontal} only drops the height, which leaves a shorter
     * vector on a sloping run - and every offset built on it, the weave and both
     * transitions included, would then be quietly scaled by the slope.</p>
     */
    public static Vec3 flatten(Vec3 direction) {
        Vec3 flat = direction.horizontal();
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    /** Minecraft's yaw for a direction: zero faces {@code +Z}, and it counts the other way round. */
    public static float headingYaw(Vec3 direction) {
        return (float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
    }

    /**
     * The corridor centre line of one run, in burrow blocks.
     *
     * <p>Built from the stored {@link BurrowLink} and never from a route being
     * walked in the overworld, and the difference matters on the one trip where
     * they disagree: a mole re-digging an established run through ground that has
     * changed height measures a new depth profile, but the corridor down here is
     * still cut to the old one and stays that way until the link is written at the
     * end of the trip. The link is where the air is.</p>
     */
    public static final class Path {

        private final Vec3[] points;

        /** Distance from the first point to each, so a fraction can find a position. */
        private final double[] lengthAt;

        private Path(Vec3[] points, double[] lengthAt) {
            this.points = points;
            this.lengthAt = lengthAt;
        }

        /**
         * The run this link left in the burrow, walked from the given end.
         *
         * <p>The mapping is {@code CorridorCarver}'s, floor first and scale second,
         * and it has to stay that way: scaling the fractional overworld point
         * instead lands up to {@link BurrowGeometry#SCALE} blocks off the line that
         * was actually cut, against a corridor five blocks wide. The centre line is
         * the one thing the carver promises is open, and it is only open where the
         * carver put it.</p>
         *
         * <p>A link stores its ends in the order they were first dug, and an animal
         * travels it in whichever direction it likes, so the list is turned round
         * when the journey starts at the far end.</p>
         *
         * @param from which end of the link the journey starts at. Anything that is
         *             not {@code link.b()} counts as starting at {@code a}
         */
        public static Path ofLink(BurrowLink link, BlockPos from) {
            List<BlockPos> centres = new ArrayList<>(link.pointCount());
            for (int i = 0; i < link.pointCount(); i++) {
                centres.add(BurrowGeometry.toBurrow(BlockPos.containing(link.pointAt(i))));
            }
            if (from.equals(link.b())) {
                Collections.reverse(centres);
            }
            return of(centres);
        }

        /**
         * @param centres the run's waypoints in burrow block coordinates, in travel
         *                order, as {@code CorridorCarver} cut them
         */
        public static Path of(List<BlockPos> centres) {
            Vec3[] points = new Vec3[centres.size()];
            for (int i = 0; i < points.length; i++) {
                BlockPos at = centres.get(i);
                // The middle of the block horizontally and its floor vertically:
                // the carve names the walking surface, and the block below it is
                // the floor it left standing.
                points[i] = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            }

            double[] lengthAt = new double[points.length];
            for (int i = 1; i < points.length; i++) {
                lengthAt[i] = lengthAt[i - 1] + points[i].distanceTo(points[i - 1]);
            }
            return new Path(points, lengthAt);
        }

        /** The same run, walked the other way. */
        public Path reversed() {
            int count = this.points.length;
            Vec3[] flipped = new Vec3[count];
            for (int i = 0; i < count; i++) {
                flipped[i] = this.points[count - 1 - i];
            }

            double[] lengthAt = new double[count];
            for (int i = 1; i < count; i++) {
                lengthAt[i] = lengthAt[i - 1] + flipped[i].distanceTo(flipped[i - 1]);
            }
            return new Path(flipped, lengthAt);
        }

        public double length() {
            return this.lengthAt[this.lengthAt.length - 1];
        }

        public int pointCount() {
            return this.points.length;
        }

        /** The position this far along, measured by arc length rather than by index. */
        public Vec3 at(double fraction) {
            double total = this.length();
            if (total <= 1.0E-6) {
                return this.points[0];
            }

            double distance = Mth.clamp(fraction, 0.0, 1.0) * total;
            for (int i = 1; i < this.lengthAt.length; i++) {
                if (distance <= this.lengthAt[i]) {
                    double segment = this.lengthAt[i] - this.lengthAt[i - 1];
                    double t = segment <= 1.0E-6 ? 1.0 : (distance - this.lengthAt[i - 1]) / segment;
                    Vec3 a = this.points[i - 1];
                    Vec3 b = this.points[i];
                    return a.add(b.subtract(a).scale(t));
                }
            }
            return this.points[this.points.length - 1];
        }

        /**
         * The unit direction of the segment this fraction falls in.
         *
         * <p>The segment's own direction rather than a smoothed one. A run is a
         * chain of straight pieces eight blocks long down here and its corners are
         * gentle by construction - the depth profile may not climb more than a
         * block per two horizontal - so there is nothing for a blend to fix, and
         * the animal's own heading is followed rather than set in any case.</p>
         */
        public Vec3 headingAt(double fraction) {
            double total = this.length();
            if (this.points.length < 2 || total <= 1.0E-6) {
                return new Vec3(0.0, 0.0, 1.0);
            }

            double distance = Mth.clamp(fraction, 0.0, 1.0) * total;
            for (int i = 1; i < this.lengthAt.length; i++) {
                if (distance <= this.lengthAt[i] && this.lengthAt[i] > this.lengthAt[i - 1]) {
                    return this.points[i].subtract(this.points[i - 1]).normalize();
                }
            }

            Vec3 last = this.points[this.points.length - 1];
            Vec3 before = this.points[this.points.length - 2];
            Vec3 tail = last.subtract(before);
            return tail.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : tail.normalize();
        }

        /**
         * How far along the run the point nearest this position sits.
         *
         * <p>A real projection onto every segment rather than the nearest waypoint.
         * Waypoints are eight blocks apart down here, so picking the nearest of
         * them would put an animal joining a run up to four blocks from where it
         * actually stands - which is the whole budget the join has before it reads
         * as a jump.</p>
         */
        public double nearestFraction(Vec3 pos) {
            double total = this.length();
            if (this.points.length < 2 || total <= 1.0E-6) {
                return 0.0;
            }

            double best = Double.MAX_VALUE;
            double along = 0.0;
            for (int i = 1; i < this.points.length; i++) {
                Vec3 a = this.points[i - 1];
                Vec3 span = this.points[i].subtract(a);
                double spanSqr = span.lengthSqr();
                double t = spanSqr <= 1.0E-6 ? 0.0 : Mth.clamp(pos.subtract(a).dot(span) / spanSqr, 0.0, 1.0);
                Vec3 on = a.add(span.scale(t));

                double distance = on.distanceToSqr(pos);
                if (distance < best) {
                    best = distance;
                    along = this.lengthAt[i - 1] + Math.sqrt(spanSqr) * t;
                }
            }
            return along / total;
        }
    }
}

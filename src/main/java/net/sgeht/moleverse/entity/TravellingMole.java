package net.sgeht.moleverse.entity;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.entity.burrow.BurrowTraversal;
import net.sgeht.moleverse.entity.burrow.TunnelWalk;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * The giant mole: an overworld mole's trip, seen from below at mole scale.
 *
 * <p>The burrow's founding rule is that no mole is ever down there, and this does
 * not break it. There is no twin: nothing here is simulated, nothing here can be
 * killed, and nothing here decides where to go. A run above is already an
 * abstraction - a position sliding along a polyline through solid ground - and
 * this is that same abstraction drawn at {@link #GIANT_SCALE}, in the corridor
 * the run carved. See {@code docs/BURROW_LIFE.md} 2d for the design.</p>
 *
 * <h2>Guided, not driven, and not on rails either</h2>
 *
 * <p>How it gets down the corridor - the weave, the ground it walks on, the two
 * transitions through the floor - is {@link TunnelWalk}, which the great worm uses
 * as well and whose javadoc carries the argument for all of it. What is left here
 * is what makes this animal <em>this</em> animal:</p>
 *
 * <ul>
 * <li><b>It is not on a timetable.</b> Speed varies, it eases off, it stops to
 *     sniff, it stops to claw at a wall, and it makes the time up afterwards. The
 *     trip's own progress is a rubber band it is tied to rather than a rail it
 *     runs on - never more than {@link #MAX_LEAD} of the run ahead or behind - so
 *     the arrival still lands with the mole above.</li>
 * <li><b>Standing in the way costs.</b> A forward sweep off its own body, two or
 *     three damage and a shove sideways off the run's axis.</li>
 * <li><b>It notices you.</b> The head turns to a player it passes and the body
 *     does not, which is the whole of what this animal thinks about them.</li>
 * </ul>
 *
 * <h2>Nothing kills it and nothing diverges</h2>
 *
 * <p>A blow does not hurt it, does not stagger it and does not turn it: it digs
 * away, which is the one response an apparition can have that costs the design
 * nothing. There is no health to sync, no aggro to resolve, and no second version
 * of the mole above whose fate would have to be reconciled with this one.</p>
 */
public class TravellingMole extends Mob {

    /**
     * What it is doing right now.
     *
     * <p>One machine and one synched byte rather than a phase and a beat kept
     * apart, because they are mutually exclusive by construction: a mole in the
     * middle of digging out of the floor is not also pausing to sniff. Synched
     * because two of these are animated and the client cannot tell them apart from
     * a position stream - a mole that has stopped to sniff and a mole that has
     * stopped to claw at a wall look identical until the animation says which.</p>
     */
    public enum Gait {
        /** Rising out of the floor. */
        EMERGING,
        /** The travelling gait. Speed ripples around the nominal rate. */
        LOPING,
        /** Slowed to a walk for a stretch, for no reason it would give. */
        EASING,
        /** Stopped, reared halfway up, nose working. */
        SNIFFING,
        /** Stopped against the wall it had drifted to, clawing at it. */
        SCRATCHING,
        /** Sinking back into the floor, after which it is gone. */
        DIVING;

        private static final Gait[] BY_ID = values();

        public byte id() {
            return (byte) this.ordinal();
        }

        static Gait byId(byte id) {
            return id >= 0 && id < BY_ID.length ? BY_ID[id] : LOPING;
        }

        /** Digging through earth, either at the ends of the run or at a wall. */
        public boolean isDigging() {
            return this == EMERGING || this == DIVING || this == SCRATCHING;
        }

        /** Covering ground, and so the only gaits that sweep. */
        public boolean isMoving() {
            return this == LOPING || this == EASING;
        }

        boolean isTransition() {
            return this == EMERGING || this == DIVING;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_GAIT =
            SynchedEntityData.defineId(TravellingMole.class, EntityDataSerializers.BYTE);

    // --- how it moves ---------------------------------------------------------

    /**
     * How much bigger than its registered box the giant mole is drawn and
     * measured at.
     *
     * <p>Not {@link BurrowGeometry#SCALE}, which is what the burrow is
     * <em>built</em> to, and that was the mistake. At four the body came out 2.8
     * blocks across against a soil beetle's 2.4, and the first person to meet one
     * could not tell them apart - the animal the whole feature exists for read as
     * another bug in a corridor.</p>
     *
     * <p>Seven is picked off the corridor rather than by taste. It puts the body
     * at 4.9 blocks against a feeding run's five: it fills the run almost exactly,
     * which is the giant moment the plan asks for, and it is the largest number
     * that still passes one. A true three times the old size would be 8.4 and
     * simply would not fit. Backbone runs are seven wide and swell to nine, so
     * there is still room to weave where a colony's spine goes - see
     * {@code CorridorProfile}.</p>
     *
     * <p>Height follows: 0.45 by seven is 3.15 against a corridor's six, so it
     * fills the width without scraping the roof. Everything else that measures the
     * animal - the sweep box, the width clamp, the shadow, the eye - reads
     * {@code getBbWidth} or the scaled state and needs no second number.</p>
     */
    private static final double GIANT_SCALE = 7.0;

    /** How long each transition takes. Long enough to read as digging, short enough not to stall the run. */
    public static final int EMERGE_TICKS = 20;
    public static final int DIVE_TICKS = 20;

    /**
     * The mole's own way of getting down a corridor.
     *
     * <p>An amplitude with nowhere to go in a feeding run, and that is correct
     * rather than wasted. At {@link #GIANT_SCALE} the body is 4.9 blocks across
     * against a run five wide, so the clamp resolves to nothing and the mole holds
     * the centre line - which is what squeezing through a run barely wide enough
     * for you looks like. In a backbone run, seven wide and swelling to nine, the
     * same amplitude gives it most of a block either way and the weave comes
     * back.</p>
     *
     * <p>The sink has to clear the animal's own height, which at this scale is
     * 3.15 blocks. Four, so that both ends of the traversal are entirely inside
     * the ground rather than a hand's breadth of mole showing above the floor.</p>
     */
    private static final TunnelWalk.Style STYLE = new TunnelWalk.Style(
            2.2,     // drift amplitude, in blocks it would like
            5.0,     // swells of that drift over a whole run
            0.25,    // daylight left between body and wall
            0.15F,   // how fast the drift follows its clamp
            6.0,     // how far a transition carries it along the run
            4.0,     // how far under the floor a transition starts and ends
            -45.0F,  // nose up as it comes out
            55.0F,   // nose down as it goes in
            0.35F);  // how fast the heading follows the actual movement

    // --- the beats ------------------------------------------------------------

    /**
     * How long each gait lasts, in ticks: shortest, and the spread on top.
     *
     * <p>The stops are short measured against a trip, which typically runs three
     * to five hundred ticks. That is not a coincidence - it is the constraint
     * {@link #MAX_LEAD} imposes, and {@link #pauseSpan} is what enforces it on a
     * run too short to pay these lengths.</p>
     */
    private static final int LOPE_TICKS = 45;
    private static final int LOPE_SPREAD = 60;
    private static final int EASE_TICKS = 24;
    private static final int EASE_SPREAD = 20;
    private static final int SNIFF_TICKS = 18;
    private static final int SNIFF_SPREAD = 14;
    private static final int SCRATCH_TICKS = 22;
    private static final int SCRATCH_SPREAD = 16;

    /** Chances, cumulative, for what follows a stretch of loping. The rest is more loping. */
    private static final float SNIFF_CHANCE = 0.16F;
    private static final float SCRATCH_CHANCE = 0.30F;
    private static final float EASE_CHANCE = 0.55F;

    /**
     * How much the sniff chance climbs for every beat that has gone by without
     * one.
     *
     * <p>A run has room for a handful of beats, so a sixth per roll leaves about a
     * third of them with no sniff in them at all - and now that the animal fills a
     * corridor, the moment it stops and lifts its nose is the moment it stops
     * being scenery. Twelve hundredths on top of sixteen reaches certainty at the
     * seventh beat, which is more than most runs have room for - so it is a thumb
     * on the scale rather than a script, and a run still usually gets its sniff
     * early and by accident.</p>
     */
    private static final float SNIFF_PITY = 0.12F;

    /** How much of the run at each end is kept clear of stops, so no beat collides with a transition. */
    private static final double NO_PAUSE_HEAD = 0.12;
    private static final double NO_PAUSE_TAIL = 0.82;

    /** Speed while easing, as a fraction of the nominal rate. */
    private static final double EASE_FACTOR = 0.4;

    /** How far the loping speed swings either side of nominal, and over how much of the run. */
    private static final double LOPE_RIPPLE = 0.18;
    private static final double LOPE_RIPPLE_WAVES = 47.0;

    /**
     * How far ahead or behind the trip above this may get, as a fraction of the
     * run, and how hard it pulls back.
     *
     * <p>Fifteen per cent of a four hundred tick trip is sixty ticks of slack,
     * which pays for any one stop with room to spare. The gain is what turns the
     * slack back into a surge: a mole a tenth of the run behind loping again comes
     * out at about half as fast again as normal, so making the time up is
     * something you watch rather than something that quietly happens.</p>
     */
    private static final double MAX_LEAD = 0.15;
    private static final double CATCH_UP_GAIN = 0.8;
    private static final double MAX_FACTOR = 2.0;

    /** How far behind it has to be before it stops choosing to stop at all. */
    private static final double PAUSE_LAG_LIMIT = 0.06;

    /**
     * How much of the slack a single stop is allowed to spend, and the floor under
     * what that leaves.
     *
     * <p>The rolled lengths above are written for a trip of the usual few hundred
     * ticks. A short run between two mounds forty blocks apart has a third of that
     * and a third of the slack, and a stop rolled for the long case would outlast
     * it - at which point {@link #MAX_LEAD}'s clamp starts dragging a mole that is
     * supposed to be standing still, which is the tram showing through in the one
     * place it would be most obvious. So a stop is cut to what the trip can
     * actually pay for. The slack in ticks is {@code MAX_LEAD / nominalRate},
     * because the rate is a fraction of the run per tick.</p>
     */
    private static final double PAUSE_BUDGET = 0.6;
    private static final int PAUSE_FLOOR = 8;

    // --- the sweep ------------------------------------------------------------

    /**
     * How far ahead of itself the sweep reaches, past its own box.
     *
     * <p>Measured from the body outwards rather than as a box of its own, so a
     * player standing at the nose is caught by the same check as one a stride
     * further on. At the speed this travels - the overworld run's three blocks a
     * second, stretched by {@code SCALE} to twelve - a tick is over half a block,
     * which is why the reach is a reach and not a plane.</p>
     *
     * <p>It is grown from {@code getBoundingBox()}, which is where the mole
     * <em>is</em> - weave, floor and all - and not from the guide it is following.
     * A mole leaning against the left wall must not hit somebody pressed against
     * the right one.</p>
     */
    private static final double SWEEP_REACH = 1.5;

    /** What being in the way costs, and what it costs the mole: nothing. */
    private static final float SWEEP_DAMAGE = 3.0F;

    /**
     * Sideways shove and the lift under it.
     *
     * <p>Sideways rather than forwards on purpose. Pushed along the run a player
     * is bulldozed down the corridor and hit again next tick; pushed off the axis
     * they are out of the way, which is the outcome the warning was for. The lift
     * is small - the fiction is being rolled over, not launched.</p>
     */
    private static final double SWEEP_KNOCKBACK = 1.3;
    private static final double SWEEP_LIFT = 0.35;

    // --- what it notices ------------------------------------------------------

    /** How far off it will look at somebody, and how far round its head goes. */
    private static final double LOOK_RANGE = 24.0;
    private static final float LOOK_LIMIT = 50.0F;
    private static final float LOOK_FOLLOW = 0.2F;

    // --- sound and pose -------------------------------------------------------

    /** Blocks covered between footfalls. Distance rather than ticks, so an easing mole treads slower. */
    private static final double FOOTFALL_DISTANCE = 5.0;

    /**
     * How far up it rears to sniff, against {@code MoleDebug.peekPitchDegrees}.
     *
     * <p>Half, not all. The rearing pose was tuned for a mole sitting up in a
     * meadow with the sky above it; the same angle in a six block corridor at four
     * times the size is a mole with its head in the ceiling.</p>
     */
    private static final float SNIFF_REAR = 0.55F;

    private static final float PEEK_BLEND = 0.15F;

    /**
     * How long it goes on without being told where the trip is before it digs away.
     *
     * <p>The safety net that makes every other ending optional. A goal torn down
     * with the mole mid-trip, a chunk that stopped ticking above, an entity
     * summoned by hand to look at - all of them leave something in a corridor that
     * nobody is going to come back for, and all of them stop the progress arriving.
     * Two seconds of silence and it leaves the way it came.</p>
     */
    private static final int ORPHAN_TICKS = 40;

    /**
     * On from the first tick of a development run, off in a shipped game.
     *
     * <p>The same property and logger the rest of the burrow uses. What it buys
     * here is the pacing: everything this animal does that is worth arguing about
     * is a number - how long a beat lasted, how far it had drifted from the trip,
     * how far along it was when it stopped - and none of them is visible from
     * inside the game. Every line is written on a state <em>change</em> rather
     * than on a tick, so a whole traversal is a dozen lines and not a thousand.</p>
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    private static void say(String line, Object... args) {
        if (DEV_LOGGING) {
            LOG.info(line, args);
        }
    }

    // --- state ----------------------------------------------------------------

    /** Scooping paws. Client only, driven from the synched gait. */
    public final AnimationState digAnimationState = new AnimationState();

    /** Head sweep and snout twitch, borrowed from the mole's own rearing pose. */
    public final AnimationState peekAnimationState = new AnimationState();

    /** The run and everything about how it is walked. Null on a client and on a mole summoned by hand. */
    private @Nullable TunnelWalk walk;

    /** How far along the trip above is. The gap to {@link TunnelWalk#progress} is the rubber band. */
    private double tripFraction;

    /** The trip's own advance per tick, learnt from two consecutive pushes. */
    private double nominalRate;
    private double lastTripFraction;

    /** Ticks in the current gait, and how many it was given. */
    private int gaitTicks;
    private int gaitSpan = EMERGE_TICKS;

    /** Which way it was leaning when it decided to claw at a wall. */
    private int scratchSide = 1;

    /** Beats rolled since the last sniff, which is what {@link #SNIFF_PITY} counts. */
    private int beatsWithoutSniff;

    /** What a pause was rolled at before the lead budget cut it, or zero. For the log only. */
    private int cutSpan;

    /** Whether the rubber band is currently doing the steering. For the log only. */
    private boolean bandSaturated;

    /** Phase of the loping speed ripple, so two moles do not breathe in step. */
    private final double ripplePhase;

    /** Blocks covered since the last footfall. */
    private double sinceFootfall;

    /** Degrees the head is turned off the body. */
    private float lookOffset;

    /** Ticks since the last progress push. See {@link #ORPHAN_TICKS}. */
    private int sinceProgress;

    /** Rearing blend for the sniff. Client only, and kept for the frame between ticks. */
    private float peekAmount;
    private float peekAmountLast;

    public TravellingMole(EntityType<? extends TravellingMole> type, Level level) {
        super(type, level);
        // The scale attribute is baked into the supplier and never goes dirty,
        // so the dimension cache from Entity's constructor would stay unscaled
        // forever - a 4x model on a 1x box. AgeableMob sets the precedent.
        this.refreshDimensions();
        // Set here rather than by the spawner, so a hand-summoned one behaves the
        // same as one the traversal made. Nothing about this entity is ever
        // driven by a goal: noAi takes LivingEntity.aiStep out of the travel
        // branch entirely, so gravity, the move control and the navigation all
        // stop and the position comes from the walk and nowhere else. noPhysics
        // goes with it, and the argument for that one is geometric - see
        // TunnelWalk, which never leaves ground it has probed.
        this.setNoAi(true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.ripplePhase = this.random.nextDouble() * Math.PI * 2.0;
    }

    /**
     * Attributes, of which exactly one matters.
     *
     * <p>{@link Attributes#SCALE} is what makes it big, and it is the right lever
     * rather than a multiply in the renderer: it scales the entity's own box as
     * well as the model - {@code LivingEntity.getDimensions} applies it, and
     * {@code LivingEntityRenderer} reads it straight off the entity - so the sweep
     * and the corridor-width clamp both measure against a body the size of the
     * thing a player can see. Renderer-side scaling would leave a mole-sized
     * hitbox inside a corridor-sized mole, and the weave would think it had twice
     * the room it has. The attribute's own range tops out at sixteen, so four sits
     * well inside it.</p>
     *
     * <p>Health and movement speed exist because the attribute map wants them and
     * for no other reason. Nothing reads the speed - the position is scripted - and
     * nothing can spend the health.</p>
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.SCALE, GIANT_SCALE)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GAIT, Gait.EMERGING.id());
    }

    // --- what the traversal drives -------------------------------------------

    /**
     * Hands over the corridor to walk and puts the mole where its first tick
     * would. Once, before the entity is added to the world.
     *
     * <p>Neither the run nor the progress is saved or synched. The client is sent
     * a position every tick like any other entity, and a path that outlived a
     * reload would describe a trip that finished hours ago.</p>
     */
    public void arm(ServerLevel burrow, TunnelWalk.Path path, double fraction) {
        this.walk = TunnelWalk.along(path, STYLE, this.random);
        this.walk.setProgress(fraction);
        this.tripFraction = this.walk.progress();
        this.lastTripFraction = this.tripFraction;
        this.walk.placeAtStart(this, burrow);
    }

    /**
     * Where the trip above has got to, as a fraction of its own length.
     *
     * <p>Pushed once per tick from {@code MoleBurrowGoal} rather than polled,
     * because polling would need this entity to hold a reference to a mole in
     * another dimension and to ask it questions that only make sense on the tick
     * the mole was moved. It is a target and not a position: what the mole does
     * with it is {@link #advanceProgress}.</p>
     *
     * <p>Two pushes are also where the nominal rate comes from. The trip advances
     * by a fixed distance a tick, so the difference between consecutive fractions
     * is exactly the rate the run is meant to be walked at, and asking for it
     * separately would be a second way of knowing the same number.</p>
     */
    public void setTripFraction(double fraction) {
        double next = Mth.clamp(fraction, 0.0, 1.0);
        double advance = next - this.lastTripFraction;
        if (advance > 0.0) {
            this.nominalRate = advance;
        }
        this.lastTripFraction = next;
        this.tripFraction = next;
        this.sinceProgress = 0;
    }

    /**
     * Ends the traversal by digging out. Idempotent: the trip's end and a blow can
     * both ask for it, and the second one must not restart the animation.
     */
    public void digAway() {
        if (this.gait() == Gait.DIVING) {
            return;
        }
        this.setGait(Gait.DIVING, DIVE_TICKS);
    }

    public Gait gait() {
        return Gait.byId(this.entityData.get(DATA_GAIT));
    }

    private void setGait(Gait gait, int span) {
        Gait was = this.gait();
        this.entityData.set(DATA_GAIT, gait.id());
        this.gaitTicks = 0;
        this.gaitSpan = span;

        this.beatsWithoutSniff = gait == Gait.SNIFFING ? 0 : this.beatsWithoutSniff + 1;
        if (this.level().isClientSide()) {
            return;
        }
        say("mole #{}: {} -> {} for {}t at {}%{}",
                this.getId(), was, gait, span, Math.round(this.progress() * 100.0),
                this.cutSpan > 0 ? " (cut from " + this.cutSpan + "t by the lead budget)" : "");
        this.cutSpan = 0;
    }

    /** How far along the run the mole itself is, which is not where the trip is. */
    private double progress() {
        return this.walk == null ? 0.0 : this.walk.progress();
    }

    /** Rearing blend for the sniff, between the tick that set it and the next. */
    public float getPeekAmount(float partialTick) {
        return Mth.lerp(partialTick, this.peekAmountLast, this.peekAmount);
    }

    // --- ticking --------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            this.animateFromGait();
            return;
        }

        if (!(this.level() instanceof ServerLevel burrow)) {
            return;
        }

        if (++this.sinceProgress > ORPHAN_TICKS) {
            this.digAway();
        }

        if (!this.advanceGait(burrow)) {
            return;
        }

        this.advanceProgress();
        this.follow(burrow);
        this.lookAround(burrow);
        this.soundAndSoil(burrow);

        if (this.gait().isMoving()) {
            this.sweep(burrow);
        }
    }

    /**
     * The pose, from the one byte the client is told.
     *
     * <p>Everything else the model needs it works out for itself: the walk cycle
     * comes from how far the entity moved between two ticks, which is already the
     * elastic speed, the stops and the weave in one number.</p>
     */
    private void animateFromGait() {
        Gait gait = this.gait();
        this.digAnimationState.animateWhen(gait.isDigging(), this.tickCount);
        this.peekAnimationState.animateWhen(gait == Gait.SNIFFING, this.tickCount);

        this.peekAmountLast = this.peekAmount;
        this.peekAmount = Mth.lerp(PEEK_BLEND, this.peekAmount, gait == Gait.SNIFFING ? SNIFF_REAR : 0.0F);
    }

    /**
     * Runs the gait timer and picks what comes next.
     *
     * @return false when the mole removed itself, in which case nothing else in
     *         this tick may touch it
     */
    private boolean advanceGait(ServerLevel burrow) {
        this.gaitTicks++;
        if (this.gaitTicks < this.gaitSpan) {
            return true;
        }

        switch (this.gait()) {
            case DIVING -> {
                this.discard();
                return false;
            }
            case EMERGING -> this.beginBeat(burrow, Gait.LOPING);
            default -> this.beginBeat(burrow, this.rollNextBeat());
        }
        return true;
    }

    /**
     * What follows the beat that has just finished.
     *
     * <p>Only loping is ever followed by something else. A stop that ran into
     * another stop would be a mole that had lost interest in going anywhere, and a
     * mole a player is waiting to see pass is a mole that has to pass.</p>
     */
    private Gait rollNextBeat() {
        if (this.gait() != Gait.LOPING || !this.mayPause()) {
            return Gait.LOPING;
        }

        // The sniff is the one beat that reads as an animal thinking rather than
        // an animal moving, and at a sixth per roll about a third of runs used to
        // finish without one. So the chance climbs every time a beat is rolled
        // without it, and is certain by the last roll a run has room for - a
        // pity timer rather than a script, so it still happens early and by
        // accident most of the time.
        float sniffChance = SNIFF_CHANCE + this.beatsWithoutSniff * SNIFF_PITY;
        float roll = this.random.nextFloat();
        if (roll < sniffChance) {
            return Gait.SNIFFING;
        }
        if (roll < Math.max(SCRATCH_CHANCE, sniffChance)) {
            return Gait.SCRATCHING;
        }
        return roll < EASE_CHANCE ? Gait.EASING : Gait.LOPING;
    }

    /**
     * Whether stopping here would be affordable.
     *
     * <p>Two refusals. Near either end of the run a stop collides with a
     * transition, and a mole that halted to sniff while already sinking into the
     * floor would look like a bug rather than an animal. And once it is already
     * behind the trip above, the slack is spent - stopping again would only end
     * with the rubber band dragging it forward, which is the tram showing
     * through.</p>
     */
    private boolean mayPause() {
        double progress = this.progress();
        return progress > NO_PAUSE_HEAD
                && progress < NO_PAUSE_TAIL
                && progress - this.tripFraction > -PAUSE_LAG_LIMIT;
    }

    /** Starts a beat, with the length and the announcement that go with it. */
    private void beginBeat(ServerLevel burrow, Gait gait) {
        switch (gait) {
            case LOPING -> this.setGait(gait, LOPE_TICKS + this.random.nextInt(LOPE_SPREAD));
            case EASING -> this.setGait(gait, this.pauseSpan(EASE_TICKS, EASE_SPREAD));
            case SNIFFING -> {
                this.setGait(gait, this.pauseSpan(SNIFF_TICKS, SNIFF_SPREAD));
                burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.MOLE_SNIFF.get(), SoundSource.NEUTRAL, 1.6F, 0.6F);
            }
            case SCRATCHING -> {
                this.setGait(gait, this.pauseSpan(SCRATCH_TICKS, SCRATCH_SPREAD));
                // Whichever wall it had already leaned towards. Picking one now
                // would mean crossing the corridor to reach it, which is a mole
                // changing its mind rather than a mole finding something.
                this.scratchSide = this.walk == null || this.walk.drift() >= 0.0 ? 1 : -1;
            }
            default -> this.setGait(gait, LOPE_TICKS);
        }
    }

    /**
     * How long a beat may last, given how much slack this particular trip has.
     *
     * <p>See {@link #PAUSE_BUDGET}. Easing counts as a pause here even though it
     * still covers ground - it spends slack at more than half the rate a full stop
     * does, and a long crawl on a short run would hit the same clamp.</p>
     */
    private int pauseSpan(int shortest, int spread) {
        int rolled = shortest + this.random.nextInt(spread);
        if (this.nominalRate <= 0.0) {
            return rolled;
        }
        int affordable = (int) (MAX_LEAD / this.nominalRate * PAUSE_BUDGET);
        int span = Math.max(PAUSE_FLOOR, Math.min(rolled, affordable));
        // Remembered rather than logged here, so the one line setGait writes can
        // carry it. A pause that keeps getting cut on every run is the signal
        // that MAX_LEAD is too tight for the pace the lane is feeding.
        this.cutSpan = span < rolled ? rolled : 0;
        return span;
    }

    /**
     * Moves the mole's own progress, tied to the trip's but not equal to it.
     *
     * <p>The band is a clamp and the gain is a spring, and both are needed. The
     * spring is what makes a stop look paid for - the mole visibly hurries
     * afterwards - and the clamp is what guarantees it never has to, because no
     * amount of dawdling can put it more than {@link #MAX_LEAD} of the run out of
     * step. Progress only ever grows: a trip that stalls above leaves this one
     * standing, not reversing.</p>
     */
    private void advanceProgress() {
        TunnelWalk walk = this.walk;
        if (walk == null || this.nominalRate <= 0.0) {
            // Nothing pushed yet, or a run of zero length. Standing still is the
            // honest answer and the orphan timer covers the rest.
            return;
        }

        double lead = walk.progress() - this.tripFraction;
        double correction = Mth.clamp(-lead / MAX_LEAD, -1.0, 1.0);
        double factor = Mth.clamp(this.gaitFactor() * (1.0 + CATCH_UP_GAIN * correction), 0.0, MAX_FACTOR);

        double next = walk.progress() + this.nominalRate * factor;
        double free = next;
        next = Math.min(next, this.tripFraction + MAX_LEAD);
        next = Math.max(next, this.tripFraction - MAX_LEAD);

        // Saturation is the band doing the animal's steering for it - dragging a
        // stopped mole forward, or holding a hurrying one back - and it is the one
        // state in here that looks like a tram from outside. Logged on the edge,
        // because while it holds it holds for many ticks running.
        boolean saturated = Math.abs(next - free) > 1.0E-9;
        if (saturated != this.bandSaturated) {
            this.bandSaturated = saturated;
            say("mole #{}: rubber band {} at {}% ({}% {} the trip)",
                    this.getId(), saturated ? "saturated" : "free again",
                    Math.round(walk.progress() * 100.0),
                    Math.round(Math.abs(lead) * 100.0), lead >= 0.0 ? "ahead of" : "behind");
        }

        walk.advanceBy(next - walk.progress());
    }

    /** Speed of the current gait, as a multiple of the trip's own rate. */
    private double gaitFactor() {
        return switch (this.gait()) {
            // Never flat. A constant multiplied by a constant is still a tram,
            // and the ripple is slow enough to read as an animal breathing
            // rather than as a stutter.
            case LOPING -> 1.0 + LOPE_RIPPLE * Math.sin(this.progress() * LOPE_RIPPLE_WAVES + this.ripplePhase);
            case EASING -> EASE_FACTOR;
            case SNIFFING, SCRATCHING -> 0.0;
            case EMERGING, DIVING -> 1.0;
        };
    }

    // --- where it actually is -------------------------------------------------

    /** Hands the tick to the walk, with the stage and the blend this gait implies. */
    private void follow(ServerLevel burrow) {
        TunnelWalk walk = this.walk;
        if (walk == null) {
            // A mole summoned by hand to look at. It stands where it was put and
            // after ORPHAN_TICKS digs itself away again.
            return;
        }

        Gait gait = this.gait();
        TunnelWalk.Stage stage = switch (gait) {
            case EMERGING -> TunnelWalk.Stage.ENTERING;
            case DIVING -> TunnelWalk.Stage.LEAVING;
            default -> TunnelWalk.Stage.TRAVELLING;
        };
        float blend = gait == Gait.EMERGING ? TunnelWalk.eased(this.gaitTicks, EMERGE_TICKS)
                : gait == Gait.DIVING ? TunnelWalk.eased(this.gaitTicks, DIVE_TICKS)
                : 1.0F;

        if (gait == Gait.SCRATCHING) {
            walk.leanTo(this.scratchSide * TunnelWalk.TO_THE_WALL);
        } else {
            walk.weaveFreely();
        }
        walk.place(this, burrow, stage, blend);
        this.sinceFootfall += walk.blocksMoved();
    }

    // --- what it notices ------------------------------------------------------

    /**
     * Turns the head to whoever is nearby, and nothing else.
     *
     * <p>The body keeps going. That is the entire characterisation: it knows you
     * are there, it is not interested, and it is not going to stop. Carried on
     * {@code yHeadRot}, which the ordinary head-rotation packet already syncs and
     * which the renderer already resolves into an angle relative to the body -
     * nothing custom travels for this.</p>
     */
    private void lookAround(ServerLevel burrow) {
        float wanted = 0.0F;

        // Creative players included, which the Entity-taking overload excludes.
        // Whoever is testing this is in creative, and a mole that pointedly
        // ignores exactly the person looking at it is a bug report waiting to
        // happen.
        Player watched = burrow.getNearestPlayer(this.getX(), this.getY(), this.getZ(), LOOK_RANGE, true);
        if (watched != null && !watched.isSpectator()) {
            float toPlayer = TunnelWalk.headingYaw(watched.position().subtract(this.position()));
            wanted = Mth.clamp(Mth.wrapDegrees(toPlayer - this.getYRot()), -LOOK_LIMIT, LOOK_LIMIT);
        }

        this.lookOffset += Mth.wrapDegrees(wanted - this.lookOffset) * LOOK_FOLLOW;
        this.setYHeadRot(this.getYRot() + this.lookOffset);
    }

    // --- what it costs to be in the way ---------------------------------------

    /**
     * Anything in front of it takes a knock. Server side and once a tick.
     *
     * <p>Vanilla's invulnerability window does most of the work: a player caught
     * by the sweep is immune for the next ten ticks, so a mole six blocks long
     * passing over them lands one hit and not eight. The knockback is applied only
     * where the damage landed, for the same reason - a shove per tick from a hit
     * that never happened would pin somebody to a wall.</p>
     */
    private void sweep(ServerLevel burrow) {
        // Level, with the body's own pitch left out. A sweep that followed the
        // slope would dip into the floor on the way down and lift off it on the
        // way up, and what is being asked is whether somebody is standing in the
        // corridor ahead - a question with no vertical part to it.
        Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
        AABB reach = this.getBoundingBox().expandTowards(look.scale(SWEEP_REACH));

        for (Player player : burrow.getEntitiesOfClass(Player.class, reach, p -> !p.isSpectator())) {
            if (!player.hurtServer(burrow, this.damageSources().mobAttack(this), SWEEP_DAMAGE)) {
                continue;
            }

            // The part of their offset that is across the run rather than along
            // it. Somebody standing dead centre leaves nothing to normalise, so
            // one side of the corridor is picked rather than none.
            Vec3 offset = player.position().subtract(this.position()).horizontal();
            Vec3 across = offset.subtract(look.scale(offset.dot(look)));
            if (across.lengthSqr() < 1.0E-4) {
                across = new Vec3(-look.z, 0.0, look.x);
            }
            across = across.normalize();

            // knockback pushes away from the direction it is handed, so the
            // direction handed to it is the opposite of where they should end up.
            player.knockback(SWEEP_KNOCKBACK, -across.x, -across.z);
            player.push(0.0, SWEEP_LIFT, 0.0);
            // Velocity only reaches the player who owns it through this flag;
            // needsSync alone sends it to everyone watching but not to them.
            player.hurtMarked = true;
        }
    }

    // --- the noise it makes ---------------------------------------------------

    /**
     * Footfalls, digging and the soil both shake loose.
     *
     * <p>Two layers under way, on the same reasoning {@code BurrowScratching}
     * gives for its own pair: rooted dirt at half pitch is the body going past,
     * and a mole's own dig event under it is the claws, and one material moving
     * over another is what a single file can never be. The claws are the same
     * event the scratching behind the walls uses to announce this in the first
     * place, which is the point - the rumour arrives before the animal, in the
     * animal's own voice.</p>
     *
     * <p>Footfalls are counted in blocks covered and not in ticks. A mole that has
     * eased off treads more slowly for the same reason a real one would, and a
     * stopped mole is silent, without any of that being said twice.</p>
     */
    private void soundAndSoil(ServerLevel burrow) {
        Gait gait = this.gait();

        if (this.sinceFootfall >= FOOTFALL_DISTANCE) {
            this.sinceFootfall = 0.0;
            burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ROOTED_DIRT_STEP, SoundSource.NEUTRAL, 1.8F, 0.45F);
            burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.MOLE_DIG.get(), SoundSource.NEUTRAL, 0.7F, 0.55F);
        }

        if (gait == Gait.SCRATCHING) {
            this.clawAtWall(burrow);
            return;
        }

        if (!gait.isTransition()) {
            return;
        }

        if (this.gaitTicks % 4 == 1) {
            burrow.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.MOLE_DIG.get(), SoundSource.NEUTRAL, 1.4F, 0.5F);
        }

        // Cast off the block it is moving through, so a corridor lined with loose
        // soil throws soil and one cut into deep earth throws that.
        BlockPos under = BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ());
        TunnelWalk.castSoil(burrow, under, this.getX(), this.getY() + 0.2, this.getZ(), 12, 0.9, 0.3);
    }

    /**
     * The wall pause: claws and a shower of whatever the wall is made of, on the
     * side the mole had already drifted to.
     *
     * <p>The wall is found rather than assumed. The weave is clamped by the probe,
     * so it stops short of the wall by a margin and a fixed offset would either
     * miss the wall or bury the particles inside it - a stride further out and one
     * block read settles which block is actually being clawed at.</p>
     */
    private void clawAtWall(ServerLevel burrow) {
        Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 side = new Vec3(-look.z, 0.0, look.x).scale(this.scratchSide);
        double out = this.getBbWidth() * 0.5 + 0.6;

        double wx = this.getX() + side.x * out;
        double wy = this.getY() + 0.9;
        double wz = this.getZ() + side.z * out;

        if (this.gaitTicks % 5 == 1) {
            burrow.playSound(null, wx, wy, wz,
                    ModSounds.MOLE_DIG.get(), SoundSource.NEUTRAL, 1.1F, 0.55F);
        }
        TunnelWalk.castSoil(burrow, BlockPos.containing(wx, wy, wz), wx, wy, wz, 5, 0.3, 0.5);
    }

    // --- what it refuses to be ------------------------------------------------

    /**
     * Nothing lands on it, and a blow makes it leave.
     *
     * <p>The two tags vanilla lets through its own invulnerability are let through
     * here too, so {@code /kill} still works on one that has somehow outstayed its
     * trip. Everything else is answered with a dig-away and a false: false is what
     * stops the hurt animation, the hurt sound, the red flash and the knockback
     * all at once, because every one of them lives past this return.</p>
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, damageSource, amount);
        }
        this.digAway();
        return false;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    /**
     * Never despawns on its own account. {@link BurrowTraversal} decides when it is
     * over and {@link #ORPHAN_TICKS} catches the case where nobody does.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Nothing shoves it and it shoves nothing.
     *
     * <p>Crowding is vanilla's answer to two mobs in one place and it is the wrong
     * one here twice over: a shove would take it off a position that was measured
     * against the walls a tick ago, and the interaction with a player in the
     * corridor is the sweep, which decides both the damage and the direction
     * itself. Two systems pushing the same player on the same tick would fight.</p>
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    /**
     * Walks the cycle at the scale it is drawn at.
     *
     * <p>The distance handed in is blocks moved this tick, and a giant covers four
     * of them for every one the animal above covers. Left alone the cycle saturates
     * and the legs blur; divided by the same {@code SCALE} that made it big, a
     * stride down here takes as long as a stride up there, which is what a large
     * animal looks like - and because it is driven by real distance, easing off and
     * stopping are already in it.</p>
     */
    @Override
    protected void updateWalkAnimation(float distance) {
        super.updateWalkAnimation(distance / (float) GIANT_SCALE);
    }
}

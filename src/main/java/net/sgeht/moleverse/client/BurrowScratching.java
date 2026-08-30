package net.sgeht.moleverse.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModSounds;

/**
 * A mole digging in the earth behind the corridor wall, heard and never seen.
 *
 * <p>The design rule for the burrow is that no live mole is ever down there -
 * the colony above is where the animals are, and a mole met in its own tunnel
 * would be a mob, not a maker. That leaves the corridors reading as abandoned,
 * which is the wrong feeling: they are freshly cut, lined, and stocked. This
 * layer is the compromise the plan asks for in {@code docs/BURROW_LIFE.md} 2c.
 * It is a sound with a position and nothing behind it. Every 45 to 120 seconds
 * a point is picked two to five blocks inside the earth, and one or two passes
 * of clawing are played there a second or so apart while a little soil comes
 * off the ceiling below. There is no entity, no server traffic, and nothing to
 * find if the player digs towards it.</p>
 *
 * <h2>Why it is a burst and not a sound</h2>
 *
 * <p>A single point sound is a block being punched somewhere - a noise. What
 * turns it into an animal is repetition that goes somewhere, and where the
 * repetition comes from has moved. The scratch file is three seconds of rhythmic
 * clawing, so it arrives already carrying the part this class used to build out
 * of four-tick gaps, and what is left here is the drift: one pass, sometimes a
 * second a stride further along. {@link #SCRATCH_DRIFT} at zero is still what
 * turns the mole back into a noise.</p>
 *
 * <p>Muffling is volume and pitch, because Minecraft has no sound occlusion -
 * a source inside solid rock is exactly as loud as one in the open. Both layers
 * are now {@code moleverse:ambient.burrow.scratch}, which is claws through
 * packed earth heard through a wall rather than the two vanilla block sounds
 * this used to layer. Still pitched into the bottom half: the recording is
 * already muffled, but a stroke at full pitch reads as something at the player's
 * own scale, and the animal on the other side of that wall is the size of a
 * hand.</p>
 *
 * <p>Two draws from the same event rather than one, and it is worth the second
 * line. The event has three variants and picks per call, so the paw and the grit
 * are usually two different scratches an eighth of a tone apart - which is what
 * a real stroke sounds like, one material moving over another, and what a single
 * file can never be. When the two draws land on the same variant it thickens
 * instead, which is fine. {@link #GRIT_VOLUME} at zero turns the second layer
 * off outright, so the panel can settle this by ear without a rebuild.</p>
 *
 * <h2>Separate from the rest of the ambience</h2>
 *
 * <p>{@link BurrowAmbience} is weather: rolls per tick that fill the corridor
 * with specks nobody is meant to look at directly. This is an event, with a
 * state machine and a position that outlives the tick that chose it, and it is
 * the only thing in the burrow that is supposed to make a player stop and turn
 * their head. The two do not share tuning, so they do not share a file. The
 * probe and the offset helper are borrowed from there rather than copied.</p>
 *
 * <p>One deliberate difference: the light gate. {@code BurrowAmbience} stops
 * its motes in a lit chamber, so that a room with lanterns in it reads as dry
 * and finished. The scratching keeps going and so does its soil, because a
 * lantern on the wall does not stop a mole on the other side of it. Light
 * makes a room safe from what spawns, not from what digs.</p>
 *
 * <h2>The dials are not final</h2>
 *
 * <p>Everything below is a mutable static rather than a constant, so that
 * {@code /moleverse burrow panel} can move it between two bursts - see
 * {@code client.debug.BurrowTunePanel}. This is the layer that most needs it: a
 * burst arrives once every minute or two, so judging a volume or a depth by
 * rebuilding and walking back down costs an evening, and every value is read
 * fresh when the burst is scheduled or played. <b>The value written here is the
 * shipped one.</b> The panel never writes back: a number settled at the slider is
 * baked in by editing this file. A burst already in flight keeps the count and
 * the gap it was given, which is one burst of lag and not worth a state machine
 * to avoid.</p>
 */
public final class BurrowScratching {

    /** Shortest and longest gap between two bursts, in ticks: 45 to 120 seconds. */
    public static int BURST_MIN_DELAY = 900;
    public static int BURST_DELAY_SPREAD = 1500;

    /**
     * Ticks before a second attempt when {@link #aim} found nothing to dig in.
     *
     * <p>A ray that leaves the corridor without meeting lining means the player
     * is standing somewhere the probe cannot work with - the middle of a wide
     * chamber, a vertical shaft, a room they have hollowed out themselves. The
     * cheap answer is to try again in a second rather than to spend the whole
     * burst on it and go quiet for two minutes, which is the failure mode
     * nobody would ever diagnose from inside the game.</p>
     */
    public static int BURST_RETRY_DELAY = 20;

    /**
     * Passes in one burst, and the tick gap between them.
     *
     * <p>These were a paw rate - two to five strokes, four to nine ticks apart -
     * because a stroke was a single block sound a third of a second long and the
     * rhythm had to be assembled out of them. It is not any more. Against a
     * three-second recording a nine-tick gap starts the second copy before a
     * sixth of the first has played, and a burst of five stacks five of them
     * inside two seconds, which is not a mole but a landslide.</p>
     *
     * <p>So one pass, or two a second or so apart and a stride further along.
     * The gap sits at the top of the range {@code BurrowKnobs} gives its slider,
     * which is the first thing to widen when these are settled by ear: a full
     * non-overlapping stride is sixty ticks and the panel stops at forty.</p>
     */
    public static int SCRATCH_MIN_COUNT = 1;
    public static int SCRATCH_COUNT_SPREAD = 2;
    public static int SCRATCH_MIN_GAP = 20;
    public static int SCRATCH_GAP_SPREAD = 20;

    /**
     * One burst in this many is aimed at the ceiling instead of at a wall.
     *
     * <p>Worth the branch: something above the player is a different and better
     * moment than something beside them, and the soil that comes down lands in
     * front of their face rather than off to one side.</p>
     */
    public static int OVERHEAD_ONE_IN = 4;

    /**
     * How far the aiming ray travels through open air before it gives up.
     *
     * <p>Corridors are five blocks wide and six high, chambers nine, so this
     * reaches a surface from anywhere a player can stand and stops rather than
     * running to the edge of the loaded chunks.</p>
     */
    public static int PROBE_REACH = 10;

    /** How far above or below eye level a wall-aimed ray starts. */
    public static double AIM_HEIGHT_SPREAD = 1.5;

    /**
     * How deep behind the surface the scratching sits, in blocks.
     *
     * <p>Under two and it is a player mining on the other side of a one-block
     * partition. Past five the distance attenuation has taken most of it and
     * the burst is a rumour. The lower bound is also a floor rather than a
     * target: {@link #aim} walks back towards the wall when the drawn depth
     * lands in air, because in a dense colony a ray five blocks long can cross
     * into the next corridor, and a scratch heard from inside an empty room is
     * a bug with an obvious cause and no obvious fix.</p>
     */
    public static int SCRATCH_MIN_DEPTH = 2;
    public static int SCRATCH_DEPTH_SPREAD = 4;

    /**
     * How far the source travels between two scratches, and the slop on it.
     *
     * <p>A fixed direction per burst, not a fresh one per scratch: a random
     * walk reads as a fault in the sound engine, while a steady line reads as
     * an animal going somewhere.</p>
     */
    public static double SCRATCH_DRIFT = 0.30;
    public static double SCRATCH_JITTER = 0.12;

    /**
     * The paw. Bottom half of the pitch range, where a stroke stops being crisp.
     *
     * <p>These three were settled against the vanilla block sounds this used to
     * layer, and the recording that replaced them is normalised to a different
     * level. Expect a pass with {@code /moleverse burrow panel} before they are
     * right again - the sliders are here for exactly this.</p>
     */
    public static float SCRATCH_VOLUME = 0.34F;
    public static float SCRATCH_MIN_PITCH = 0.50F;
    public static float SCRATCH_PITCH_SPREAD = 0.18F;

    /** The second draw under it, quieter and a shade lower. Zero switches it off. */
    public static float GRIT_VOLUME = 0.20F;
    public static float GRIT_PITCH_FACTOR = 0.92F;

    /** Motes shaken off the ceiling per scratch, and how far under it they start. */
    public static int SHOWER_MIN = 2;
    public static int SHOWER_SPREAD = 3;
    public static double SHOWER_DROP_GAP = 0.15;

    /** Ticks until the next burst; negative while the player is not in the burrow. */
    private static int burstCountdown = -1;

    /** Scratches left in the burst being played, and ticks until the next one. */
    private static int scratchesLeft;
    private static int scratchDelay;

    /** Where the scratching is, inside the earth. Moves by the drift as it plays. */
    private static double anchorX;
    private static double anchorY;
    private static double anchorZ;

    /** The last open point before the earth, and the column the soil falls down. */
    private static double surfaceX;
    private static double surfaceY;
    private static double surfaceZ;

    /** The direction the burst travels, already scaled to {@link #SCRATCH_DRIFT}. */
    private static double driftX;
    private static double driftZ;

    private BurrowScratching() {
    }

    /**
     * One client tick. Called from {@link BurrowAmbience#tick()} after the
     * dimension check, so this never runs outside the burrow.
     *
     * <p>The common path is one decrement and a return. A burst costs at most
     * {@link #PROBE_REACH} block lookups once, plus one short column walk per
     * scratch.</p>
     */
    static void tick(ClientLevel level, LocalPlayer player, RandomSource random) {
        if (scratchesLeft > 0) {
            if (--scratchDelay > 0) {
                return;
            }
            scratch(level, random);
            scratchesLeft--;
            scratchDelay = SCRATCH_MIN_GAP + random.nextInt(SCRATCH_GAP_SPREAD);
            return;
        }
        if (burstCountdown < 0) {
            // First tick after arriving. A full delay, so that stepping out of the
            // shaft is never answered by a mole in the same second.
            burstCountdown = BURST_MIN_DELAY + random.nextInt(BURST_DELAY_SPREAD);
            return;
        }
        if (--burstCountdown > 0) {
            return;
        }
        burstCountdown = aim(level, player, random)
                ? BURST_MIN_DELAY + random.nextInt(BURST_DELAY_SPREAD)
                : BURST_RETRY_DELAY;
    }

    /** Forget the burst in progress. Called when the player leaves the burrow. */
    static void disarm() {
        burstCountdown = -1;
        scratchesLeft = 0;
    }

    /**
     * Pick a point in the earth for the next burst, or report that there is none.
     *
     * <p>A ray is walked outwards a block at a time - horizontally at a random
     * bearing, or straight up - until it leaves the open corridor. The last open
     * point on it is the surface, which is where the soil will fall from; the
     * anchor is a few blocks further along the same line, inside the lining.</p>
     *
     * <p>The lining test is by block rather than by solidity on purpose. Loose
     * soil, deep earth and root nodules are the three things the carver puts
     * around a corridor, so they are the three things a mole can be digging
     * through. A chest or a wall of cobblestone the player built is solid too
     * and would place a mole inside their own storage room.</p>
     */
    private static boolean aim(ClientLevel level, LocalPlayer player, RandomSource random) {
        boolean overhead = random.nextInt(OVERHEAD_ONE_IN) == 0;
        double dirX = 0.0;
        double dirY = 0.0;
        double dirZ = 0.0;
        if (overhead) {
            dirY = 1.0;
        } else {
            double bearing = random.nextDouble() * Math.PI * 2.0;
            dirX = Math.cos(bearing);
            dirZ = Math.sin(bearing);
        }

        double x = player.getX();
        double y = player.getEyeY() + (overhead ? 0.0 : BurrowAmbience.spread(random, AIM_HEIGHT_SPREAD));
        double z = player.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean walled = false;
        for (int step = 0; step < PROBE_REACH; step++) {
            cursor.set(Mth.floor(x + dirX), Mth.floor(y + dirY), Mth.floor(z + dirZ));
            if (!level.getBlockState(cursor).isAir()) {
                walled = true;
                break;
            }
            x += dirX;
            y += dirY;
            z += dirZ;
        }
        if (!walled) {
            return false;
        }

        int depth = SCRATCH_MIN_DEPTH + random.nextInt(SCRATCH_DEPTH_SPREAD);
        while (depth >= SCRATCH_MIN_DEPTH) {
            cursor.set(
                    Mth.floor(x + dirX * depth),
                    Mth.floor(y + dirY * depth),
                    Mth.floor(z + dirZ * depth));
            if (isLining(level.getBlockState(cursor))) {
                break;
            }
            depth--;
        }
        if (depth < SCRATCH_MIN_DEPTH) {
            return false;
        }

        anchorX = x + dirX * depth;
        anchorY = y + dirY * depth;
        anchorZ = z + dirZ * depth;
        surfaceX = x;
        surfaceY = y;
        surfaceZ = z;

        double driftBearing = random.nextDouble() * Math.PI * 2.0;
        driftX = Math.cos(driftBearing) * SCRATCH_DRIFT;
        driftZ = Math.sin(driftBearing) * SCRATCH_DRIFT;

        scratchesLeft = SCRATCH_MIN_COUNT + random.nextInt(SCRATCH_COUNT_SPREAD);
        // Next tick rather than this one, which costs nothing visible and keeps
        // the aiming lookups and the first two sounds on separate ticks.
        scratchDelay = 1;
        return true;
    }

    /**
     * One stroke: two layered sounds at the anchor, then the soil it shakes loose.
     *
     * <p>The anchor moves before the sound rather than after, so the first
     * stroke of a burst is already one drift step away from the point
     * {@link #aim} measured - the mole is passing, not starting.</p>
     *
     * <p>{@code distanceDelay} is false. It only does anything past ten blocks,
     * and everything here is closer than that; passing false says that the
     * rhythm of a burst is this scheduler's business and not the sound
     * engine's.</p>
     */
    private static void scratch(ClientLevel level, RandomSource random) {
        anchorX += driftX + BurrowAmbience.spread(random, SCRATCH_JITTER);
        anchorZ += driftZ + BurrowAmbience.spread(random, SCRATCH_JITTER);
        surfaceX += driftX;
        surfaceZ += driftZ;

        float pitch = SCRATCH_MIN_PITCH + random.nextFloat() * SCRATCH_PITCH_SPREAD;
        SoundEvent stroke = ModSounds.BURROW_SCRATCH.get();
        level.playLocalSound(anchorX, anchorY, anchorZ, stroke,
                SoundSource.AMBIENT, SCRATCH_VOLUME, pitch, false);
        level.playLocalSound(anchorX, anchorY, anchorZ, stroke,
                SoundSource.AMBIENT, GRIT_VOLUME, pitch * GRIT_PITCH_FACTOR, false);

        soilShower(level, random);
    }

    /**
     * A few motes off the ceiling above the surface point, in its own colour.
     *
     * <p>The column is probed from the surface rather than from the anchor,
     * because the anchor is inside solid earth and a particle spawned there is
     * one nobody will ever see. Once the drift has carried the surface point
     * into a wall the probe returns nothing and the burst finishes on sound
     * alone, which is correct - there is no ceiling over that spot to shed
     * anything.</p>
     *
     * <p>Same particle as the ambient motes in {@link BurrowAmbience}, for the
     * same reason: {@code FALLING_DUST} takes its tint from the block state it
     * is handed, so what falls is the colour of the ceiling it fell off.</p>
     */
    private static void soilShower(ClientLevel level, RandomSource random) {
        BlockPos ceiling = BurrowAmbience.probeCeiling(level, surfaceX, surfaceY, surfaceZ);
        if (ceiling == null) {
            return;
        }
        BlockParticleOption dust =
                new BlockParticleOption(ParticleTypes.FALLING_DUST, level.getBlockState(ceiling));
        int motes = SHOWER_MIN + random.nextInt(SHOWER_SPREAD);
        for (int mote = 0; mote < motes; mote++) {
            level.addParticle(
                    dust,
                    ceiling.getX() + random.nextDouble(),
                    ceiling.getY() - SHOWER_DROP_GAP - random.nextDouble() * 0.5,
                    ceiling.getZ() + random.nextDouble(),
                    0.0,
                    0.0,
                    0.0);
        }
    }

    /** The three blocks a corridor is cut through, and so the three a mole digs. */
    private static boolean isLining(BlockState state) {
        return state.is(ModBlocks.LOOSE_SOIL.get())
                || state.is(ModBlocks.DEEP_EARTH.get())
                || state.is(ModBlocks.ROOT_NODULE.get());
    }
}

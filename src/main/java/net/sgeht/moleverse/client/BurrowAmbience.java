package net.sgeht.moleverse.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.registry.ModBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * What the burrow sounds and looks like when nothing is happening in it.
 *
 * <p>The burrow is solid {@code deep_earth} with corridors cut out of it, no sky
 * and one light source. Left alone that renders as an unlit box: the walls are
 * still and the air is empty. The layers here - drifting motes, a drip off a
 * named ceiling block, spores under the glow mycelium and a sparse sound every
 * twenty seconds or so - exist to say that the space is inside something rather
 * than merely dark.</p>
 *
 * <p>All of them are weather: a roll per tick, no memory, nothing a player is
 * meant to look at directly. The one thing down here that is an event rather
 * than weather lives next door in {@link BurrowScratching}, which this class
 * drives but does not tune.</p>
 *
 * <p>The third layer, the brown distance, used to live here too and no longer
 * does. It is four attributes on the {@code moleverse:burrow} biome now, which
 * is where a colour belongs. One of them looks like a mistake and is not: the
 * biome's {@code visual/sky_color} repeats its {@code visual/fog_color} rather
 * than staying black, because {@code AtmosphericFogEnvironment.getBaseColor}
 * lerps the fog colour towards the sky colour by an amount that falls as the
 * render distance rises - about a sixth of the way at twelve chunks. Repeating
 * the colour makes that lerp a no-op, so the distance is the same brown at
 * every render distance. A dimension with a ceiling and {@code skybox: none}
 * draws no sky, so nothing else reads the attribute. The burrow used to inherit
 * {@code minecraft:deep_dark}, whose sky colour is a pale blue, and that lerp is
 * what washed the brown towards mauve and is why this class overrode it.</p>
 *
 * <h2>Sparse on purpose</h2>
 *
 * <p>Every effect is gated on a die roll per client tick, not on a timer that
 * fills the air. Roughly one mote per ten ticks and one drip per four seconds is
 * the whole budget: at any moment two or three specks are in flight. That is
 * enough to read as movement in the corner of the eye and little enough that
 * nothing ever stands between the player and the corridor. Anything denser turns
 * atmosphere into weather, and weather in a tunnel is a bug.</p>
 *
 * <p>A lit chamber is exempt. Above {@link #REFUGE_LIGHT} block light the motes
 * and drips stop entirely, so a room somebody has hung lanterns in reads as dry
 * and finished while the corridor outside keeps crumbling. The sound layer is
 * left running there, because it is what places the room inside the earth. The
 * gate is on light rather than on the player standing still: standing still is
 * exactly when the atmosphere is looked at, so suppressing it then would remove
 * the effect from the only moment it is visible.</p>
 *
 * <p>The spores are exempt from the exemption. They come off the glow mycelium,
 * which is the thing making the light in the first place, so gating them on
 * light would switch them off exactly where their source is.</p>
 *
 * <h2>Cost</h2>
 *
 * <p>{@link #tick} leaves on a reference comparison when the player is anywhere
 * else. Inside the burrow the common path is three die rolls, one decrement in
 * {@link BurrowScratching} and a return; the probes that do run walk at most
 * {@link #CEILING_REACH} block lookups up a single column, or
 * {@link #SPORE_PROBES} single lookups, and only after their gate has already
 * fired. Nothing here is held between ticks except one countdown.</p>
 *
 * <p>Client only, and it stays that way - there is nothing in an ambience the
 * server needs to agree with, so nothing is sent and nothing is asked. Two
 * players in the same corridor see different specks, which is correct: so do two
 * people in the same room.</p>
 *
 * <h2>The rates are not final</h2>
 *
 * <p>Every rate, radius and delay below is a mutable static rather than a
 * constant, so that {@code /moleverse burrow panel} can move it while the corridor
 * is in view - see {@code client.debug.BurrowTunePanel}. Nothing here is held
 * between ticks except the countdown, so a slider takes effect on the next roll
 * and there is nothing to rebuild. <b>The value written here is the shipped one.</b>
 * The panel never writes back: a number settled at the slider is baked in by
 * editing this file.</p>
 */
public final class BurrowAmbience {

    /** Average ticks between two drifting motes. One roll per tick against this. */
    public static int MOTE_ONE_IN = 10;

    /** Average ticks between two ceiling drips. Rarer than the motes by design. */
    public static int DRIP_ONE_IN = 80;

    /**
     * Average ticks between two attempts at a spore, and darts per attempt.
     *
     * <p>Read these two together with {@link #SPORE_RADIUS}: an attempt throws
     * {@link #SPORE_PROBES} darts into the box around the player and produces at
     * most one spore, so the actual rate is the dart rate times the share of
     * that box that is glow mycelium. That is the whole trick. A corridor with
     * one patch overhead gets a mote every few seconds, a chamber lined with the
     * stuff gets a slow steady fall, and bare earth costs three block lookups a
     * few times a second and produces nothing. No search, no cache, and the
     * effect is automatically "near the mycelium" without ever asking where the
     * mycelium is.</p>
     *
     * <p>Low on purpose beyond that. A spore lives 500 to 1000 ticks - vanilla's
     * number, not ours - so one per six seconds still leaves half a dozen of
     * them hanging in the air at any moment, which is already at the top of what
     * "occasional" can mean.</p>
     */
    public static int SPORE_ONE_IN = 6;
    public static int SPORE_PROBES = 3;

    /**
     * Half the edge of the box the spore darts land in.
     *
     * <p>Small, and that is the tuning knob that matters most: the dart hit rate
     * falls with the cube of this, so widening it to catch a distant patch also
     * thins out the near one. Three and a half blocks is about a corridor's
     * width, which is the range at which a mote under a ceiling patch is still a
     * mote rather than a lit pixel.</p>
     */
    public static double SPORE_RADIUS = 3.5;

    /** How far from the player a mote may appear. Corridors are five blocks wide. */
    public static double MOTE_RADIUS = 7.0;

    /**
     * How far from the player a drip may appear.
     *
     * <p>Smaller than {@link #MOTE_RADIUS}: a drip is a single slow particle that
     * only reads if the ceiling it hangs from is identifiable, and past a few
     * blocks it is one pixel of blue.</p>
     */
    public static double DRIP_RADIUS = 4.0;

    /**
     * How far up a column is searched for a ceiling.
     *
     * <p>Corridors are six blocks high and chambers nine, so this reaches the
     * ceiling from anywhere a player can stand, and gives up rather than scanning
     * to the build limit when it is pointed into a shaft.</p>
     */
    public static int CEILING_REACH = 10;

    /** Distance below the ceiling block a mote starts, before a random block on top. */
    public static double MOTE_DROP_GAP = 0.2;

    /** Block light at eye level from which the motes and drips stop. */
    public static int REFUGE_LIGHT = 12;

    /** Shortest and longest gap between two ambient sounds, in ticks. */
    public static int SOUND_MIN_DELAY = 300;
    public static int SOUND_DELAY_SPREAD = 260;

    /**
     * Where an ambient sound is placed, relative to the player.
     *
     * <p>Deliberately not checked against the corridor: a settling sound belongs
     * behind the wall, in earth the player cannot reach. Past ten blocks the
     * client delays the sound by its travel time, which is the whole of the
     * "distant" cue and costs nothing.</p>
     */
    public static double SOUND_MIN_DISTANCE = 7.0;
    public static double SOUND_DISTANCE_SPREAD = 9.0;
    public static double SOUND_VERTICAL_SPREAD = 3.0;

    /** Ticks until the next ambient sound; negative while the player is elsewhere. */
    private static int soundCountdown = -1;

    private BurrowAmbience() {
    }

    /**
     * One client tick of burrow ambience. Call from {@code ClientTickEvent.Post}.
     *
     * <p>Returns on the first line for every player who is not in the burrow, which
     * is nearly all of them nearly all of the time.</p>
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !ModDimensions.isBurrow(level)) {
            // Disarm, so that walking back in does not fire a sound on the first tick.
            soundCountdown = -1;
            BurrowScratching.disarm();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        RandomSource random = level.random;
        ambientSound(level, player, random);
        BurrowScratching.tick(level, player, random);

        if (random.nextInt(SPORE_ONE_IN) == 0) {
            sporeMote(level, player, random);
        }

        boolean wantMote = random.nextInt(MOTE_ONE_IN) == 0;
        boolean wantDrip = random.nextInt(DRIP_ONE_IN) == 0;
        if (!wantMote && !wantDrip) {
            return;
        }
        if (level.getBrightness(LightLayer.BLOCK, player.blockPosition()) >= REFUGE_LIGHT) {
            return;
        }
        if (wantMote) {
            driftingMote(level, player, random);
        }
        if (wantDrip) {
            ceilingDrip(level, player, random);
        }
    }

    /**
     * A speck of the ceiling coming loose and falling through the corridor.
     *
     * <p>{@link net.minecraft.core.particles.ParticleTypes#FALLING_DUST} rather than
     * one of the other candidates because it is the only one whose colour is read
     * off a block state - the mote is literally the colour of the block it fell
     * from, {@code MapColor.DIRT} under plain earth and podzol under a root beam,
     * and it stays right if those blocks are ever retextured. It also accelerates
     * downwards and caps its speed, which is what a crumb of soil does.
     * {@code WHITE_ASH} and {@code ASH} are basalt-delta particles that drift
     * sideways and read as open air above a fire, {@code SPORE_BLOSSOM_AIR} hangs
     * for most of a minute on almost no gravity, and {@code MYCELIUM} barely moves
     * at all, so none of them can say "something fell". The spore is the right
     * particle for something growing, which is what {@link #sporeMote} uses it
     * for.</p>
     *
     * <p>The velocity handed to {@code addParticle} is ignored for this type - the
     * provider spends those three arguments on the tint - so it is passed as zero
     * and gravity does the rest.</p>
     */
    private static void driftingMote(ClientLevel level, LocalPlayer player, RandomSource random) {
        double x = player.getX() + spread(random, MOTE_RADIUS);
        double z = player.getZ() + spread(random, MOTE_RADIUS);
        BlockPos ceiling = probeCeiling(level, x, player.getEyeY(), z);
        if (ceiling == null) {
            return;
        }
        BlockState state = level.getBlockState(ceiling);
        double y = ceiling.getY() - MOTE_DROP_GAP - random.nextDouble();
        level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y, z, 0.0, 0.0, 0.0);
    }

    /**
     * A drop or a crumb hanging off a named ceiling block, then falling.
     *
     * <p>Only {@code glow_mycelium} and {@code root_beam} produce one, so the effect
     * points at the two things on a corridor ceiling that are worth looking at
     * instead of decorating the earth evenly. The mycelium is wet - it is built
     * with {@code SoundType.SLIME_BLOCK} and lights the place - so it gets
     * {@code DRIPPING_WATER}, which hangs for two seconds and then spawns its own
     * falling particle without any further help from here. A root is dry, so it
     * sheds dust in its own colour.</p>
     */
    private static void ceilingDrip(ClientLevel level, LocalPlayer player, RandomSource random) {
        double probeX = player.getX() + spread(random, DRIP_RADIUS);
        double probeZ = player.getZ() + spread(random, DRIP_RADIUS);
        BlockPos ceiling = probeCeiling(level, probeX, player.getEyeY(), probeZ);
        if (ceiling == null) {
            return;
        }
        BlockState state = level.getBlockState(ceiling);
        ParticleOptions particle;
        if (state.is(ModBlocks.GLOW_MYCELIUM.get())) {
            particle = ParticleTypes.DRIPPING_WATER;
        } else if (state.is(ModBlocks.ROOT_BEAM.get())) {
            particle = new BlockParticleOption(ParticleTypes.FALLING_DUST, state);
        } else {
            return;
        }
        // On the underside of the block face, not on its edge: a drop on the corner
        // of a cube looks like a rendering fault rather than like water.
        double x = ceiling.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = ceiling.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(particle, x, ceiling.getY() - 0.1, z, 0.0, 0.0, 0.0);
    }

    /**
     * A spore leaving the glow mycelium, if any of a few darts happens to hit some.
     *
     * <p>{@link net.minecraft.core.particles.ParticleTypes#SPORE_BLOSSOM_AIR} is
     * the right one and its name is misleading: the provider tints it
     * {@code (0.32, 0.5, 0.22)}, a muted green, and it is the crimson spore next
     * to it in the registry that is pink. It carries almost no gravity, lives
     * most of a minute, and vanilla caps the whole type at a thousand particles
     * at once, so a patch of mycelium sheds a slow steady fall that cannot run
     * away no matter what the rate above is set to. Its own registry neighbours
     * are all worse here: {@code MYCELIUM} is grey and barely moves,
     * {@code FALLING_SPORE_BLOSSOM} is a drip that lands and splashes, and
     * {@code WARPED_SPORE} is dark blue and sized down to nearly nothing.</p>
     *
     * <p>Only mycelium with air under it produces anything, and one spore is
     * enough per attempt - the loop returns on the first hit rather than
     * emptying a patch's worth of darts into the same ceiling.</p>
     */
    private static void sporeMote(ClientLevel level, LocalPlayer player, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int probe = 0; probe < SPORE_PROBES; probe++) {
            cursor.set(
                    Mth.floor(player.getX() + spread(random, SPORE_RADIUS)),
                    Mth.floor(player.getEyeY() + spread(random, SPORE_RADIUS)),
                    Mth.floor(player.getZ() + spread(random, SPORE_RADIUS)));
            if (!level.getBlockState(cursor).is(ModBlocks.GLOW_MYCELIUM.get())) {
                continue;
            }
            cursor.move(Direction.DOWN);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            level.addParticle(
                    ParticleTypes.SPORE_BLOSSOM_AIR,
                    cursor.getX() + random.nextDouble(),
                    cursor.getY() + 0.9,
                    cursor.getZ() + random.nextDouble(),
                    0.0,
                    0.0,
                    0.0);
            return;
        }
    }

    /**
     * Every twenty-odd seconds, one sound from somewhere in the earth around.
     *
     * <p>All four are vanilla events, so no sound file is added and no entry in
     * {@code ModSounds} is needed. Rooted dirt and deepslate breaking, pitched
     * down to half, stop being "a block broke" and become a shift in the ground
     * and a knock somewhere in the rock - the pitch shift is doing the work, the
     * unpitched sounds are far too crisp and immediate. The dripstone drip is the
     * trickle, at its own pitch. {@code AMBIENT_CAVE} is in the set but at one
     * roll in ten, because it is the loudest and most recognisable sound in the
     * game's vocabulary and one per two minutes is already generous; vanilla will
     * hardly ever play it here itself, since the biome's mood counter needs a dark
     * spot and the corridors are lit.</p>
     *
     * <p>{@link SoundSource#AMBIENT} rather than {@code BLOCKS}, so the whole layer
     * sits under the slider a player would look for when it is too much.</p>
     */
    private static void ambientSound(ClientLevel level, LocalPlayer player, RandomSource random) {
        if (soundCountdown < 0) {
            // First tick after arriving. A full delay, so the sound never lands on
            // top of whatever brought the player down here.
            soundCountdown = SOUND_MIN_DELAY + random.nextInt(SOUND_DELAY_SPREAD);
            return;
        }
        if (--soundCountdown > 0) {
            return;
        }
        soundCountdown = SOUND_MIN_DELAY + random.nextInt(SOUND_DELAY_SPREAD);

        SoundEvent sound;
        float volume;
        float pitch;
        int roll = random.nextInt(10);
        if (roll < 4) {
            sound = SoundEvents.ROOTED_DIRT_BREAK;
            volume = 0.30F;
            pitch = 0.50F + random.nextFloat() * 0.15F;
        } else if (roll < 7) {
            sound = SoundEvents.DEEPSLATE_BREAK;
            volume = 0.22F;
            pitch = 0.45F + random.nextFloat() * 0.15F;
        } else if (roll < 9) {
            sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
            volume = 0.35F;
            pitch = 0.80F + random.nextFloat() * 0.40F;
        } else {
            sound = SoundEvents.AMBIENT_CAVE.value();
            volume = 0.25F;
            pitch = 0.80F + random.nextFloat() * 0.20F;
        }

        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = SOUND_MIN_DISTANCE + random.nextDouble() * SOUND_DISTANCE_SPREAD;
        level.playLocalSound(
                player.getX() + Math.cos(angle) * distance,
                player.getEyeY() + spread(random, SOUND_VERTICAL_SPREAD),
                player.getZ() + Math.sin(angle) * distance,
                sound,
                SoundSource.AMBIENT,
                volume,
                pitch,
                true);
    }

    /**
     * The first block above {@code fromY} in the column through {@code (x, z)}, or
     * null when that starting point is inside a block or the column stays open past
     * {@link #CEILING_REACH}.
     *
     * <p>The test on the starting point is what keeps the effects inside the
     * corridor: most of a sphere around the player is solid earth, and a mote
     * spawned in there is a particle nobody will ever see. Every caller here
     * starts at eye level; {@link BurrowScratching} starts at the height its own
     * ray happened to run at, which is the only reason this takes the height as
     * an argument at all.</p>
     */
    static @Nullable BlockPos probeCeiling(ClientLevel level, double x, double fromY, double z) {
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));
        if (!level.getBlockState(cursor).isAir()) {
            return null;
        }
        for (int step = 0; step < CEILING_REACH; step++) {
            cursor.move(Direction.UP);
            if (!level.getBlockState(cursor).isAir()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** A random offset in {@code [-radius, radius]}. */
    static double spread(RandomSource random, double radius) {
        return (random.nextDouble() * 2.0 - 1.0) * radius;
    }
}

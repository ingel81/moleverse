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
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.registry.ModBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * What the burrow sounds and looks like when nothing is happening in it.
 *
 * <p>The burrow is solid {@code deep_earth} with corridors cut out of it, no sky
 * and one light source. Left alone that renders as an unlit box: the walls are
 * still, the air is empty and the distance is black. The three layers here -
 * drifting motes, a sparse sound every twenty seconds or so, and brown fog -
 * exist to say that the space is inside something rather than merely dark.</p>
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
 * <h2>Cost</h2>
 *
 * <p>{@link #tick} leaves on a reference comparison when the player is anywhere
 * else. Inside the burrow the common path is two die rolls and a return; the two
 * probes that do run walk at most {@link #CEILING_REACH} block lookups up a
 * single column, and only after their gate has already fired. Nothing here is
 * held between ticks except one countdown.</p>
 *
 * <p>Client only, and it stays that way - there is nothing in an ambience the
 * server needs to agree with, so nothing is sent and nothing is asked. Two
 * players in the same corridor see different specks, which is correct: so do two
 * people in the same room.</p>
 */
public final class BurrowAmbience {

    /** Average ticks between two drifting motes. One roll per tick against this. */
    private static final int MOTE_ONE_IN = 10;

    /** Average ticks between two ceiling drips. Rarer than the motes by design. */
    private static final int DRIP_ONE_IN = 80;

    /** How far from the player a mote may appear. Corridors are five blocks wide. */
    private static final double MOTE_RADIUS = 7.0;

    /**
     * How far from the player a drip may appear.
     *
     * <p>Smaller than {@link #MOTE_RADIUS}: a drip is a single slow particle that
     * only reads if the ceiling it hangs from is identifiable, and past a few
     * blocks it is one pixel of blue.</p>
     */
    private static final double DRIP_RADIUS = 4.0;

    /**
     * How far up a column is searched for a ceiling.
     *
     * <p>Corridors are six blocks high and chambers nine, so this reaches the
     * ceiling from anywhere a player can stand, and gives up rather than scanning
     * to the build limit when it is pointed into a shaft.</p>
     */
    private static final int CEILING_REACH = 10;

    /** Distance below the ceiling block a mote starts, before a random block on top. */
    private static final double MOTE_DROP_GAP = 0.2;

    /** Block light at eye level from which the motes and drips stop. */
    private static final int REFUGE_LIGHT = 12;

    /** Shortest and longest gap between two ambient sounds, in ticks. */
    private static final int SOUND_MIN_DELAY = 300;
    private static final int SOUND_DELAY_SPREAD = 260;

    /**
     * Where an ambient sound is placed, relative to the player.
     *
     * <p>Deliberately not checked against the corridor: a settling sound belongs
     * behind the wall, in earth the player cannot reach. Past ten blocks the
     * client delays the sound by its travel time, which is the whole of the
     * "distant" cue and costs nothing.</p>
     */
    private static final double SOUND_MIN_DISTANCE = 7.0;
    private static final double SOUND_DISTANCE_SPREAD = 9.0;
    private static final double SOUND_VERTICAL_SPREAD = 3.0;

    /**
     * The colour of the burrow's distance: warm, dark, brown.
     *
     * <p>The dimension has a fixed time, no sky and {@code sky_light_level: 0.0},
     * so vanilla's own inputs to the fog colour - daylight, sky tint, void
     * darkness - carry no information here and the result is very nearly black.
     * There is nothing worth preserving in it, so it is replaced outright.</p>
     */
    private static final float FOG_RED = 0.190F;
    private static final float FOG_GREEN = 0.135F;
    private static final float FOG_BLUE = 0.095F;

    /**
     * How hard a bright incoming fog colour lifts the brown.
     *
     * <p>Night vision brightens the fog colour before the event fires. Setting the
     * brown flat would throw that away and leave a black wall around a lit world,
     * so the incoming brightness is kept as a multiplier instead.</p>
     */
    private static final float FOG_LIFT = 3.0F;

    /** Where the brown starts and where it is total, in blocks. */
    private static final float FOG_START = 5.0F;
    private static final float FOG_END = 26.0F;

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
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        RandomSource random = level.random;
        ambientSound(level, player, random);

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
     * sideways and read as open air above a fire, {@code SPORE_BLOSSOM_AIR} is pink
     * and lit and belongs to lush caves, and {@code MYCELIUM} barely moves at all,
     * so none of them can say "something fell".</p>
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
     * The first block above eye level in the column through {@code (x, z)}, or null
     * when the column is walled in at eye level or open past {@link #CEILING_REACH}.
     *
     * <p>The eye-level test is what keeps the effects inside the corridor: most of
     * a sphere around the player is solid earth, and a mote spawned in there is a
     * particle nobody will ever see.</p>
     */
    private static @Nullable BlockPos probeCeiling(ClientLevel level, double x, double eyeY, double z) {
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(eyeY), Mth.floor(z));
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
    private static double spread(RandomSource random, double radius) {
        return (random.nextDouble() * 2.0 - 1.0) * radius;
    }

    /**
     * Paints the burrow's distance brown. Subscribe on {@code NeoForge.EVENT_BUS}.
     *
     * <p>Fired once per frame from {@code FogRenderer.computeFogColor}, for every
     * dimension, so the guard is the first thing it does.</p>
     */
    public static void onComputeFogColour(ViewportEvent.ComputeFogColor event) {
        if (!inBurrow(event)) {
            return;
        }
        float lift = 1.0F + FOG_LIFT * Math.max(event.getRed(), Math.max(event.getGreen(), event.getBlue()));
        event.setRed(Math.min(FOG_RED * lift, 1.0F));
        event.setGreen(Math.min(FOG_GREEN * lift, 1.0F));
        event.setBlue(Math.min(FOG_BLUE * lift, 1.0F));
    }

    /**
     * Brings the distance in close. Subscribe on {@code NeoForge.EVENT_BUS}.
     *
     * <p>Only the atmospheric fog is touched. Water, lava and powder snow arrive
     * through the same event with their own environment already applied, and
     * overwriting those would mean a bucket of water in the burrow looks like a
     * bug in this class.</p>
     *
     * <p>The numbers are read at the scale of the corridor, not of the player: at
     * a quarter of normal size a haze that closes at {@link #FOG_END} blocks is
     * felt as a hall fading out about a hundred metres away. It is short enough
     * that a long straight run disappears into the earth instead of ending at a
     * visible wall, and long enough that a nine-block chamber is never fogged.</p>
     */
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC || !inBurrow(event)) {
            return;
        }
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(FOG_END);
    }

    /** The camera's entity always has a level, and it is the one being rendered. */
    private static boolean inBurrow(ViewportEvent event) {
        return ModDimensions.isBurrow(event.getCamera().entity().level());
    }
}

package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sgeht.moleverse.entity.GreatWorm;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.entity.critter.Earthworm;
import net.sgeht.moleverse.entity.critter.SoilBeetle;
import net.sgeht.moleverse.entity.predator.Shrew;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * Puts animals into the burrow, because vanilla cannot.
 *
 * <h2>Why the biome spawner does not work down here</h2>
 *
 * <p>This class started as a way to stock great worms around a chamber and grew
 * into the dimension's whole population system, and the reason is worth writing
 * down because it is not a tuning problem and no amount of weight fixes it.</p>
 *
 * <p>{@code NaturalSpawner.getRandomPosWithin} picks a spawn position by taking
 * a random column in the chunk and then a random height in it:</p>
 *
 * <pre>
 *   int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
 *   int l = Mth.randomBetweenInclusive(level.random, level.getMinY(), k);
 * </pre>
 *
 * <p>The burrow is a solid box. {@code dimension_type/burrow.json} gives it
 * {@code min_y: 0} and {@code height: 256}, and the flat generator fills it with
 * bedrock at 0 and deep earth from 1 to 255 - so the surface heightmap reads 256
 * whether or not anything has been carved underneath, and that roll is uniform
 * over 258 possible heights. A feeding run is
 * {@link BurrowGeometry#CORRIDOR_HEIGHT} blocks tall. Six of 258 is
 * <strong>2.3%</strong>, and that is the best case - a column the corridor
 * actually passes through. Every other column in the burrow is solid top to
 * bottom and contributes nothing.</p>
 *
 * <p>The very first thing {@code spawnCategoryForPosition} does with that
 * position is {@code if (!blockstate.isRedstoneConductor(chunk, pos))}, and deep
 * earth is a full solid block, so 97.7% of rolls return immediately having
 * touched nothing. There is no error and no log line. A playtest with the
 * ambient weights at 60 and 28 produced one beetle and no grubs, which is
 * exactly what that arithmetic predicts.</p>
 *
 * <h2>What this does instead</h2>
 *
 * <p>It spawns on the runs themselves. A {@link BurrowLink}'s waypoints are the
 * centre line of a carved corridor - the same points {@code CorridorCarver}
 * walks to cut it, and the block below each one is its floor by construction -
 * so picking a waypoint is picking corridor air by definition rather than by
 * luck. The 2.3% becomes 100% and the whole problem disappears.</p>
 *
 * <p>It is a trickle, not a wave: one animal of a kind per pass, only while the
 * count near a player is under target, and only into the band between
 * {@link #SPAWN_MIN} and {@link #SPAWN_MAX} so nothing appears in somebody's
 * face. Nothing here overrides despawning - everything is created with
 * {@link EntitySpawnReason#NATURAL} and thins itself out the ordinary way when
 * the player leaves, which is what keeps a long session from silting up.</p>
 */
public final class BurrowLife {

    // --- stocking a chamber on arrival ---------------------------------------

    /** How far around an arrival great worms are placed. */
    private static final int RANGE = 48;

    /**
     * How far around it they are <em>counted</em>, which is further.
     *
     * <p>The two used to be one number and that broke the moment great worms
     * started travelling. A worm sets off down a run every few minutes and can be
     * a couple of hundred blocks away when a player next arrives; counted only
     * within {@link #RANGE} it is invisible to the cap, and the arrival stocks a
     * fresh pair on top of it. Repeat that over an evening's visits and the colony
     * fills with worms, with nothing in the log to say why.</p>
     *
     * <p>Wide enough to cover most of a journey and no wider: colonies sit
     * hundreds of overworld blocks apart, which is thousands down here, so this
     * cannot reach into a neighbour's corridors and count its worms against
     * ours.</p>
     */
    private static final int WORM_COUNT_RANGE = 160;

    /** At most this many within that range. A corridor with two in it is busy. */
    private static final int CAP = 2;

    /** Tries at finding air with a floor before giving up on a worm. */
    private static final int ATTEMPTS = 12;

    // --- the trickle ---------------------------------------------------------

    /**
     * Ticks between passes. Three seconds.
     *
     * <p>Slow enough that the scan below is free even with several players down
     * there, fast enough that walking into an empty gallery and turning round
     * finds it inhabited.</p>
     */
    private static final int POPULATION_INTERVAL = 60;

    /** How far around a player its own animals are counted, in burrow blocks. */
    private static final int POPULATION_RANGE = 48;

    /**
     * The band a new animal may appear in.
     *
     * <p>Sixteen is far enough that nothing materialises in view of a player
     * who is standing still - a corridor bends long before that - and forty
     * keeps it inside the stretch they are about to walk into rather than
     * somewhere they will never go. No line-of-sight test: at these distances
     * the geometry does the work, and a ray cast per candidate would cost more
     * than the whole rest of this pass.</p>
     */
    private static final int SPAWN_MIN = 16;
    private static final int SPAWN_MAX = 40;

    /**
     * How far out candidates are gathered at all.
     *
     * <p>The widest upper bound any lane below uses, and it exists so the walk
     * over the waypoints happens once per pass rather than once per creature.
     * Each lane then narrows the shared list to its own band - which is why the
     * distance test appears twice and only looks redundant.</p>
     */
    private static final int CANDIDATE_MAX = 48;

    /**
     * Blocks of clear air a spawn point needs around and above it.
     *
     * <p>One block on each side and two high, so a three by three by two space
     * with floor under the middle. That is not a guess, it is the largest
     * resident: a soil beetle is 0.6 wide with {@code Attributes.SCALE} at 4, so
     * its box is 2.4 across and reaches 1.2 either side of the point it stands
     * on - into the neighbouring column, diagonals included. Two high covers the
     * beetle and the grub at 1.6, the shrew at 1.58, and the great worm at 1.5.
     *
     * <p>This used to test one column, two high, which was the unscaled size and
     * fitted nothing that actually spawns here. A corridor is five wide and the
     * waypoints are its centre line, so the check passes almost everywhere and
     * earns its keep only at the ends of runs and the edges of junctions - which
     * is exactly where a worm used to appear inside a wall.</p>
     */
    private static final int CLEARANCE = 1;
    private static final int CLEARANCE_HEIGHT = 2;

    /**
     * How many of each there should be within {@link #POPULATION_RANGE}.
     *
     * <p>Worms outnumber beetles because a burrow is a worm's home and a
     * beetle's thoroughfare. Both are low: these are ambience, and the number
     * that reads as alive is far smaller than the number that reads as an
     * infestation.</p>
     */
    private static final int EARTHWORM_TARGET = 6;
    private static final int SOIL_BEETLE_TARGET = 4;

    /**
     * Shrews, which are not ambience and are counted much more tightly.
     *
     * <p>Two within a radius of 48 is one encounter at a time. The pack is what
     * makes it an encounter rather than a nuisance - a single shrew is a thing
     * you kill without stopping - so a lane that fires spawns a pair as often as
     * a lone one.</p>
     */
    private static final int SHREW_TARGET = 2;
    private static final int SHREW_PACK = 2;

    /**
     * How near a shrew may appear, in burrow blocks.
     *
     * <p>Further out than the ambient band. Nothing that bites should arrive
     * inside the distance a player can react in, and the vanilla spawner drew
     * the same line at 24 - {@code isRightDistanceToPlayerAndSpawnPoint} refuses
     * anything closer. Keeping the number is keeping a rule that was already
     * protecting players before this class took the job over.</p>
     */
    private static final int SHREW_MIN = 24;

    /**
     * Block light a shrew will accept at the spot it appears in.
     *
     * <p>This is the gate the vanilla monster category used to supply, moved
     * here because the category no longer gets to make the decision - see the
     * class note. It is deliberately not the dimension's own
     * {@code monster_spawn_light_level} of 0: pitch black is a smaller share of
     * the burrow than it looks, since glow mycelium at level 9 spills several
     * blocks, and a rule that only fires at absolute zero would trade one kind
     * of never-spawning for another. Three keeps the design - darkness is
     * danger, a lit corridor is safe - while leaving the unlit stretches
     * genuinely usable.</p>
     */
    private static final int SHREW_DARK = 3;

    /**
     * The great worm, which is a landmark rather than a population.
     *
     * <p>Two questions, two radii, and they are genuinely different questions -
     * which is why this is not one number with extra words.</p>
     *
     * <p><b>Is one near enough to be met?</b> {@link #GREAT_WORM_TARGET} within
     * {@link #GREAT_WORM_RANGE}. Wider than the critter radius because a great
     * worm four blocks long is worth walking towards, and because it roams: it
     * will not be where it was put.</p>
     *
     * <p><b>Does the colony already have its worms?</b> {@link #CAP} within
     * {@link #WORM_COUNT_RANGE} - the same ceiling and the same radius the
     * arrival path uses, so the two cannot disagree about what a full colony
     * looks like.</p>
     *
     * <p>Both have to say yes. The wide one is what stops the colony filling up:
     * without it a player walking a long run leaves the first worm behind, finds
     * nothing within 64, and is handed another every few minutes until the
     * corridors are full of them. The narrow one is what stops the wide one
     * being useless: a single worm 150 blocks away satisfies a colony census and
     * is never seen by anybody. The design is "a colony has one or two great
     * worms", not "every bubble gets one" - and the second one only ever appears
     * because a player is somewhere the first is not.</p>
     */
    private static final int GREAT_WORM_TARGET = 1;
    private static final int GREAT_WORM_RANGE = 64;

    /**
     * Where a great worm may appear, in burrow blocks.
     *
     * <p>Further out than anything else. It is four blocks long and fills a
     * corridor; having one resolve into view at sixteen blocks would read as it
     * being placed there, which is the one thing the arrival path already gets
     * accused of.</p>
     */
    private static final int GREAT_WORM_MIN = 24;
    private static final int GREAT_WORM_MAX = 48;

    /**
     * How far out, in <em>overworld</em> blocks, runs are gathered from.
     *
     * <p>{@link ColonyStore#linksNear} measures to a run's two ends, which are
     * mounds, and a long run can pass close to a player while both of its mounds
     * are far away. This is therefore deliberately much wider than
     * {@link #SPAWN_MAX} converted back up - it is a cheap prefilter over a list
     * of records, and the waypoint distances below are what actually decide.</p>
     */
    private static final int LINK_SEARCH_RADIUS = 96;

    private BurrowLife() {
    }

    /**
     * Keeps the corridors around every player populated.
     *
     * <p>Called once per burrow tick; it does its own interval. Cheap to call on
     * a tick it does nothing on, which is the common case.</p>
     */
    public static void tick(ServerLevel burrow) {
        if (burrow.getGameTime() % POPULATION_INTERVAL != 0 || burrow.players().isEmpty()) {
            return;
        }

        // The colonies live in the overworld: the burrow is a projection of the
        // mole network above it, and the runs are stored where the moles dug
        // them. Null while the server is still coming up, or in the odd world
        // that has no overworld.
        ServerLevel overworld = burrow.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        ColonyStore store = ColonyStore.get(overworld);

        for (ServerPlayer player : burrow.players()) {
            if (!player.isSpectator()) {
                populate(burrow, store, player);
            }
        }
    }

    /**
     * One pass for one player.
     *
     * <p>Counts before it looks for anywhere to put anything, because the
     * common case is that both kinds are at target and the walk over the
     * waypoints is the only part of this with a cost worth avoiding.</p>
     */
    private static void populate(ServerLevel burrow, ColonyStore store, ServerPlayer player) {
        int worms = count(burrow, player, Earthworm.class, POPULATION_RANGE);
        int beetles = count(burrow, player, SoilBeetle.class, POPULATION_RANGE);
        int shrews = count(burrow, player, Shrew.class, POPULATION_RANGE);
        boolean greatWorm = greatWormWanted(burrow, player);

        if (worms >= EARTHWORM_TARGET && beetles >= SOIL_BEETLE_TARGET
                && shrews >= SHREW_TARGET && !greatWorm) {
            return;
        }

        List<BlockPos> spots = corridorSpots(burrow, store, player);
        if (spots.isEmpty()) {
            return;
        }
        Vec3 eye = player.position();
        RandomSource random = burrow.getRandom();

        // One of each at most per pass. A shortfall of five worms fills over
        // fifteen seconds rather than arriving as a heap, which is the
        // difference between a corridor that has animals in it and a corridor
        // that just had animals put in it.
        if (worms < EARTHWORM_TARGET || beetles < SOIL_BEETLE_TARGET) {
            List<BlockPos> ambient = inBand(spots, eye, SPAWN_MIN, SPAWN_MAX);
            if (!ambient.isEmpty()) {
                if (worms < EARTHWORM_TARGET) {
                    release(burrow, ModEntities.EARTHWORM.get(), pick(random, ambient));
                }
                if (beetles < SOIL_BEETLE_TARGET) {
                    release(burrow, ModEntities.SOIL_BEETLE.get(), pick(random, ambient));
                }
            }
        }
        if (shrews < SHREW_TARGET) {
            releaseShrews(burrow, eye, spots, random);
        }
        if (greatWorm) {
            List<BlockPos> far = inBand(spots, eye, GREAT_WORM_MIN, GREAT_WORM_MAX);
            if (!far.isEmpty()) {
                release(burrow, ModEntities.GREAT_WORM.get(), pick(random, far));
            }
        }
    }

    /**
     * Whether this player is owed a great worm.
     *
     * <p>Both radii have to agree - see {@link #GREAT_WORM_TARGET} for why they
     * are asking different things. The narrow question is asked first because it
     * is the cheaper box and answers no far more often.</p>
     */
    private static boolean greatWormWanted(ServerLevel burrow, ServerPlayer player) {
        if (count(burrow, player, GreatWorm.class, GREAT_WORM_RANGE) >= GREAT_WORM_TARGET) {
            return false;
        }
        return count(burrow, player, GreatWorm.class, WORM_COUNT_RANGE) < CAP;
    }

    /** The subset of a candidate list inside one lane's distance band. */
    private static List<BlockPos> inBand(List<BlockPos> spots, Vec3 eye, int min, int max) {
        List<BlockPos> band = new ArrayList<>();
        for (BlockPos spot : spots) {
            double distance = spot.getCenter().distanceToSqr(eye);
            if (distance >= min * min && distance <= max * max) {
                band.add(spot);
            }
        }
        return band;
    }

    /**
     * A pack of shrews, further out than the ambient lane and only in the dark.
     *
     * <p>The two extra conditions are filtered here rather than in
     * {@link #corridorSpots} because the ambient lane wants neither of them and
     * pays for neither: the light lookup happens only on the passes where a
     * shrew is actually owed, which is rare, and only over the candidates that
     * already survived the distance test.</p>
     *
     * <p>The pack goes to one spot rather than one spot each. They are a group
     * that arrived together and they push apart on the first tick; scattering
     * them over the corridor would make two shrews rather than a pack of
     * two.</p>
     */
    private static void releaseShrews(ServerLevel burrow, Vec3 eye,
            List<BlockPos> spots, RandomSource random) {
        List<BlockPos> dark = new ArrayList<>();
        for (BlockPos spot : inBand(spots, eye, SHREW_MIN, SPAWN_MAX)) {
            if (burrow.getBrightness(LightLayer.BLOCK, spot) <= SHREW_DARK) {
                dark.add(spot);
            }
        }
        if (dark.isEmpty()) {
            return;
        }

        BlockPos den = pick(random, dark);
        int pack = 1 + random.nextInt(SHREW_PACK);
        for (int i = 0; i < pack; i++) {
            release(burrow, ModEntities.SHREW.get(), den);
        }
    }

    /**
     * Every waypoint near this player that something could stand on.
     *
     * <p>Waypoints rather than a search of the volume. This is the whole point
     * of the class - see the note at the top - and it is also why there is no
     * attempt limit here the way {@link #findRoom} has one: a failed candidate
     * costs three block lookups, and the list is short enough to walk in
     * full.</p>
     *
     * <p>Every point is still checked. A run in the ledger is not necessarily a
     * run in the ground: the reconciler carves lazily, chunk by chunk, so a link
     * near the player may be planned and not yet cut, or cut in the chunks
     * behind them and not the ones ahead.</p>
     */
    private static List<BlockPos> corridorSpots(ServerLevel burrow, ColonyStore store, ServerPlayer player) {
        BlockPos above = BurrowGeometry.toOverworld(player.blockPosition());
        Vec3 eye = player.position();
        List<BlockPos> spots = new ArrayList<>();

        for (BurrowLink link : store.linksNear(above, LINK_SEARCH_RADIUS)) {
            for (int index = 0; index < link.pointCount(); index++) {
                BlockPos point = CorridorCarver.burrowPoint(link, index);
                double distance = point.getCenter().distanceToSqr(eye);
                if (distance < SPAWN_MIN * SPAWN_MIN || distance > CANDIDATE_MAX * CANDIDATE_MAX) {
                    continue;
                }
                if (standable(burrow, point)) {
                    spots.add(point.immutable());
                }
            }
        }
        return spots;
    }

    private static BlockPos pick(RandomSource random, List<BlockPos> spots) {
        return spots.get(random.nextInt(spots.size()));
    }

    private static int count(ServerLevel burrow, ServerPlayer player, Class<? extends Mob> type, int range) {
        double size = range * 2.0;
        return burrow.getEntitiesOfClass(type, AABB.ofSize(player.position(), size, size, size)).size();
    }

    private static <T extends Mob> void release(ServerLevel burrow, EntityType<T> type, BlockPos spot) {
        // NATURAL and not one of the reasons that sets persistence: these are
        // meant to come and go, and the despawn is half of what keeps the
        // population where the targets say it should be.
        T mob = type.create(burrow, EntitySpawnReason.NATURAL);
        if (mob == null) {
            return;
        }
        Vec3 foot = spot.getBottomCenter();
        mob.snapTo(foot.x, foot.y, foot.z, burrow.getRandom().nextFloat() * 360.0F, 0.0F);
        burrow.addFreshEntity(mob);
    }

    // --- stocking a chamber ---------------------------------------------------

    /**
     * Stocks the corridors around a chamber with great worms, if they are not
     * stocked already.
     *
     * <p>Called after carving rather than during it: a worm placed in a corridor
     * that is still being dug would be pushed out of the wall it is standing
     * in.</p>
     *
     * <p>Kept alongside {@link #tick}'s great worm lane rather than replaced by
     * it, and the two do not fight: both count over {@link #WORM_COUNT_RANGE}
     * against the same {@link #CAP}, so whichever runs first satisfies the
     * other. This one is the arrival guarantee - a worm in the corridors the
     * moment somebody steps out of the chamber, with no three second wait - and
     * the lane in {@code tick} is what makes the animal's presence independent
     * of how the player got down here. Only this path used to exist, which is
     * why anyone arriving by ladder, command or creative flight met none.</p>
     *
     * <p>Counted over {@link #WORM_COUNT_RANGE} and placed within {@link #RANGE},
     * because a great worm no longer stays where it was put - see that constant
     * for what happens when the two are the same number.</p>
     */
    public static void stock(ServerLevel burrow, BlockPos chamber) {
        AABB around = AABB.ofSize(chamber.getCenter(),
                WORM_COUNT_RANGE * 2.0, 32.0, WORM_COUNT_RANGE * 2.0);
        List<GreatWorm> already = burrow.getEntitiesOfClass(GreatWorm.class, around);
        if (already.size() >= CAP) {
            return;
        }

        RandomSource random = burrow.getRandom();
        for (int placed = already.size(); placed < CAP; placed++) {
            BlockPos spot = findRoom(burrow, chamber, random);
            if (spot == null) {
                return;
            }
            release(burrow, ModEntities.GREAT_WORM.get(), spot);
        }
    }

    /**
     * A carved spot with a floor under it and room above.
     *
     * <p>Searched rather than computed, and it can stay that way: it runs once
     * per arrival inside a chamber that has just been carved, so unlike the
     * trickle it is looking at ground it knows is there. The waypoint trick
     * would not help here anyway - a chamber is a room, not a run.</p>
     */
    private static BlockPos findRoom(ServerLevel burrow, BlockPos chamber, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            cursor.set(
                    chamber.getX() + random.nextInt(RANGE * 2) - RANGE,
                    chamber.getY() + random.nextInt(9) - 4,
                    chamber.getZ() + random.nextInt(RANGE * 2) - RANGE);

            if (standable(burrow, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /**
     * Air to stand in, air above it, and something solid underneath.
     *
     * <p>Air with ground under it is carved corridor by definition - nothing
     * else down there is hollow - so this doubles as "is this actually dug
     * yet".</p>
     */
    private static boolean standable(ServerLevel burrow, BlockPos pos) {
        if (!burrow.isLoaded(pos) || burrow.getBlockState(pos.below()).isAir()) {
            return false;
        }

        // Sized to the widest thing this class places rather than to one block -
        // see CLEARANCE. Runs only on candidates that already passed the
        // distance test, which is what keeps eighteen lookups affordable.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -CLEARANCE; dx <= CLEARANCE; dx++) {
            for (int dz = -CLEARANCE; dz <= CLEARANCE; dz++) {
                for (int dy = 0; dy < CLEARANCE_HEIGHT; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!burrow.getBlockState(cursor).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

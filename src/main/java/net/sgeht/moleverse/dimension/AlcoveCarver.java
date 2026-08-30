package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.RunLevel;
import net.sgeht.moleverse.entity.critter.Grub;
import net.sgeht.moleverse.registry.ModBlocks;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * The store rooms budding off a colony's deep runs.
 *
 * <p>Moles keep <em>worm larders</em>: an earthworm bitten through the head
 * segments is paralysed rather than killed, and a cache of them - hundreds at a
 * time - keeps through a winter when the soil is too hard to hunt in. It is the
 * one thing a mole does that needs a room rather than a tunnel.</p>
 *
 * <p>{@link ChamberFurnisher} already cuts a larder as <em>furniture</em>: a niche
 * in a chamber wall with a face of packed worms at the back. This is the same
 * fact promoted to a room. A chamber is one per mound and a colony has a handful
 * of them; a larder alcove is one every {@link #SPACING} blocks along every deep
 * run, so finding one is something that happens while you are walking rather than
 * only when you arrive somewhere.</p>
 *
 * <h2>Deep runs only</h2>
 *
 * <p>{@link RunLevel#FEEDING} is the everyday run just under the turf - a mole
 * hunting, not a mole storing. The permanent runs are where a colony keeps things,
 * and restricting the larders to them means a player learns something by walking
 * one: the deep network is worth following.</p>
 *
 * <h2>The spacing is one run's worth</h2>
 *
 * <p>{@link #SPACING} is {@code MIN_EXIT_DISTANCE * SCALE}, the shortest run a mole
 * will ever dig expressed in burrow blocks - the same figure {@link Junctions} and
 * {@link LevelShafts} space themselves by. A run is between one and about one and a
 * third of that long today, so in practice every deep run gets exactly one larder
 * and no run can carry two. That is the bound worth having: it is a property of how
 * long a run is rather than a number somebody picked, and a colony that later digs
 * longer runs gets more larders without anybody retuning anything.</p>
 *
 * <h2>The alcove is a lobe, not a niche</h2>
 *
 * <p>A small domed room whose middle sits {@link #OFFSET} blocks to one side of the
 * corridor's centre line, so it overlaps the corridor's own bore by two or three
 * blocks and there is no doorway to cut and no wall to probe for. That overlap is
 * also why this needs no precondition: an alcove carved before its corridor is a
 * sealed pod, and the corridor opens straight into it when it arrives.</p>
 *
 * <p>It is deliberately a block shorter than a corridor. A store you duck into
 * reads as a store; and it keeps the alcove under
 * {@code CorridorProfile.MAX_LIT_HEIGHT}, so nothing about it confuses the height
 * contract that tells a junction from a corridor - see {@link Junctions}. What it
 * does do is widen the slice: {@code TunnelDecorator}'s sweep measures the span
 * across the run, finds it past {@code MAX_SPAN} here and stops, so the two or
 * three slices either side of a larder get no corridor dressing. That is the right
 * outcome rather than a cost - the alcove brings its own floor and its own lamp,
 * and a trodden corridor line laid across the mouth of a store room would fight
 * both.</p>
 *
 * <h2>Position derived, like everything down here</h2>
 *
 * <p>Which side the alcove buds off, which squares of the wall are packed with
 * worms, how much of the floor is mud: all hashes of block positions, none of them
 * drawn from a {@code RandomSource}. So a larder is the same store on the tenth
 * visit as on the first and comes out identical however many chunks cut it.</p>
 *
 * <p><strong>The worms themselves are blocks and nothing else.</strong> The plan
 * asks for a live worm or two inside; an entity is not idempotent and does not
 * belong to a chunk that may be reconciled a dozen times, which is the same line
 * {@code ChamberFeature} draws around the great worm and the way home. Stocking one
 * belongs with whoever owns an arrival.</p>
 */
public final class AlcoveCarver {

    /**
     * How wide the alcove is, as a radius.
     *
     * <p>Three, so it is seven across: a room you step into and turn round in,
     * against a chamber's thirteen. Any smaller and the worm faces are close enough
     * together to read as a single wall.</p>
     */
    private static final int RADIUS = 3;

    /**
     * And how tall.
     *
     * <p>Five, one short of a feeding corridor. See the class javadoc: it is what
     * makes the alcove read as a recess off the run rather than as a widening of
     * it.</p>
     */
    private static final int HEIGHT = 5;

    /** How many of the topmost layers curve inwards, so the alcove has a roof rather than a lid. */
    private static final int DOME = 2;

    /** How far the bottom layer is pulled in, so the wall meets the floor on a curve. */
    private static final int FILLET = 1;

    /**
     * How far the middle of the alcove sits from the corridor's centre line.
     *
     * <p>{@code CorridorProfile.WIDEST_RADIUS} plus the alcove's own radius, less
     * two. Those two blocks of overlap are the whole doorway: whatever section the
     * run was cut to, the two volumes share ground and the alcove opens into the
     * corridor without anything being cut through a wall.</p>
     *
     * <p>Two rather than one, and the second is not slack. A corridor's centre line
     * wanders a block off the straight and its radius can pinch to
     * {@code CorridorProfile.NARROWEST_RADIUS} - both at once, and both away from
     * the alcove - so a single block of nominal overlap can come out as no overlap
     * at all and leave a plug of soil across the mouth of the store.</p>
     */
    private static final int OFFSET = CorridorProfile.WIDEST_RADIUS + RADIUS - 2;

    /**
     * Blocks of run between two larders.
     *
     * <p>The shortest run a mole will ever dig, in burrow blocks. See the class
     * javadoc for why that is the right unit rather than a round number.</p>
     */
    private static final int SPACING = BurrowConstants.MIN_EXIT_DISTANCE * BurrowGeometry.SCALE;

    /**
     * How far a larder has to stay clear of either mound of its run.
     *
     * <p>A chamber's radius plus everything this class writes, so the two can never
     * touch. A chamber already has larders of its own in its walls, and an alcove cut
     * into its rim would be a second, worse answer laid over the first.</p>
     */
    private static final int MOUND_CLEARANCE = BurrowGeometry.CHAMBER_RADIUS + OFFSET + RADIUS;

    /** Chance a square of the alcove's wall is packed with worms. A cache is lumpy; a tiled wall of worms is a texture. */
    private static final float LARDER_DENSITY = 0.4F;

    /** Chance a square of the floor is mud rather than lining. Under one, so the floor has an edge instead of being a stamped disc. */
    private static final float MUD_DENSITY = 0.7F;

    /** Radius of the patch of light in the roof. Small: it is a lamp over a store, not a lit ceiling. */
    private static final int LAMP_RADIUS = 1;

    /** How far past everything the bounds reach, on {@code CorridorFeature}'s argument. */
    private static final int MARGIN = 2;

    /** Clients see the change and nothing else reacts, exactly as in {@link ChamberFurnisher}. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * Share of alcoves that come with a grub in them.
     *
     * <p>Half, hashed from where the alcove is, so which larders have gone bad is a
     * property of the place rather than of when a chunk happened to load. A player
     * who clears one and comes back finds it clear; the one two runs over is still
     * infested, every time.</p>
     */
    private static final float GRUB_CHANCE = 0.5F;

    // Salts. Distinct so that two decisions never agree by accident and land on the same block.
    private static final long SALT_SIDE = 0x0FEE_1000L;
    private static final long SALT_FACE = 0x0FEE_1001L;
    private static final long SALT_FLOOR = 0x0FEE_1002L;
    private static final long SALT_GRUB = 0x0FEE_1003L;

    private AlcoveCarver() {
    }

    /**
     * Where one larder goes, in burrow space.
     *
     * @param run   the run it buds off. Kept so that whoever holds a larder can say
     *              which run produced it without finding it a second time - the plan
     *              layer names its features after exactly that
     * @param index how many larders of this run lie behind it. With the run, this is
     *              the name: a run's length is fixed by its two mounds, so the index
     *              set is stable even when the run is re-dug through changed ground
     * @param x     middle of the alcove, offset to one side of the corridor
     * @param walkY the corridor's walking surface here, and the alcove's own floor.
     *              The block below it is never touched, exactly as in a corridor
     * @param z     the same as {@code x}
     */
    public record Larder(BurrowLink run, int index, int x, int walkY, int z) {

        /**
         * Every block cutting and stocking this larder can reach: the room, the ring
         * of worms outside its wall, and the soil lining around all of it.
         *
         * <p>{@code CorridorCarver.SHELL_MAX} in all six directions, the floor
         * included - a chunk is only asked about a feature whose bounds reach into
         * it, so a box drawn to the cut alone would leave the lining stopping at a
         * chunk border with deep earth showing beside it.</p>
         */
        public BoundingBox bounds() {
            int reach = RADIUS + 1 + CorridorCarver.SHELL_MAX;
            return new BoundingBox(
                    this.x - reach, this.walkY - 1 - CorridorCarver.SHELL_MAX, this.z - reach,
                    this.x + reach, this.walkY + HEIGHT + CorridorCarver.SHELL_MAX, this.z + reach)
                    .inflatedBy(MARGIN);
        }
    }

    /**
     * Where this run's larders belong, in order along it.
     *
     * <p>Pure arithmetic on the link - no level, no block reads, no world at all, so
     * the answer exists before any of the ground does. {@link Junctions#crossingsOf}
     * and {@link LevelShafts#crossingsOf} are the same seam.</p>
     *
     * <p>Empty for a feeding run, for a run with no length to measure, and for a run
     * short enough that every larder position would land in a chamber. All three are
     * decided from the link and nothing else, so the same run gives the same list
     * whoever asks and whether or not any ground is loaded.</p>
     */
    public static List<Larder> lardersOf(BurrowLink run) {
        if (run.level() == RunLevel.FEEDING || run.pointCount() < 2) {
            return List.of();
        }

        List<BlockPos> points = new ArrayList<>(run.pointCount());
        for (int i = 0; i < run.pointCount(); i++) {
            points.add(CorridorCarver.burrowPoint(run, i));
        }

        // Plan length, waypoint by waypoint. The vertical is left out on purpose:
        // "every so many blocks along the run" is a statement about the ground it
        // crosses, and a run that dips into a valley has not got longer.
        double[] reached = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            reached[i] = reached[i - 1] + planDistance(points.get(i - 1), points.get(i));
        }
        double length = reached[points.size() - 1];

        List<Larder> larders = new ArrayList<>();
        for (int index = 0; (index + 0.5) * SPACING < length; index++) {
            double along = (index + 0.5) * SPACING;
            if (along < MOUND_CLEARANCE || length - along < MOUND_CLEARANCE) {
                continue;
            }
            Larder larder = larderAt(run, index, points, reached, along);
            if (larder != null) {
                larders.add(larder);
            }
        }
        return List.copyOf(larders);
    }

    /**
     * One larder, at this distance along the run.
     *
     * <p>Null where the run has no bearing to bud sideways from, which is a run
     * climbing straight up - possible on a steep hillside, where the vertical scale
     * can put two waypoints in the same column. An alcove there has no side to be
     * on.</p>
     */
    private static @Nullable Larder larderAt(BurrowLink run, int index, List<BlockPos> points,
            double[] reached, double along) {
        int segment = points.size() - 2;
        while (segment > 0 && reached[segment] > along) {
            segment--;
        }

        BlockPos from = points.get(segment);
        BlockPos to = points.get(segment + 1);
        double span = reached[segment + 1] - reached[segment];
        double t = span <= 0.0 ? 0.0 : Math.clamp((along - reached[segment]) / span, 0.0, 1.0);

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearing = Math.sqrt(dx * dx + dz * dz);
        if (bearing <= 0.0) {
            return null;
        }

        int cx = from.getX() + (int) Math.round(dx * t);
        int cz = from.getZ() + (int) Math.round(dz * t);
        int walkY = from.getY() + (int) Math.round((to.getY() - from.getY()) * t);

        // Which side. Hashed from where the corridor is rather than from the index,
        // so two larders on one run do not both end up to the left of it and a run
        // that was re-dug through changed ground keeps whichever side its ground
        // gave it.
        int side = CorridorCarver.noise(SALT_SIDE, cx, walkY, cz) < 0.5F ? 1 : -1;
        double perpX = -dz / bearing * side;
        double perpZ = dx / bearing * side;

        return new Larder(run, index,
                cx + (int) Math.round(perpX * OFFSET), walkY, cz + (int) Math.round(perpZ * OFFSET));
    }

    // --- Cutting --------------------------------------------------------------

    /**
     * Opens the alcove, writing only inside {@code clamp}.
     *
     * <p>Null is the unbounded case. Every layer is decided from the arithmetic
     * before the box is consulted, so an alcove cut chunk by chunk is the same alcove
     * as one cut in a single call - and cutting it again costs reads and nothing
     * else, because {@link CorridorCarver#discAndShell} only ever clears ground and
     * lines earth.</p>
     *
     * <p>It does not wait for the corridor. The two volumes overlap by construction
     * (see the class javadoc), so an alcove cut into ground the carver has not
     * reached yet is a pod that the run opens into when it arrives, rather than a
     * sealed room beside it.</p>
     */
    public static void cut(ServerLevel burrow, Larder larder, @Nullable BoundingBox clamp) {
        int reach = RADIUS + CorridorCarver.SHELL_MAX;
        if (clamp != null && misses(clamp,
                larder.x() - reach, larder.walkY() - CorridorCarver.SHELL_MAX, larder.z() - reach,
                larder.x() + reach, larder.walkY() + HEIGHT + CorridorCarver.SHELL_MAX,
                larder.z() + reach)) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int layer = -CorridorCarver.SHELL_MAX; layer < HEIGHT + CorridorCarver.SHELL_MAX; layer++) {
            int away = layer < 0 ? -layer : Math.max(0, layer - (HEIGHT - 1));
            int nearest = Math.clamp(layer, 0, HEIGHT - 1);
            CorridorCarver.discAndShell(burrow, larder.x(), larder.walkY() + layer, larder.z(),
                    radiusAt(nearest), away, cursor, clamp);
        }
    }

    /**
     * How wide the alcove is cut at one layer: the wall, less the fillet at the foot
     * and less whatever the dome has taken.
     *
     * <p>No roughness. The chamber and the junction both break their domes up because
     * a smooth one at their size reads as turned; seven blocks across has no room for
     * a shape that varies, and what a bite would take out of it is the difference
     * between a store you can stand in and one you cannot.</p>
     */
    private static double radiusAt(int layer) {
        int span = RADIUS - Math.max(0, FILLET - layer);
        int flat = HEIGHT - DOME;
        if (layer < flat) {
            return span;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (DOME + 1.0), 0.0, 1.0);
        return Math.min(span, RADIUS * Math.sqrt(1.0 - t * t));
    }

    // --- Stocking -------------------------------------------------------------

    /**
     * Studs the alcove's walls with worms, muds its floor and lights its roof,
     * writing only inside {@code clamp}.
     *
     * <p>The second pass, and unlike a chamber's larders it is one for a different
     * reason: nothing here probes. {@code ChamberFurnisher.cutLarder} has to wait
     * because it cuts into a wall that a corridor may still be about to open, and it
     * validates fifty blocks before writing one. An alcove is already a room by the
     * time this runs and every square it touches is decided by arithmetic, so this
     * could in principle go in with the cut. It is held back anyway, because the
     * reconciler's two phases are what let a re-carved feature be re-dressed - see
     * {@code BurrowFeature.decorateWithin}.</p>
     *
     * <p>Everything goes through {@link #replaceGround}, which takes raw ground and
     * nothing else. That is what keeps the worms out of the corridor: the squares of
     * the ring that the run has opened are air, and air is refused.</p>
     */
    public static void stock(ServerLevel burrow, Larder larder, @Nullable BoundingBox clamp) {
        int cx = larder.x();
        int wy = larder.walkY();
        int cz = larder.z();
        int reach = RADIUS + 1;

        if (clamp != null && misses(clamp,
                cx - reach, wy - 1, cz - reach, cx + reach, wy + HEIGHT, cz + reach)) {
            return;
        }

        BlockState worms = ModBlocks.WORM_LARDER.get().defaultBlockState();
        BlockState mud = Blocks.MUD.defaultBlockState();
        BlockState soil = ModBlocks.LOOSE_SOIL.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!withinDisc(dx, dz, reach)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;

                // The floor. Mud because a larder is damp - worms kept alive in it -
                // and because it is the one material down here that says so without a
                // block of its own.
                boolean wet = CorridorCarver.noise(SALT_FLOOR, x, wy - 1, z) < MUD_DENSITY;
                replaceGround(burrow, cursor.set(x, wy - 1, z), wet ? mud : soil, clamp);

                // The wall: the band between what this layer was cut to and one block
                // past it. Decided rather than probed, on ChamberFurnisher's argument
                // - the alcove's radius is arithmetic, and a probe would have to ask
                // about blocks another chunk may not have written yet.
                for (int layer = 0; layer < HEIGHT; layer++) {
                    if (withinCut(dx, dz, radiusAt(layer))) {
                        continue;
                    }
                    int y = wy + layer;
                    if (CorridorCarver.noise(SALT_FACE, x, y, z) < LARDER_DENSITY) {
                        replaceGround(burrow, cursor.set(x, y, z), worms, clamp);
                    }
                }
            }
        }

        lightTheRoof(burrow, cx, wy, cz, cursor, clamp);
        stockGrub(burrow, larder, clamp);
    }

    /**
     * Puts a grub in the alcove, which is the only place in the burrow one can
     * live.
     *
     * <p>The grub is the larder's own vermin: it sits against a worm larder and
     * chews through it unless the larder is lit, which is what turns the burrow's
     * lighting from decoration into upkeep. It also leaves after
     * {@link Grub#GIVE_UP_TICKS} with nothing to eat in range - and that is what
     * broke the population. Grubs were spawned by the biome at random floor
     * positions, larders exist only in these alcoves and in the nest's trove, so
     * very nearly every grub that ever spawned found no larder and left. The first
     * playtest found none at all. A creature whose whole design is "lives at a
     * larder" has to be put at a larder; the spawner cannot know where one is.</p>
     *
     * <p><strong>Persistent, and that is the whole point.</strong>
     * {@code BurrowCritter} deliberately leaves {@code removeWhenFarAway} true so
     * that naturally spawned critters do not silt up a corridor. A stocked grub is
     * not one of those: it belongs to this alcove, this alcove is decorated exactly
     * once, and a grub that evaporated the moment the player walked away would never
     * be replaced - which is the bug again, one layer down. Its own timer still
     * works, because that is an explicit {@code discard} rather than a distance
     * check, and it never fires here: it has a larder in reach by construction.</p>
     *
     * <p><strong>Only the chunk that owns the middle spawns it.</strong> An alcove
     * is stocked once per chunk it overlaps, and an entity added this tick is not
     * yet visible to the count below - so without the clamp test four chunks would
     * each place one and the count would agree with all four. The count is still
     * there as the guard that matters, for the dev re-dress button, which runs this
     * pass again over a room that already has its grub.</p>
     *
     * <p>The alcove's own lamp does not make it safe, and that is deliberate. Threads
     * are light nine and {@link Grub#SAFE_LIGHT} is eight, so only the larders
     * against the lamp are out of reach; the rest of the wall is on a timer until
     * somebody brings a torch. That is the trade the creature exists to offer.</p>
     */
    private static void stockGrub(ServerLevel burrow, Larder larder, @Nullable BoundingBox clamp) {
        BlockPos spot = new BlockPos(larder.x(), larder.walkY(), larder.z());
        if (clamp != null && !clamp.isInside(spot)) {
            return;
        }
        if (CorridorCarver.noise(SALT_GRUB, spot.getX(), spot.getY(), spot.getZ()) >= GRUB_CHANCE) {
            return;
        }

        // The middle of the alcove is air over floor by construction, but a run cut
        // across it since is not, and neither is a room a player has filled in.
        if (!burrow.isLoaded(spot)
                || !burrow.getBlockState(spot).isAir()
                || !burrow.getBlockState(spot.above()).isAir()
                || burrow.getBlockState(spot.below()).isAir()) {
            return;
        }

        // The box the grub itself hunts in, so the question this asks is exactly
        // "is there already one this larder would serve". Alcoves are a whole run
        // apart, so no two of them can talk each other out of a grub.
        AABB around = AABB.ofSize(spot.getCenter(),
                Grub.SEARCH_RANGE * 2.0, Grub.SEARCH_HEIGHT * 2.0, Grub.SEARCH_RANGE * 2.0);
        if (!burrow.getEntitiesOfClass(Grub.class, around).isEmpty()) {
            return;
        }

        Grub grub = ModEntities.GRUB.get().create(burrow, EntitySpawnReason.NATURAL);
        if (grub == null) {
            return;
        }
        // The bearing is the one thing here drawn from a stream rather than hashed.
        // Which way a grub happens to be facing is not a property of the place and
        // nothing measures it.
        grub.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                burrow.getRandom().nextFloat() * 360.0F, 0.0F);
        grub.setPersistenceRequired();
        burrow.addFreshEntity(grub);
    }

    /**
     * Puts a lamp in the roof of the alcove.
     *
     * <p>A store nobody can see is a store nobody finds, and a lit recess off a dark
     * corridor is what draws a player across to look - the same job the single lamp
     * in {@code ChamberFurnisher.cutLarder} does. The middle block is lit whatever
     * the dice say, because a larder whose light rolled empty would be an unlit store
     * beside an undressed stretch of corridor, which is nothing at all.</p>
     */
    private static void lightTheRoof(ServerLevel burrow, int cx, int wy, int cz,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        int y = wy + HEIGHT;

        for (int dx = -LAMP_RADIUS; dx <= LAMP_RADIUS; dx++) {
            for (int dz = -LAMP_RADIUS; dz <= LAMP_RADIUS; dz++) {
                if (withinDisc(dx, dz, LAMP_RADIUS)) {
                    replaceGround(burrow, cursor.set(cx + dx, y, cz + dz), glow, clamp);
                }
            }
        }
    }

    // --- Placement ------------------------------------------------------------

    /**
     * Turns raw ground into something.
     *
     * <p>{@code ChamberFurnisher.replaceEarth}'s rule and its whole argument: raw
     * ground and nothing else, so this cannot touch air a corridor opened, a
     * decoration that has made its choice, or a block anybody built with - and
     * calling {@link #stock} again finds its own work already done.</p>
     *
     * <p>Deliberately <em>not</em> {@link ModBlocks#ROOT_NODULE}. The alcove is cut
     * with {@link CorridorCarver#discAndShell}, so its walls are lined by
     * {@code CorridorCarver.line} like every other surface down here and carry
     * nodules at the same ambient rate. Studding worms over one would make whether a
     * block is a nodule depend on what was built around it instead of on where it
     * is, which is the one property the pocket mechanic rests on. A nodule in this
     * wall simply gets no worms.</p>
     */
    private static boolean replaceGround(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if ((clamp != null && !clamp.isInside(pos)) || !burrow.isLoaded(pos)) {
            return false;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!existing.is(ModBlocks.DEEP_EARTH.get()) && !existing.is(ModBlocks.LOOSE_SOIL.get())) {
            return false;
        }
        if (existing == state) {
            return true;
        }
        return burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    // --- Arithmetic -----------------------------------------------------------

    private static double planDistance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The integer disc the whole dimension is cut with, for the reason {@code CorridorCarver} gives. */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return radius >= 0 && dx * dx + dz * dz <= radius * radius + radius;
    }

    /**
     * The same for a real radius, which is what the cut takes.
     *
     * <p>It has to be the same test {@link CorridorCarver#discAndShell} makes, and
     * not a rounded version of it: this is what tells the wall from the room, and a
     * band that started a block inside the cut would pack worms into air while one
     * that started a block outside would bury the visible face in lining.</p>
     */
    private static boolean withinCut(int dx, int dz, double radius) {
        return radius >= 0.0 && dx * dx + dz * dz <= radius * radius + radius;
    }

    /** Whether a box misses the clamp entirely. Six comparisons rather than an allocation. */
    private static boolean misses(BoundingBox clamp, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        return clamp.maxX() < minX || clamp.minX() > maxX
                || clamp.maxY() < minY || clamp.minY() > maxY
                || clamp.maxZ() < minZ || clamp.minZ() > maxZ;
    }
}

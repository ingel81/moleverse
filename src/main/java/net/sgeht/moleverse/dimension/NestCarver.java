package net.sgeht.moleverse.dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.entity.burrow.BurrowConstants;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.RunLevel;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * The one room a colony lives in, at the middle of everything it dug.
 *
 * <p>Real moles build exactly one nest chamber per colony: dry, lined with dragged
 * grass and leaves, slept in and raised young in, and set apart from the feeding
 * runs by being the only place in the system that is not a passage. Everything
 * else down here is a widening of something - a chamber is where a mound comes
 * down, a junction is where two runs cross, a shaft is where two levels meet. The
 * nest is the only volume in the burrow that exists for its own sake, and that is
 * the whole design brief: it has to read as a destination the moment you step into
 * it.</p>
 *
 * <h2>Why it is the largest room</h2>
 *
 * <p>The first person to walk a colony said the crossings were confusing, and the
 * reason was not that they were large - it was that nothing distinguished an
 * important room from an accidental one. The junctions have since been shrunk.
 * "Big" moves here, and being unique in the colony it can carry the meaning that
 * a dozen identical crossings could not. {@link #RADIUS} nine against a chamber's
 * six and a junction's four, {@link #HEIGHT} twelve against a chamber's nine.</p>
 *
 * <h2>The height contract, from the other side</h2>
 *
 * <p>{@link #HEIGHT} is far past {@code CorridorProfile.MAX_LIT_HEIGHT}, which is
 * what {@code TunnelDecorator}'s ceiling probe searches. That is not incidental
 * and it is not a cost: a sweep that wanders in here finds no ceiling within
 * reach, decides it is standing in a room rather than a corridor, and leaves the
 * whole slice alone - see {@link Junctions} for the contract in full. So the nest
 * gets none of the corridor's floor materials, trodden lines, wall speckle or
 * puddles, and everything in it is this class's doing. The spurs are the
 * deliberate exception: they are cut at a corridor's own section, so a sweep that
 * reaches one dresses it as the corridor it is.</p>
 *
 * <h2>Where it goes</h2>
 *
 * <p>At {@link Colony#core}, which is the mound a colony started from and is never
 * recomputed - the anatomy's centre was already in the data and nothing in the
 * burrow marked it. The depth is the colony's deepest <em>level</em> measured from
 * the core's own surface height, which is the same convention
 * {@code ChamberFeature.floorAt} follows and falls back to the feeding depth for
 * the same reason.</p>
 *
 * <p>It is deliberately not the deepest absolute waypoint height anywhere in the
 * colony. A colony spread down a hillside has runs sixty burrow blocks below the
 * core, and a nest placed at that height would sit in solid earth under its own
 * corridors with two spurs climbing out of it. The level is the part of a run's
 * depth that belongs to the colony; the rest belongs to the ground it happens to
 * cross.</p>
 *
 * <h2>The spurs</h2>
 *
 * <p>Runs join mounds, so nothing ends at the core and the nest would otherwise be
 * a sealed room. Two spurs join it to the two nearest corridors of the colony -
 * two rather than one because a single spur is a cul-de-sac, and rather than four
 * because a room with a door on every side is a junction again. They are cut at
 * the feeding section with no swings: a spur is a few blocks long and the
 * modulation that keeps a sixty block run from reading as a pipe has nothing to
 * say over that distance.</p>
 *
 * <h2>Every decision comes from the position</h2>
 *
 * <p>{@link ChamberFurnisher}'s rule and its reasoning: nothing draws from a
 * {@code RandomSource}, every roll is a hash of a block position, so the nest is
 * the same room on the tenth visit as on the first and comes out identical however
 * many chunks furnish it and in whatever order. The probes are the place that rule
 * has teeth - see {@link #isOpen}, which has to list everything this class puts
 * into open space or the room measures smaller every time it is dressed.</p>
 */
public final class NestCarver {

    // --- The room -------------------------------------------------------------

    /**
     * How wide the room is, as a radius.
     *
     * <p>Nine, against {@link BurrowGeometry#CHAMBER_RADIUS} six. Large enough that
     * you cannot see both walls at once from the doorway, which is the threshold at
     * which a space stops reading as a wide corridor - and it is the one place in
     * the burrow where that is wanted.</p>
     */
    public static final int RADIUS = 9;

    /**
     * And how tall.
     *
     * <p>Twelve. Three things ride on it being this much rather than a little more
     * than a chamber's nine: the dome has room to be a dome rather than a lid, the
     * ceiling is far enough away that the light has to be staged rather than
     * flooded, and it is comfortably past every reach {@code TunnelDecorator} has -
     * see the class javadoc.</p>
     */
    public static final int HEIGHT = 12;

    /**
     * How many of the topmost layers curve inwards.
     *
     * <p>Five, which is a little over a third of the height. The chamber's argument
     * at a larger size: without it the room is a drilled silo with a flat lid, and
     * the dome is the difference between a room and a shaft.</p>
     */
    private static final int DOME = 5;

    /**
     * How far the bottom layer is pulled in, so the wall meets the floor on a curve
     * instead of at a right angle.
     *
     * <p>One block, {@code CorridorCarver.CHAMBER_FILLET}'s figure and its reason.
     * It also gives the wall pockets a sill to stand on: a pocket is cut a block
     * outside the wall at head height, and the fillet is what is left underneath
     * it.</p>
     */
    private static final int FILLET = 1;

    /**
     * How coarse the roughness on the dome is: one decision per this many blocks of
     * height.
     *
     * <p>Per layer rather than per column, which is {@link Junctions}'s grain and
     * not the chamber's. A whole layer steps in or it does not, so the bell comes
     * down in a profile that varies rather than in a turned curve - and at this
     * radius a per-column bite is a texture nobody reads as shape from the floor
     * twelve blocks below.</p>
     */
    private static final int DOME_ROUGH_CELL = 2;

    /** Share of the domed layers left standing a block proud of the curve they were cut from. */
    private static final float DOME_ROUGH_CHANCE = 0.3F;

    // --- The bed --------------------------------------------------------------

    /** Half width of the sleeping hollow. Three gives a dish seven across: a bed for an animal at this scale. */
    private static final int HOLLOW_RADIUS = 3;

    /** Chance a square of the woven rim is there at all. The gaps are what make it a nest edge rather than a kerb. */
    private static final float RIM_DENSITY = 0.7F;

    /** Chance a rim block is stacked two high. A wall you look over on one side and step over on another reads as woven. */
    private static final float RIM_HIGH_CHANCE = 0.35F;

    /**
     * Share of the footing under the woven rim that glows.
     *
     * <p>Half, and this is what the room is lit by. Do the arithmetic:
     * {@link ModBlocks#GLOW_MYCELIUM} is light level nine, the dome is twelve blocks
     * up and the nearest pillar knot is six blocks out, so a nest lit only from above
     * and from the pillars has a floor at light zero exactly where the bed is.
     * Threads growing in the earth around the rim are what make this the one warm
     * room down here instead of the largest dark one.</p>
     *
     * <p>It lights the <em>room</em> and nothing more. The trove's own gate wants
     * light eight, which only a block orthogonally against the reading position can
     * give - the rim is four blocks out and reads about four there. That is the
     * frame's job, not this one; see {@link #warmHollow}.</p>
     *
     * <p>Under the rim rather than in it, and that is not a taste decision.
     * {@link #ceilingOf} treats these threads as ceiling, so one standing in the
     * open volume at the rim would have a ceiling probe stop a block above the floor
     * and light the bed's own rim as if it were the dome. Below the walking surface
     * nothing probes at all.</p>
     */
    private static final float RIM_GLOW_CHANCE = 0.5F;

    /**
     * Side of the warm hollow, in blocks.
     *
     * <p>Two, so the patch is unmistakably deliberate without being a floor
     * feature. It is the one spot in the room a player is meant to dig, and it is
     * in the same place on every visit with a block signature nothing else in the
     * dimension produces. See {@link #warmHollow}.</p>
     */
    public static final int WARM_HOLLOW_SIDE = 2;

    /**
     * How many courses of trove sit under the lid.
     *
     * <p>Two, which with {@link #WARM_HOLLOW_SIDE} is eight blocks: enough to be a
     * find rather than a block, shallow enough that the course below it is the
     * dimension's own deep earth and the pocket therefore has a floor nobody can dig
     * through.</p>
     */
    public static final int WARM_HOLLOW_DEPTH = 2;


    // --- The wall pockets -----------------------------------------------------

    /** How many niches are cut round the wall. Six is one every sixty degrees, which reads as a ring without reading as a colonnade. */
    private static final int POCKETS = 6;

    /** How far past the room's own radius a pocket writes. Two, which is what the plus-shaped niche reaches. */
    public static final int POCKET_REACH = 2;

    /** Layers above the walking surface a pocket is hollow at. Head height, so it is looked into rather than stepped over. */
    private static final int POCKET_FLOOR = 1;

    private static final int POCKET_TOP = 2;

    // --- The pillars ----------------------------------------------------------

    /** How far out on each diagonal a root stands. Six leaves the middle of the room clear and the wall a long way further out. */
    private static final int PILLAR_DIAGONAL = 6;

    /** How far a root may wander off its diagonal, so four of them do not read as masonry. */
    private static final int PILLAR_JITTER = 1;

    /** How far down from the ceiling a root frays sideways, so it meets the dome rather than butting into it. */
    private static final int PILLAR_CAPITAL = 2;

    /** Chance a block of that fraying is filled. Under one, so a root ends in the earth rather than in a plate. */
    private static final float PILLAR_CAPITAL_DENSITY = 0.6F;

    /**
     * How far up a pillar the glowing knot sits.
     *
     * <p>Three, which is head height. This is where the room's light actually comes
     * from: {@link ModBlocks#GLOW_MYCELIUM} is light level nine and the ceiling is
     * twelve blocks up, so a nest lit only from the dome is a nest with a dark
     * floor. Four knots in the middle of the room and six lamps round the wall are
     * what make it warm rather than merely lit, and both are at the height a lamp
     * would be if anybody had hung one.</p>
     */
    private static final int PILLAR_GLOW = 3;

    // --- The dome's light -----------------------------------------------------

    /** Share of the ceiling that glows directly over the bed. */
    private static final float GLOW_CORE = 0.4F;

    /** And out at the wall. Lower, so the light reads as pooling over the middle rather than as a lit lid. */
    private static final float GLOW_EDGE = 0.15F;

    /** How far in from the wall the ceiling passes stop, so they can never meet a wall pocket and have to reason about it. */
    private static final int CEILING_INSET = 2;

    /** Chance a square of the dome trails roots. Sparse on purpose: a ceiling of roots is a hedge. */
    private static final float FRINGE_CHANCE = 0.05F;

    // --- The floor ------------------------------------------------------------

    /** Side of one patch of floor material. One material per cell, because a floor that changes per square is noise rather than wear. */
    private static final int FLOOR_CELL = 3;

    /** Chance a square inside a patch is actually laid. The rest stays lining, and that is what gives a patch an edge. */
    private static final float FLOOR_DENSITY = 0.8F;

    /** Chance a square of the room floor is carpeted over. Flat, so it costs no headroom and can be stood in. */
    private static final float CARPET_CHANCE = 0.25F;

    // --- The spurs ------------------------------------------------------------

    /** How many corridors the nest is joined to. Two: one is a cul-de-sac, four is a junction. */
    private static final int SPURS = 2;

    /**
     * Where a spur starts, measured from the middle of the room.
     *
     * <p>One block inside the wall, so the first slice of the spur overlaps the
     * room and there can be no plug between the two however the dome and the
     * fillet came out.</p>
     */
    private static final int SPUR_MOUTH = RADIUS - 1;

    /** The section a spur is cut to. A feeding run, which is the everyday corridor and the right size for a doorway. */
    private static final RunLevel SPUR_LEVEL = RunLevel.FEEDING;

    /** How many of a spur's topmost layers are pulled in, so it is an arch rather than a slot. */
    private static final int SPUR_ARCH = 2;

    /** How far up the quarter ellipse the crown of that arch sits. Short of one, or the apex closes to a column. */
    private static final double SPUR_CROWN = 0.8;

    /**
     * Blocks of spur between two pools of light in its roof.
     *
     * <p>A spur belongs to no {@link net.sgeht.moleverse.entity.burrow.BurrowLink},
     * so {@code CorridorCarver.decorateRun} never walks one and
     * {@code TunnelDecorator} never lights it. Without this the two approaches to the
     * brightest room in the dimension are the two darkest passages in it, which is
     * exactly backwards. Six blocks is a third of the decorator's own glow spacing,
     * because a spur is short and one pool in the middle of it would be a lamp rather
     * than a lit way in.</p>
     */
    private static final int SPUR_GLOW_SPACING = 6;

    /** Radius of one of those pools. Small, and ragged at the rim - it is a patch of growth, not a lamp. */
    private static final int SPUR_GLOW_RADIUS = 1;

    /** Chance a block at the rim of a pool is lit. The middle is lit whatever the dice say. */
    private static final float SPUR_GLOW_DENSITY = 0.6F;

    // --- Reach and flags ------------------------------------------------------

    /**
     * How far from the middle anything here writes, the lining aside.
     *
     * <p>Public because the bounds are built from it and a caller has to have the
     * chunks: the pockets are cut outside the room, past what the room's own radius
     * covers.</p>
     */
    public static final int REACH = RADIUS + POCKET_REACH;

    /** How far past everything the bounds reach, on {@code CorridorFeature}'s argument: a generous box costs a walk, a tight one leaves earth nobody comes back for. */
    private static final int MARGIN = 2;

    /** Clients see the change and nothing else reacts, exactly as in {@link ChamberFurnisher}. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final double TAU = Math.PI * 2.0;

    /** Returned by a probe that failed to find what it went looking for. */
    private static final int NO_LEVEL = Integer.MIN_VALUE;

    // Salts. Distinct so that two decisions never agree by accident and land on the same block.
    private static final long SALT_DOME_ROUGH = 0x0BED_1000L;
    private static final long SALT_FLOOR_FILL = 0x0BED_1001L;
    private static final long SALT_FLOOR_KIND = 0x0BED_1002L;
    private static final long SALT_CARPET = 0x0BED_1003L;
    private static final long SALT_RIM = 0x0BED_1004L;
    private static final long SALT_RIM_HIGH = 0x0BED_1005L;
    private static final long SALT_RIM_GLOW = 0x0BED_100CL;
    private static final long SALT_POCKET_TURN = 0x0BED_1006L;
    private static final long SALT_PILLAR_X = 0x0BED_1007L;
    private static final long SALT_PILLAR_Z = 0x0BED_1008L;
    private static final long SALT_PILLAR_CAPITAL = 0x0BED_1009L;
    private static final long SALT_GLOW = 0x0BED_100AL;
    private static final long SALT_FRINGE = 0x0BED_100BL;

    private NestCarver() {
    }

    // --- What a nest is -------------------------------------------------------

    /**
     * A short dug run joining the nest to one corridor.
     *
     * @param from a block inside the nest wall, so the two volumes overlap
     * @param to   the point on the corridor's centre line the spur reaches
     */
    public record Spur(BlockPos from, BlockPos to) {

        /** Every block cutting this spur can reach, the soil lining included. */
        public BoundingBox bounds() {
            CorridorProfile profile = CorridorProfile.of(SPUR_LEVEL);
            int reach = profile.radius() + CorridorCarver.SHELL_MAX;
            return new BoundingBox(
                    Math.min(this.from.getX(), this.to.getX()) - reach,
                    Math.min(this.from.getY(), this.to.getY()) - CorridorCarver.SHELL_MAX,
                    Math.min(this.from.getZ(), this.to.getZ()) - reach,
                    Math.max(this.from.getX(), this.to.getX()) + reach,
                    Math.max(this.from.getY(), this.to.getY()) + profile.height() - 1
                            + CorridorCarver.SHELL_MAX,
                    Math.max(this.from.getZ(), this.to.getZ()) + reach);
        }
    }

    /**
     * Where one colony's nest goes, in burrow space.
     *
     * @param colony the colony's id, which is what names the feature
     * @param core   the mound the colony started from, in the overworld. Folded into
     *               the fingerprint so that a nest can never inherit a ledger entry
     *               from a colony that reused an id
     * @param centre the <em>walking surface</em> at the middle of the room, the same
     *               convention a corridor centre and a chamber centre follow: the
     *               block below it is floor and is left alone
     * @param spurs  the runs joining it to the network, nearest first
     */
    public record Nest(int colony, BlockPos core, BlockPos centre, List<Spur> spurs) {

        public Nest {
            spurs = List.copyOf(spurs);
        }

        /**
         * Everything cutting and dressing this nest can reach.
         *
         * <p>The room and its lining, the pockets outside the wall, the course of
         * coarse dirt under the warm hollow, and every spur. Err large, on
         * {@code BurrowFeature.bounds}'s argument - a chunk is only asked about a
         * feature whose bounds reach into it, and a box drawn tight leaves a stripe
         * of earth nobody ever comes back for.</p>
         */
        public BoundingBox bounds() {
            int reach = REACH + CorridorCarver.SHELL_MAX;
            BoundingBox box = new BoundingBox(
                    this.centre.getX() - reach,
                    this.centre.getY() - 1 - WARM_HOLLOW_DEPTH - CorridorCarver.SHELL_MAX,
                    this.centre.getZ() - reach,
                    this.centre.getX() + reach,
                    this.centre.getY() + HEIGHT + CorridorCarver.SHELL_MAX,
                    this.centre.getZ() + reach);

            for (Spur spur : this.spurs) {
                box = BoundingBox.encapsulating(box, spur.bounds());
            }
            return box.inflatedBy(MARGIN);
        }
    }

    /**
     * Where this colony's nest belongs, spurs and all.
     *
     * <p>Pure arithmetic on the colony and its links - no level, no block reads, no
     * world at all, which is what lets the plan layer hand a chunk a nest before any
     * of the ground exists. {@link Junctions#crossingsOf} and
     * {@link LevelShafts#crossingsOf} are the same seam.</p>
     *
     * <p>Hand it the colony's own runs. A run belonging to somebody else would put a
     * spur through a wall into a corridor this colony never dug.</p>
     */
    public static Nest nestOf(Colony colony, List<BurrowLink> colonyRuns) {
        BlockPos centre = BurrowGeometry.toBurrow(colony.core()).atY(floorOf(colony, colonyRuns));
        return new Nest(colony.id(), colony.core(), centre, spursOf(centre, colonyRuns));
    }

    /**
     * The walking surface of the nest, in burrow space.
     *
     * <p>The colony's deepest run <em>level</em>, measured down from the core's own
     * surface height, and the feeding depth where the colony has dug nothing at all.
     * That is {@code ChamberFeature.floorAt}'s convention: the level is the part of
     * a run's depth that belongs to the colony, and the rest belongs to whatever
     * ground the run happens to cross. See the class javadoc for why the absolute
     * waypoint heights are the wrong answer here even though they are the right one
     * at a mound.</p>
     */
    public static int floorOf(Colony colony, List<BurrowLink> colonyRuns) {
        int deepest = BurrowConstants.DEPTH_FEEDING;
        for (BurrowLink run : colonyRuns) {
            deepest = Math.max(deepest, run.level().depth());
        }
        return BurrowGeometry.burrowY(colony.core().getY() - deepest);
    }

    /**
     * The low corner of the warm hollow's lid, at floor level.
     *
     * <p>The one spot in the nest a player is meant to dig out, and it is in the
     * same place on every visit. The lid is a {@link Blocks#MOSS_BLOCK} square
     * {@link #WARM_HOLLOW_SIDE} on a side at this height - green against the
     * {@link Blocks#HAY_BLOCK} the rest of the bed is made of, which is a
     * combination that occurs nowhere else in the dimension.</p>
     *
     * <p>Under it, {@link #WARM_HOLLOW_DEPTH} courses of the same footprint are the
     * trove: the upper course is all {@link ModBlocks#WORM_LARDER}, the lower all
     * {@link ModBlocks#ROOT_NODULE}. Both already carry loot tables, so the room's
     * one treasure is a mixed haul out of blocks that exist rather than a mechanic
     * bolted onto a marker. Under <em>that</em> is deep earth, so the pocket has a
     * floor.</p>
     *
     * <p>An exact count rather than a hashed share: what a trove is worth has to be
     * the same in every colony, and a density over eight blocks would swing between
     * one and five. Four larders is also the floor a corridor alcove sets - it studs
     * a whole wall band at {@code AlcoveCarver.LARDER_DENSITY} and comes out well
     * over ten - and a trove poorer than ordinary furniture would be the wrong way
     * round.</p>
     *
     * <p>Exact means exact, which takes {@link #placeTrove} to hold: every block of
     * the hollow is laid over an ambient lining pocket as readily as over plain
     * lining, or the count quietly comes out three and five instead.</p>
     *
     * <h2>The frame is the mechanism</h2>
     *
     * <p>Ringing the lid, at lid level, are eight blocks of
     * {@link ModBlocks#GLOW_MYCELIUM}, orthogonally against the lid and never on its
     * corners. They are not decoration and the arithmetic is why. A larder's
     * glow-worm drop asks whether a neighbouring block is at light eight; the burrow
     * has no skylight, so that is pure block light; threads are light nine and block
     * light falls by one a block, so a source clears eight from the very next block
     * and nowhere else. The bed's own rim at radius four reads four or five at the
     * middle and would never have carried it. Each lid square touches two frame
     * blocks, so the air a broken lid leaves reads eight and every larder under it
     * passes - and mining the frame out switches the whole trove off, which is
     * light-as-upkeep in the one room where it is worth something.</p>
     *
     * <p><strong>One thing a playtest will call a bug.</strong> Light updates are
     * queued on a block change and flushed later in {@code ServerChunkCache.tick},
     * so a block broken in the <em>same tick</em> as the one above it reads stale
     * light. Mining down by hand is ticks apart and works; FTB Ultimine, which is in
     * the dev runtime, takes a vein in one action and yields no glow worms at all,
     * and an explosion behaves the same way. Arguably right - careful harvesting
     * beating strip-mining is what {@code docs/BURROW_LIFE.md} section 4 asks for -
     * but it is not going to look deliberate from inside the game.</p>
     */
    public static BlockPos warmHollow(Nest nest) {
        return nest.centre().below();
    }

    // --- Cutting --------------------------------------------------------------

    /**
     * Cuts the room and its spurs, writing only inside {@code clamp}.
     *
     * <p>Null is the unbounded case. Anything else is one chunk taking its share:
     * every layer is decided from the arithmetic before the box is consulted, so a
     * nest cut chunk by chunk is the same nest as one cut in a single call.</p>
     *
     * <p>Always safe to call again. Everything goes through
     * {@link CorridorCarver#discAndShell}, which clears ground and lines earth and
     * can do neither twice.</p>
     */
    public static void cut(ServerLevel burrow, Nest nest, @Nullable BoundingBox clamp) {
        hollow(burrow, nest.centre(), clamp);
        for (Spur spur : nest.spurs()) {
            cutSpur(burrow, spur, clamp);
        }
    }

    /**
     * Opens the room itself: a domed cylinder, lined as it is cut.
     *
     * <p>One sweep per layer through the carver's own pass, so the wall of a nest is
     * the loose soil every other wall down there is and the rule about what may be
     * cleared has exactly one copy. The layers either side of the room cut nothing
     * and line only, which is what closes the skin over the dome and under the floor
     * instead of leaving a rim.</p>
     */
    private static void hollow(ServerLevel burrow, BlockPos centre, @Nullable BoundingBox clamp) {
        int reach = REACH + CorridorCarver.SHELL_MAX;
        if (clamp != null && misses(clamp,
                centre.getX() - reach, centre.getY() - CorridorCarver.SHELL_MAX, centre.getZ() - reach,
                centre.getX() + reach, centre.getY() + HEIGHT + CorridorCarver.SHELL_MAX,
                centre.getZ() + reach)) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int layer = -CorridorCarver.SHELL_MAX; layer < HEIGHT + CorridorCarver.SHELL_MAX; layer++) {
            int away = layer < 0 ? -layer : Math.max(0, layer - (HEIGHT - 1));
            // The curve is read at the nearest layer the room actually has, not at
            // the layer being written: the soil over the dome has to wrap the shape
            // that was cut, and a bite sampled a layer higher is a different shape.
            int nearest = Math.clamp(layer, 0, HEIGHT - 1);
            CorridorCarver.discAndShell(burrow, centre.getX(), centre.getY() + layer, centre.getZ(),
                    roomRadiusAt(nearest, centre), away, cursor, clamp);
        }
    }

    /**
     * How wide the room is cut at one layer.
     *
     * <p>The wall, less the fillet at the foot, less whatever the dome has taken -
     * and on the domed layers, less the odd block of roughness. The topmost layer is
     * left exactly on the curve: it is what the ceiling probes measure against, and a
     * crown that varied would put the dome's light a layer inside the earth.</p>
     */
    private static double roomRadiusAt(int layer, BlockPos centre) {
        int span = RADIUS - Math.max(0, FILLET - layer);
        double dome = domeAt(layer);
        double radius = Math.min(span, dome);

        if (dome < span && layer < HEIGHT - 1) {
            radius = Math.max(0.0, radius - bite(centre.getX(), centre.getY() + layer, centre.getZ()));
        }
        return radius;
    }

    /**
     * What the dome allows at this layer.
     *
     * <p>A quarter ellipse over the top {@link #DOME} layers. The parameter stops
     * short of one so the apex keeps a usable radius instead of closing to a single
     * column - a dome, not a spire.</p>
     */
    private static double domeAt(int layer) {
        int flat = HEIGHT - DOME;
        if (layer < flat) {
            return RADIUS;
        }

        double t = Mth.clamp((layer - flat + 1.0) / (DOME + 1.0), 0.0, 1.0);
        return RADIUS * Math.sqrt(1.0 - t * t);
    }

    /**
     * Whether the dome keeps this layer a block proud of the curve it was cut from.
     *
     * <p>Hashed from where the layer is in the world rather than from where it is in
     * the room, which is the discipline the whole dimension works to: a lump is then
     * a property of the place and comes back identical however often, and in
     * whatever order, the nest is cut.</p>
     */
    private static int bite(int x, int y, int z) {
        return CorridorCarver.noise(SALT_DOME_ROUGH,
                Math.floorDiv(x, DOME_ROUGH_CELL),
                Math.floorDiv(y, DOME_ROUGH_CELL),
                Math.floorDiv(z, DOME_ROUGH_CELL)) < DOME_ROUGH_CHANCE ? 1 : 0;
    }

    /**
     * Cuts one spur: a short corridor from the nest wall to a run.
     *
     * <p>Walked in single block steps along the straight line joining the two ends,
     * which is {@link CorridorCarver#carve}'s own step and for its own reason - the
     * cross-section is several blocks wide, so a coarser step would still join up,
     * but only for as long as no section is narrow.</p>
     *
     * <p>The step count comes from the three dimensional length, so a spur that has
     * to climb never rises more than a block per slice. The floor is left standing
     * under every slice, exactly as in a corridor, so a climbing spur comes out as a
     * staircase rather than as a ramp nobody can walk up.</p>
     */
    private static void cutSpur(ServerLevel burrow, Spur spur, @Nullable BoundingBox clamp) {
        CorridorProfile profile = CorridorProfile.of(SPUR_LEVEL);
        BlockPos from = spur.from();
        BlockPos to = spur.to();

        if (clamp != null && misses(clamp, spur.bounds())) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        int steps = Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            int x = from.getX() + (int) Math.round(dx * t);
            int y = from.getY() + (int) Math.round(dy * t);
            int z = from.getZ() + (int) Math.round(dz * t);

            spurSection(burrow, profile, x, y, z, cursor, clamp);
            if (step % SPUR_GLOW_SPACING == 0) {
                lightSpur(burrow, x, y + profile.height(), z, cursor, clamp);
            }
        }
    }

    /**
     * Grows a pool of light into the roof of a spur.
     *
     * <p>In the carve pass rather than the dressing one, which is {@link Junctions}'s
     * choice for the crown over a crossing and for its reason: nothing here probes.
     * The roof of a spur is at the section's own height by arithmetic, and the layer
     * above the cut has just been lined, so the threads replace soil the same call
     * put there.</p>
     *
     * <p>Ground only, which makes it idempotent without a second thought: threads are
     * not ground, so a second visit finds its work done, and a block a player has put
     * up there is left alone.</p>
     */
    private static void lightSpur(ServerLevel burrow, int cx, int y, int cz,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();

        for (int dx = -SPUR_GLOW_RADIUS; dx <= SPUR_GLOW_RADIUS; dx++) {
            for (int dz = -SPUR_GLOW_RADIUS; dz <= SPUR_GLOW_RADIUS; dz++) {
                if (!withinDisc(dx, dz, SPUR_GLOW_RADIUS)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean middle = dx == 0 && dz == 0;
                if (middle || CorridorCarver.noise(SALT_GLOW, x, y, z) < SPUR_GLOW_DENSITY) {
                    replaceGround(burrow, cursor.set(x, y, z), glow, clamp);
                }
            }
        }
    }

    /**
     * One cross-section of spur.
     *
     * <p>No swings, unlike a corridor's. A spur is a handful of blocks long and the
     * modulation that keeps a sixty block run from reading as plumbing has nothing
     * to say over that distance - what it would do instead is put a bulge either
     * side of a doorway that is supposed to be cut to its section.</p>
     */
    private static void spurSection(ServerLevel burrow, CorridorProfile profile, int x, int y, int z,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        int height = profile.height();
        double radius = profile.radius();

        for (int layer = -CorridorCarver.SHELL_MAX; layer < height + CorridorCarver.SHELL_MAX; layer++) {
            int away = layer < 0 ? -layer : Math.max(0, layer - (height - 1));
            int nearest = Math.clamp(layer, 0, height - 1);
            CorridorCarver.discAndShell(burrow, x, y + layer, z,
                    spurRadiusAt(nearest, height, radius), away, cursor, clamp);
        }

        // The one promise the section cannot break: the centre line, open to head
        // height and a block over it. The arithmetic already says the disc covers
        // it; "already says" is the wrong footing for the claim that a nest can be
        // walked out of.
        for (int layer = 0; layer < CorridorProfile.LOWEST_SECTION; layer++) {
            if (writes(clamp, x, y + layer, z)) {
                CorridorCarver.clear(burrow, cursor.set(x, y + layer, z));
            }
        }
    }

    /** The arch at the top of a spur, on {@code CorridorCarver.sectionRadiusAt}'s quarter ellipse. */
    private static double spurRadiusAt(int layer, int height, double radius) {
        int fromTop = height - 1 - layer;
        if (fromTop >= SPUR_ARCH) {
            return radius;
        }

        double t = SPUR_CROWN * (SPUR_ARCH - fromTop) / SPUR_ARCH;
        return Math.max(CorridorProfile.NARROWEST_RADIUS, radius * Math.sqrt(1.0 - t * t));
    }

    // --- Finding the spurs ----------------------------------------------------

    /**
     * The two corridors the nest opens onto.
     *
     * <p>Nearest first, and sorted by the distance and then by where the corridor
     * was met - never by the order the store handed the runs over. A nest whose
     * spurs swapped ends when the store was rewritten would be carved a second time
     * beside itself, which is the argument {@code BurrowPlan} makes about every
     * ordering in the plan layer.</p>
     *
     * <p>A run with a single waypoint has no length to meet and is skipped, the same
     * way {@code BurrowPlan} skips it for a corridor.</p>
     */
    private static List<Spur> spursOf(BlockPos centre, List<BurrowLink> colonyRuns) {
        record Candidate(BlockPos target, long distanceSqr) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BurrowLink run : colonyRuns) {
            if (run.pointCount() < 2) {
                continue;
            }
            BlockPos target = nearestPointOf(run, centre);
            candidates.add(new Candidate(target, planDistanceSqr(centre, target)));
        }

        candidates.sort(Comparator.<Candidate>comparingLong(Candidate::distanceSqr)
                .thenComparingLong(candidate -> candidate.target().asLong()));

        List<Spur> spurs = new ArrayList<>(SPURS);
        for (int i = 0; i < Math.min(SPURS, candidates.size()); i++) {
            spurs.add(spurTo(centre, candidates.get(i).target()));
        }
        return spurs;
    }

    /**
     * The point of this run closest to the nest, in burrow space.
     *
     * <p>Projected in plan view and lifted to the run's own height there, which is
     * {@link Junctions}'s reconstruction and for its reason: waypoints are eight
     * blocks apart down here, so snapping to the nearest one would put the spur's
     * far end further from the corridor than the corridor is wide. Depth is read at
     * the projected parameter rather than minimised, because what a spur has to
     * reach is the corridor, not the deepest thing the run does.</p>
     */
    private static BlockPos nearestPointOf(BurrowLink run, BlockPos centre) {
        BlockPos best = CorridorCarver.burrowPoint(run, 0);
        long bestDistance = Long.MAX_VALUE;
        BlockPos from = best;

        for (int i = 1; i < run.pointCount(); i++) {
            BlockPos to = CorridorCarver.burrowPoint(run, i);
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double lengthSqr = dx * dx + dz * dz;
            double t = lengthSqr <= 0.0 ? 0.0 : Math.clamp(
                    ((centre.getX() - from.getX()) * dx + (centre.getZ() - from.getZ()) * dz) / lengthSqr,
                    0.0, 1.0);

            BlockPos here = new BlockPos(
                    from.getX() + (int) Math.round(dx * t),
                    from.getY() + (int) Math.round((to.getY() - from.getY()) * t),
                    from.getZ() + (int) Math.round(dz * t));
            long distance = planDistanceSqr(centre, here);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = here;
            }
            from = to;
        }
        return best;
    }

    /**
     * The spur joining the nest to one corridor point.
     *
     * <p>It starts a block inside the wall so that the two volumes overlap and no
     * plug can be left between them. A corridor that already runs through the room -
     * which the plan allows and nothing forbids - gives a spur of no length, and
     * that is the right answer: the room is already open onto it.</p>
     */
    private static Spur spurTo(BlockPos centre, BlockPos target) {
        double dx = target.getX() - centre.getX();
        double dz = target.getZ() - centre.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= SPUR_MOUTH) {
            return new Spur(target, target);
        }

        double t = SPUR_MOUTH / length;
        return new Spur(new BlockPos(
                centre.getX() + (int) Math.round(dx * t),
                centre.getY(),
                centre.getZ() + (int) Math.round(dz * t)), target);
    }

    private static long planDistanceSqr(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // --- Dressing -------------------------------------------------------------

    /**
     * Turns the cut room into a nest, writing only inside {@code clamp}.
     *
     * <p>The second pass, and it has to be one. Everything here that has a height
     * <em>measures</em> - the pillars and the dome's light probe upwards for a
     * ceiling - and a probe run against a chunk that is still solid earth reads the
     * chunk border as the top of the room. So the reconciler holds this back until
     * the ground the probes will walk over exists.</p>
     *
     * <p>Only the writes are bounded. The probes read the whole room whatever box is
     * in force, because they decide <em>what the room is</em>, and a decision that
     * changed with the chunk being worked on is exactly what this class is built not
     * to have. The consequence is worth stating: a clamped call still needs the
     * middle of the room loaded, and a rim chunk that arrives while the centre is
     * away dresses nothing and waits for a pass that can see the room.</p>
     */
    public static void furnish(ServerLevel burrow, Nest nest, @Nullable BoundingBox clamp) {
        BlockPos centre = nest.centre();
        int cx = centre.getX();
        int wy = centre.getY();
        int cz = centre.getZ();

        // The lowest thing here writes is the bottom course of the trove, and the
        // highest is the light in the dome.
        if (clamp != null && misses(clamp,
                cx - REACH, wy - 1 - WARM_HOLLOW_DEPTH, cz - REACH,
                cx + REACH, wy + HEIGHT, cz + REACH)) {
            return;
        }
        if (!isNest(burrow, cx, wy, cz)) {
            return;
        }

        int[][] pillars = pillarFeet(cx, wy, cz);

        dressFloor(burrow, cx, wy, cz, pillars, clamp);
        bedTheHollow(burrow, cx, wy, cz, clamp);
        weaveTheRim(burrow, cx, wy, cz, clamp);
        cutPockets(burrow, cx, wy, cz, clamp);
        raisePillars(burrow, cx, wy, cz, pillars, clamp);
        lightTheDome(burrow, cx, wy, cz, clamp);
    }

    /**
     * Lays the floor of the room, everywhere the set pieces have not claimed.
     *
     * <p>One material per three block cell, which is {@link ChamberFurnisher}'s
     * argument for a chamber and holds harder here: a floor that picks a material
     * per square is noise, and this room is three times the area.</p>
     *
     * <p>No gravel and no clay, unlike a chamber. This is the one room the colony
     * sleeps in, and every stony material kept out of it is a step towards the floor
     * reading as soft.</p>
     */
    private static void dressFloor(ServerLevel burrow, int cx, int wy, int cz, int[][] pillars,
            @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (!withinDisc(dx, dz, RADIUS) || claimed(dx, dz, pillars, cx, cz)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (CorridorCarver.noise(SALT_FLOOR_FILL, x, floorY, z) >= FLOOR_DENSITY) {
                    continue;
                }

                Block material = floorMaterial(Math.floorDiv(x, FLOOR_CELL), Math.floorDiv(z, FLOOR_CELL));
                replaceGround(burrow, cursor.set(x, floorY, z), material.defaultBlockState(), clamp);
                if (CorridorCarver.noise(SALT_CARPET, x, wy, z) < CARPET_CHANCE) {
                    fillAir(burrow, cursor.set(x, wy, z), Blocks.MOSS_CARPET.defaultBlockState(), clamp);
                }
            }
        }
    }

    /**
     * Ground a set piece has spoken for.
     *
     * <p>The point is not to avoid overwriting - {@link #replaceGround} could not
     * overwrite a laid floor anyway. It is that a set piece must find raw ground when
     * it gets there, on the second visit as much as on the first, whichever chunk it
     * is being written from. One statement of who owns what, and no two of them
     * overlap.</p>
     */
    private static boolean claimed(int dx, int dz, int[][] pillars, int cx, int cz) {
        if (withinDisc(dx, dz, HOLLOW_RADIUS + 1)) {
            return true;
        }
        for (int[] pillar : pillars) {
            if (cx + dx == pillar[0] && cz + dz == pillar[1]) {
                return true;
            }
        }
        return false;
    }

    /** What a patch of nest floor is made of. Soft materials only; see {@link #dressFloor}. */
    private static Block floorMaterial(int cellX, int cellZ) {
        int roll = (int) (CorridorCarver.noise(SALT_FLOOR_KIND, cellX, cellZ, 0) * 100.0F);
        if (roll < 45) {
            return ModBlocks.LOOSE_SOIL.get();
        }
        if (roll < 80) {
            return Blocks.ROOTED_DIRT;
        }
        return Blocks.COARSE_DIRT;
    }

    /**
     * Beds the sleeping hollow, and signs the warm spot in the middle of it.
     *
     * <p>Dry grass dragged into a dish, which is what a mole's nest actually is -
     * {@link Blocks#HAY_BLOCK} is the vanilla block that reads as packed dry grass
     * and it is used nowhere else in this dimension. The warm hollow takes the middle
     * squares out of it, and the split is decided here rather than by writing one
     * over the other: {@link #replaceGround} refuses a block that is already
     * something, so a hollow laid after the bedding would simply never appear.</p>
     */
    private static void bedTheHollow(ServerLevel burrow, int cx, int wy, int cz,
            @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -HOLLOW_RADIUS; dx <= HOLLOW_RADIUS; dx++) {
            for (int dz = -HOLLOW_RADIUS; dz <= HOLLOW_RADIUS; dz++) {
                if (!withinDisc(dx, dz, HOLLOW_RADIUS)
                        || isWarmHollow(dx, dz) || isHollowFrame(dx, dz)) {
                    continue;
                }
                replaceGround(burrow, cursor.set(cx + dx, floorY, cz + dz),
                        Blocks.HAY_BLOCK.defaultBlockState(), clamp);
            }
        }

        digTheTrove(burrow, cx, wy, cz, cursor, clamp);
    }

    /**
     * Sets the warm hollow into the bedding and packs the pocket under it.
     *
     * <p>A lid of moss, green against the gold of the bed, and under it the one
     * treasure the burrow has. Both of the blocks in the pocket already carry loot
     * tables of their own, so the trove is a mixed haul out of what exists rather
     * than a mechanic bolted onto a marker - see {@link #warmHollow} for the full
     * signature and for what the bed's light has to do with it.</p>
     *
     * <p>Which two blocks are larders is turned by a hash of the room's position and
     * they are always diagonally opposite, so the pocket is never two larders deep on
     * one side and never none. The count is exact rather than a density for the
     * reason {@link #TROVE_LARDERS} gives.</p>
     */
    private static void digTheTrove(ServerLevel burrow, int cx, int wy, int cz,
            BlockPos.MutableBlockPos cursor, @Nullable BoundingBox clamp) {
        int floorY = wy - 1;
        BlockState lid = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        BlockState nodules = ModBlocks.ROOT_NODULE.get().defaultBlockState();
        BlockState worms = ModBlocks.WORM_LARDER.get().defaultBlockState();

        for (int dx = 0; dx < WARM_HOLLOW_SIDE; dx++) {
            for (int dz = 0; dz < WARM_HOLLOW_SIDE; dz++) {
                placeTrove(burrow, cursor.set(cx + dx, floorY, cz + dz), lid, clamp);
                for (int course = 1; course <= WARM_HOLLOW_DEPTH; course++) {
                    placeTrove(burrow, cursor.set(cx + dx, floorY - course, cz + dz),
                            course == 1 ? worms : nodules, clamp);
                }
            }
        }

        // The frame, and it is the mechanism rather than the decoration. See
        // warmHollow: every larder's glow-worm drop asks whether the block above it
        // is at light eight, threads are light nine, and block light falls by one a
        // block - so the only place a source can stand is orthogonally against the
        // square that becomes air when the lid comes off. Each lid square touches
        // two of these, so every larder in the course below passes.
        for (int dx = -1; dx <= WARM_HOLLOW_SIDE; dx++) {
            for (int dz = -1; dz <= WARM_HOLLOW_SIDE; dz++) {
                if (isHollowFrame(dx, dz)) {
                    placeTrove(burrow, cursor.set(cx + dx, floorY, cz + dz), glow, clamp);
                }
            }
        }
    }

    /**
     * Whether this square of the bed is the lit frame around the warm hollow.
     *
     * <p>The eight squares orthogonally against the lid and no others - the corners
     * touch nothing that becomes air and would be light spent on looks. One statement
     * of it for the two passes that have to agree: {@link #bedTheHollow} keeps the
     * bedding off these squares and {@link #digTheTrove} lays them.</p>
     */
    private static boolean isHollowFrame(int dx, int dz) {
        boolean acrossX = dx >= 0 && dx < WARM_HOLLOW_SIDE;
        boolean acrossZ = dz >= 0 && dz < WARM_HOLLOW_SIDE;
        boolean edgeX = dx == -1 || dx == WARM_HOLLOW_SIDE;
        boolean edgeZ = dz == -1 || dz == WARM_HOLLOW_SIDE;
        return (acrossX && edgeZ) || (acrossZ && edgeX);
    }

    /** Whether this square of the bed is the warm hollow. One statement of it, for the two passes that have to agree. */
    private static boolean isWarmHollow(int dx, int dz) {
        return dx >= 0 && dx < WARM_HOLLOW_SIDE && dz >= 0 && dz < WARM_HOLLOW_SIDE;
    }

    /**
     * Weaves the raised rim round the hollow.
     *
     * <p>What makes it a bed rather than a patch of hay on the floor. Left ragged and
     * only sometimes two blocks high, because a mole builds a nest by dragging
     * material into a heap rather than by laying a course - and the gaps are what let
     * you step into it from any side.</p>
     *
     * <p>{@link ModBlocks#ROOT_BEAM} and nothing else in the standing part, which is
     * not a colour choice: {@link #isOpen} has to list everything this class puts
     * into open space, and the beam is already on it because the chamber puts one
     * there too.</p>
     */
    private static void weaveTheRim(ServerLevel burrow, int cx, int wy, int cz,
            @Nullable BoundingBox clamp) {
        int reach = HOLLOW_RADIUS + 1;
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        BlockState footing = Blocks.ROOTED_DIRT.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (withinDisc(dx, dz, HOLLOW_RADIUS) || !withinDisc(dx, dz, reach)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;

                // Threads in the earth under the rim, showing through its gaps. This
                // is the room's light rather than a garnish on it - see RIM_GLOW_CHANCE.
                boolean lit = CorridorCarver.noise(SALT_RIM_GLOW, x, wy - 1, z) < RIM_GLOW_CHANCE;
                replaceGround(burrow, cursor.set(x, wy - 1, z), lit ? glow : footing, clamp);
                if (CorridorCarver.noise(SALT_RIM, x, wy, z) >= RIM_DENSITY) {
                    continue;
                }
                fillAir(burrow, cursor.set(x, wy, z), beam, clamp);
                if (CorridorCarver.noise(SALT_RIM_HIGH, x, wy, z) < RIM_HIGH_CHANCE) {
                    fillAir(burrow, cursor.set(x, wy + 1, z), beam, clamp);
                }
            }
        }
    }

    /**
     * Cuts the niches round the wall and lights them.
     *
     * <p>Six recesses at head height, a block into the wall, each with a wad of
     * bedding on its sill and a lamp in its roof. They do two jobs at once and the
     * second is the one that matters: a room twelve blocks tall lit only from its
     * dome has a dark floor, because {@link ModBlocks#GLOW_MYCELIUM} is light level
     * nine. These are where a lamp would be if anybody had hung one.</p>
     *
     * <p>The ring is turned by a hash of the room's own position, so two nests in one
     * world do not face the same way. Nothing here probes: the wall is at the room's
     * own radius by construction, and a niche whose square turns out to be air - a
     * spur mouth took that side - simply fails {@link #replaceGround} and is not
     * cut.</p>
     */
    private static void cutPockets(ServerLevel burrow, int cx, int wy, int cz,
            @Nullable BoundingBox clamp) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double turn = CorridorCarver.noise(SALT_POCKET_TURN, cx, wy, cz);

        for (int pocket = 0; pocket < POCKETS; pocket++) {
            double angle = (pocket + turn) * (TAU / POCKETS);
            int px = cx + (int) Math.round(Math.cos(angle) * (RADIUS + 1));
            int pz = cz + (int) Math.round(Math.sin(angle) * (RADIUS + 1));
            if (!writesColumn(clamp, px, pz, 1)) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!withinDisc(dx, dz, 1)) {
                        continue;
                    }
                    for (int y = wy + POCKET_FLOOR; y <= wy + POCKET_TOP; y++) {
                        clearGround(burrow, cursor.set(px + dx, y, pz + dz), clamp);
                    }
                    // The sill the fillet left under the niche, with bedding on it.
                    replaceGround(burrow, cursor.set(px + dx, wy, pz + dz),
                            Blocks.HAY_BLOCK.defaultBlockState(), clamp);
                }
            }
            replaceGround(burrow, cursor.set(px, wy + POCKET_TOP + 1, pz),
                    ModBlocks.GLOW_MYCELIUM.get().defaultBlockState(), clamp);
        }
    }

    /**
     * Where the roots holding the dome stand.
     *
     * <p>On the diagonals, and the jitter is what keeps four of them from reading as
     * a colonnade. A root that would land in the bed is dropped rather than moved:
     * three roots hold a ceiling up as convincingly as four, and a room with a pillar
     * in the middle of its bed does not.</p>
     */
    private static int[][] pillarFeet(int cx, int wy, int cz) {
        int[][] found = new int[4][2];
        int count = 0;
        int corner = 0;

        for (int signX = -1; signX <= 1; signX += 2) {
            for (int signZ = -1; signZ <= 1; signZ += 2) {
                int x = cx + signX * (PILLAR_DIAGONAL + jitter(SALT_PILLAR_X, cx, wy, cz + corner));
                int z = cz + signZ * (PILLAR_DIAGONAL + jitter(SALT_PILLAR_Z, cx, wy, cz + corner));
                corner++;

                if (withinDisc(x - cx, z - cz, HOLLOW_RADIUS + 2)) {
                    continue;
                }
                found[count][0] = x;
                found[count][1] = z;
                count++;
            }
        }

        int[][] pillars = new int[count][];
        System.arraycopy(found, 0, pillars, 0, count);
        return pillars;
    }

    /**
     * Stands the roots up and hangs the room's real light on them.
     *
     * <p>Each is one column from the floor to the ceiling it holds, fraying sideways
     * over the last courses so it meets the dome rather than butting into it, with a
     * glowing knot at head height - see {@link #PILLAR_GLOW}. The knot is why the
     * pillars earn their place twice.</p>
     */
    private static void raisePillars(ServerLevel burrow, int cx, int wy, int cz, int[][] pillars,
            @Nullable BoundingBox clamp) {
        BlockState beam = ModBlocks.ROOT_BEAM.get().defaultBlockState();
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int[] pillar : pillars) {
            int x = pillar[0];
            int z = pillar[1];
            // A root writes into its own column and into the four beside it, so the
            // whole nine has to be out of the box before it is skipped.
            if (!writesColumn(clamp, x, z, 1)) {
                continue;
            }
            int ceilingY = ceilingOf(burrow, x, wy, z);
            if (ceilingY == NO_LEVEL) {
                continue;
            }

            for (int y = wy; y < ceilingY; y++) {
                fillAir(burrow, cursor.set(x, y, z), y == wy + PILLAR_GLOW ? glow : beam, clamp);
            }
            replaceGround(burrow, cursor.set(x, wy - 1, z), Blocks.MOSS_BLOCK.defaultBlockState(), clamp);

            for (Direction side : Direction.Plane.HORIZONTAL) {
                int nx = x + side.getStepX();
                int nz = z + side.getStepZ();
                for (int y = Math.max(ceilingY - PILLAR_CAPITAL, wy + 1); y < ceilingY; y++) {
                    if (CorridorCarver.noise(SALT_PILLAR_CAPITAL, nx, y, nz) < PILLAR_CAPITAL_DENSITY) {
                        fillAir(burrow, cursor.set(nx, y, nz), beam, clamp);
                    }
                }
            }
        }
    }

    /**
     * Grows the light and the roots into the dome.
     *
     * <p>Denser over the bed than out at the wall, so the ceiling reads as pooling
     * over the middle of the room rather than as a lit lid. Both passes stop
     * {@link #CEILING_INSET} short of the wall, which is not a taste decision: it
     * keeps them clear of the wall pockets, so no column is ever both cut by a pocket
     * and probed by a ceiling pass and there is nothing for the two to disagree
     * about when they are run from different chunks.</p>
     */
    private static void lightTheDome(ServerLevel burrow, int cx, int wy, int cz,
            @Nullable BoundingBox clamp) {
        int reach = RADIUS - CEILING_INSET;
        BlockState glow = ModBlocks.GLOW_MYCELIUM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!withinDisc(dx, dz, reach) || !writesColumn(clamp, cx + dx, cz + dz, 0)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int ceilingY = ceilingOf(burrow, x, wy, z);
                if (ceilingY == NO_LEVEL) {
                    continue;
                }

                float chance = GLOW_EDGE + (GLOW_CORE - GLOW_EDGE)
                        * (1.0F - Math.min(1.0F, (float) Math.sqrt(dx * dx + dz * dz) / reach));
                if (CorridorCarver.noise(SALT_GLOW, x, ceilingY, z) < chance) {
                    replaceGround(burrow, cursor.set(x, ceilingY, z), glow, clamp);
                    continue;
                }
                if (CorridorCarver.noise(SALT_FRINGE, x, ceilingY, z) < FRINGE_CHANCE) {
                    fillAir(burrow, cursor.set(x, ceilingY - 1, z),
                            Blocks.HANGING_ROOTS.defaultBlockState(), clamp);
                }
            }
        }
    }

    // --- Probes ---------------------------------------------------------------

    /**
     * Whether this is a nest worth furnishing.
     *
     * <p>Open in the middle with something under it, open well out in all four
     * directions, and - the test a chamber cannot pass - still open at
     * {@code CORRIDOR_HEIGHT + 1}, which is exactly as far as
     * {@code TunnelDecorator}'s ceiling probe searches. That is the height contract
     * read from this side, and it is the guard that lets {@link #furnish} be called
     * on any position at all.</p>
     */
    private static boolean isNest(ServerLevel burrow, int cx, int wy, int cz) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (!burrow.isLoaded(cursor.set(cx, wy, cz)) || !isOpen(burrow.getBlockState(cursor))) {
            return false;
        }
        if (burrow.getBlockState(cursor.set(cx, wy - 1, cz)).isAir()) {
            return false;
        }
        if (!burrow.isLoaded(cursor.set(cx, wy + BurrowGeometry.CORRIDOR_HEIGHT + 1, cz))
                || !isOpen(burrow.getBlockState(cursor))) {
            return false;
        }

        for (Direction side : Direction.Plane.HORIZONTAL) {
            cursor.set(cx + side.getStepX() * (RADIUS - 2), wy, cz + side.getStepZ() * (RADIUS - 2));
            if (!burrow.isLoaded(cursor) || !isOpen(burrow.getBlockState(cursor))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The block that caps this column.
     *
     * <p>Threads already grown on the dome count as ceiling and roots already hung
     * count as open, which is {@link ChamberFurnisher}'s trick and the whole of what
     * makes this class order independent: a column measured after somebody else's
     * pass has to measure the same as one measured before it. Anything else overhead
     * means a player has built up there, and then this column gets nothing.</p>
     */
    private static int ceilingOf(ServerLevel burrow, int x, int walkY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = walkY + 1; y <= walkY + HEIGHT; y++) {
            if (!burrow.isLoaded(cursor.set(x, y, z))) {
                return NO_LEVEL;
            }
            BlockState state = burrow.getBlockState(cursor);
            if (isCeilingBlock(state)) {
                return y;
            }
            if (!isOpen(state)) {
                return NO_LEVEL;
            }
        }
        return NO_LEVEL;
    }

    /**
     * Nest space: air, or something this class stands in it without closing it off.
     *
     * <p>The list has to include everything {@link #furnish} puts into open space or
     * the room measures smaller every time it is dressed - which is the bug
     * {@code TunnelDecorator} was caught by and the reason its probe reads through
     * this rather than through {@code isAir}. It is deliberately short: the bedding
     * and the warm hollow are laid <em>below</em> the walking surface and the wall
     * pockets are cut outside the room, so the only solid this class ever leaves
     * standing in the open volume is the root beam.</p>
     */
    private static boolean isOpen(BlockState state) {
        return state.isAir()
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(ModBlocks.ROOT_BEAM.get());
    }

    /**
     * Ground nobody has made anything of yet: the fill of the dimension, or its
     * lining.
     *
     * <p>Deliberately <em>not</em> {@link ModBlocks#ROOT_NODULE}, which
     * {@code CorridorCarver.liningAt} scatters through every surface it lines.
     * Whether a block is a nodule is a pure function of where it is, and dressing
     * over one would make that false - a pocket would survive or not depending on
     * which room happened to be built around it. So a nodule in a nest wall stays a
     * nodule, and the square it occupies simply goes undressed. At the rate the
     * lining places them that is a block or two in the whole room.</p>
     *
     * <p>{@link #placeTrove} is the single exception, and only over the twenty
     * blocks of the warm hollow. See there for why the treasure that defines a spot
     * is not the same thing as dressing scattered over it.</p>
     */
    private static boolean isRawGround(BlockState state) {
        return state.is(ModBlocks.DEEP_EARTH.get()) || state.is(ModBlocks.LOOSE_SOIL.get());
    }

    /**
     * What counts as the roof of the room.
     *
     * <p>Wider than {@link #isRawGround}, and the gap between the two is the point:
     * a nodule is a ceiling this pass must <em>recognise</em> and must not
     * <em>write to</em>. Recognising it costs nothing and keeps a pillar standing
     * and the column measured; leaving it alone keeps the pocket. Run the two
     * together and one nodule in a dome either eats a root pillar or eats a find.</p>
     *
     * <p>{@code TunnelDecorator} draws the same distinction between its own
     * {@code isCeiling} and {@code ours}, which is where the shape of this came
     * from.</p>
     */
    private static boolean isCeilingBlock(BlockState state) {
        return isRawGround(state)
                || state.is(ModBlocks.ROOT_NODULE.get())
                || state.is(ModBlocks.GLOW_MYCELIUM.get());
    }

    // --- Placement ------------------------------------------------------------

    /**
     * Turns raw ground into something.
     *
     * <p>{@link ChamberFurnisher#replaceEarth}'s rule and its whole argument: raw
     * ground and nothing else, so this cannot touch air a player dug, a decoration
     * that has made its choice, or a block anybody built with - and calling
     * {@link #furnish} again finds its own work already done.</p>
     */
    private static boolean replaceGround(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if (!writes(clamp, pos) || !burrow.isLoaded(pos)) {
            return false;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!isRawGround(existing)) {
            return false;
        }
        if (existing == state) {
            return true;
        }
        return burrow.setBlock(pos, state, PLACE_FLAGS);
    }

    /**
     * Lays one block of the warm hollow, over an ambient pocket as readily as over
     * lining.
     *
     * <p>The one exception to {@link #isRawGround}'s rule in this class, and its
     * scope is exactly the twenty blocks the hollow is made of: the lid, the two
     * courses of trove and the frame of threads around them.</p>
     *
     * <p><strong>Why the exception is not a hole in the pocket rule.</strong> That
     * rule protects a pocket from <em>dressing</em> - from moss, hay, floor
     * materials, ceiling light, everything this class scatters around a room - so
     * that whether a block is a pocket stays a function of where it is rather than
     * of what happened to be built over it. The hollow is not dressing. It is the
     * one authored treasure in the colony, and it defines the spot rather than
     * decorating it; an ambient pocket that hashed onto it is not a find being
     * destroyed but a smaller find being replaced by a larger one in the same
     * place.</p>
     *
     * <p>Without it the hollow comes out wrong roughly one nest in twenty, and
     * silently: {@code CorridorCarver.liningAt} hashes a pocket into six blocks in a
     * thousand of everything it lines, the hollow's own courses are lined before
     * they are packed, and a larder that refused to be written left the trove three
     * larders and five nodules instead of four and four. A missing frame block is
     * worse than a missing larder - the frame is what carries the light the
     * glow-worm gate reads, so a hole in it would take the payload off a larder that
     * did get placed.</p>
     */
    private static void placeTrove(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if (!writes(clamp, pos) || !burrow.isLoaded(pos)) {
            return;
        }
        BlockState existing = burrow.getBlockState(pos);
        if (!isRawGround(existing) && !existing.is(ModBlocks.ROOT_NODULE.get())) {
            return;
        }
        if (existing != state) {
            burrow.setBlock(pos, state, PLACE_FLAGS);
        }
    }

    /** Opens raw ground up. The same rule, and the only thing here that removes anything. */
    private static void clearGround(ServerLevel burrow, BlockPos pos, @Nullable BoundingBox clamp) {
        replaceGround(burrow, pos, Blocks.AIR.defaultBlockState(), clamp);
    }

    /** Fills open space. Air only, so a second visit never stacks a decoration onto the one it left. */
    private static void fillAir(ServerLevel burrow, BlockPos pos, BlockState state,
            @Nullable BoundingBox clamp) {
        if (writes(clamp, pos) && burrow.isLoaded(pos) && burrow.getBlockState(pos).isAir()) {
            burrow.setBlock(pos, state, PLACE_FLAGS);
        }
    }

    // --- The clamp ------------------------------------------------------------

    /** Whether a write at this position is this call's to make. Null means every position is. */
    private static boolean writes(@Nullable BoundingBox clamp, BlockPos pos) {
        return clamp == null || clamp.isInside(pos);
    }

    private static boolean writes(@Nullable BoundingBox clamp, int x, int y, int z) {
        return clamp == null || clamp.isInside(x, y, z);
    }

    /**
     * Whether this column is worth probing at all.
     *
     * <p>Everything with a ceiling probe in front of it asks this first: the probe is
     * a dozen block reads up the room and it is pure waste when the column it would
     * decide about is somebody else's to write.</p>
     */
    private static boolean writesColumn(@Nullable BoundingBox clamp, int x, int z, int spread) {
        return clamp == null || clamp.intersects(x - spread, z - spread, x + spread, z + spread);
    }

    /**
     * Whether a box misses the clamp entirely.
     *
     * <p>Written out rather than built as a {@link BoundingBox} and handed to
     * {@code intersects}, which is {@code CorridorCarver}'s reasoning: six
     * comparisons beat an allocation on a path a chunk walks for every feature that
     * comes anywhere near it.</p>
     */
    private static boolean misses(BoundingBox clamp, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        return clamp.maxX() < minX || clamp.minX() > maxX
                || clamp.maxY() < minY || clamp.minY() > maxY
                || clamp.maxZ() < minZ || clamp.minZ() > maxZ;
    }

    private static boolean misses(BoundingBox clamp, BoundingBox box) {
        return misses(clamp, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    // --- Patches --------------------------------------------------------------

    /**
     * The integer disc the whole dimension is cut with:
     * {@code radius * radius + radius} is the squared radius of a circle drawn half a
     * block outside the ring, which keeps the diagonals from being cut back to a plus
     * sign.
     */
    private static boolean withinDisc(int dx, int dz, int radius) {
        return radius >= 0 && dx * dx + dz * dz <= radius * radius + radius;
    }

    /** A block or two off where something would otherwise sit exactly. */
    private static int jitter(long salt, int a, int b, int c) {
        return (int) (CorridorCarver.noise(salt, a, b, c) * (PILLAR_JITTER * 2 + 1)) - PILLAR_JITTER;
    }
}

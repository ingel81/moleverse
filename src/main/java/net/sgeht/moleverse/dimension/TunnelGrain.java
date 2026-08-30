package net.sgeht.moleverse.dimension;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.sgeht.moleverse.registry.ModBlocks;

/**
 * What the earth is like around here.
 *
 * <p>A decoration pass that rolls every block on its own gives speckle: gravel
 * beside moss beside a root, evenly, forever, and a corridor that looks the same
 * in both directions no matter how far you walk. Real ground is not like that. A
 * spadeful taken a few paces along is a different spadeful - it is all root here,
 * all stone there, and quite often nothing at all.</p>
 *
 * <p>So the ground is divided into cells of {@link #CELL} blocks square, each cell
 * draws one grain, and every individual roll in {@link TunnelDecorator} asks the
 * grain first. Gravel is common where the ground is stony and absent where it is
 * not; roots cross a rooty stretch and stay out of a bare one; the walls of a
 * mossy stretch are green and the walls of a bare stretch are earth. Nothing is
 * forbidden anywhere - the shares below only tilt the odds - but the tilt is
 * strong enough that a stretch has a character you could describe.</p>
 *
 * <p>{@link #BARE} is the one that does the most work, and it is the easiest to
 * mistake for a gap in the design. A corridor where everything is happening is a
 * corridor where nothing is: the plain stretches are what the other three are read
 * against.</p>
 *
 * <p>The cell is keyed on world x and z alone. It knows nothing about which way a
 * run goes, so two corridors crossing a cell - or one corridor whose axis is
 * measured differently from one call to the next - still agree about the ground
 * they are cut through, and a grain never shifts under a re-decoration.</p>
 *
 * <h2>The dials are not final</h2>
 *
 * <p>Every share and every chance below is a mutable static rather than a
 * constant, so that {@code /moleverse burrow panel} can move it while somebody is
 * standing in the corridor it describes - see
 * {@code client.debug.BurrowTunePanel} for why that is the only way a number like
 * {@link #MOSSY_FRINGE} can be judged at all. <b>The value written here is the
 * shipped one.</b> The panel never writes back: a number settled at the slider is
 * baked in by editing this file. The material tables further down stay where they
 * are, because a table is a decision rather than a dial.</p>
 *
 * <p>Public for the panel's sake and for that alone - it lives in another package.
 * The enum's own methods stay package-private, so what the rest of the mod can see
 * is unchanged.</p>
 */
public enum TunnelGrain {

    /** Roots break the earth apart: rooted dirt underfoot, root beams jutting out of the walls. */
    ROOTY,

    /** Damp and green. Clay and packed mud, moss over the floor, and the ground a seep is at home in. */
    MOSSY,

    /** Stone in the soil. At this scale a pebble is a boulder and a band of tuff is a cliff in the wall. */
    STONY,

    /** Plain earth, and the rests between the other three. Without it none of them read as anything. */
    BARE;

    /** Edge length of a cell of one grain. Eight is roughly two strides at this scale - long enough to notice, short enough to change. */
    public static int CELL = 8;

    /** Share of the ground that is rooty. The three shares below take from the top; whatever is left over is {@link #BARE}. */
    public static float ROOTY_SHARE = 0.30F;

    /** Share of the ground that is mossy. Kept under the rooty share because damp ground reads as a place, and places should be rarer than background. */
    public static float MOSSY_SHARE = 0.20F;

    /** Share of the ground that is stony. */
    public static float STONY_SHARE = 0.25F;

    // --- Wall texture --------------------------------------------------------

    /** Chance a band of rooty wall is a vein of something rather than plain earth. Roots tear the soil up, so this wall is busy. */
    public static float ROOTY_WALL_VEIN = 0.38F;

    /** Chance a band of mossy wall is a vein. */
    public static float MOSSY_WALL_VEIN = 0.33F;

    /** Chance a band of stony wall is a vein. The highest of the four: banded stone is the whole point of a stony stretch. */
    public static float STONY_WALL_VEIN = 0.42F;

    /** Chance a band of bare wall is a vein. Low on purpose - a bare wall that is nearly plain is what makes the other three walls readable. */
    public static float BARE_WALL_VEIN = 0.12F;

    // --- Ceiling texture -----------------------------------------------------

    /** Chance a ceiling pocket forms in rooty ground. */
    public static float ROOTY_CEILING_GRIT = 0.36F;

    /** Chance a ceiling pocket forms in mossy ground. */
    public static float MOSSY_CEILING_GRIT = 0.29F;

    /** Chance a ceiling pocket forms in stony ground. Grit overhead is what tells you the stone is above as well as beside you. */
    public static float STONY_CEILING_GRIT = 0.52F;

    /** Chance a ceiling pocket forms in bare ground. */
    public static float BARE_CEILING_GRIT = 0.13F;

    // --- Root stubs ----------------------------------------------------------

    /** Chance a rooty wall block pushes a root stub out into the corridor. The only obstacle down here you meet at shoulder height. */
    public static float ROOTY_ROOT_STUB = 0.15F;

    /** Chance a mossy wall block pushes a stub out. */
    public static float MOSSY_ROOT_STUB = 0.06F;

    /** Chance a stony wall block pushes a stub out. Nearly none: roots go around stone, not through it. */
    public static float STONY_ROOT_STUB = 0.02F;

    /** Chance a bare wall block pushes a stub out. */
    public static float BARE_ROOT_STUB = 0.04F;

    // --- The fringe ----------------------------------------------------------

    /** How much grows in the corners of a rooty stretch. */
    public static float ROOTY_FRINGE = 0.30F;

    /** How much grows in the corners of a mossy stretch. The wettest ground and the lushest edges. */
    public static float MOSSY_FRINGE = 0.42F;

    /** How much grows in the corners of a stony stretch. Little takes hold between the stones. */
    public static float STONY_FRINGE = 0.10F;

    /** How much grows in the corners of a bare stretch. */
    public static float BARE_FRINGE = 0.16F;

    // Salts. The material tables live here and the placement salts live in
    // TunnelDecorator; the two sets keep separate family prefixes so that moving a
    // roll between the files can never silently make it agree with another one.
    private static final long SALT_GRAIN = 0x6BA1_0000L;
    private static final long SALT_FLOOR_KIND = 0x6BA1_0001L;
    private static final long SALT_WALL_KIND = 0x6BA1_0002L;
    private static final long SALT_CEILING_KIND = 0x6BA1_0003L;
    private static final long SALT_PATH_KIND = 0x6BA1_0004L;
    private static final long SALT_BANK_KIND = 0x6BA1_0005L;

    /** The grain of the cell this column falls in. */
    static TunnelGrain at(int x, int z) {
        float roll = TunnelNoise.at(SALT_GRAIN, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), 0);
        if (roll < ROOTY_SHARE) {
            return ROOTY;
        }
        if (roll < ROOTY_SHARE + MOSSY_SHARE) {
            return MOSSY;
        }
        if (roll < ROOTY_SHARE + MOSSY_SHARE + STONY_SHARE) {
            return STONY;
        }
        return BARE;
    }

    /**
     * What the ground beside a seep is made of.
     *
     * <p>The one table that ignores the grain. Water decides what the ground next
     * to it is like whatever the ground was before, which is exactly why a seep
     * reads as an event: for two blocks around it the corridor stops being
     * whatever stretch it was in.</p>
     */
    static BlockState bankOf(int x, int y, int z) {
        int roll = (int) (TunnelNoise.at(SALT_BANK_KIND, x, y, z) * 100.0F);
        if (roll < 45) {
            return Blocks.MUD.defaultBlockState();
        }
        if (roll < 80) {
            return Blocks.CLAY.defaultBlockState();
        }
        return Blocks.PACKED_MUD.defaultBlockState();
    }

    /**
     * What a stretch of floor is made of.
     *
     * <p>Chosen once per stretch rather than per square: a length of corridor that
     * is gravel and then coarse dirt and then soil reads as a place that changes,
     * whereas the same blocks shuffled square by square read as noise.</p>
     *
     * <p>Loose soil is in every column of the table, because it is the burrow's own
     * material and has to stay the thing a corridor is normally made of. If it ever
     * stops being the most likely answer overall, the rest stops being a change of
     * pace and becomes the pace.</p>
     */
    BlockState floorOf(int cell, int axisId) {
        int roll = (int) (TunnelNoise.at(SALT_FLOOR_KIND, cell, axisId, 0) * 100.0F);
        return switch (this) {
            case ROOTY -> {
                if (roll < 38) {
                    yield Blocks.ROOTED_DIRT.defaultBlockState();
                }
                if (roll < 68) {
                    yield ModBlocks.LOOSE_SOIL.get().defaultBlockState();
                }
                if (roll < 88) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                yield Blocks.MOSS_BLOCK.defaultBlockState();
            }
            case MOSSY -> {
                if (roll < 34) {
                    yield Blocks.MOSS_BLOCK.defaultBlockState();
                }
                if (roll < 60) {
                    yield ModBlocks.LOOSE_SOIL.get().defaultBlockState();
                }
                if (roll < 76) {
                    yield Blocks.CLAY.defaultBlockState();
                }
                if (roll < 90) {
                    yield Blocks.PACKED_MUD.defaultBlockState();
                }
                yield Blocks.ROOTED_DIRT.defaultBlockState();
            }
            case STONY -> {
                if (roll < 34) {
                    yield Blocks.GRAVEL.defaultBlockState();
                }
                if (roll < 62) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                if (roll < 84) {
                    yield ModBlocks.LOOSE_SOIL.get().defaultBlockState();
                }
                if (roll < 94) {
                    yield Blocks.TUFF.defaultBlockState();
                }
                yield Blocks.ANDESITE.defaultBlockState();
            }
            case BARE -> {
                if (roll < 72) {
                    yield ModBlocks.LOOSE_SOIL.get().defaultBlockState();
                }
                if (roll < 90) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                yield Blocks.ROOTED_DIRT.defaultBlockState();
            }
        };
    }

    /**
     * What one block of wall is.
     *
     * <p>All full blocks, so that a mineral bud or a root stub can hang off any of
     * them without asking what it is hanging from.</p>
     *
     * <p>The pillar in the rooty column is laid along the run rather than upright.
     * A root that follows the corridor reads as the thing the corridor was dug
     * beside; the same block standing on end reads as a log somebody buried.</p>
     */
    BlockState wallOf(int x, int y, int z, Direction.Axis runAxis) {
        int roll = (int) (TunnelNoise.at(SALT_WALL_KIND, x, y, z) * 100.0F);
        return switch (this) {
            case ROOTY -> {
                if (roll < 44) {
                    yield Blocks.ROOTED_DIRT.defaultBlockState();
                }
                if (roll < 74) {
                    yield Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState().setValue(RotatedPillarBlock.AXIS, runAxis);
                }
                if (roll < 92) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                yield Blocks.MOSS_BLOCK.defaultBlockState();
            }
            case MOSSY -> {
                if (roll < 40) {
                    yield Blocks.MOSS_BLOCK.defaultBlockState();
                }
                if (roll < 64) {
                    yield Blocks.CLAY.defaultBlockState();
                }
                if (roll < 86) {
                    yield Blocks.ROOTED_DIRT.defaultBlockState();
                }
                yield Blocks.PACKED_MUD.defaultBlockState();
            }
            case STONY -> {
                if (roll < 38) {
                    yield Blocks.TUFF.defaultBlockState();
                }
                if (roll < 64) {
                    yield Blocks.ANDESITE.defaultBlockState();
                }
                if (roll < 86) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                yield Blocks.CLAY.defaultBlockState();
            }
            case BARE -> {
                if (roll < 55) {
                    yield Blocks.COARSE_DIRT.defaultBlockState();
                }
                if (roll < 85) {
                    yield Blocks.ROOTED_DIRT.defaultBlockState();
                }
                yield Blocks.CLAY.defaultBlockState();
            }
        };
    }

    /**
     * What a pocket in the ceiling is.
     *
     * <p>Shorter tables than the walls, because a ceiling is seen from below and
     * from a distance: two materials that differ clearly beat four that blur into
     * one grey. Whatever is added here has to be added to
     * {@code TunnelDecorator.isCeiling} in the same edit, or the ceiling stops
     * being recognised as a ceiling and climbs a block on every visit.</p>
     */
    BlockState ceilingGritOf(int x, int y, int z, Direction.Axis runAxis) {
        int roll = (int) (TunnelNoise.at(SALT_CEILING_KIND, x, y, z) * 100.0F);
        return switch (this) {
            case ROOTY -> roll < 60
                    ? Blocks.ROOTED_DIRT.defaultBlockState()
                    : Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState().setValue(RotatedPillarBlock.AXIS, runAxis);
            case MOSSY -> roll < 55 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.CLAY.defaultBlockState();
            case STONY -> {
                if (roll < 50) {
                    yield Blocks.TUFF.defaultBlockState();
                }
                if (roll < 80) {
                    yield Blocks.ANDESITE.defaultBlockState();
                }
                yield Blocks.COARSE_DIRT.defaultBlockState();
            }
            case BARE -> Blocks.COARSE_DIRT.defaultBlockState();
        };
    }

    /**
     * What the pressed ground either side of the walking line is.
     *
     * <p>Two materials at most per grain, and both of them dark and flat. A path is
     * recognised by being duller than what surrounds it, not by being another
     * colour.</p>
     */
    BlockState pathOf(int x, int y, int z) {
        int roll = (int) (TunnelNoise.at(SALT_PATH_KIND, x, y, z) * 100.0F);
        return switch (this) {
            case ROOTY -> roll < 70 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
            case MOSSY -> roll < 65 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            case STONY -> roll < 60 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            case BARE -> roll < 80 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
        };
    }

    /** How likely one band of wall at one height is a vein rather than plain earth. */
    float wallVeinChance() {
        return switch (this) {
            case ROOTY -> ROOTY_WALL_VEIN;
            case MOSSY -> MOSSY_WALL_VEIN;
            case STONY -> STONY_WALL_VEIN;
            case BARE -> BARE_WALL_VEIN;
        };
    }

    /** How likely a pocket of grit sits in the ceiling here. */
    float ceilingGritChance() {
        return switch (this) {
            case ROOTY -> ROOTY_CEILING_GRIT;
            case MOSSY -> MOSSY_CEILING_GRIT;
            case STONY -> STONY_CEILING_GRIT;
            case BARE -> BARE_CEILING_GRIT;
        };
    }

    /** How likely a wall block pushes a root stub out into the corridor. */
    float rootStubChance() {
        return switch (this) {
            case ROOTY -> ROOTY_ROOT_STUB;
            case MOSSY -> MOSSY_ROOT_STUB;
            case STONY -> STONY_ROOT_STUB;
            case BARE -> BARE_ROOT_STUB;
        };
    }

    /**
     * How much life the corners of a corridor hold here.
     *
     * <p>One number for the whole fringe rather than one per thing that grows in
     * it. Moss along the foot of a wall, a mushroom in the angle and root ends
     * trailing off the crown are all answers to the same question - how damp and
     * how alive this stretch of earth is - and a single dial keeps them agreeing:
     * a lush corner is lush in all three at once, and a stony one is bare in all
     * three, instead of a mossy stretch coming out with no mushrooms in it because
     * two tables happened to disagree. {@code TunnelDecorator} splits this one roll
     * between them.</p>
     */
    float fringeChance() {
        return switch (this) {
            case ROOTY -> ROOTY_FRINGE;
            case MOSSY -> MOSSY_FRINGE;
            case STONY -> STONY_FRINGE;
            case BARE -> BARE_FRINGE;
        };
    }
}

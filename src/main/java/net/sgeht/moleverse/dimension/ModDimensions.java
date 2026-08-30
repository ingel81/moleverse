package net.sgeht.moleverse.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.dimension.DimensionType;
import net.sgeht.moleverse.Moleverse;
import org.jetbrains.annotations.Nullable;

/**
 * Keys for the burrow dimension, and the two lookups every caller needs.
 *
 * <p>The dimension itself is data, not code: {@code data/moleverse/dimension/burrow.json}
 * and {@code data/moleverse/dimension_type/burrow.json}. There is nothing to register
 * on a bus - the server reads both at load. This class exists so that the identifier
 * is written down once.</p>
 *
 * <h2>Why a flat generator with a single block</h2>
 *
 * <p>The burrow is solid ground that corridors are cut into afterwards, so world
 * generation only has to produce the ground. {@code minecraft:flat} is the one
 * vanilla generator that does exactly that and nothing else: it no-ops
 * {@code applyCarvers}, {@code buildSurface} and {@code spawnOriginalMobs}, and with
 * {@code features: false} and {@code lakes: false} the biome's own feature list is
 * dropped rather than merged. An empty {@code structure_overrides} switches off the
 * remaining source of unasked-for blocks. Nothing arrives down there that the mod
 * did not put there.</p>
 *
 * <h2>Why moleverse:burrow rather than a borrowed vanilla biome</h2>
 *
 * <p>A biome is only consulted for two things here - mob spawning and the environment
 * attributes it contributes - because the flat generator ignores its features and
 * carvers. That was long enough satisfied by {@code deep_dark}, whose spawner list is
 * empty in every category. What it also carries is a sky colour, and a biome sets that
 * as a positional layer over the dimension's constant one and wins: the pale blue bled
 * into the burrow's fog and had to be fought from client code. The custom biome is what
 * ends that, and it is where the creature waves' spawn lists go.</p>
 *
 * <h2>Settings worth explaining</h2>
 *
 * <p>{@code coordinate_scale: 1.0} because {@link BurrowGeometry} maps overworld
 * coordinates onto burrow coordinates itself; vanilla's own scaling on top would apply
 * the stretch twice and only to horizontal axes.</p>
 *
 * <p>Beds and respawn anchors are off and raids and pillager patrols cannot start,
 * because all four are surface mechanics that would have to pick a spot in a world made
 * of solid rock. {@code piglins_zombify} and {@code water_evaporates} are left at their
 * defaults - the burrow is neither the nether nor a place piglins reach.</p>
 *
 * <p>{@code sky_light_level: 0.0} rather than the nether's 4.0: it drives
 * {@code skyDarken}, so zero is a world that is as dark as the game can call it, and
 * with {@code has_fixed_time} and no timeline nothing ever moves it. Light in the
 * burrow has to be carried or hung.</p>
 */
public final class ModDimensions {

    /** The burrow. Same identifier as the two JSON files that define it. */
    public static final ResourceKey<Level> BURROW =
            ResourceKey.create(Registries.DIMENSION, Moleverse.id("burrow"));

    /**
     * The burrow's dimension type. Only needed by {@link #isBurrow(LevelReader)}, which
     * has no other handle on which dimension it is looking at.
     */
    private static final ResourceKey<DimensionType> BURROW_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Moleverse.id("burrow"));

    private ModDimensions() {
    }

    /**
     * The burrow as a live level.
     *
     * <p>Null when the dimension failed to load - a broken datapack, or a world saved
     * before the mod was added. Callers that dig, teleport or tick down there have to
     * handle that instead of assuming the dimension exists.</p>
     */
    public static @Nullable ServerLevel burrowLevel(MinecraftServer server) {
        return server.getLevel(BURROW);
    }

    public static boolean isBurrow(Level level) {
        return level.dimension() == BURROW;
    }

    /**
     * The same question where only a {@link LevelReader} is in hand - block methods such
     * as {@code canSurvive} get nothing better.
     *
     * <p>{@code LevelReader} carries no dimension key, only the {@link DimensionType}
     * value, so the answer has to come from the registry. The cheap path is tried first
     * because a real {@code Level} is what every runtime caller passes; the registry
     * lookup is left for the readers that appear during world generation, so that a
     * block placed by generation still gets a truthful answer rather than a false.</p>
     */
    public static boolean isBurrow(LevelReader level) {
        if (level instanceof Level realLevel) {
            return isBurrow(realLevel);
        }
        return level.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getValue(BURROW_TYPE)
                == level.dimensionType();
    }
}

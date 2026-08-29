package net.sgeht.moleverse.dimension;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.sgeht.moleverse.entity.GreatWorm;
import net.sgeht.moleverse.registry.ModEntities;

/**
 * Puts the burrow's own animal into the burrow.
 *
 * <p>Not a spawn rule in a biome file, deliberately. The burrow borrows a vanilla
 * biome, and anything added there would appear in that biome in the overworld
 * too - a great worm crawling through a deep dark cavern is not the joke this mod
 * is making. So the dimension stocks itself, at the one moment it already has a
 * player's attention and a freshly carved corridor to put something in.</p>
 *
 * <p>It counts before it spawns. A player who walks in and out of a mound ten
 * times must not end up with ten worms in one gallery, and a cap is a cheaper
 * answer to that than a timer.</p>
 */
public final class BurrowLife {

    /** How far around an arrival worms are counted and placed. */
    private static final int RANGE = 48;

    /** At most this many within that range. A corridor with two in it is busy. */
    private static final int CAP = 2;

    /** Tries at finding air with a floor before giving up on a worm. */
    private static final int ATTEMPTS = 12;

    private BurrowLife() {
    }

    /**
     * Stocks the corridors around a chamber, if they are not stocked already.
     *
     * <p>Called after carving rather than during it: a worm placed in a corridor
     * that is still being dug would be pushed out of the wall it is standing
     * in.</p>
     */
    public static void stock(ServerLevel burrow, BlockPos chamber) {
        AABB around = AABB.ofSize(chamber.getCenter(), RANGE * 2.0, 32.0, RANGE * 2.0);
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

            GreatWorm worm = ModEntities.GREAT_WORM.get().create(burrow, EntitySpawnReason.NATURAL);
            if (worm == null) {
                return;
            }
            worm.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            burrow.addFreshEntity(worm);
        }
    }

    /**
     * A carved spot with a floor under it and room above.
     *
     * <p>Searched rather than computed, because where the corridors run is a
     * property of the colony above and not something this class can know. Air
     * with solid ground under it is carved corridor by definition - nothing else
     * down there is hollow.</p>
     */
    private static BlockPos findRoom(ServerLevel burrow, BlockPos chamber, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            cursor.set(
                    chamber.getX() + random.nextInt(RANGE * 2) - RANGE,
                    chamber.getY() + random.nextInt(9) - 4,
                    chamber.getZ() + random.nextInt(RANGE * 2) - RANGE);

            if (!burrow.isLoaded(cursor)) {
                continue;
            }
            boolean roomy = burrow.getBlockState(cursor).isAir()
                    && burrow.getBlockState(cursor.above()).isAir()
                    && !burrow.getBlockState(cursor.below()).isAir();
            if (roomy) {
                return cursor.immutable();
            }
        }
        return null;
    }
}

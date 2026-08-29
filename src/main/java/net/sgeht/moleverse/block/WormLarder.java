package net.sgeht.moleverse.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Earth packed solid with paralysed earthworms: a mole's winter store.
 *
 * <p>Real moles bite an earthworm through the head segments, which paralyses it
 * without killing it, and pack it away in a cache near the nest - hundreds of
 * them, kept alive against a frost when the soil is too hard to hunt in. It is
 * the one thing a mole does that needs a room rather than a tunnel, which is why
 * it is what a chamber in the burrow is built around. See
 * {@code ChamberFurnisher}, which is the only thing that places it.</p>
 *
 * <p>No block entity, and it does not need one. A larder holds nothing that
 * varies: it is not an inventory, it does not fill up again, and what a player
 * gets out of it is the loot table's business. Every worm in it is in the block,
 * and breaking the block is how you get them.</p>
 *
 * <p>The class exists for one thing beyond the name: {@link #animateTick}. A
 * larder sits in an unlit recess most of the time, and a wall that occasionally
 * sheds a few crumbs is a wall a player walks over to look at. Nothing here runs
 * on the server.</p>
 */
public class WormLarder extends Block {

    public static final MapCodec<WormLarder> CODEC = simpleCodec(WormLarder::new);

    /**
     * One animation tick in this many does anything.
     *
     * <p>The client already calls {@code animateTick} on a random sample of the
     * blocks near the player rather than on all of them, so this only has to take
     * the edge off. Low enough that a larder in view crumbles every few seconds,
     * high enough that a wall of them is not a dust storm.</p>
     */
    private static final int CRUMBLE_CHANCE = 4;

    /** How far outside the face a crumb starts. Just clear of it, or the particle is drawn inside the block and never seen. */
    private static final double FACE_OFFSET = 0.55;

    /** Spread across the face. A quarter of a block either way keeps the crumbs to one spot rather than dusting the whole side. */
    private static final double FACE_SPREAD = 0.25;

    public WormLarder(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<WormLarder> codec() {
        return CODEC;
    }

    /**
     * Sheds a few crumbs of earth off an open face, now and then.
     *
     * <p>Something alive is shifting in there, and this is all the room the block
     * has to say so. Only a face with air against it: a larder is normally packed
     * into a wall on five sides and dust drawn inside the earth is dust nobody
     * sees.</p>
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(CRUMBLE_CHANCE) != 0) {
            return;
        }

        Direction face = Direction.getRandom(random);
        if (!level.getBlockState(pos.relative(face)).isAir()) {
            return;
        }

        // Spread across the face but not along its normal, so the crumbs sit on
        // the surface rather than hanging in the air in front of it. Speed stays
        // zero: mycelium particles drift on their own, and pushing them makes
        // them read as something being thrown rather than shaken loose.
        double x = pos.getX() + 0.5 + face.getStepX() * FACE_OFFSET + spread(random, face.getStepX());
        double y = pos.getY() + 0.5 + face.getStepY() * FACE_OFFSET + spread(random, face.getStepY());
        double z = pos.getZ() + 0.5 + face.getStepZ() * FACE_OFFSET + spread(random, face.getStepZ());
        level.addParticle(ParticleTypes.MYCELIUM, x, y, z, 0.0, 0.0, 0.0);
    }

    private static double spread(RandomSource random, int step) {
        return step != 0 ? 0.0 : (random.nextDouble() - 0.5) * 2.0 * FACE_SPREAD;
    }
}

package net.sgeht.moleverse.dimension.plan;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.NestCarver;

/**
 * The one room a colony lives in, at the middle of everything it dug.
 *
 * <p>A wrapper and nothing more: {@link NestCarver} already knows where a nest
 * belongs, how to cut one and how to bed it, and this only puts the questions a
 * chunk asks in front of it. The colony is what names it - there is exactly one
 * nest per colony and there is no second thing it could be confused with, so the id
 * is a name and needs nothing after it.</p>
 *
 * <h2>It is the tallest thing in the burrow, and that is a contract</h2>
 *
 * <p>{@code NestCarver.HEIGHT} is twelve against a corridor's six and a junction's
 * eight, well past {@code CorridorProfile.MAX_LIT_HEIGHT}. So a
 * {@code TunnelDecorator} sweep that wanders into a nest finds no ceiling within
 * reach, reads the slice as a room rather than as a corridor, and leaves it alone -
 * see {@link net.sgeht.moleverse.dimension.Junctions} for the argument in full.
 * That is exactly right here: everything in the room is the nest's own dressing and
 * a corridor's trodden line laid across a bed would fight it.</p>
 *
 * <p>The spurs are the deliberate exception. They are cut at a corridor's own
 * section, so a sweep that reaches one dresses it as the corridor it is meant to
 * be.</p>
 */
public record NestFeature(NestCarver.Nest nest) implements BurrowFeature {

    @Override
    public String key() {
        return "nest:" + this.nest.colony();
    }

    /**
     * Where the room goes, and where its spurs reach.
     *
     * <p>The core is folded in as well as the centre, so that a nest can never
     * inherit a ledger entry from a colony that was removed and had its id reused.
     * The spurs are in it because they are the half of this feature that moves: a
     * colony that digs a new run nearer its core re-aims them, and the room they
     * leave has to be cut again for the new mouths even though the room itself has
     * not moved a block.</p>
     *
     * <p>It reaches as far as the numbers and no further, which is
     * {@code CorridorFeature}'s own caveat: retuning the room's radius or its dome
     * changes the shape without changing anything folded in here. That is what
     * {@link BurrowFeature#HASH_SEED}'s generation counter is for.</p>
     */
    @Override
    public int contentHash() {
        int hash = BurrowFeature.HASH_SEED;
        hash = BurrowFeature.fold(hash, this.nest.colony());
        hash = BurrowFeature.fold(hash, this.nest.core());
        hash = BurrowFeature.fold(hash, this.nest.centre());
        for (NestCarver.Spur spur : this.nest.spurs()) {
            hash = BurrowFeature.fold(hash, spur.from());
            hash = BurrowFeature.fold(hash, spur.to());
        }
        return hash;
    }

    @Override
    public BoundingBox bounds() {
        return this.nest.bounds();
    }

    /**
     * Cuts the room and its spurs.
     *
     * <p>Always true: a nest is cut out of whatever earth is in front of it and has
     * no precondition a later visit could satisfy. A spur whose corridor has not been
     * carved yet still gets cut, and ends in earth at the place that corridor will
     * open - which is the same bargain a corridor makes with the chamber at its
     * end.</p>
     */
    @Override
    public boolean carveWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        NestCarver.cut(burrow, this.nest, chunkClamp);
        return true;
    }

    /**
     * Beds the room, once there is a room to measure.
     *
     * <p>This is the half that has to wait. The pillars and the dome's light probe
     * upwards for a ceiling, and a probe run while a neighbouring chunk is still
     * solid earth reads the chunk border as the top of the room and lights it there -
     * which nothing afterwards takes away, because a lit block is not deep earth any
     * more.</p>
     *
     * <p>It dresses whenever the clamp overlaps the room at all, not only from the
     * chunk that holds the middle. Every decision comes from the block position about
     * to be written to, so a nest bedded in quarters by four chunks in any order
     * comes out the same room - which is what {@code NestCarver} says it was built
     * for.</p>
     */
    @Override
    public void decorateWithin(ServerLevel burrow, @Nullable BoundingBox chunkClamp) {
        NestCarver.furnish(burrow, this.nest, chunkClamp);
    }
}

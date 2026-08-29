package net.sgeht.moleverse.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.RunLevel;

/**
 * The runs stored around a player, sent to that player's client so the debug
 * overlay can draw them.
 *
 * <p>Needed because tunnels are the one thing particles cannot show. A colony
 * border lies on the surface and a particle on it is visible; a run lies two to
 * six blocks down, and a particle inside solid ground is hidden by the very
 * blocks it is meant to reveal. The overlay draws its lines "always on top",
 * which is exactly what is wanted here - but it lives on the client and the runs
 * live in server data, so they have to travel.</p>
 *
 * <p>Debug traffic, and it behaves like it: nothing is sent unless somebody
 * turned the view on with {@code /moleverse colony tunnels on}.</p>
 */
public record BurrowLinksPayload(List<Run> runs) implements CustomPacketPayload {

    public static final Type<BurrowLinksPayload> TYPE = new Type<>(Moleverse.id("burrow_links"));

    /**
     * One run, in the shape the client needs to draw it: two ends, a level, and
     * one height per waypoint. The same fields {@link BurrowLink} stores, minus
     * the bookkeeping nobody can see.
     */
    public record Run(BlockPos a, BlockPos b, int level, List<Integer> depths) {

        public static final StreamCodec<ByteBuf, Run> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Run::a,
                BlockPos.STREAM_CODEC, Run::b,
                ByteBufCodecs.VAR_INT, Run::level,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), Run::depths,
                Run::new);

        public static Run of(BurrowLink link) {
            return new Run(link.a(), link.b(), link.level().ordinal(), link.depths());
        }

        public RunLevel runLevel() {
            return RunLevel.byIndex(this.level);
        }
    }

    public static final StreamCodec<ByteBuf, BurrowLinksPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, Run.STREAM_CODEC), BurrowLinksPayload::runs,
            BurrowLinksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

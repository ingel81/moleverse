package net.sgeht.moleverse.debug;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.network.BurrowLinksPayload;

/**
 * Sends the runs stored around each player to that player, so the network
 * overlay can draw what the server really has.
 *
 * <p>Unlike the colony border this cannot be done with particles: a run lies
 * under the ground, and a particle inside a block is hidden by it. The overlay
 * draws through terrain, which is what makes it the right instrument - it only
 * lacks the data, and this hands it over.</p>
 *
 * <p>Off by default and silent while off. Nothing is sent unless somebody asked
 * for it with {@code /moleverse colony tunnels on}, and when they switch it off
 * the client drops what it has on its own after a moment.</p>
 */
public final class TunnelView {

    /**
     * Ticks between two deliveries.
     *
     * <p>A second. The runs change only when a mole finishes a trip, so this is
     * about picking up new ones rather than keeping a picture alive - the client
     * holds the last delivery for longer than this.</p>
     */
    private static final int INTERVAL = 20;

    /** How far around a player a run still gets sent. */
    private static final int RANGE = 96;

    private static boolean enabled;
    private static int ticks;

    private TunnelView() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        ticks = 0;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Called from the level tick. Does nothing at all while switched off. */
    public static void tick(ServerLevel level) {
        if (!enabled || level.players().isEmpty()) {
            return;
        }
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;

        ColonyStore store = ColonyStore.get(level);
        for (ServerPlayer player : level.players()) {
            List<BurrowLink> near = store.linksNear(player.blockPosition(), RANGE);
            List<BurrowLinksPayload.Run> runs = near.stream().map(BurrowLinksPayload.Run::of).toList();
            PacketDistributor.sendToPlayer(player, new BurrowLinksPayload(runs));
        }
    }
}

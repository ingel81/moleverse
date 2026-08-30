package net.sgeht.moleverse.client.debug;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.dimension.TunnelDecorator;

/**
 * Dresses the corridor the player is standing in again, with whatever the sliders
 * currently say.
 *
 * <p>Decoration is placed once, by the server, when a run is carved. So a slider
 * in the panel's decoration half changes nothing that already exists - it changes
 * what the next stretch of corridor comes out like, and the next stretch is
 * usually a walk away. This is the shortcut: it hands
 * {@link TunnelDecorator#decorate} a grid of positions around the player and lets
 * it dress the corridor it finds there a second time.</p>
 *
 * <h2>Why that is safe, and where it lies</h2>
 *
 * <p>The decorator is idempotent and additive. Every roll it makes is a hash of
 * the block position, so a second pass reaches the same conclusions as the first
 * one; and it only ever writes into the corridor shell or into air, never removing
 * anything. Running it twice with the same numbers therefore does nothing at all,
 * and running it twice with a raised density adds what the raised density asks
 * for.</p>
 *
 * <p><b>A lowered density is the case this cannot show</b>, and the button says so
 * by calling itself additive. Nothing here takes a block away, so turning the
 * light down and pressing it leaves every lamp exactly where it was. The only
 * honest way to read a value downwards is a stretch of corridor that has never
 * been dressed - walk to the end of a run, or carve a new one.</p>
 *
 * <h2>Where it reaches</h2>
 *
 * <p>{@link #RADIUS} blocks of it, sampled every {@link #STEP} on both horizontal
 * axes at the player's own height. Three is close enough that no corridor can slip
 * between two samples: a run is five blocks wide, and any four consecutive columns
 * hold a sample. Vertically it reaches as far as the decorator's own floor search
 * does, which is one corridor height either way - so the run the player is
 * standing in, and not the deck above it.</p>
 *
 * <h2>Single player only, and that is not a limitation worth fixing</h2>
 *
 * <p>The sliders write into statics in the server's own classes. Where this client
 * runs the server - a single player world, or the LAN host the dev client turns
 * itself into - those are the same statics the decorator reads. On a client
 * connected to somebody else's server they are a private copy that nothing will
 * ever read, so the panel greys the decoration half out instead of pretending. The
 * ambience half is client code and works anywhere.</p>
 */
final class BurrowRedress {

    /** How far around the player corridors are dressed again. About a corridor's worth of view. */
    private static final int RADIUS = 24;

    /** Spacing of the sample grid. Under half a corridor's width, so none is missed. */
    private static final int STEP = 3;

    private BurrowRedress() {
    }

    /**
     * Whether the decoration half of the panel can do anything on this client.
     *
     * <p>True in a single player world and on the machine hosting a LAN world -
     * which the dev client always is, because {@code DevWorldPublisher} opens
     * every world it enters.</p>
     */
    static boolean ownsTheServer() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    /**
     * Runs the decorator over the corridor around the player again.
     *
     * <p>Queued onto the server thread, because that is the only thread allowed
     * to write blocks. The position is taken here, on the client, so that walking
     * away between the press and the pass cannot move the work somewhere else.</p>
     */
    static void redress() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !ownsTheServer()) {
            say("Not our world - decoration is placed by the server, and this client is not it.");
            return;
        }
        if (!ModDimensions.isBurrow(player.level())) {
            say("Not in the burrow - there is nothing to dress up here.");
            return;
        }

        BlockPos here = player.blockPosition();
        server.execute(() -> {
            ServerLevel burrow = ModDimensions.burrowLevel(server);
            if (burrow == null) {
                minecraft.execute(() -> say("No burrow dimension - the datapack did not load."));
                return;
            }
            int passes = 0;
            for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
                for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                    BlockPos centre = here.offset(dx, 0, dz);
                    if (!burrow.isLoaded(centre)) {
                        continue;
                    }
                    // The random is handed straight through and deliberately
                    // unused - every roll the decorator makes comes from the
                    // block position. See its class javadoc.
                    TunnelDecorator.decorate(burrow, centre, burrow.getRandom());
                    passes++;
                }
            }
            int done = passes;
            minecraft.execute(() -> say(done + " sample(s) re-dressed within " + RADIUS
                    + " blocks. Additive: nothing was taken away."));
        });
    }

    /** One line into the player's chat. Every message from the panel goes through here. */
    static void say(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.literal(text).withStyle(ChatFormatting.AQUA), false);
        }
    }
}

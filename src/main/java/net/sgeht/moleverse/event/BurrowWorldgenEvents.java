package net.sgeht.moleverse.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.sgeht.moleverse.entity.burrow.WeaselIncursion;
import net.sgeht.moleverse.dimension.BurrowLife;
import net.sgeht.moleverse.entity.burrow.BurrowTraversal;
import net.sgeht.moleverse.Moleverse;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.dimension.plan.BurrowReconciler;

/**
 * The two events that make the burrow build itself.
 *
 * <p>Kept apart from {@link MoleverseGameEvents} because they are one mechanism
 * rather than a handful of unrelated hooks, and because the chunk load handler
 * has a rule attached to it that is worth having in front of whoever edits it
 * next - see {@link #onChunkLoad}.</p>
 *
 * <p>Everything is filtered to the burrow on the server. {@code ChunkEvent.Load}
 * fires on the client too, for every chunk of every dimension, and
 * {@code LevelTickEvent.Post} fires for every level twenty times a second, so the
 * two guards are what keeps this class free on a world nobody has dug under.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class BurrowWorldgenEvents {

    private BurrowWorldgenEvents() {
    }

    /**
     * A burrow chunk became live: note it, and touch nothing.
     *
     * <p><strong>The queue is the whole handler, and it has to stay that way.</strong>
     * The event's own javadoc says it fires before the chunk is promoted to
     * {@code FULL} and that interactions with the level must be delayed until the
     * next game tick to prevent deadlocking the game. Carving here would be
     * carving inside chunk loading.</p>
     *
     * <p>New chunks and chunks read off disk alike, deliberately. "Generated
     * before the colony dug this run" and "never generated at all" have to come
     * out the same, and the ledger is what tells them apart - an old chunk simply
     * finds that everything is missing.</p>
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && ModDimensions.isBurrow(level)) {
            BurrowReconciler.enqueue(event.getChunk().getPos());
        }
    }

    /** A tick later, with the chunk full and the level safe to write to. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && ModDimensions.isBurrow(level)) {
            BurrowReconciler.drain(level);
        BurrowLife.tick(level);
        BurrowTraversal.tick(level);
        WeaselIncursion.tick(level);
        }
    }

    /**
     * The burrow went away: drop the backlog with it.
     *
     * <p>The queue is static, so a single-player client that leaves one world for
     * another would otherwise carry the first world's positions into the second.</p>
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && ModDimensions.isBurrow(level)) {
            BurrowReconciler.forget();
        BurrowTraversal.forget();
        WeaselIncursion.forget();
        }
    }
}

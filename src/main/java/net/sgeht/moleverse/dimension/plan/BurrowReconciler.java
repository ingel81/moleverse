package net.sgeht.moleverse.dimension.plan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sgeht.moleverse.dimension.AlcoveCarver;
import net.sgeht.moleverse.dimension.BoltHoles;
import net.sgeht.moleverse.dimension.BurrowGeometry;
import net.sgeht.moleverse.dimension.ModDimensions;
import net.sgeht.moleverse.dimension.NestCarver;
import net.sgeht.moleverse.entity.burrow.BurrowLink;
import net.sgeht.moleverse.entity.burrow.Colony;
import net.sgeht.moleverse.entity.burrow.ColonyStore;
import net.sgeht.moleverse.registry.ModAttachments;

/**
 * Brings a burrow chunk into line with what the colony above it has dug.
 *
 * <p>The whole point of {@code docs/BURROW_WORLDGEN.md} lands here: carving used
 * to hang on <em>somebody entered the burrow</em> and now hangs on <em>this
 * ground now exists</em>. A chunk asks {@link BurrowPlan} which features pass
 * through it, subtracts what its own {@link BurrowLedger} says it already has,
 * and carves the remainder clamped to its own footprint. Nothing here looks at a
 * neighbour's blocks to decide what to cut, which is the property that lets the
 * world appear under a walking player, a travelling mole, or nobody at all.</p>
 *
 * <h2>The queue exists because of one line of javadoc</h2>
 *
 * <p>{@code ChunkEvent.Load} fires <em>before</em> the chunk is promoted to
 * {@code FULL}, and says in as many words that interactions with the level must
 * be delayed until the next game tick to prevent deadlocking the game. So the
 * event handler does exactly one thing - {@link #enqueue} - and
 * {@link #drain(ServerLevel)} does the work a tick later, with the chunk live and
 * the level safe to touch.</p>
 *
 * <h2>Carving and dressing are a tick or more apart</h2>
 *
 * <p>Carving only writes; dressing <em>measures</em> - it probes for the floor,
 * the ceiling and both walls before it puts anything down. Run against a chunk
 * whose neighbours are still solid earth, those probes read the chunk border as
 * the end of the corridor and dress it as one, leaving a seam of wall speckle
 * across open air that no later pass removes. So a chunk is dressed only once it
 * <em>and all eight of its neighbours</em> are loaded and have nothing left to
 * carve. After any chunk settles, the nine chunks of its own neighbourhood are
 * asked whether that just became true for them.</p>
 *
 * <h2>Everything is on the server thread</h2>
 *
 * <p>Both entry points run inside a server tick, and so does {@link #linkChanged},
 * which mole AI reaches through {@code ColonyStore.record}. That is what makes it
 * legal to read the overworld's {@code ColonyStore} from a handler for the burrow
 * and to ask the chunk source for chunks without generating any -
 * {@code getChunkNow} answers null off the main thread, and null is also what it
 * answers for a chunk that is not there, so an off-thread call would look like an
 * unloaded world rather than fail.</p>
 */
public final class BurrowReconciler {

    private static final Logger LOG = LoggerFactory.getLogger("moleverse.burrow");

    /**
     * On from the first tick of a Gradle run, off in a shipped game - the same
     * property {@code BurrowLog} reads, for the same reason: the interesting part
     * of worldgen happens in the seconds after a world is entered, long before
     * anybody could type a command to switch a log on.
     */
    private static final boolean DEV_LOGGING = Boolean.getBoolean("moleverse.devLogging");

    /**
     * How many queued chunks one tick reconciles.
     *
     * <p>The number exists for the burst, not for the steady state. A player
     * walking loads a chunk or two a second and the queue is empty most ticks; a
     * teleport hands over a whole render distance at once, and carving several
     * hundred chunks inside one tick is a visible freeze. Four spreads that over a
     * few seconds, which is slower than the player can walk into it.</p>
     */
    private static final int CHUNKS_PER_TICK = 4;

    /**
     * Chunk positions waiting for a tick, packed.
     *
     * <p>A set, so a chunk that is loaded, unloaded and loaded again inside one
     * tick is reconciled once; insertion-ordered, so the chunk a player is walking
     * towards is not overtaken by one queued after it.</p>
     */
    private static final Set<Long> QUEUE = new LinkedHashSet<>();

    private BurrowReconciler() {
    }

    // --- the triggers ---------------------------------------------------------

    /**
     * Notes that a burrow chunk wants looking at.
     *
     * <p>Called from the chunk load event, where this must be the entire handler,
     * and from this class itself when a chunk's neighbour has just carved
     * something that the chunk may have been waiting for.</p>
     */
    public static void enqueue(ChunkPos chunk) {
        QUEUE.add(chunk.toLong());
    }

    /**
     * Drops everything still waiting.
     *
     * <p>For the burrow being unloaded. The queue is static, so without this a
     * single-player client that leaves one world and enters another would start
     * the second one with the first one's backlog - harmless, because every
     * position is reconciled against the new world's own store and its own
     * ledgers, and still not something to leave lying around.</p>
     */
    public static void forget() {
        QUEUE.clear();
    }

    /**
     * Reconciles up to {@link #CHUNKS_PER_TICK} of the waiting chunks.
     *
     * <p>The plan is derived once for the whole drain rather than once per chunk:
     * it is the same answer for every chunk in the same tick, and deriving it is a
     * walk over the colony's links.</p>
     */
    public static void drain(ServerLevel burrow) {
        if (QUEUE.isEmpty()) {
            return;
        }

        // Taken out of the queue before anything is carved, because carving a
        // chunk can enqueue its neighbours and a set cannot be added to while it
        // is being iterated.
        List<ChunkPos> taken = new ArrayList<>(CHUNKS_PER_TICK);
        Iterator<Long> waiting = QUEUE.iterator();
        while (waiting.hasNext() && taken.size() < CHUNKS_PER_TICK) {
            taken.add(new ChunkPos(waiting.next()));
            waiting.remove();
        }

        List<BurrowFeature> plan = planFor(burrow.getServer());
        for (ChunkPos chunk : taken) {
            reconcile(burrow, chunk, plan);
        }
    }

    /**
     * A run was just dug or re-dug: have the burrow catch up where anybody could
     * be watching.
     *
     * <p>This is the living half of the design. Chunk load covers the ground a
     * player walks into; this covers the ground a player is already standing in
     * when a mole above finishes a run, which is the case where nothing would ever
     * load the chunk again and the corridor would simply never appear.</p>
     *
     * <p>Only loaded chunks are touched, and they are enqueued rather than carved
     * on the spot: this is called from the middle of mole AI, mid tick, and a run
     * that crosses thirty chunks would be thirty chunks of carving inside somebody
     * else's tick. One tick later nobody can tell the difference.</p>
     *
     * <p>The bounds are worked out from the plan rather than passed in, because
     * the caller is {@code ColonyStore} and the affected ground is not only the
     * corridor: a run ending at a mound can drop that mound's chamber floor, which
     * is a room of its own several blocks wide. Both chambers and the corridor go
     * into the box, and so does everything the anatomy hangs off the run - a larder
     * buds off its side, a bolt-hole climbs a dozen blocks above its ceiling, and
     * the colony's nest re-aims its spurs at whichever corridors are now nearest its
     * core. None of those is inside the corridor's own box, and a room that waited
     * for a chunk to be loaded again would be a room nobody standing there ever sees
     * appear.</p>
     *
     * @param overworld the level the store hangs off, which is the one the burrow
     *                  mirrors
     */
    public static void linkChanged(ServerLevel overworld, BurrowLink link) {
        MinecraftServer server = overworld.getServer();
        ServerLevel burrow = ModDimensions.burrowLevel(server);
        if (burrow == null) {
            // No burrow to keep up to date - a broken datapack, or the game test
            // server, which builds its world without any datapack dimensions.
            return;
        }

        ColonyStore store = ColonyStore.get(overworld);
        List<BurrowLink> colonyRuns = store.linksOf(link.colony());
        BoundingBox affected = null;

        if (link.pointCount() >= 2) {
            affected = new CorridorFeature(link).bounds();
        }

        for (BlockPos mound : List.of(link.a(), link.b())) {
            List<BurrowLink> runs = ChamberFeature.runsAt(colonyRuns, mound);
            if (runs.isEmpty()) {
                continue;
            }
            warnAboutUnreachableRuns(mound, runs);
            affected = union(affected, ChamberFeature.of(mound, runs).bounds());
        }

        for (AlcoveCarver.Larder larder : AlcoveCarver.lardersOf(link)) {
            affected = union(affected, larder.bounds());
        }

        BoltHoles.Stub stub = BoltHoles.on(link);
        if (stub != null) {
            affected = union(affected, stub.bounds());
        }

        Colony colony = colonyOf(store, link.colony());
        if (colony != null && !colonyRuns.isEmpty()) {
            affected = union(affected, NestCarver.nestOf(colony, colonyRuns).bounds());
        }

        if (affected != null) {
            enqueueLoadedWithin(burrow, affected);
        }
    }

    /** The two boxes together, or the second one where there is no first yet. */
    private static BoundingBox union(@Nullable BoundingBox affected, BoundingBox box) {
        return affected == null ? box : BoundingBox.encapsulating(affected, box);
    }

    /**
     * The colony with this id, or null where it has been removed.
     *
     * <p>A walk over a handful of colonies rather than a lookup, because
     * {@code ColonyStore} keeps them as a list and this is asked once per dig. Null
     * is a real answer: a link outlives the colony that dug it until the store is
     * pruned, and a nest for a colony that no longer exists is a room with no core to
     * put it at.</p>
     */
    private static @Nullable Colony colonyOf(ColonyStore store, int id) {
        for (Colony colony : store.all()) {
            if (colony.id() == id) {
                return colony;
            }
        }
        return null;
    }

    /**
     * Carves and dresses these chunks now, with no regard for whether their
     * neighbours are ready.
     *
     * <p>For the one caller that cannot wait a tick: a player being put into a
     * chamber has to arrive in a finished room rather than in earth, and the
     * suffocation damage while a tick handler caught up would be real. It is safe
     * exactly there and nowhere else - the chamber's own chunk ring is loaded
     * before this is called, so the dressing pass has real ground to measure
     * against in every direction it probes.</p>
     *
     * <p>Both phases run over the whole list rather than per chunk: every chunk is
     * carved, and only then is any of them dressed, which is the same ordering the
     * neighbourhood rule buys the queue.</p>
     *
     * <p>The plan is handed in rather than derived, because both callers have one
     * that is not simply {@link #planFor}: the arrival adds the room it is about
     * to put a player in, and the game tests reconcile a single corridor with no
     * chamber at either end to confuse the comparison.</p>
     *
     * @return how many features were applied, carved and dressed counted together
     */
    public static int reconcileNow(ServerLevel burrow, List<ChunkPos> chunks, List<BurrowFeature> plan) {
        int applied = 0;

        for (ChunkPos chunk : chunks) {
            LevelChunk loaded = chunkAt(burrow, chunk);
            if (loaded != null) {
                applied += carve(burrow, loaded, chunk, plan);
            }
        }

        for (ChunkPos chunk : chunks) {
            LevelChunk loaded = chunkAt(burrow, chunk);
            if (loaded != null) {
                applied += decorate(burrow, loaded, chunk, plan);
            }
        }

        return applied;
    }

    // --- reconciling one chunk ------------------------------------------------

    /**
     * One chunk's turn: carve what it is missing, then see whose dressing that
     * just unblocked.
     *
     * <p>A chunk that carved something wakes its neighbours. A shaft and a
     * junction both refuse to build where the corridors they join are not open
     * yet, and a crossing near a chunk border is asked about by chunks either side
     * of it - so the chunk holding the crossing can carve the corridors and settle
     * the shaft while the neighbour is still being told no. Without this the
     * neighbour's refusal would stand for the life of the world: nothing else
     * would ever ask it again. The cascade terminates because a pass either
     * settles something that was not settled before or wakes nobody.</p>
     */
    private static void reconcile(ServerLevel burrow, ChunkPos pos, List<BurrowFeature> plan) {
        LevelChunk chunk = chunkAt(burrow, pos);
        if (chunk == null) {
            // Unloaded again between the load event and this tick. Its diff is
            // waiting for it at the next load.
            return;
        }

        int carved = carve(burrow, chunk, pos, plan);
        if (carved > 0) {
            wakeNeighbours(burrow, pos, plan);
        }

        int dressed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                dressed += decorateWhenReady(burrow, new ChunkPos(pos.x + dx, pos.z + dz), plan);
            }
        }

        if (DEV_LOGGING && carved + dressed > 0) {
            LOG.info("burrow chunk {} {}: {} feature(s) carved, {} dressed, {} still queued",
                    pos.x, pos.z, carved, dressed, QUEUE.size());
        }
    }

    /**
     * Carves everything the plan puts in this chunk that the ledger does not
     * already account for.
     *
     * <p>In the plan's own order, which is corridors, chambers, then the crossings
     * that need both - so a chunk finishes what it started rather than leaving the
     * crossing for the next reconcile.</p>
     *
     * <p>A feature that answers false is <strong>not</strong> written down. False
     * means it was turned away for a reason that can be different later, and a
     * ledger entry would mean nothing ever asks it again.</p>
     */
    private static int carve(ServerLevel burrow, LevelChunk chunk, ChunkPos pos, List<BurrowFeature> plan) {
        BurrowLedger ledger = ledgerOf(chunk);
        BoundingBox clamp = BurrowPlan.clampFor(pos, burrow);

        Map<String, Integer> carved = null;
        Set<String> decorated = null;
        int applied = 0;

        for (BurrowFeature feature : BurrowPlan.intersecting(plan, pos)) {
            String key = feature.key();
            int hash = feature.contentHash();
            if (ledger.isCarved(key, hash)) {
                continue;
            }
            if (!feature.carveWithin(burrow, clamp)) {
                continue;
            }

            if (carved == null) {
                carved = new LinkedHashMap<>(ledger.carved());
                decorated = new LinkedHashSet<>(ledger.decorated());
            }
            carved.put(key, hash);
            // Cut again means dressed again. What the last pass put down belongs
            // to a shape that has since changed, and leaving the key in would
            // mean a re-dug run kept the old run's furniture for good.
            decorated.remove(key);
            applied++;
        }

        if (applied > 0) {
            store(chunk, new BurrowLedger(carved, decorated));
        }
        return applied;
    }

    /**
     * Dresses this chunk if, and only if, it and all eight of its neighbours are
     * loaded with nothing left to carve.
     *
     * <p>The rule the dressing pass needs, stated in chunks: a probe that walks
     * out of this chunk has to find carved ground or real earth on the other side,
     * never a chunk that has simply not had its turn.</p>
     */
    private static int decorateWhenReady(ServerLevel burrow, ChunkPos pos, List<BurrowFeature> plan) {
        LevelChunk chunk = chunkAt(burrow, pos);
        if (chunk == null) {
            return 0;
        }

        // Asked before the neighbourhood is, because it is the cheaper question
        // and it is false for nearly every chunk this sweep reaches: the eight
        // around a chunk that just carved something are mostly long since
        // dressed, and each of them would otherwise cost nine more walks over
        // the plan to establish nothing.
        if (nothingLeftToDress(chunk, pos, plan)) {
            return 0;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos around = new ChunkPos(pos.x + dx, pos.z + dz);
                if (!carveSettled(burrow, around, plan)) {
                    return 0;
                }
            }
        }

        return decorate(burrow, chunk, pos, plan);
    }

    /** Dresses every feature of this chunk that has not been dressed since it was cut. */
    private static int decorate(ServerLevel burrow, LevelChunk chunk, ChunkPos pos, List<BurrowFeature> plan) {
        BurrowLedger ledger = ledgerOf(chunk);
        BoundingBox clamp = BurrowPlan.clampFor(pos, burrow);

        Set<String> decorated = null;
        int applied = 0;

        for (BurrowFeature feature : BurrowPlan.intersecting(plan, pos)) {
            String key = feature.key();
            if (ledger.isDecorated(key)) {
                continue;
            }

            feature.decorateWithin(burrow, clamp);
            if (decorated == null) {
                decorated = new LinkedHashSet<>(ledger.decorated());
            }
            decorated.add(key);
            applied++;
        }

        if (applied > 0) {
            store(chunk, new BurrowLedger(ledger.carved(), decorated));
        }
        return applied;
    }

    /** Whether every feature of this chunk has been dressed since it was last cut. */
    private static boolean nothingLeftToDress(LevelChunk chunk, ChunkPos pos, List<BurrowFeature> plan) {
        BurrowLedger ledger = ledgerOf(chunk);
        for (BurrowFeature feature : BurrowPlan.intersecting(plan, pos)) {
            if (!ledger.isDecorated(feature.key())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this chunk is loaded and has nothing of the plan left to carve.
     *
     * <p>False for a chunk that is not loaded, which is the answer that matters:
     * an absent chunk is not a settled one, and dressing beside it would measure
     * against ground that is not there yet.</p>
     */
    private static boolean carveSettled(ServerLevel burrow, ChunkPos pos, List<BurrowFeature> plan) {
        LevelChunk chunk = chunkAt(burrow, pos);
        if (chunk == null) {
            return false;
        }

        BurrowLedger ledger = ledgerOf(chunk);
        for (BurrowFeature feature : BurrowPlan.intersecting(plan, pos)) {
            if (!ledger.isCarved(feature.key(), feature.contentHash())) {
                return false;
            }
        }
        return true;
    }

    /** Queues the loaded neighbours that still have something outstanding. */
    private static void wakeNeighbours(ServerLevel burrow, ChunkPos pos, List<BurrowFeature> plan) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos neighbour = new ChunkPos(pos.x + dx, pos.z + dz);
                if (neighbour.equals(pos)) {
                    continue;
                }
                if (chunkAt(burrow, neighbour) != null && !carveSettled(burrow, neighbour, plan)) {
                    enqueue(neighbour);
                }
            }
        }
    }

    // --- the pieces -----------------------------------------------------------

    /**
     * Everything the burrow ought to contain, from the colonies above it.
     *
     * <p>Public because a caller that means to add something to the plan needs to
     * see it first - {@code BurrowTransit} does exactly that for a mound the
     * colony has not recorded a run to yet. Deriving it is a walk over the store,
     * so ask once per batch of chunks rather than once per chunk.</p>
     *
     * <p>Empty where there is no overworld to read, which is the game test server
     * and nothing else a player would ever see.</p>
     */
    public static List<BurrowFeature> planFor(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return List.of();
        }

        ColonyStore store = ColonyStore.get(overworld);
        return BurrowPlan.featuresOf(store.all(), store.allLinks());
    }

    /**
     * The chunk if it is live, null otherwise.
     *
     * <p>{@code getChunkNow} and never {@code getChunk}: the second one generates
     * what it cannot find, and a reconciler that pulled chunks into existence
     * around every chunk it touched would load the whole burrow from one
     * teleport.</p>
     */
    private static @Nullable LevelChunk chunkAt(ServerLevel burrow, ChunkPos pos) {
        ServerChunkCache chunks = burrow.getChunkSource();
        return chunks.getChunkNow(pos.x, pos.z);
    }

    /** The ledger, without writing a default one into a chunk that has none. */
    private static BurrowLedger ledgerOf(LevelChunk chunk) {
        BurrowLedger ledger = chunk.getExistingDataOrNull(ModAttachments.BURROW_LEDGER);
        return ledger == null ? BurrowLedger.EMPTY : ledger;
    }

    /**
     * Replaces the ledger and marks the chunk.
     *
     * <p>Both halves, always. An attachment set on a chunk does not mark it
     * unsaved by itself - {@code AttachmentType}'s javadoc says so outright - and
     * a ledger that is not written is a chunk that carves its whole share again on
     * every load, for ever.</p>
     */
    private static void store(LevelChunk chunk, BurrowLedger ledger) {
        chunk.setData(ModAttachments.BURROW_LEDGER, ledger);
        chunk.markUnsaved();
    }

    /** Queues every loaded burrow chunk the box reaches into. */
    private static void enqueueLoadedWithin(ServerLevel burrow, BoundingBox bounds) {
        int fromX = SectionPos.blockToSectionCoord(bounds.minX());
        int toX = SectionPos.blockToSectionCoord(bounds.maxX());
        int fromZ = SectionPos.blockToSectionCoord(bounds.minZ());
        int toZ = SectionPos.blockToSectionCoord(bounds.maxZ());

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (chunkAt(burrow, pos) != null) {
                    enqueue(pos);
                }
            }
        }
    }

    /**
     * Says so when a run leaves a mound higher than its chamber can reach.
     *
     * <p>The carver drops a gallery above the chamber ceiling, and it is right to
     * - a gallery clamped down to the roof would only put a player four blocks
     * closer to a mouth still out of reach. What the world ends up with is a real
     * tunnel above the chamber with no way up to it, and nothing else in the game
     * would ever say so.</p>
     *
     * <p>It is said <em>here</em>, where a link is written, and deliberately not
     * on the per-chunk path: the same fact would be restated thousands of times as
     * chunks loaded, which is how a diagnostic stops being read. Once per dig is
     * once per occurrence.</p>
     *
     * <p>It takes a changed surface to happen at all - the depth of a run is
     * sampled when that run is recorded, so two runs at one mound can be measured
     * against two different ground heights if somebody raised the ground or a tree
     * grew between the two recordings. Rare, and not self-announcing, which is
     * exactly the combination worth a line in the log.</p>
     */
    private static void warnAboutUnreachableRuns(BlockPos mound, List<BurrowLink> runs) {
        int floor = ChamberFeature.floorAt(mound, runs);
        for (int layer : ChamberFeature.mouthLayers(mound, runs, floor)) {
            if (layer >= BurrowGeometry.CHAMBER_HEIGHT) {
                LOG.warn("mound {}: a run leaves {} blocks above the chamber floor, "
                        + "past the {} the chamber is tall - it gets no gallery, "
                        + "so its corridor cannot be reached from inside",
                        mound, layer, BurrowGeometry.CHAMBER_HEIGHT);
            }
        }
    }
}

# Worldgen for the burrow - the plan

Status: built 2026-08-29, by three parallel agents plus a reconciler wave; all
game tests pass. Kept for the reasoning and the
reference-mod survey. Current state lives in `BURROW.md`.

## The problem, restated in one line

Carving hangs on *somebody entered the burrow*; it must hang on *this ground now
exists*. Everything else in this file is the consequence of moving it.

## What Minecraft itself does, and where we can and cannot copy it

Vanilla generation is a pure function of the seed. A chunk is built on a worker
thread, in isolation, by asking "what does the seed say about these 16x16
columns" - and that is why walking towards the edge of the Nether just works:
every chunk can answer its own question without looking at any other chunk, any
other dimension, or any runtime state.

Worldgen mods live inside that same contract. Biomes O' Plenty (checked on its
`1.21.11` branch) is instructive precisely because of what it does *not* have:
no custom `ChunkGenerator`, no dimension of its own. Its whole worldgen package
is region classes for biome distribution (via TerraBlender), surface rules, and
`carver`/`feature`/`placement` implementations - plug-ins into the vanilla
pipeline, all seed-driven, consulted on worker threads, never reading runtime
state. That is the shape to copy *when the input is the seed*.

Two more mods, checked for the two shapes a custom dimension can take:

* **Twilight Forest** had the real thing - `ChunkGeneratorTwilight extends
  ChunkGeneratorWrapper` on its `1.19.x` branch, wrapping a vanilla noise
  generator. Its inputs are instructive: everything comes through the codec and
  the seed-derived `RandomState`, `fillFromNoise` runs via
  `CompletableFuture.supplyAsync(..., Util.backgroundExecutor())`, and no method
  on the generation path ever touches a `ServerLevel` or `SavedData`. Just as
  instructive: the class is *gone* from `1.20.x` onwards - the terrain moved
  into vanilla density functions, and the `chunkgenerators` package now holds
  nothing but `DensityFunction` implementations. The one big mod that had a
  custom generator retired it in favour of the data-driven pipeline.

* **Dimensional Doors** is the closest analogue to the burrow - a dimension
  whose content is decided by runtime data, not by the seed. Its answer is
  exactly the split proposed here: the dimension's registered generator is
  `BlankChunkGenerator` (a `fillFromNoise` that returns the chunk untouched),
  and every pocket is placed afterwards by `PocketGenerator`, on the server
  thread, with a `ServerLevel` in hand. The generator generates nothing; the
  runtime placer does everything.

The burrow cannot be that, and the reason is the premise of the whole design:
its shape is the **history of the colony above it**, not a function of the seed.
The colony lives in `ColonyStore`, which is `SavedData` hanging off the
overworld, and moles keep writing to it while the game runs. Two consequences:

* **A real `ChunkGenerator` is the wrong tool.** `fillFromNoise` runs on worker
  threads. `DimensionDataStorage` is not thread-safe, the overworld's store is a
  cross-dimension reach, and a snapshot handed to the generator would go stale
  the moment a mole digs. It could be made to work with an immutable snapshot
  swapped atomically from the server thread - but even then it only covers
  chunks that have never been generated. A run dug *after* a chunk was generated
  crosses ground that already exists, and no generator is ever asked about that
  chunk again. The runtime path is needed regardless, so the generator would be
  a second copy of the same logic with worse threading.

* **Chunk load is the right event.** `ChunkEvent.Load` fires on the server for
  every chunk that becomes live, with `isNewChunk()` distinguishing freshly
  generated from read-off-disk - and we need both, because "generated before the
  link existed" and "never generated" must come out the same. Its javadoc
  carries the one rule that shapes the implementation: the event fires *before*
  the chunk is promoted to FULL, and interactions with the level must be
  **delayed until the next game tick** to prevent deadlocking the game. So the
  event only enqueues; a tick handler drains.

The vanilla property worth copying is not the thread pool, it is the contract:
**a chunk answers its own question**. Chunk (x,z) of the burrow asks "which
pieces of the colony's network pass through me" and carves exactly those,
clamped to its own 16x16 footprint, regardless of what any neighbour has or has
not done. That is what makes the world appear under a walking player, a
travelling mole, or nobody at all - and it is idempotent by construction.

## The design

### 1. The plan layer: features

A new pure-computation module (`dimension/plan/`) turns the colony's data into a
list of **features**, each of which knows three things:

| | |
|---|---|
| **key** | stable identity: `corridor:<colonyId>:<a>:<b>`, `chamber:<mound>`, `shaft:<linkA>x<linkB>:<n>`, `junction:<linkA>x<linkB>` |
| **hash** | content fingerprint: the depths profile, the level, the mouth layers - whatever, if changed, means "carve me again" |
| **bounds** | a bounding box in burrow space, corridor radius included |

plus one operation: `carveWithin(ServerLevel burrow, BoundingBox chunkClamp)`.

The features are exactly the four things `BurrowTransit.enter` calls today:

* **Corridor** per `BurrowLink` - `CorridorCarver.carve`
* **Chamber** per mound that has a shrink-post-worthy chamber - `carveChamber`
  plus `ChamberFurnisher.furnish`, `placeWayOut`, `BurrowLife.stock`
* **Shafts** and **junctions** per intersecting link pair - today computed
  inside `LevelShafts.connect` / `Junctions.cut` from the whole colony's runs;
  the *finding* of crossings moves into the plan layer (it is arithmetic on
  links, no level access), the *carving* stays where it is and learns the clamp

Deriving features from `ColonyStore` is deterministic: same store, same feature
list, same hashes. The store plays the role the seed plays in vanilla.

### 2. The clamp

Every carve method gets a `BoundingBox` parameter and refuses to write outside
it. This replaces the silent `isLoaded` skip as the *normal* bounding mechanism
- the `isLoaded` check in `clear()` stays as a last-line guard, but under the
clamp it should never fire, and when it does it may log, because now it is a
bug rather than Tuesday.

A feature crossing four chunks is carved four times, one clamped quarter per
chunk, each complete in itself. Carving is already idempotent (only deep earth
turns to air), so overlap costs reads, not correctness.

**Decoration is already order-independent.** `TunnelDecorator` ignores the
`RandomSource` it is handed and derives every roll from a hash of the block
position - the class was built idempotent for exactly this situation. The clamp
only has to keep calling `decorate` for segment centres near the chunk;
overlapping and repeated calls are already safe. `ChamberFurnisher` needs the
same check before it is trusted the same way.

**Entities are not idempotent.** `BurrowLife.stock` spawns worms; carving a
chamber twice must not stock it twice. Stocking belongs to the one chunk that
contains the chamber centre, and the applied-hash in that chunk's ledger (below)
is what makes it once-only.

### 3. The ledger: a chunk attachment

A NeoForge data attachment on burrow chunks (`AttachmentType` with a codec, so
it persists): a map `featureKey -> appliedHash`.

Reconciling a chunk is a diff: features whose bounds intersect the chunk,
minus entries already in the ledger at the same hash. Carve the difference with
the chunk's clamp, write the new hashes. An empty ledger on an old chunk is not
a special case - it is simply "everything is missing", which re-carves already
carved air and settles the ledger. Existing worlds migrate themselves.

A `reshaped` link (the ground above changed, a mole re-dug) changes the hash and
is carved again on next load; the old corridor's air stays, which is the
existing behaviour and reads as history. A pruned link leaves its corridor as an
abandoned run - flavour, not damage.

### 4. The two triggers

* **Chunk becomes live.** `ChunkEvent.Load`, server side, burrow dimension, new
  and from-disk alike: enqueue the `ChunkPos`. A `LevelTickEvent` handler on the
  burrow drains the queue - next tick, chunk now FULL, level access legal -
  checks the chunk is still loaded, reconciles it. Cost per chunk: a walk over
  the colony lists (dozens of links today; a spatial index is a later
  optimisation with an obvious seam) plus the carving itself, which only
  happens where the diff is non-empty.

* **The colony digs.** `ColonyStore.record` (and anything else that changes a
  feature) notifies the reconciler with the changed feature's bounds; every
  *loaded* burrow chunk intersecting them is reconciled immediately. That is the
  living-world half: a player standing in the burrow watches a new corridor
  arrive because a mole above just finished the run. Unloaded chunks need
  nothing - their diff waits for them at the next load.

### 5. What `BurrowTransit.enter` becomes

Thinner. It keeps: the mound check, computing the chamber position, the
teleport. It keeps one carve: reconciling the chamber's own chunks
synchronously before the teleport (the existing `loadChamberChunks` ring),
because the player must not arrive inside earth waiting for a tick handler.
Everything else - the runs, the shafts, the junctions, the rest of the colony -
comes from the two triggers, exactly as it does for every other chunk. The
comment that promised "everything further along is dug as they walk into it"
becomes true.

Player movement then does the rest with no code of ours: the player's ticket
ring loads chunks ahead of the walk, each load reconciles, corridors are always
finished beyond the torchlight before anybody reaches them.

## What this deliberately does not do

* No custom `ChunkGenerator`. The flat solid generator stays; generation stays
  trivial and thread-safe, and all shape comes from reconciliation on the
  server thread. If profiling ever says otherwise, the plan layer's determinism
  is exactly what a snapshot-fed generator would need - the door stays open.
* No force-loading beyond the existing chamber ring at the moment of entry.
* No per-feature progress tracking inside a chunk: a feature is applied to a
  chunk wholly or not at all, and the clamp makes "wholly" cheap.

## Build order

Each step compiles and tests on its own; the seams are the module boundaries.

1. **Plan layer.** Feature records, keys, hashes, bounds; crossing-finder moved
   out of `LevelShafts`/`Junctions`. Pure, game-testable arithmetic.
2. **The clamp.** `BoundingBox` through `CorridorCarver`, `Junctions`,
   `LevelShafts`, `ChamberFurnisher`; decorator re-seeding per waypoint.
   Existing game tests keep passing with an unbounded box.
3. **The ledger.** Attachment type, diff, reconcile-one-chunk. Game test: carve
   a link across a chunk border chunk by chunk, assert the corridor is whole
   and the ledger settled; reconcile twice, assert the second pass writes
   nothing.
4. **The triggers.** Load-event queue + tick drain; `record` -> loaded-chunk
   sweep. Log one line per reconciled chunk with the carve count while tuning
   (`moleverse.devBurrowLog`), silent otherwise.
5. **Slim `enter`.** Drop the colony-wide carving, keep the chamber ring.
   Update `BURROW.md`.

## The risks worth naming

* **The Load event fires pre-FULL.** Nothing touches the level in the event;
  the queue is the whole handler. This is the rule the javadoc states and the
  deadlock it prevents is real.
* **Reconcile-on-record while iterating.** `record` is called from mole AI mid
  server tick; reconciling immediately means carving mid-tick, which is legal
  (it is the same thread) but a burst. If a burst ever shows in the profiler,
  the notify can defer to the same queue the load event uses - one tick later,
  nobody can tell.
* **Ledger codec.** Same rule as `ColonyStore`: a codec that cannot read its
  own file destroys it. `optionalFieldOf` with defaults from day one.
* **Hash stability.** The hash must be computed from the data, never from
  object identity, and must survive a save/load round trip - a game test
  asserts it.

## Team execution (2026-08-29)

Built by parallel agents per `moleverse-delegation` rules: agents own files
exclusively, the main thread owns registries, gradle and git.

* **Wave 1, three agents in parallel.**
  * *plan-layer*: new package `dimension/plan/` (features, keys, hashes,
    bounds); owns `Junctions.java` + `LevelShafts.java` to extract pure
    crossing-finders and add per-crossing clamped carve entry points.
  * *carver-shape*: owns `CorridorCarver.java`, `ChamberFurnisher.java`,
    `CorridorProfile.java`; the BoundingBox clamp, plus organic cross-section
    modulation (position-hashed, deterministic - the corridors should read as
    dug earth, not drilled pipe).
  * *tunnel-optics*: owns `TunnelDecorator.java`; better placement - clusters,
    wall-hugging, varied glow pools - same position-hash discipline.
  * Interface contract dictated up front: every carve entry gains a trailing
    `@Nullable BoundingBox clamp` overload, null meaning unbounded; old
    signatures stay as delegating overloads so existing callers and tests
    compile.
* **Wave 2, one agent**: the ledger attachment, the reconciler (load-queue +
  tick drain + record-notify), slimming `BurrowTransit.enter`, game tests.
* **Wave 3, main thread**: registration wiring, build, game tests, docs.

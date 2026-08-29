# The burrow below - build notes

Status: being built. Everything here is untested in play, and every module is a
separate commit so that any one of them can be dropped without taking the rest
with it.

The design and the argument for it are in `IDEAS.md`. This file is only the
build: what is in which module, what the numbers are, and where the seams run.

## Modules, in the order they stack

| Module | Files | Depends on |
|---|---|---|
| **Geometry** | `dimension/BurrowGeometry.java` | nothing |
| **Dimension** | `dimension/ModDimensions.java`, `data/moleverse/dimension*/burrow.json` | nothing |
| **Carving** | `dimension/CorridorCarver.java` | Geometry, the link store |
| **Content** | `dimension/TunnelDecorator.java` | Carving |
| **Transit** | `dimension/BurrowTransit.java`, `block/ShrinkPost.java` | all of the above |
| **Chambers** | `dimension/ChamberFurnisher.java`, `block/WormLarder.java` | Carving |
| **Ambience** | `client/BurrowAmbience.java` | Dimension |
| **Tests** | `test/BurrowGameTests.java`, `test/ModGameTests.java` | Geometry, Carving |
| **Blocks** | `deep_earth`, `root_beam`, `glow_mycelium`, `worm_larder`, `shrink_post` in the registries | nothing |

Dropping a module upwards is safe: without Transit the dimension exists and can
be entered with a command; without Content the corridors are bare; without
Carving the dimension is a solid block of nothing.

## The numbers, and what they mean

All in `BurrowGeometry`, all provisional.

| Name | Value | What it decides |
|---|---|---|
| `SCALE` | 4 | One overworld block is four down there. The fiction is that the player is a quarter of their size |
| `VERTICAL_SCALE` | 2 | Deliberately smaller. Runs follow the ground, and at the full scale a three block dip becomes a twelve block drop - every hillside colony would be a staircase |
| `CORRIDOR_WIDTH` | 5 | Odd, so a corridor has a centre line. Somewhere between four and eight is worth walking; this is a starting point to look at, not a settled number |
| `CORRIDOR_HEIGHT` | 6 | A little more than the width, so it reads as a burrow rather than a pipe |
| `CHAMBER_RADIUS` | 6 | A mound's room below, and where the way out stands |
| `OVERWORLD_DATUM` / `BURROW_DATUM` | 64 / 128 | Sea level lands in the middle of the dimension's 256 block range |

Nobody in the world can measure the ratio between the horizontal and the vertical
scale, which is exactly why they are allowed to differ.

## Seams worth knowing

**Corridors are blocks, not data.** A carved run persists because the air
persists. There is no record of what has been dug and none is needed: the
question "is this already carved" is answered by looking.

**Nothing generates.** The dimension is solid fill from a flat generator with no
features, no structures and no caves. Everything a player sees down there was put
there by `CorridorCarver` at runtime, which is what keeps the two worlds from
having to agree about anything.

**The way out is checked at the moment of use**, not stored. A chamber maps back
to an overworld position; if a mound still stands there, the door works. Somebody
breaking that mound closes a door rather than corrupting anything.

**Carving is bounded by loaded chunks.** A run whose far end is not loaded is
carved as far as it goes and finished on the next visit. That is a limitation
worth remembering when a corridor appears to end in a wall.

## What is deliberately missing

* No mole is ever down there. The burrow mirrors what they dug, not where they
  are - a moving animal in two worlds at once is a synchronisation problem, and
  the whole design exists to avoid one.
* Nothing is generated per biome. Every colony's burrow looks the same for now.
* No shrinking is simulated. The player keeps their size and the world is built
  four times larger, which is the same statement in blocks rather than in code.

## What is proven and what is not

`./gradlew runGameTestServer` runs six tests on every invocation: the geometry
round trip and its clamp, carving clearing ground and leaving a floor,
`alreadyCarved` before and after, and the link store surviving a write and a
read. They pass.

That is arithmetic and block placement. What no test can reach is whether the
place is worth being in - whether a corridor reads as a burrow, whether the light
is enough to walk by, whether arriving in a chamber feels like arriving
somewhere. All of that is waiting for a person.

## The vertical clamp

The burrow is 256 blocks tall and the vertical scale is two, so only overworld
heights within about sixty of sea level map inside it. Everything outside is
clamped rather than allowed to run off the end - a superflat test world sits at
-60 and a mountain colony at 140, and both would otherwise carve at a height that
does not exist, which fails silently and buries whoever arrives.

Two very different overworld heights can therefore share one burrow level. That
is the right trade: colonies are hundreds of blocks apart horizontally, so a
collision in the vertical costs nothing.

## The corridors are not built - and the reason is where, not how much

Found on 2026-08-29, the first time anybody went down. A player arriving in a
chamber finds one large room with a few stubs and no corridors at all. The
expectation - a junction with runs leading off it - is right, and cannot happen.

### What actually runs

`BurrowTransit.enter` carves everything at the moment somebody uses the shrink
post: the chamber, the runs that end at that mound, then shafts and junctions
across the colony. Nothing carves afterwards. The comment there says *"Everything
further along is dug as they walk into it"* and that mechanism does not exist -
the only per-tick work in the burrow is `BurrowRescue.tick`, and nothing else is
subscribed to `LevelTickEvent`.

### Why most of a run vanishes

Every write the carver makes is guarded:

```java
static boolean clear(ServerLevel burrow, BlockPos.MutableBlockPos pos) {
    if (!burrow.isInsideBuildHeight(pos.getY()) || !burrow.isLoaded(pos)) {
        return false;
    }
```

Correct in itself - the alternative is carving into chunks nobody asked for - but
it fails **silently**. And `loadChamberChunks` loads only what the chamber
touches, which is `CHAMBER_RADIUS` 6, so at most two chunks each way.

Measured in the world it was found in: the runs were 14.7 and 17.6 overworld
blocks, which at `SCALE` 4 is 59 and 70 blocks of corridor. Roughly sixteen of
those blocks lie inside the loaded chamber chunks. The rest is skipped without a
line in the log.

### The shape the fix should have

Not "load more before carving". The mistake is that digging was hung on an event
that has nothing to do with place: *somebody entered the burrow*, rather than
*this ground now exists*.

The right shape is the one worldgen already has, and the Nether is the reference:
walk towards the edge and what is beyond gets built as you arrive. Applied here -
**a chunk asks which runs pass through it and carves those**, with the
`ColonyStore` as the seed the way a seed drives terrain. Idempotent by
construction, no force loading, and it covers the case nobody is watching: a mole
travelling below while the player is elsewhere.

**The hazard, and the tamer variant.** Real chunk generation runs off-thread and
must not reach into another dimension's `SavedData` - and `ColonyStore` hangs on
the overworld. A generator inside the burrow cannot get at it cleanly. Carving on
**chunk load**, on the server thread, avoids that entirely: the overworld is one
field access away there, the chunks around a player are loaded by definition, and
the cost spreads over arrival instead of landing in one teleport.

### What already exists to build it from

| Piece | Where |
|---|---|
| `carve(ServerLevel, BurrowLink)` | `CorridorCarver:178` - whole run, idempotent |
| `alreadyCarved(ServerLevel, BurrowLink)` | `CorridorCarver:309` |
| `linksNear(BlockPos, int radius)` | `ColonyStore:229` - overworld coordinates |
| `linksOf(int colony)` | `ColonyStore:224` |
| `toBurrow` / `toOverworld` | `BurrowGeometry:69,84` |
| `pointAt(int)` / `pointCount()` | `BurrowLink:77,96` - the run as a polyline |

### Open questions for that session

* **What is the unit of work** - a whole run when any part of it enters a loaded
  chunk, or only the part inside that chunk? The carver takes whole runs today.
* **How does a chunk find its runs?** `linksNear` is a radius query in overworld
  coordinates; a chunk in the burrow is a box in burrow coordinates. Something
  has to translate, and at `SCALE` 4 a burrow chunk is four overworld blocks.
* **What triggers it** - chunk load, or a player-proximity sweep? Load is
  cheaper and matches worldgen; proximity re-carves ground a player broke.
* **Does `BurrowTransit.enter` keep carving anything at all** beyond the chamber,
  or does it become just the chamber plus a teleport?
* **What happens to shafts and junctions**, which today are cut across the whole
  colony at entry and would have the same problem.

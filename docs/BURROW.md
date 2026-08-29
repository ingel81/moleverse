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

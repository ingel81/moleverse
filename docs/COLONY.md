# Colonies, links and route depth

Status: planned, not built. The foundation `IDEAS.md` keeps calling for - the one
thing the burrow below, the exchange chest and the main runs all stand on.

## The short version

Three changes that only make sense together:

1. A colony gets an **identity and a boundary**. Today a network is whatever
   mounds happen to chain together near a mole, which means it drifts across the
   world instead of being a place.
2. A travelled link gets **written down**. Nothing about a trip survives a reload
   today, so there is nothing to mirror below and nothing to reward above.
3. A route gets a **depth class**. Every route currently runs two blocks under
   the surface, so a stored depth profile would be the same number sixteen times.

Each is small. Apart they are pointless: a link store without depth carries no
information, depth without a boundary has nothing to bound it, and a boundary
without a link store cannot be shown to anybody.

### What an hour of play looks like

A colony left running for an hour reaches something close to two dozen mounds,
spreads well past the village it started in, and trails a single long link out to
one distant mound - the creep, visible in one screenshot. The local density cap
holds; nothing caps the whole.

The same picture shows the second reason depth classes are not decoration: the
links **cross each other**, repeatedly. At a uniform depth of two blocks, two
crossing links occupy the same space, and once that is scaled up for the burrow
below every crossing stops being two corridors and becomes a plaza - the dense
part of a colony would mirror as one cleared hall rather than a network. Depth
separation is what keeps a crossing a crossing: a feeding run at two and a main
run at twelve pass over and under each other, and the vertical structure that
makes the burrow worth walking comes from exactly that.

## Why this is the foundation and not a feature

* The burrow below is a function of stored links. No links, no corridors.
* Not the exchange chest. That was the second argument for storing a depth
  profile and it is gone: the trade reads what a player offers, not where the
  courier had been. Recorded here because a plan that keeps a dead justification
  standing invites somebody to build for it later.
* A colony that creeps has no extent, so the mirrored burrow has no extent
  either - and an endless burrow cannot be authored, only generated.

## Decisions

**The unit of persistence is the link, not the trip.** A trip is an event; a link
is an edge between two mounds that gets walked again and again. The burrow
mirrors edges. Use counts belong on the edge, and the resulting graph is
*historical* - links actually travelled - rather than the implicit proximity
graph `MoundNetwork` builds today.

**Store the depth profile, derive everything else.** `BurrowRoute.between`
interpolates x and z linearly between the two mounds and only the depth is
chosen freely per waypoint. Endpoints plus step count reconstruct x and z
exactly, so a link needs endpoints, a step count and one depth value per
waypoint. About two bytes per waypoint instead of three doubles.

**Do not store biomes.** They are a lookup from x and z at query time, and a
stored copy is stale the moment anything changes them.

**Write on arrival only.** The guard clauses in `MoleBurrowGoal` abort trips
routinely; recording attempts would fill the store with runs that never happened.
Partial routes can be added later if the burrow turns out to want them.

**The core is the first mound of a colony and is never recomputed.** A centroid
that moves with its members is exactly the drift this is meant to stop.

**The extent is 128x128, half-width 64.** That number is already in the code as
`NETWORK_SCAN_MAX`; what it lacks is a fixed point to measure from.

**Saturation disperses.** At `NETWORK_MAX_MEMBERS` a colony stops growing and a
mole may leave to found a new core at a minimum separation. The player's trap and
release goes through the same path, which is what makes founding a colony
deliberate rather than a side effect.

**A player-placed mound joins by containment.** Inside a colony's box it is a
member; outside every box it is a block until a mole adopts it.

**No migration.** Development only, single player, and a new world is one command
away. Existing worlds are explicitly not carried over - a decision, not an
oversight.

**Resuming a trip across a reload stays out of scope.** The mole already
serialises through `ValueInput`/`ValueOutput`, so it is feasible, but it only
tidies recovery cases and the burrow does not need it. Keeping it out is what
stops this foundation from turning into a rewrite.

## Data

### Colony

| Field | Note |
|---|---|
| id | stable across reloads, used as the key everywhere else |
| core | `BlockPos` of the founding mound, fixed for the life of the colony |
| founded | game time, for ordering and for debug output |
| members | mound positions; the POI index stays the authority on what is still a mound |
| links | the edges below |

### Link

| Field | Note |
|---|---|
| a, b | mound positions, stored in a canonical order so the pair is one key |
| steps | waypoint count; with a and b this reconstructs every x and z |
| depth class | feeding run, main run, deep run |
| depths | one value per waypoint, the only thing that cannot be recomputed |
| uses | how often it has been travelled; main runs and decay hang off this |
| last used | game time, for pruning and for the burrow to age a corridor |

A typical route is 28 to 36 blocks at `WAYPOINT_SPACING = 2`, so roughly sixteen
waypoints and well under a hundred bytes per link. A saturated colony of 32
mounds carries a few dozen links - a handful of kilobytes. Size is not a design
constraint here; the boundary already keeps it finite, which is why no eviction
policy is needed.

### Where it lives

`SavedData` on the overworld, holding every colony. Chunk-bound storage is wrong:
a link spans chunks by definition, and a colony spans many.

The registration shape for `SavedData` changed to a codec-based form in the 1.21
line. **Look it up in the decompiled sources before writing it**, the way the
rest of this project does, because every tutorial still shows the old API.

## Constants to add

All of these are numbers to settle in game, not on paper. They start as the
figures below and get a slider where a slider helps.

| Name | Start | Reason |
|---|---|---|
| `COLONY_EXTENT` | 64 | half-width; matches the existing scan radius |
| `COLONY_MIN_SEPARATION` | 256 | so two colonies do not overlap boxes or share ground |
| `DEPTH_FEEDING` | 2 | today's `ROUTE_DEPTH`; the everyday run just under the turf |
| `DEPTH_MAIN` | 4 | the backbone a colony keeps and reuses |
| `DEPTH_CHAMBER` | 6 | the main burrow and its chambers, the deepest anything goes |
| `MAIN_RUN_CHANCE` | to find | how often a trip takes the backbone instead of a feeding run |

## Phases

### Phase 1 - the colony

Give a network an identity: a record with a core and a box, mounds assigned by
containment, and the refusal that comes with it. A dig site outside the box is
declined with a new reason - `outside the colony's ground` - which has to reach
the log like every other refusal, because a mole that will not dig with no
visible cause is this mechanic's worst failure mode.

The debug overlay draws the box and marks the core.

**Done when** a colony stops growing at its edge instead of creeping, the overlay
shows why, and the log names the refusal.

### Phase 2 - the link store

Write a link on arrival, read it back after a reload, prune it lazily when either
endpoint is no longer a mound. A dump command prints what is stored.

**Done when** links survive a world reload, a broken mound removes its links on
the next query, and the dump matches what the overlay draws.

### Phase 3 - depth levels

Three levels rather than one: feeding runs at two, main runs at four, the main
burrow and its chambers at six. Real moles are built the same way - the whole
spread stays inside the topsoil and nothing here digs towards bedrock.

**All three are offsets from the local surface, not absolute heights.**
`depthAt` already works that way and only needs to take the level as an argument;
the runs follow the ground, so a colony on a slope gets runs that follow the
slope. The existing upward clamp in `BurrowRoute.between` stays exactly as
important, because the heightmap still counts a wall or a tree trunk as ground.

Two blocks of separation sounds like nothing and is not, because the burrow below
multiplies it: at a scale of four, two levels lie eight blocks apart, which is
what turns a crossing into an overpass instead of a plaza. And the relief down
there then comes from the landscape rather than from how far a mole digs - a
colony on a hillside mirrors as a burrow that climbs.

**Done when** the three levels are distinguishable in the overlay, crossings
separate instead of merging, and a colony on sloping ground produces a profile
that follows the slope.

### Phase 4 - dispersal

A saturated colony sends a mole out to found a new core beyond
`COLONY_MIN_SEPARATION`. The trap-and-release path the player will use later is
the same code with a different trigger.

**Done when** a saturated colony produces a second one at distance, both persist,
and neither adopts the other's mounds.

## Debug tooling

The rule from `MOLEHILL.md` holds: build the instrument, do not guess the number.

* `/moleverse network` extended to draw the colony box and its core.
* `/moleverse network links` - dump stored links with class, use count and depth
  range.
* `/moleverse mole dive <class>` - force the depth class of the next trip, so a
  deep run can be watched without waiting for the dice.
* A log line for every new refusal, above all the boundary one.

## To verify before writing code

* The `SavedData` registration form in this version.
* Whether a POI query can be bounded by a box cheaply, or whether containment has
  to be filtered after a radius query.
* How often a run at six blocks meets a cave or water. Far less often than the
  first version of this plan assumed, but the aborts `NOT_SOLID` and `LIQUID`
  already exist and a cut cave has to be either an abort or a junction - decide
  it with the log open.
* That the test world can show any of this at all. The current one is superflat:
  grass at -61, bedrock at -64, so `depthAt` clamps every run to `minY + 1` and
  all three levels collapse onto one. Phases 1 and 2 are terrain-independent and
  can stay there; phase 3 needs a normal world.

## Deliberately out of scope

* Migration of existing worlds.
* Resuming an interrupted trip across a reload.
* Carving anything in the overworld.
* The burrow dimension itself. This is its foundation, and the point of building
  it separately is that it has to be right before anything expensive stands on
  it.

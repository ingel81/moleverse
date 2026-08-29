# Work log

Short entries, newest last. What was done, why, and what is still open - enough
to pick the thread up again without rereading a diff.

## 2026-08-28/29 - the roof mole, colonies, and a design round

### Fixed

* **A mole surfaced on a village roof and stayed there.** Two separate defects.
  `beginEmerging` told a hill from a roof by walking the column and asking
  whether it was solid - but a village wall is solid from the soil to the eaves,
  so a mole coming up inside one passed the test and was set down on the ridge.
  It then refused every trip, because nothing up there is diggable, and paced for
  nine minutes until it was pushed off. The column test now also asks whether the
  landing spot has soil under it, and a mole stranded on ground it cannot dig for
  thirty seconds is carried back to the nearest soil (`STRANDED_RESCUE_DELAY`).
  Both confirmed in play: `the surface above is not soil - returning to the entry`
  appeared within the hour, and a mole put on a roof by hand walked off on its
  own before the rescue was needed - which is the intended order.
* **Orphan mounds.** `findFreshSite` drew a distance up to `NEW_TRAVEL_MAX` (16)
  and then rounded to block coordinates, which can round outward -
  `NETWORK_LINK_MAX` is also 16, so a fresh mound could land a fraction beyond
  the chain reach of the mound it was dug from and never join any network. Seen
  at 16.12 blocks, with two of three mounds unconnected. The site is now clamped
  from above as well as below.

### Built

* **Colonies (phase 1 of `COLONY.md`).** A colony is a fixed core and a 128x128
  box around it, stored in `ColonyStore` (`SavedData`, codec-based registration).
  Membership is derived from the box rather than kept in a list, so a player
  knocking a mound away needs no bookkeeping. Exit choice and fresh sites are
  bounded by the box; a mole on unclaimed ground founds a colony unless another
  core is within `COLONY_MIN_SEPARATION` (144, chosen so the band where nobody
  may found is 16 blocks wide rather than 128).
* **Tunnels in the network overlay.** `BurrowRoute.between` and
  `MoundNetwork.surfaceAt` now take a `LevelReader`, so the client can rebuild
  the same route from the same heightmap. The overlay draws the real waypoint
  chain at its real depth instead of a straight line between two mounds - no
  payload, no second source of truth.
* **`/moleverse colony info | list | show on|off`.** The outline is a repeating
  server-side particle draw of every colony's border, magenta, one particle per
  block, only within 80 blocks of a player - which is also what keeps it from
  loading chunks.

### Decided

* The burrow below is a dimension that mirrors the tunnel network and nothing
  else, made affordable by one constraint: the surface is not reachable from it.
  See `IDEAS.md`.
* An attachment is its own block on a **prepared** mound, not a property of the
  mound and not a block entity on every molehill. The molehill is one pixel tall,
  so anything set on the block above it would hang in the air.
* Depths are 2 / 4 / 6 blocks under the local surface - feeding runs, main runs,
  chambers - not the 24 to 48 the first draft assumed.
* Trade reads what the player offers, not where the courier had been.
* The shrinking lore is still open; three attempts are recorded in `IDEAS.md`
  along with why each was dropped.

### Observed in play

* Two moles in one colony share its mounds, having joined by standing on its
  ground - the membership rule working as intended.
* Moles never despawn (`Animal.removeWhenFarAway` returns false), so a stuck one
  stays stuck forever. That is why the stranding rescue exists.
* A colony grows mounds on its own but never moles: breeding is player-driven,
  so a 32-mound colony can consist of one animal.

## 2026-08-29, night - phases two to four, and a socket

Built while nobody was watching, so everything below is **untested in play**. It
compiles, the data generators run, a review pass found nothing, and
`runGameTestServer` boots a dedicated server to completion - which proves
registration, tags, the two-block point of interest, the payload handler and
world loading, and proves nothing at all about behaviour. Not one mole has been
watched doing any of it.

### Built

* **The link store** (`COLONY.md` phase 2). Every completed run is written down:
  both mounds, the depth level, one height per waypoint, a use count. Held in
  `ColonyStore` next to the colonies. Only clean arrivals are recorded - a trip
  that hit water, open air or the roof guard describes no tunnel. Stale runs are
  pruned when somebody asks, and only where the chunk is loaded, because "no
  mound" and "nothing loaded" look the same from a data structure.
* **Depth levels** (phase 3). Feeding runs at two blocks, main runs at four, a
  chamber level at six that nothing chooses yet. A pair of mounds keeps the level
  of its first run, so an established run cannot change depth under the burrow
  that will one day mirror it.
* **Stored runs in the overlay** (phase 3b). Depth levels break the client's own
  reconstruction, which assumes one depth; particles cannot fix it, because a
  particle inside solid ground is hidden by it. So the server sends what it has -
  a payload, a toggle, one colour per level - and the overlay draws that instead
  while it is arriving.
* **Dispersal** (phase 4). A mole in a colony that is full and out of room walks
  out, in short legs, on a bearing away from its core, until it stands where a
  colony may be founded. It refuses to leave if it would empty the colony.
* **The prepared mound.** A second mound block with a rim, modelled in Blockbench
  and exported by hand, plus the tag, the point-of-interest type covering both
  blocks, loot, models and both locales. Nothing sits on it yet - this is phase A
  of `ATTACHMENTS.md`, the socket rather than the fitting.

### What to test first

1. `/moleverse colony links` after a few trips - runs appear, survive a reload,
   and disappear when their mound is broken.
2. `/moleverse colony tunnels on` together with `/moleverse network on` - main
   runs must be drawn two blocks below feeding runs, in a different colour.
3. A prepared mound placed from the creative tab: a mole must treat it exactly
   like a molehill - travel to it, open its shaft, come up out of it. If the
   point-of-interest type is wrong it will be invisible to every colony, and that
   is the most likely thing to be broken here.
4. A shaft lantern on that prepared mound: it should light up as a mole surfaces
   and go dark five seconds later, and pop off when the mound under it is broken.
   Its model uses the vanilla lantern texture as a placeholder and will look
   rough.
5. Dispersal needs a full colony and two moles, so it will not show up by
   accident. `/moleverse colony list` before and after.

### Also built

* **The attachment socket** (`ATTACHMENTS.md` phase B) and a **shaft lantern**
  on it - a lamp that lights while a mole is coming out of the mound beneath it.
  It is the smallest thing that proves the socket, and it doubles as the first
  way to watch a colony work without a debug overlay.

### Learned about the toolchain

* **Parchment was already switched on** in `gradle.properties`, and the mapped
  sources are in `build/moddev/artifacts/neoforge-21.11.45-sources.jar` - real
  parameter names, javadoc and NeoForge's patch comments. Every API question
  tonight was answered from the raw neoformruntime decompile instead, which has
  none of that. `CLAUDE.md` now points at the right file.
* That mistake cost something concrete: the raw decompile shows `SavedDataType`
  demanding a `DataFixTypes`, so the colony store borrowed the vanilla level type.
  The patched record takes null and has a three-argument constructor for exactly
  this. Fixed.

### Deliberately not done

* No interaction turns a molehill into a prepared one yet - what preparing costs
  is still open, so for now both the prepared mound and the lantern are placed
  from the creative tab.
* No recipe, no cost, and no texture of their own. The lantern borrows the
  vanilla one.

## 2026-08-29, deep night - the burrow exists

Six modules, six commits, each one droppable without the others - see
`BURROW.md` for the map and the seams.

* **The dimension.** `moleverse:burrow`, solid `deep_earth` from bedrock to 256,
  flat generator with no features, no lakes and no structures. Dark, ceilinged,
  no skylight, coordinate scale 1 because the mod does its own mapping. The
  dimension type in 1.21.11 uses an `attributes` block rather than the flat field
  list every older example shows - worth knowing before touching that file.
* **Geometry.** Four times horizontally, two vertically. The vertical is smaller
  on purpose: runs follow the ground, and at the full scale a three block dip
  becomes a twelve block drop.
* **Carving.** A stored run becomes a 5x6 corridor along its waypoints,
  interpolated so it comes out as a tunnel rather than a string of boxes. Only
  ever eats `deep_earth` or air, skips unloaded chunks, and answers "already
  carved" by looking rather than by remembering.
* **Decoration.** Everything is decided from the block position, so a corridor
  keeps its character between visits. Pools of glow mycelium with dark stretches
  between them, root beams to duck under, gravel and clay underfoot, a seep every
  sixty blocks, mineral speckle in the walls.
* **Transit.** A shrink post on a prepared mound takes a player down, carving the
  chamber and the runs that meet there; the same post in the chamber brings them
  back, and refuses when the mound above has been broken.
* **Preparing a mound** is now a player action: right-click a molehill with loose
  soil. It was creative-tab only before.
* **`/moleverse burrow enter | leave | carve | info`** for getting down there
  without playing for it.

Verified: compiles, data generators run, and `runGameTestServer` boots with the
dimension registered and no parse errors - which proves the datapack is correct
and proves nothing about whether a corridor is walkable.

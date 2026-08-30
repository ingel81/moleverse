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

### Second wave, same night

* **Chambers are furnished.** Worm larders in the walls - moles really do store
  paralysed earthworms, and at this scale that is furniture - plus nests, root
  pillars holding the wider ceiling, and the way out standing in the middle.
  Breaking a larder gives the worms back, which is so far the only thing down
  there worth carrying home.
* **Ambience.** Dust in the air, the occasional settle or trickle, and brown
  close fog instead of the default black. All client side, all returning
  immediately anywhere but the burrow.
* **Six game tests**, run by `runGameTestServer` on every invocation: the
  geometry round trip and its clamp, carving clearing ground while leaving a
  floor, `alreadyCarved` before and after, and the link store surviving a write
  and a read.
* **The vertical clamp**, found by writing those tests. The mapping ran straight
  out of the dimension for any colony far from sea level - a superflat world at
  -60, a mountain at 140 - and carving at a height that does not exist fails
  silently and buries whoever arrives. The test caught the clamp's own side
  effect one run later, which is the whole argument for having tests at all.

### Third wave - something to meet down there

* **The great worm.** The earthworm that is farmed above ground, at the scale
  the burrow is built to: about four blocks long, harmless, slow, and it crawls
  with a wave running down its segments rather than walking. Its shape came out
  of a generator script under `art/generators/` rather than being placed cuboid
  by cuboid - the same reasoning as the mole's animations.
* **The burrow stocks itself.** Spawning is not a biome rule, because the burrow
  borrows a vanilla biome and anything added there would appear in the overworld
  too. Instead a chamber that a player opens gets up to two worms in the
  corridors around it, counted first so that walking in and out ten times does
  not produce ten worms.

### Still open when this stopped

* The review arrived late and found three of the same kind: block reads in the
  decorator's probes that were not guarded by a loaded-chunk check. In this
  dimension an unguarded read does not merely load a chunk, it generates one, so
  dressing a long run could have turned into a freeze. Guarded, and the reviewer
  cleared arrival, the way back, replacement rules and sides.
* Textures are vanilla stand-ins - `deep_earth` borrows rooted dirt, the lantern
  borrows the vanilla lantern, the larder borrows muddy mangrove roots. Only the
  great worm has a texture of its own.
* Nothing sets the price of a shrink post. It is placed from the creative tab,
  because what the way in should cost is the one design question still open.

### Fourth wave - fittings, economy and a way out

* **Exchange station** - a machine with a screen and two inventories. Worms in,
  finds out, moved across by a mole surfacing through the mound beneath it. A
  full station never eats a worm. The find table is a placeholder until the worm
  tiers grade it.
* **Grunting post** - the one fitting that needs no mound: a stake driven into
  soil, rasped across, and worms come up. Real technique, and the first source of
  worms that does not involve breaking somebody's molehill.
* **Colony board** - what `/moleverse colony info` says, said in the world.
* **Worm box and two more worms.** Soil and plant matter in, worms out, slowly -
  a composter's relative with no block entity at all. The fat worm buys better
  finds, the glow worm is the bridge to what lights the burrow.
* **Mole trap and mole in a sack.** Baited with a worm, springs on the next mole
  to surface, and the catch is carried in an item that survives a reload.
  Releasing it somewhere far enough away is how a colony gets founded on purpose.
  A trap blocks its mound while it stands - catching costs the network a door.
* **Recipes**, so none of this is creative-only any more, and an **advancement
  tree** that doubles as tomorrow's checklist: an advancement that never fires is
  a feature that never ran.
* **Level shafts.** Feeding runs and main runs lie four blocks apart down there,
  one above the other, and until now nothing connected them - the burrow was two
  networks that never met. Shafts are cut where two runs cross.
* **A way out when the door closes.** Breaking the mound above a visitor used to
  trap them with no exit, no beds and unbreakable fill. Now the burrow watches
  for it.
* **Own textures**, generated from scripts under `art/generators/` rather than
  drawn by hand. Only the shaft lantern still borrows from vanilla.

### Fifth wave - the burrow reads as a network

* **A main run no longer looks like a feeding run.** Corridor size is a profile
  per level rather than one constant, so a colony's backbone is visibly the
  backbone and a feeding run is a side passage. The width stays odd, because a
  corridor without a centre line has nowhere to lay a floor.
* **Junctions.** Where two runs of a level cross, a place instead of an overlap.
  Capped per colony, never on top of a chamber - one continuous cavern would be
  worse than no junctions at all.
* Caught by the tests again: the carving test asserted the old global width and
  failed on a change that was correct. It now measures against the profile of the
  level it carved.

### One bug found by an agent reading somebody else's wiring

`BurrowTransit.enter` handed the shaft and junction cutters only the runs that
end at the mound being entered. Two runs sharing an endpoint cross only at that
endpoint, and both cutters reject a crossing sitting on a mound - so from that
call site neither could ever cut anything, silently, forever. Both now get the
colony's whole set of runs, and the debug carve command cuts through what it
carves. Worth remembering as a shape: a filter that is correct for one caller and
fatal for another, with no error either way.

### What the texture pass taught

Worth keeping, because each of these cost rounds:

* **A hard outline all the way round eats everything small.** At 16 pixels a
  drawstring and a snout become one black clump. Shade from the silhouette
  instead.
* **Tapering is a factor on the whole width, not a subtraction from it.**
  Subtract and the flanks stay vertical: the sack comes out a crate.
* **Random walks with right-angle turns always read as a maze.** Roots and
  mycelium sit on sine curves with whole-number frequencies instead, which also
  makes them tile.
* **Pure pixel noise flickers at 16 pixels.** Soil gets its body from clumps.
* **Two blocks of the same material need different grain**, or they are two names
  for one block - the trap lays its planks vertically because the worm box lays
  its slats flat.
* An item has to be tellable from its neighbours in a hotbar **on every axis at
  once**: the sack against the pelt is tall rather than round, warm rather than
  cold grey, and its accent sits low at the neck rather than across the top.

The great worm was the outlier the audit caught: 3965 colours across 128x128,
where the mole uses 150 and a block uses 7. Regenerated at 9. It looked fine on
its own, which is exactly why nobody noticed - it only fails next to the rest.

## Handover - testing starts here

Nothing below has been played. Work through it in this order; each step depends
on the one before actually having worked.

**1. A colony forms.** New world, then `/moleverse mole log on` before the first
mole digs, or the colony is founded silently. Expect `founded colony #1`. Then
`/moleverse colony show on` for its border and `/moleverse colony links` once a
few trips have happened.

**2. Runs are recorded and have depths.** `/moleverse colony links` must show
feeding runs and main runs at different levels. If every run is a feeding run,
`MAIN_RUN_CHANCE` never fired.

**3. A prepared mound is still a mound.** Right-click a molehill with loose soil,
then watch a mole travel to it, open its shaft and come up. **This is the most
likely thing to be broken**: if the point-of-interest type does not cover both
blocks, a prepared mound is invisible to every colony and everything downstream
silently stops.

**4. Fittings answer.** Shaft lantern lights when a mole surfaces. Colony board
reports. Exchange station takes a worm and gives something back. Grunting post
brings worms up and goes on cooldown.

**5. Down.** Shrink post on a prepared mound, right-click. Expect a chamber, the
runs of that colony carved, a way out standing in the middle, up to two great
worms. Then: is the corridor walkable, is the light enough, can you get from a
feeding run to a main run through a shaft, do junctions read as places.

**6. Back up.** Right-click the post below. Then break the mound above while
somebody is down there and check the rescue.

**7. Dispersal** needs a full colony and two moles, so it will not show up by
accident.

The **advancement tree is the checklist**: one that never fires is a feature that
never ran.

Known unknowns, in likely order of trouble: the point of interest covering both
mound blocks; whether carving keeps up with a player walking a long run; whether
the burrow is navigable at all in the dark; the exchange station's screen syncing
its two inventories; and the mole trap's stored animal surviving a reload.

## 2026-08-29, morning - the first play, and a machine to replace it

The handover above was written blind. This is what happened when the mod was
actually run, and how the running stopped needing a person.

### What the first client session showed

The mechanic works. A colony was founded thirty seconds in, runs were recorded at
both levels - 162 feeding runs at depth 2 and 79 main runs at depth 4, so
`MAIN_RUN_CHANCE` fires - and the travel estimator was exact on every single
trip, never off by a tick.

The bug was in the shape the handover predicted for something else entirely: a
mole that does nothing, with no visible cause. Of 520 burrow attempts, 245 were
refused with `too near another colony to start one here`, and one animal
accounted for 114 of them across seven and a half minutes. That is the unclaimed
band between `COLONY_EXTENT` and `COLONY_MIN_SEPARATION`, where `ColonyStore.at`
finds no colony to join and `found` still refuses to start one.

`MoleEmigrateGoal` was supposed to be the way out and could not be: `canUse`
returned false whenever the mole belonged to no colony, which is the definition
of standing in the band. So the only exit was `wanderNow`, an undirected stroll
across eighty blocks. Chance eventually carried the mole out. That was the whole
mechanism.

### Then the client became the bottleneck

Finding that took a person playing for sixteen minutes and a grep across eight
thousand lines. Everything after it was found by a server nobody watched.
`tools/soak/soak.sh` runs a scenario headless; `docs/TESTING_AUTOMATION.md` has
the reasoning and the traps. The short version is four facts:

* `/forceload` takes a ticket at level 15 and entity ticking starts at 31, so
  forced chunks simulate with nobody logged in.
* `pause-when-empty-seconds` defaults to 60 and then returns before
  `tickChildren`. An empty server ticks *nothing* until that is set to 0.
* `/tick sprint` is equivalent to waiting, because nothing here reads a clock -
  `currentTimeMillis`, `nanoTime` and `Instant` appear nowhere in `src/main/java`
  and every duration is a multiple of `TICKS_PER_SECOND`. Measured at 3000 to
  6000 ticks per second: an hour of game time in ten to twenty seconds.
* Nothing spawns without a player, so scenarios summon at fixed coordinates -
  which is the only way to get the same starting state twice anyway.

Two hours of game time, six moles, about two minutes of real time.

### Three more bugs, each found by a run

* **The emigration stopped on the line.** Arrival was `isFreeGround`, which turns
  true at exactly `COLONY_MIN_SEPARATION` - a line, not a place. The mole stood
  down there, strolled, and was back in the band a step later: seventeen further
  refusals after it had "arrived".
* **The log lied about which case it was.** `BurrowLog.emigrating` said `leaving a
  full colony` unconditionally, including for moles that belonged to no colony at
  all - which is now the commoner of the two.
* **`EMIGRATION_MARGIN` never applied.** Aiming at separation plus 48, founding at
  separation, short by exactly 48 in every run. Not a slip: `ColonyStore.found`
  accepts at the lower threshold and `MoleBurrowGoal` asks it every three
  seconds, while arrival is only checked while walking. The lower threshold is
  checked more often and always wins. Colony spacing comes from
  `COLONY_MIN_SEPARATION` alone, and the javadoc now says so instead of promising
  otherwise.

### Colonies moved apart

`COLONY_MIN_SEPARATION` went from 144 to 224 with `COLONY_EXTENT` left at 64, so
the ground between two territories went from sixteen blocks to ninety-six. The
old note on that constant argued against exactly this, on the grounds that a mole
in the band paces and does nothing - true when it was written, and the reason the
number was low. That was a missing exit rather than an argument for a narrow
band, and with the exit built the width costs a walk instead of a stall. Measured
after: 28 refusals across two hours and six animals, against 114 from one animal
before.

### What a soak run cannot do, and two that were thrown away

A run only measures what the scenario sets up, and two of the first five were
worthless in ways that looked entirely healthy from the outside:

* One used a default world and put five of six moles on a mountainside where
  nothing is diggable. It measured geography. All five then walked out of the
  loaded square and froze against its boundary at z=126, which the log faithfully
  recorded as an hour of inactivity.
* One deleted `run/saves/soak2` and started the server on `run/soak2`, because a
  dedicated server does not use the client's `saves` directory. It ran on the
  previous run's colonies, produced forty thousand lines and 3919 successful
  burrows, and proved nothing at all.

Neither failed. Both produced output that read as a result. The scenario file now
checks the starting state before summoning anything, and the script aborts if the
world it meant to delete is still there.

### Still open

Unchanged from the handover: the point of interest covering both mound blocks,
carving keeping up with a walking player, whether the burrow is navigable in the
dark, the exchange station's screen, the mole trap across a reload. None of that
was touched today - the runs never went underground.

Added by the runs:

* **A mole reaches a known mound in 5% of attempts** - 454 approaches, 22
  arrivals, 430 `path to the entry mound exhausted` recoveries. Every failure
  ends in a fresh hole where the mole stands, which is what drove the density cap
  and produced fourteen mounds in a heap. Seen only on uneven ground with mounds
  packed together; flat terrain hides it entirely.
* **`ground is not diggable` never names the block.** The most common refusal in
  the first run, 1373 times, and undiagnosable: `passesGuards` refuses at
  `MoleBurrowGoal:290` and the block name is written at 299.
* **Dispersal from a full colony cannot fire, and that is a design question.**
  Two moles on one colony filled it to `NETWORK_MAX_MEMBERS` inside the first
  hour and then made 2578 trips over seven more without one refusal. `leaveWish`
  is set only where `exit == null`, so the condition is "full *and* no trip at
  all" - and a full colony is the least likely place to run out of trips. The
  full-colony half of `MoleEmigrateGoal` has therefore never run. Whether a full
  colony should push somebody out at all is undecided; if it should, the wish has
  to hang on fullness rather than on being stuck.
* **Nothing caps moles per colony.** `NETWORK_MAX_MEMBERS` bounds mounds, not
  animals, and with dispersal unreachable a fed pair breeds an unbounded
  population into one territory of thirty-two mounds. Slow - five minutes between
  breedings, twenty for a juvenile to grow - but with no ceiling.
* **A juvenile follows the nearest adult, not its parent.**
  `MoleFollowMotherGoal.findDivingAdult` takes whichever grown mole is nearest and
  diving. Cosmetic while both adults do the same thing, wrong as soon as they do
  not.

## 2026-08-29, afternoon - the first descent

Somebody went down for the first time. Three findings, and two of them were the
same bug wearing different clothes.

### One mistake, four places

A mound is `noCollision` and raises no heightmap. Every fitting except the
lantern is solid and does. So on a mound wearing one, `MoundNetwork.surfaceAt`
lands two blocks high - and four separate pieces of code took that answer as the
mound's own position:

* **Surfacing.** `beginEmerging` walked the column, found the hollow mound in it,
  and turned the mole back with *"ground above is built over"*. An exchange
  station refused four consecutive deliveries. The station exists so that a mole
  can surface through the mound beneath it, so the fitting made impossible the
  one thing it is for.
* **The overlay.** `findMounds` looked at the top block, found air, and drew
  neither the mound nor any line ending at it - which is how a working colony
  came to look like it had lost both its exchange stations.
* **The way home.** `moundAbove` reported *"the mound above this chamber is
  gone"* to a player standing under a mound that was plainly still there. The
  shrink post that let them in was what hid it.
* And `isWayOut`, through the same method, so the post said the same thing when
  merely looked at.

All four now resolve through `MoundAttachment.moundUnder`, which is the one place
that knows a fitting sits on a mound. Worth remembering as a shape: the overlay
comment cited the server's identity as its justification, and the server had the
same wrong assumption - two places agreeing with each other is not evidence.

### And one that is not a slip

The corridors are not built. `BURROW.md` has the full account: carving is hung on
somebody entering the burrow rather than on ground coming into existence, and
every write into an unloaded chunk is skipped without a word. Sixteen blocks of a
sixty-block run survive. The fix is the shape worldgen already has - a chunk
carves the runs that pass through it - and it is a session of its own.

## 2026-08-29, evening - the burrow generates like a world

The corridors-are-not-built finding from the afternoon is closed, the way the
morning entry said it should be: a chunk asks which pieces of the colony's
network pass through it and carves those. Three agents in parallel built the
plan layer (`dimension/plan/` - features with key, hash, bounds, clamped
carve), the clamp through every carver plus an organic cross-section (value
noise along the run, arched ceiling, mouths tapered to plain section), and a
rebuilt decorator (8-block grain cells give stretches a character - rooty,
mossy, stony, bare - wall veins instead of speckle, staged glow pools with a
hard darkness cap, trodden path flanks, seep banks). A fourth wired it live:
a ledger attachment per chunk, a load-queue drained a few chunks per tick,
decoration deferred until the 3x3 neighbourhood is carved, and
`ColonyStore.record` notifying the reconciler so a run appears below while the
mole above is still digging. `enter` shrank to mound check, chamber ring,
teleport.

The deferred-decoration rule earned its keep before anybody played: the
cross-chunk game test caught `walkLevel` probing with bare `isAir`, so a
dressed slice measured one block higher on the next visit and paved its own
carpets over. The fix was a predicate and a missing guard on the moss carpet -
and the invariant (a dressed slice, dressed again, measures identically) is now
stated in the class and held by the test. Eight game tests, all green. Not yet
seen by a person: whether the organic sections and the grain read as intended
from inside. That is the next descent.

### Glow-worm gate vs. same-tick breaks

Glow-worm gate reads neighbour block light, and light updates flush at end of
tick - so blocks broken in the *same* tick (FTB Ultimine vein-mining,
explosions) read pre-break light and yield no glow worms. Intended: careful
harvesting beats strip-mining. Not a bug.

## 2026-08-29, night - anatomy, biome, loot, creatures

The full BURROW_LIFE plan was approved in one word and built in waves. Wave A:
the nest chamber at the colony core (largest room, hay-bedded hollow, the warm
hollow trove - four larders over four nodules under a moss lid, lit by an
L-8-correct frame), larder alcoves off deep runs, bolt-hole stubs (honestly
documented as not reaching the overworld - the burrow is its own box), a
custom moleverse:burrow biome (1.21.11 moved biome effects into environment
attributes; every tutorial is wrong), and root-nodule wall pockets with loot
tables (light-gated glow worms via six-face neighbour checks). Wave B: three
creatures - earthworm, soil beetle, grub - authored through the Blockbench MCP
pipeline (the viewport caught floating legs, a covered elytra seam, and a
stepped grub arc that pure code review had passed), spawning as AMBIENT
because CREATURE only passes the filter every 400 ticks and the flat generator
never runs the base-population pass.

The cross-chunk equality test earned its keep three more times: a nodule
ceiling broke the decorator's sweep gate (predicate gap), nodules silently
cancelled a quarter of all larders (all-or-nothing validation meeting a block
nobody told it about), and the nest trove lost larders to its own lining. The
test fixtures now sit on fixed lanes 32 chunks apart - they had been sharing
chunks, and the per-chunk ledger attachment does not care about height bands.

Still open: shrew and weasel, the scratching ambience, advancements and
recipes, the fortress mound (all running as packages), the way-home refusal
(instrumented, awaiting one reproduction), and commits.

## 2026-08-29, late night - the second playtest and what it taught

The playtest loop ran all evening: user below, findings up, fixes down, restart.
What it produced, in the order it was found:

* **The way home refused whenever nobody was upstairs.** 124 refusals in one
  log: getHeight answers the world floor for an unloaded chunk, so the mound
  read as gone exactly when no mole kept the ground warm. leave() now loads
  its one target chunk; isWayOut stays optimistic rather than dragging chunks
  in for a glance.
* **The shrink post was the wrong thing to climb out of.** Replaced below by
  the root ladder - braided roots from the chamber ceiling, right-click to
  surface, unbreakable (the rope is the rescue route; pushReaction BLOCK, or
  a piston could do what a pickaxe cannot). Existing chambers migrate on next
  entry. First model was a crossed pair of planes; now one run of 2px boxes.
* **Vanilla spawning cannot populate this dimension.** Measured, not felt:
  random volume rolls hit corridor air 0.7% of the time in crossed chunks,
  0% elsewhere - one beetle all evening, zero grubs. All spawning moved to
  BurrowLife: waypoint-based trickle per player (worms, beetles, shrews with
  their own dark-and-distance gates), grubs stocked at larder alcoves by
  position hash, spawner lists emptied. The link polylines are guaranteed air.
* **Scaled fauna wore unscaled hitboxes.** Attributes.SCALE baked into the
  supplier never goes dirty, so Entity's constructor-cached dimensions stay
  1x while the renderer reads live - a 4x model half inside a wall. Fix:
  refreshDimensions() in every scaled entity's constructor (AgeableMob is
  vanilla's own precedent).
* **The travelling mole existed but could not appear.** Two independent
  causes: the wet-route loop meant no link was ever recorded (fixed: routes
  are probed for liquid before digging, wet ground is shunned and walked away
  from), and in singleplayer the overworld does not tick while the player is
  below, so trip events cannot fire at all. The ambient lane answers the
  second: every 2-4 minutes the local colony walks one of its own runs -
  same entity, same guided walk, self-paced, one per colony across both
  lanes, entering 48 blocks ahead of the player and passing rather than
  meeting.
* **The rescue countdown fired on every corridor walk** - SURFACE_WINDOW=6
  predates corridors ("a few paces off the chamber floor"). Being fixed:
  two-stage strandedness, the colony's surviving mounds as the real question.
* **Scale audit** (reference: mole 15cm = player): fauna consciously heroic
  (beetle bigger than biology, user's call), weasel out of the spawn list -
  a real weasel outsizes a mole, so it returns as a corridor-filling
  incursion event, not a mob. The travelling mole goes to SCALE 7 after
  being mistaken for a beetle at 4.
* **Light** 0.12 -> 0.04 -> 0.02 by request; ladders, larders and the nest
  frame carry the navigation.
* Art detail pass by two agents: six creatures gained geometry (mole left
  alone - tuned animations outweigh detail), 14 block/item/GUI textures
  reworked, all three mounds now generated asymmetric from one recipe.

Open for tomorrow: the traversal debugging session (full dev logging is being
laid in tonight - gait transitions, weave clamps, rubber-band saturation),
the soak run verdict, panel-tuned constants to bake, and the commits.

### The soak verdict (burrow_era, 2026-08-30 00:11)

Two hours of game time, three colonies, 2001 completed trips, zero exceptions,
zero ticking-entity errors, zero way-home refusals. The null paths held: with
nobody below, every armed traversal ended with "no player came within 64
blocks of the run" - the gate named itself, nothing spawned into the empty
dimension, and the ambient clock stayed silent as designed. The wet-route fix
shows in the shape of the run: the refusal summary is "none".

One open eye for tomorrow: the fortress mound writes no log line, so the soak
cannot say whether the three colony cores actually wear it - first in-game
look at a core mound answers it.

### The idle-time batch (2026-08-30, small hours)

Four packages while the user was away: the weasel incursion is built (guided
hunt at 4.95 wide, swelling hiss plus critter panic as the ten-second warning,
bolt-holes proven safe by geometry, 1-2 pelts guaranteed); the creature sound
accents are generated and wired (worm slither continuous while it flows,
beetle clicks at long intervals, grub munching, ladder rustle); the whole
debug surface is dev-gated behind the new DevGate umbrella (a shipped build
had every /moleverse client command typeable by anyone - closed); and
docs/KINGDOM.md holds the 1.0 concept - a fungal kingdom under all colonies,
entered by feeding the warm hollow until it fruits (Hebeloma radicosum is
real), with Passage as the proposed second gift. That last one carries the
decision that needs the user: whether fast travel between colonies is what
1.0 is for.

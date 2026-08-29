# Ideas

A collection, not a plan. Nothing here is decided and nothing here is built.
`ROADMAP.md` says what the stages are for; this file says what could go in them
and, more importantly, why some of it should not.

Written down after a brainstorming round so that the reasoning survives. Where an
idea was argued down the argument is kept rather than the idea deleted - a
rejected idea that comes back a year later should meet its own counter-argument,
not a blank page.

## What the collection is organised around

Three points came out of the round before the ideas did, and most of what follows
is a variation on one of them.

**The network is the asset.** The most valuable thing already built is not the
mole, it is the mound network: the POI index, the chained neighbourhood, the
waypoint routes, and the rule that a mound a player placed counts as a full
member. An idea that spends that is cheaper to build and better to play than one
that ignores it. `MOLEHILL.md` already calls the waypoint route "the hook 0.3
will hang on".

**The mound is the socket.** Chest, trap, lantern, marker - they are attachments
to `mole_mound` rather than blocks of their own. One block, several fittings.
This is what finally gives a player-placed mound a purpose, and it means trading
with wild moles needs no taming mechanic first.

**Nothing carves the overworld.** Every idea that has moles dig real tunnels
through the world someone lives in is a grief vector and a support burden. The
burrow below is where tunnels become walkable, and it is a world that belongs to
nobody.

## The burrow below

A dimension that mirrors the tunnel network and nothing else. The idea started as
"a dimension that is exactly the network, only bigger", which is prohibitively
expensive, and became affordable through one constraint: **the surface is not
reachable from it**.

### Why that constraint is the whole trick

With no way up, the mirror never has to reproduce the overworld. No houses, no
trees, no caves, no blocks a player placed. None of it is visible from below, so
none of it has to be kept in sync. What is left is chambers where mounds are,
corridors where routes are, and solid fill everywhere else. The problem shrinks
from "keep two worlds consistent forever" to "carve a graph".

### Shape

* The dimension is solid to begin with - a fill of deep earth, no generation
  worth the name, a small vertical range and an unbreakable ceiling of roots.
* Travel is our own code at both ends rather than the vanilla coordinate scale: a
  mound at `(x, z)` leads to a chamber at `(x*S, z*S)`.
* On arrival the chamber is dug out, along with the routes that meet at that
  mound. More is dug ahead of the player as they walk, from the same waypoint
  geometry the overworld route uses, multiplied by `S`.
* Carved corridors are ordinary blocks and persist in the region files. A second
  visit digs nothing again - the air is the record.
* An exit works only where the mound above it still stands. A mound somebody
  knocked away is a door that closed, not corrupt data.

### Travel is slower, not faster

Bigger corridors mean stretched coordinates, so a trip below covers a quarter of
the ground it would above. That is the opposite of the nether and it is welcome:
the whole fast-travel balancing problem disappears, the mirror is a place rather
than a highway, and distance is itself a limit on how far anyone strays.

### Scale is the content

At 1:1 a mole run is a featureless tube and there is nothing to author. That is
the argument for stretching it: at four times, the run is a gallery, and the
small life of the soil becomes the large life of the burrow.

* An earthworm is an animal the size of a horse, shouldering along the corridor -
  the same creature farmed above.
* Beetle grubs, centipedes, springtail swarms: a fauna that can exist at no other
  scale.
* Roots are beams. Supports grow rather than get built.
* Gravel is a boulder field and a stone is a block in the road.
* Fungal mycelium is a lit web across the ceiling. That is where light comes
  from, not from torches.
* Seep water drips, gathers, becomes a ford.
* The worm larders a colony really keeps - worms stored alive - are a chamber
  rather than a detail.
* The moles themselves pass as large animals. A mole digging by is an event.

Weather reaches down: rain means seep water and worms rising, frost means hard,
still ground.

The tone to hold: sober and biological, with an edge of being a guest somewhere
that did not plan for you.

### Limits, in layers

| Layer | Means | Effect |
|---|---|---|
| Physical | the fill cannot be broken | leaving the corridors is impossible rather than forbidden - nothing to police |
| Topological | corridors exist only where routes do | the world is exactly as large as the network; more room means more moles |
| Spatial | the scale factor `S` | four paces below for one above; being far away costs real time |
| Economic | entry needs an attachment on the mound | not every molehill is a door |

Air or light pressure could go on top if those four turn out not to be enough.
They should not go on before that: a timer that hurries the player is the
cheapest kind of difficulty and the first thing to make exploring tiring.

### What falls out of it

The mirror is a record of animal behaviour rather than a designed space. Dead
ends where a route was abandoned, dense knots under an old colony, one long
corridor where a mole travelled far. None of that can be authored and all of it
comes free with the mechanic.

### What it needs

One new foundation: **persisted routes**. Nothing is written to disk today
(`MOLEHILL.md`, "Recovery on load"), and without a stored route there is nothing
to mirror. Everything else is small: dimension and dimension type as datapack
JSON, a fill block, the transition at both ends, and the digging ticker. No
custom chunk generator, no cross-dimension lookup during world generation, no
mixin.

To verify against the decompiled sources before any of it is written: the
dimension JSON fields in this version, and which hook owns a custom transition
between dimensions.

### Open questions

* How large is `S`? It is three knobs rather than one, and they only look like
  the same number - nobody in the game can measure the ratio between them.
  * **Position** decides how far it is between chambers. A typical route of
    thirty blocks becomes a 120 block walk at four and a 240 block walk at eight.
  * **Cross-section** decides how much there is to author. An empty 4x4 run reads
    as tight and therefore plausible; an empty 8x8 run reads as an unfinished
    hall. The larger the scale, the less it forgives emptiness.
  * **Vertical** is what actually breaks at a large factor. Routes wander a few
    blocks in Y, so at eight a three block dip is a 24 block drop. Either ramps
    everywhere, or Y scales weaker than X and Z.

  Carving volume grows with the cube: the same route is roughly 1,900 blocks of
  air at four and 15,400 at eight. Settled by walking it rather than by
  arithmetic - a debug command that digs a sample stretch with given values in a
  test world, in the spirit of the pose panel. This is a question to look at, not
  to calculate.

  Working assumption: the corridor cross-section lands **somewhere between 4x4
  and 8x8**. So the test walks that range rather than comparing its two ends, and
  the carver takes the width as a parameter from the start rather than baking one
  in.
* What is down there besides corridors? Empty galleries get dull fast. The burrow
  structure planned for 0.5 probably belongs here rather than in the overworld,
  with worm larders, root growth and a matriarch in a large node.
* Does a corridor grow ahead of a player while a mole digs above? A strong image
  if it works.
* Is building allowed below? Floor fittings only - light, supports, chests. Free
  building turns it into a bunker.

## Mound attachments

### Exchange chest

A chest set on a mound becomes a trading post. Worms go in, finds come out. It
needs no taming and no command: moles surface at that mound anyway.

**What you put in decides what comes back.** The trade reads the offering, not
the courier: a better worm buys a better return, the way bartering works
elsewhere in the game. Where the delivering mole had been was tried as the rule
and dropped - it makes the same worm worth different things for reasons a player
cannot see, and an exchange nobody can predict is not an exchange.

Everything past that - which tiers exist, what each one returns, whether rarity
comes from the worm alone or from something else offered with it - is
deliberately left open until the worm chain itself is settled.

Open: does every arriving mole deliver, or only against a worm? Must the shaft be
open? What happens when the chest is full - does the mole stop delivering or
start avoiding the mound?

### Trap

A trap on a mound, a worm as bait, and the next mole to surface there is caught.
A caught mole becomes a mole in a sack.

This is the non-violent way to acquire one, and it settles the contradiction in
the pelt armour idea: no dead mole is needed for anything. Uses: relocate it and
start a colony somewhere deliberately, breed it, tame it later.

Open: does the trap need a live network to catch anything - and does a trap block
that mound for travel? It should. Catching costing throughput is the interesting
version.

### Later

Lantern, marker, and the entrance fitting for the burrow below. All the same
socket.

### How an attachment is represented

Settled early, because everything later leans on it: **an attachment is its own
block**, not a property of the mound and not a block entity every mound carries.
And it does not go straight onto a molehill - the mound is prepared first.

* A block state property would multiply the mound's states - it already has an
  open flag and two variants - and a chest needs a block entity regardless.
* A block entity on every mound is the expensive one: a colony grows two dozen
  mounds in an hour and hundreds over a world's life, and almost none of them
  will ever carry an attachment.
* A separate block keeps plain mounds dumb and cheap, gives every attachment its
  own model, behaviour and loot, and puts a block entity only where one is
  actually needed.

**The mound is prepared first.** A molehill is a flat heap - the first element of
its model is one pixel of sixteen tall - so anything set on the block above it
would hang in the air with most of a block of nothing underneath. The step
between is a **prepared mound**: the player shores the heap up into a block with
a rim and a level top, and only that takes a fitting. Two things fall out of it
at once. The gap disappears, because the fitting now rests on something. And not
every molehill in a meadow is a trading post: preparing one costs material and is
a decision, which is exactly the gate the sockets needed and did not have.

So the chain is **mound - prepared mound - attachment**, and each step is a
player's doing except the first.

What the family shares: a fitting needs a prepared mound under it, it breaks when
that goes, and all of them answer the same hook when a mole surfaces there -
which `MoleBurrowGoal` already visits when it opens an entry or places an exit.

Consequences to handle when it gets built:

* The point-of-interest type is currently built from the mound block's own
  states (`ModPoi`). A prepared mound is a different block and would drop out of
  the index, and with it out of every colony. The type has to cover the whole
  family, and `MoleMound.isMound`, `setOpen` and `canPlaceAt` with it.
* The open flag has to survive preparation: a mound may be prepared while a mole
  is down its shaft.
* The mound is `noCollision`, so a surfacing mole stands inside the mound's own
  block. A prepared one is a solid rim and has to leave that space free, or moles
  stop being able to use the very mounds a player cares about most.

## The worm economy

Worms are what goes in, so worm production is the single knob for the rate of
everything else. One number instead of five.

* A worm box, a relative of the composter: soil and plant matter in, worms out,
  slowly.
* Tiers are the natural extension - earthworm, fat worm for a better table,
  glow-worm as both a rarity and a light source in corridors.
* Worm grunting, which is a real technique: drive a stake into the ground, rasp
  across it, and worms come up. Active harvesting for the player and bait for the
  moles at once, and worth having precisely because it is true.
* Feeding rare finds back into the box for better worms closes the loop.

Keep worms a currency towards moles only. The moment a villager accepts one it
becomes an emerald farm and the rate stops being the player's problem.

## Other ideas that spend the network

* **Worm post.** Throw an item into an open mound and a mole carries it
  underground to another mound of the network. Player-placed mounds already
  count, so the endpoints are buildable today. Short range only: trips tick only
  in the entity-ticking area, and that has to be stated up front or it reads as a
  bug.
* **Listening tube.** Sneak on a mound, or use an item, and directional sounds
  and particles show where the links run. The survival version of the debug
  overlay, and an answer to "how does anyone see an underground mechanic".
* **Prospector.** The mole samples blocks near its waypoints while travelling -
  it iterates over them anyway - and marks the exit mound when ore lay along the
  route. Folded into the exchange chest above.
* **Soil aeration.** Fields within reach of a live network grow faster. Real
  moles loosen soil and eat pests. The tension is better than a village conflict:
  the mound blocks a bed while the network below fertilises the field, so
  tolerating or evicting becomes a real decision - and it needs no villager AI.
* **Sculk as a boundary.** Moles are nearly blind and live by vibration, so
  routes avoid sculk and every network ends at the deep dark. One block check in
  route validation, and a piece of narrative for the dimension.

## The player's own digging

* **Digging claws.** A tool that tears through `mole_diggable` and is useless on
  stone. Its own niche beside the pickaxe, and the simplest thing here to build.
* **Dig command.** A tamed mole digs a run where it is pointed, paid for with a
  worm. Keeps the mole the verb of the mod, but a block-breaking mob is a grief
  vector and a slow quarry bot unless it is fenced in hard: diggable blocks only,
  within network reach only.
* **Main runs.** Real colonies keep deep runs used for generations, wide enough
  that other animals use them too. A heavily used route widened by a matriarch is
  walkable tunnels without inventing any magic, and it gives the matriarch a
  function instead of just a texture.

## Resources and structures

* Soil finds - clay nodules, fossils, amber - dug up by moles, in soil layers
  only. No new ore in stone.
* Mole earth: a mound taken with silk touch becomes fertiliser, which ties the
  mod to farming.
* A digging harness from pelt: faster underground, no fall damage from a run,
  silent. A set bonus rather than flat armour, and only if pelt can be had
  without killing.
* A burrow structure with root pillars, a worm larder and a matriarch. Probably
  below rather than in the overworld, see above.
* Relocation as the real colony mechanic: release a caught mole somewhere and it
  digs its first mound there. The player then builds networks deliberately
  instead of finding them.

## Kept out on purpose

| Idea | Why not |
|---|---|
| Free ore scenting | X-ray in a friendly coat. Devalues mining and any other mod's ore balance. Only survives tied to the network, as the prospector above |
| Village conflict with farmers | Touching the villager brain invites a mixin and a maintenance bill every Minecraft version, for very little play. Soil aeration replaces it |
| Mole-form potion | Camera inside blocks, suffocation bypass, a physics-free player on a server. A pile of special cases. Acceptable as an endgame reward, never as an early shortcut |
| Routes carved in the overworld | Real tunnels through somebody's cellar. The burrow below does the same thing in a world that belongs to nobody |
| Abandoned runs from a carver | Carvers run before features, so empty carved runs are just ugly caves. Only worth it once there is 0.5 content to put in them |
| Pelt armour from kills | Contradicts keeping the mole alive. If pelt at all, then from moulting or breeding |
| A mirror of the surface | Two worlds to keep consistent forever, and it breaks the moment somebody removes a mound. The unreachable-surface version above is the affordable one |

Cheap and good, worth taking whenever there is room: a mole in a sack on the
axolotl-bucket pattern, a Jade tooltip for mole and mound state, advancements,
underground rumbling with dust when a mole tunnels nearby, biome variants, and a
star-nosed mole with a sense of its own.

## Lore and the way in

The player really is shrunk - a quarter of their size, which is why a mole run
reads as a gallery. The mod stays sober about how moles and worms live; the one
fantastic step is what those animals eventually hand over.

**The gift itself is open.** What is settled is only the shape of it: the animals
hand it over, worms or moles, and not before a midgame spent with them. What it
is made of, how it is earned, whether it is spent per trip or kept and recharged,
whether a mole has to be present at the mound - none of that is decided. An
earlier attempt to pin it down leaned on trade rules that have since changed and
was dropped with them.

Rejected on the way here, so that they do not come back unexamined: the burrow
being ancient (explains size, but not why the galleries follow a route a mole ran
last week - and the correspondence is the point); the burrow as something the
colony hears rather than a place (explains every limit, and reads as
metaphysics); shrinking by water loss after the fashion of a worm in drought
(good consequences, wrong register).

Open: whether the gift is spent per trip or kept and recharged, whether a mole
has to be present at the mound, and what the second gift looks like.

## The kingdom below

An endgame dimension past the burrow, deliberately left open for now. One thing
about it is settled, and it is the thing that matters for everything before it:
**whatever is needed to reach it exists only in the burrow.** The midgame
dimension is not a detour on the way to the endgame, it is the sole source of it.

## The one thing everything waits on

Persisted routes. The burrow below needs them, main runs need them, and a mole
coming back from a chunk reload in the middle of a trip would stop being a
recovery case. It is the smallest foundation with the most hanging off it, which
makes it the obvious core of 0.3.

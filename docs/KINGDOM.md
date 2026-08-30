# The kingdom below - the 1.0 concept

Status: proposed, nothing built. This is the answer to the two lines the roadmap
left open - *how the kingdom is entered, and what the second gift is* - written
now rather than at 1.0, because both answers reach backwards into 0.4 and 0.5
and change what those stages have to leave lying around.

One concept, argued. The alternatives that were weighed get a paragraph each at
the point where they were dropped, in the habit `IDEAS.md` established: a
rejected idea that comes back in a year should meet its own counter-argument
rather than a blank page.

## The compass

`BURROW_LIFE.md` set the rule that made the burrow work: **at mole scale, the
small life of the soil is the large life of the world**, and nothing gets
invented that a spade would not turn up. The kingdom is where that rule is asked
to carry an endgame, and the only honest way to do that is to go on obeying it
one layer further down. So the question this document answers is not "what would
be impressive here" but "what is actually under a mole run, and what does it look
like at the size a mole would grant it".

The answer turns out to need no invention at all, which is the best thing about
it.

## 1. What the kingdom is

### The image

A corridor's last stretch stops being soil. The walls go pale and fibrous and a
spade sinks into them like bread, and then the far side opens and there is no
ceiling any more - there is a **body**. Cords the thickness of a corridor run
away overhead in every direction and do not end anywhere a torch can reach.
Between them hangs everything the world above has finished with: a season of
leaves stacked in strata like sediment, a trunk gone soft enough to walk into, a
beetle's wing case lying like a hull. All of it is being taken apart, slowly,
by the thing the cords belong to.

And all of it is lit. The glow a colony gets one hand's width of around its nest
frame is the ordinary condition of everything here, because the mycelium that
lights the burrow's ceiling is a thread of this and always was. The burrow is
dark and the light is carried; the kingdom is bright and there is nowhere dark
to stand.

That inversion is the single image, and everything below is a consequence of it.

### Why it is a kingdom

Because it is one - the taxonomic rank, not a monarchy. Moles are Animalia; the
roots coming down through the roof of a feeding run are Plantae; below and around
both is the kingdom that eats what they leave. The largest living things anybody
has ever measured are soil fungi: one honey-fungus mycelium in Oregon covers
nine square kilometres and is somewhere between two and eight thousand years old,
a single individual, entirely underground, connected to the roots of a whole
forest.

The joke is told exactly once, in the dimension's description, and then never
again. What survives it is a straight face: the kingdom below is a fungus, and
the fungus is real, and every single thing in it can be pointed at in a
mycology text.

### Why moles have it

They do not, and that is the point.

The burrow below is a mirror. It is exactly as large as a colony's history,
carved out of the runs those moles really dug, dark, local, and *yours*. It
cannot surprise anybody, because nothing is in it that the player's own animals
did not put there. That is what makes it a good midgame and what makes it a
hopeless endgame: a mirror has no elsewhere in it.

The kingdom is the original. It is older than the colony, larger than the world's
worth of colonies put together, the same for every player and every save, and it
has been carrying on underneath every meadow in the world the whole time. A mole
colony is a capillary off it. The moles live on its edge the way a village lives
on a river - they did not make it, they do not own it, and they have been
harvesting from it since the first mound.

| | The burrow | The kingdom |
|---|---|---|
| Shape comes from | the colony's stored links | the seed |
| How many | one per colony, disconnected | one per world, continuous |
| Light | carried and hung; darkness is the wild part | everywhere; cover is the rare thing |
| Age | last week | older than the save |
| Ground | may be dug; the fill may not be broken | not soil at all |
| Travel | four blocks below for one above - slower than walking | see §3 - faster |

## 2. The way in

### The fact this is built on

Real mycologists find real mole nests by looking for a mushroom.

*Hebeloma radicosum* - the rooting poisonpie - fruits on the abandoned nests and
latrines of moles and shrews and on almost nothing else. It is one of the
ammonia fungi: the nest is a nitrogen hotspot, the fungus specialises in exactly
that, and the fruiting body sends a long rooting pseudorhiza straight down
through the soil to the chamber that fed it. Sagara's group in Japan used the
mushroom as a survey instrument - find the stalk, follow the root, dig, and there
is the nest.

So the way into the kingdom is a mushroom that grows on a mole's nest and whose
root goes down. Nothing about that had to be invented, which means the mod's
compass holds all the way to the last door in it.

### The mechanic

**The nest's warm hollow is fed, the mycelium spreads, and it fruits.**

1. **Feed the hollow.** The warm hollow at the colony core takes offerings, and
   what it takes is the burrow's own dead matter: root nodules out of the walls,
   worm larders, chitin flakes, spoiled worms. Everything on that list exists
   only below - which is the roadmap's one settled constraint, satisfied without
   a new material being invented for the purpose. In plain terms the player is
   composting, at the one spot in the colony where composting is what actually
   happens.

2. **It answers.** Glow mycelium spreads out of the hollow along the nest's
   walls and ceiling, block by block, further after every offering. This is the
   part that matters most and it is not decoration: it is the gate's progress
   bar, and it is a thing in the world rather than a number nobody can see. The
   mod's oldest working rule is *build the instrument, do not guess the number*;
   here the instrument and the fiction are the same object.

3. **It fruits.** When the mat has eaten enough, one fruiting body grows in the
   hollow - at burrow scale a stool the size of the room, cap against the
   ceiling, stipe going down through the floor and out of sight. The stipe is the
   pseudorhiza and the pseudorhiza is the way down.

The way back up is the same stipe, climbed, which is `RootLadder` with a
different texture and none of its behaviour changed: unbreakable, right-click at
the top, and already the burrow's proven answer to *how does anybody get out of
here*.

### What this costs the player, and why that is the right price

A colony that opens is a colony that has been kept. To fruit a nest a player must
have found the nest at all - which means having walked a colony's deep runs, and
that is already the hardest thing to do in 0.5 - then dug its walls, harvested
its larders without collapsing them, lit them so the grubs leave them alone, and
carried the results back to the middle. Every one of those is an existing
mechanic that currently pays out in items and now pays out in a destination.

The nest stops being a nice room with a trove in it and becomes the colony's
reason to exist. `BURROW_LIFE.md` wanted the warm hollow to be "reason to find
the Hauptbereich, not just look at it"; this is that, at the scale the ending
needs.

### The fruiting body is breakable, and the mycelium is not

A mushroom is fruit; the organism is the mat. So a fruiting body can be broken -
by a careless swing, a grub, an incursion - and the mat regrows it, faster than
the first time, because the mat is already fed. That removes the trap where one
mistake destroys an endgame, and it is also simply what fungi do. The door
closes; the door does not die.

This mirrors the burrow's own rule exactly - "an exit works only where the mound
above it still stands", checked at the moment of use rather than stored - so
there is one idea about doors in this mod rather than two.

### Why not the others

**Standing with the colony**, earned through the exchange economy. This was the
most obvious candidate and it fails on an argument the mod has already made once:
`IDEAS.md` threw out route-dependent trade because "an exchange nobody can
predict is not an exchange". A hidden accumulating trust counter is the same
mistake at the largest possible stake, and it lands on the mod's worst failure
mode - an animal that will not act, for a reason nobody can see. It also fails
the roadmap's constraint outright: standing is earned above ground, at a station
on a mound, by a player who need never have gone down.

**A sealed deep run below the deepest `RunLevel`.** Tempting, because
`RunLevel.CHAMBER` exists and nothing digs it yet. It is wrong for two reasons,
and the first one has already been paid for once: the burrow is its own
256-block box, not ground underneath the real surface, and `BoltHoles` carries
the correction in its javadoc because the first version of that feature promised
a shaft to the overworld it could not have. A diggable seam in the floor also
reopens the physical limit the unbreakable fill exists to close - leaving the
corridors is currently impossible rather than forbidden, which is why there is
nothing to police. And digging needs nothing that exists only in the burrow, so
it fails the roadmap's single settled constraint on its own terms.

**The giant mole leads you there.** The best image of the four and the most
expensive. `TravellingMole`'s whole cheapness is that it carries no state:
nothing is simulated, nothing decides where to go, and a blow makes it dig away
rather than diverge, because an apparition with no obligations cannot get out of
sync with the trip it depicts. A guide needs a destination, a memory of who is
following, and a response to being lost - all state, all of it exactly what the
entity's own javadoc says it does not have. It also cannot go anywhere the
corridor does not: it walks a link's polyline and nothing else. Kept in a smaller
job it is very good, and §6 gives it one - it is what reacts at the threshold.

**A crafted portal frame.** Dungeon grammar in the one mod that refuses dungeon
grammar, and it turns the key into an inventory problem rather than a place. The
mod has never once asked a player to assemble a shape.

**Killing something.** There is nothing down there worth killing for a door, and
building one would mean inventing the mod's first boss to be its gatekeeper. The
weasel incursion is already the burrow's fight and it is a survival event, not a
gate.

## 3. The second gift

The first gift is the shrinking - whatever it finally turns out to be made of,
its shape is settled in `IDEAS.md`: the animals hand it over, and not before a
midgame spent with them. It buys the burrow.

**The second gift is passage: the kingdom is one place beneath every colony, so
the ground stops being distance.**

### What that means in play

Two colonies, two fed nests, two fruiting bodies. Go down at one, walk, come up
at the other - and the second one may be two thousand blocks away across an ocean
the player has never crossed on foot.

There is no menu, no list of destinations and no teleport. The doors are places.
They sit in the kingdom at the colony's own coordinates divided down, they are
visible landmarks in a lit world, and the player walks between them past whatever
the generator put in the way. The gift is geography, not a user interface, which
is also why the kingdom has to be worth walking through and why §6 spends most of
its effort on that rather than on plumbing.

### The scale stack

This is the piece that reverses an earlier decision and it should be reversed
deliberately.

| Layer | Position scale | Effect on travel |
|---|---|---|
| Overworld | 1:1 | walking |
| Burrow | x4 | four blocks below for one above - **slower** than walking, on purpose |
| Kingdom | ÷8 | one block below for eight above - **faster**, and the reward for the whole game |

`IDEAS.md` is proud, correctly, that the burrow made the fast-travel balancing
problem disappear by being slow. The kingdom brings it back, on purpose, at the
end, and the price is set by what a door costs rather than by a cooldown: a
player cannot put a door where they like, only where moles founded a colony and
the player then kept it, found its nest and fed it. Wanting a door somewhere new
means getting a colony to exist there - which is what `MoleEmigrateGoal` and
trap-and-release were built for and what neither of them has ever had a reason to
be used for.

The whole stack then reads as one idea instead of two numbers: **the deeper you
go, the more ground a step covers.** Which is also the fiction - the deeper layer
is the older, wider, more connected one.

Eight is a starting point to walk, not a figure to calculate, exactly as
`CORRIDOR_WIDTH` was. Two colonies at `COLONY_MIN_SEPARATION` land eighteen
blocks apart down there, which is right - neighbours should be neighbours - and a
two-thousand-block trek becomes two hundred and fifty blocks, which is still a
real walk rather than a hallway. Sixteen would make it a hallway.

Only x and z map. The kingdom's height is its own, because there is nothing to
mirror: this dimension corresponds to nothing.

### What it also yields, and why that stays small

The kingdom is where soil is made, so what comes home from it is soil - the
finished article, black and alive. It belongs in exactly two places that already
exist: the worm box's best feed, and a fifth tier on the exchange station above
the glow worm. That closes the mod's loop rather than opening a new one - the
endgame's material makes the *first* mechanic better instead of replacing it, and
the worm economy stays the only economy in the mod.

The chitin gear line and the trove block, both parked in `BURROW_LIFE.md` with
reasons, get their top-end material here if they are ever built. Listed so the
parking note points somewhere; not part of this concept.

### Why not the other second gifts

**The mole-form potion.** `IDEAS.md` pre-approves it - "acceptable as an endgame
reward, never as an early shortcut" - so it has standing. It still loses. Its own
rejection paragraph lists the bill: camera inside blocks, suffocation bypass, a
physics-free player on a server, a pile of special cases. And it is a power
fantasy where this mod has spent four stages building a place fantasy. If it ever
lands it should land as a curiosity found in the kingdom, not as the reason to go.

**Something that changes the overworld** - fertility spreading from a network, a
fungus that walks up into a forest. Attractive and wrong for one specific reason:
every version of it carves, alters or occupies ground that belongs to somebody,
and "nothing carves the overworld" is the oldest rule in `IDEAS.md`. Soil
aeration already occupies that space cheaply and does not need an endgame.

**Going smaller again.** A third shrink is a rhyme rather than a gift, and it is
free anyway: the kingdom corresponds to nothing, so everything in it is authored
at whatever size reads best. A woodlouse the size of a cart needs no multiplier
in a carver, only a model. This is a property of the place, described in §1, and
it should not be dressed up as a reward.

## 4. What carries it, honestly

### The good news first

The burrow needed the plan layer, the ledger, the reconciler and the whole
runtime-carving apparatus for one reason: its shape is the history of a colony,
which is runtime data, and `BURROW_WORLDGEN.md` spends two pages establishing
that no `ChunkGenerator` can be fed that. **None of that applies here.** The
kingdom's shape is a function of the seed, so for the first time in this project
the vanilla pipeline is the right tool used the way it was designed - noise
settings, biomes, features, placement, all data, all on worker threads, all of it
the shape that document surveyed Biomes O' Plenty for and then could not use.

The kingdom is therefore *cheaper per square metre than the burrow was*, despite
being the endgame. That is worth saying plainly, because the instinct after
0.4/0.5 will be to assume a second dimension costs what the first one did.

The one piece of runtime placement that survives is the door: a colony's opening
sits at coordinates only the `ColonyStore` knows, so arriving there force-loads a
ring and guarantees a small chamber before the teleport - which is precisely
`BurrowTransit.enter` after it was slimmed, reused rather than rewritten.

### What is already paid for

| Machinery | What it does here |
|---|---|
| Dimension as datapack JSON (`ModDimensions`) | same pattern, new file; the settings and their reasons are already written down |
| The custom-biome work | 1.21.11 moved biome effects into environment attributes and every tutorial is wrong; that was found once and is now free. The kingdom wants three to five biomes and each is cheap |
| `TunnelWalk` | guided traversal for an animal too wide to pathfind in a tube. The kingdom's cords are tubes; anything large that travels one uses it unchanged |
| The creature stack | `BurrowCritter`, `BurrowPredator`, `NipAndDartGoal`, `ProwlAndLungeGoal`, `WithdrawGoal`, and `FleeLightGoal` - which inverts into seek-cover by flipping one comparison |
| The scaled-entity lesson | `refreshDimensions()` in the constructor, or a 4x model walks around half inside a wall. Found the hard way, applies to every animal down here |
| The sound pipeline | `ModSounds`, `ModSoundProvider`, the ElevenLabs generator, and biome ambient/mood/music slots already wired |
| Position-hashed determinism | `TunnelDecorator` and `TunnelGrain`'s discipline - every roll from a hash of the position - is what makes decoration order-independent, and it is the right habit for features too |
| `RootLadder` | the stipe, unchanged |
| `BurrowRescue` | stranded-player handling, which a second dimension needs on day one |
| The advancement discipline | asking for a *material* where a location cannot be named, since a generated room has no coordinates anybody can write down |
| The dev instruments | the knob panel, `moleverse.dev*` gating, the soak harness, the `/moleverse` command tree |

One inherited scar should **not** be carried over: `BurrowLife` exists because
vanilla spawning cannot populate a solid box - 97.7% of spawn rolls land inside
deep earth and return having touched nothing. The kingdom is an ordinary noise
world with real open volume and a real surface heightmap, so `NaturalSpawner`
works there and biome spawner lists are enough. That measurement was about the
burrow's geometry, not about the mod.

### What genuinely has to be built

* **The terrain.** Noise settings or density functions for a world of cavities
  between cords. This is the real unknown and the thing that decides whether the
  place is any good; vanilla's `minecraft:caves` preset is the nearest reference,
  and Twilight Forest's retirement of its custom generator in favour of density
  functions - already surveyed in `BURROW_WORLDGEN.md` - is the precedent for
  which direction to go.
* **The blocks.** The mat, the cords as a pillar family, litter strata, fruiting
  bodies, the stipe. Perhaps a dozen, with textures.
* **The gate.** Feeding interaction on the warm hollow, the spread as a growth
  state, the fruiting body, the descent and the return.
* **The fauna.** Three or four animals at the new size.
* **The doors.** Which colonies have fruited - a flag on `Colony` or a small
  saved list - plus the arrival chamber and two-way transit.

### The effort shape

Five waves, in the order they earn their place, following `BURROW_LIFE.md`'s
habit of making each one independently shippable.

1. **The dimension.** Dimension and dimension type JSON, noise settings, one
   biome, the ground blocks. Enterable by command, walkable, and empty. The
   entire point of this wave is to answer the only question that matters - *is
   the shape worth being in* - before a single feature is authored on top of it.
   Cheap to throw away, which is why it goes first.
2. **The gate.** Feeding, spread, fruiting, descent, stipe. Ends with a player
   reaching the kingdom by playing rather than by typing. After this wave the
   thing exists as a game.
3. **Dressing.** Cords, litter strata, the inverted light economy, two or three
   biomes, sound. This is where it stops being terrain and starts being a place.
4. **Fauna.** The cast, on vanilla spawning, with the burrow's creature bases.
5. **Passage.** The second door, arrival rules, the advancement capstone, and the
   balance pass on the ÷8 that can only be done by walking it.

Recommendation, and it is the same one that worked for `BURROW_LIFE.md`: approve
1 and 2 together, because together they are the smallest thing that can be judged
honestly, and judge 3 to 5 one at a time in play.

## 5. The line

The taste is settled everywhere else in this mod: no chests, no dungeon grammar,
nothing a spade would not turn up. The kingdom is the one place allowed to break
scale into wonder, so the line has to be drawn precisely rather than waved at.

**The wonder is in the scale and in the light. It is never in the authorship.**

Everything down there must still be something findable with a hand lens in a
spadeful of woodland soil - only the size of a house. That is the whole rule, and
it is testable: for any block, creature or room, somebody must be able to name
the organism or the process. If the answer is "it looked cool", it does not go in.

What may not be there, with reasons rather than a list:

* **No chests, still.** `BURROW_LIFE.md`'s ruling holds and holds harder here:
  everything worth taking is in the world. A chest in the kingdom would say the
  place was furnished by someone, and the whole fiction is that it was grown.
* **No mole king, no court, no throne.** Moles are solitary and territorial. A
  mole monarchy would be the first outright lie this mod tells, and it would tell
  it in the last room. The matriarch idea already landed correctly, as the nest.
* **No ruins, no writing, no architecture.** "Ancient" was rejected once already,
  for the burrow, and ruins drag chests and lore tablets in behind them. Nothing
  down there was ever built.
* **No boss.** The kingdom's pressure comes from its geography - see below - and
  a health bar at the end of this particular mod would be a different game's
  ending stapled on.
* **No second economy.** Worms are the currency and stay the currency. The moment
  the kingdom mints something the exchange station does not understand, the mod
  has two halves.
* **No free building.** Floor fittings only, exactly as proposed for the burrow.
  Free building turns wonder into a base within an hour.
* **It may not be a better overworld.** No ore that beats mining, no crop that
  beats farming. What comes home is soil, and soil feeds the mechanic the mod
  started with.

What it *may* be, that the burrow may not:

* **Bright, and vast.** Long sightlines, a ceiling nothing reaches, and no torch
  needed. The burrow's grammar is darkness with hung light; this is its opposite
  and the contrast is most of the effect.
* **Not a mirror.** Authored, seeded, identical for everyone, and older than the
  save.
* **Fast**, in overworld terms.
* **Beautiful.** The mod has been sober for four stages. This is where it is
  allowed to be beautiful, and the sobriety is what will make it land.

### Where the danger comes from, since it is no longer the dark

The burrow taught one survival lesson - light is safety, unlit stretches are the
wild part of the colony - and the kingdom takes that lesson away by giving light
everywhere. Something has to replace it, and it should come out of the geography
rather than out of larger numbers.

It does: **there is no earth here.** No diggable walls, no bolt-holes, nowhere to
put your back. In the burrow the practised response to something coming down the
corridor is to duck into a niche, and that response does not exist in a world
made of fungus and litter. Cover is a thing you find rather than a thing you dig,
and the animals that hunt here - a centipede, a pseudoscorpion, both entirely
real and both nightmarish at this size - are faster than anything in the burrow
and do not lose interest.

That is the whole escalation, and it needs no new systems: the same predator
goals, a different reason they work.

## 6. Loose ends worth catching

**The giant mole at the threshold.** `TravellingMole` is rejected as a guide in
§2 and it is very good at a smaller job: when a colony's nest is close to
fruiting, the ambient lane's passes get more frequent near the nest, and one of
them stops at the wall the spade would go into. Pure flavour, no state, nothing
about the entity changes.

**The echo shard keeps its job.** It sits in the exchange station's table at one
trade in a hundred as "the only one that says the tunnels go somewhere", and the
temptation is to cash that cheque here by making the kingdom sculk. It should not
be cashed. `IDEAS.md` already has the better use - moles live by vibration and
their routes shun sculk, so every network ends at the deep dark - and making the
kingdom sculk would hand this mod's ending to vanilla's ending, in a biome that
already has an owner. Sculk stays the *edge*: where the mycelium stops, the
sculk begins, and the kingdom is bounded by the one thing that will not be eaten.

**The advancement tree gets its cap.** Four nodes, and each is a real test in the
sense the tree's javadoc means - fed the hollow, it fruited, went down, and came
up in a different meadow. The last one is the mod's ending and the only
advancement in it that cannot be reached by one colony.

**The water table.** Real - mole runs genuinely stop where the ground goes wet -
and it was considered as the kingdom's whole identity before the fungus won. A
swimming endgame throws away `TunnelWalk`, the creature stack and the light
economy in one move. It comes back as a *biome* of the kingdom, which is where it
belongs: a black lake under the cords, with a ford across it.

## 7. Open decisions

These need the user, not an agent.

1. **Is passage the second gift?** This is the one that matters. Accepting it
   reverses `IDEAS.md`'s deliberate decision to have no fast travel, and it
   decides what 1.0 is *for*: a network of colonies rather than a place to visit.
   Everything else in this document survives a no - the kingdom, the mushroom,
   the fungus, the waves - but the ÷8, the second door and wave 5 all fall with
   it, and the reward would have to be found somewhere else.

2. **What the first gift is made of.** Still open from `IDEAS.md`, and this
   concept does not settle it. It only assumes the first gift is place-bound and
   per colony, the way the shrink post already is in practice. If it turns out to
   be a carried item instead, the fruiting body still works unchanged - but the
   two gifts should be decided as a pair, since the roadmap pairs them.

3. **The scale.** ÷8 is a starting point to walk, in the spirit of every other
   number in this project. It wants the same treatment `CORRIDOR_WIDTH` got: a
   debug command that drops two doors at a given factor in a test world, and a
   walk between them.

4. **Whether the burrow's warm hollow can be spent this way.** Feeding the hollow
   consumes the colony's one dependable glow-worm source while it grows. That is
   probably a good tension and it is definitely a decision - the alternative is a
   separate heap block beside the hollow, which is safer and less interesting.

5. **How many doors a world should end up with.** No cap is proposed. A player
   who founds twenty colonies gets twenty doors and a private underground metro,
   which may be exactly the endgame or may be one colony too many.

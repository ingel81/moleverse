# Life below - the plan for anatomy, creatures and atmosphere

Status: built 2026-08-29, one long day from approval to playtest. Anatomy,
biome, loot, five creatures, predators (weasel now an incursion design),
ambience, progression, economy tiers, generated sound, the giant mole with its
ambient lane, and the root ladder all shipped; population moved off the vanilla
spawner onto BurrowLife's waypoint trickle after the 0.7% geometry finding.
Still open from this plan: the weasel incursion event itself, creature-wave
sound accents, the chitin gear line and the trove block (parked). Written after the first real playtest of the
generated burrow. The worldgen mechanics underneath are settled
(`BURROW_WORLDGEN.md`, `BURROW.md`); this file plans what the playtest asked
for: "atmosphärischer, mehr Leben, die typischen Dinge, die Maulwürfe so haben."

Already in flight in the same session, not part of this plan: the loose-soil
lining (soft diggable walls, deep earth only 2-3 blocks out), browner deep
earth, thinner fog, denser edge flora, rounder sections, smaller junctions,
and the way-home diagnostic.

## The compass for every decision here

The burrow's one idea: **at mole scale, the small life of the soil is the large
life of the world.** Every creature, room and sound must be something actually
found in or around a real mole's burrow, given the size a mole would grant it.
Real burrows have a rich, well-documented anatomy - that is the treasure this
plan mines. Nothing gets invented that a spade would not turn up.

## 1. Colony anatomy - the rooms a real burrow has

Real moles build, per colony: one **nest chamber** (dry, lined with grass and
leaves, used for sleeping and raising young), **food larders** (famously: live
worms, bitten so they cannot leave), a network of deep permanent runs and
shallow feeding runs (already modelled as `RunLevel`), vertical shafts
(modelled), molehills (modelled), and in wet ground a single oversized
**fortress mound** over the nest. That maps onto features like this:

### 1a. The nest chamber - the Hauptbereich

One per colony, at the colony **core** (`Colony.core()` exists and nothing in
the burrow marks it today - the anatomy's centre is already in the data).

* A `NestFeature` in the plan layer, keyed `nest:<colonyId>`, placed at the
  core's burrow position at the depth of the colony's deepest run.
* Deliberately the **largest room in the colony** - the playtest found big
  junctions confusing precisely because nothing distinguishes an important
  room from an accidental one. Junctions are being shrunk; the nest is where
  "big" moves to, and being unique it reads as a destination. Radius ~9-10
  against the chamber's 6.
* Dressed as a nest: floor of moss carpet / dried-grass reading (hay-adjacent
  vanilla blocks where they exist, moss otherwise), wall pockets, a raised
  sleeping hollow in the middle, warm dense glow lighting - the one cosy room
  in the dimension.
* Runs do not end at the core (links join mounds), so the nest connects by two
  short dug spurs to the two nearest corridors - computed in the plan layer,
  carved like any feature.

### 1b. Worm larders - the Futterkammern

`WormLarder` blocks and `cutLarder` already exist in `ChamberFurnisher` -
larders exist as furniture. Promote them to rooms:

* A `LarderFeature`: a small side alcove (radius 3-4) budding off a **deep**
  run every ~40-60 blocks, keyed on the run and an index. Walls studded with
  worm-larder blocks, one or two live worms inside, floor of mud.
* This is also the gameplay loot room: worm larders are the thing worth
  finding, harvesting (worms as food/bait/trade) and defending.

### 1c. Bolt-holes

Real burrows have short escape shafts. A `BoltHoleFeature`: a rare, short
dead-end stub climbing steeply off a corridor, two blocks wide, twelve blocks
of rise, ending in a lit plug of soil. The original plan text promised it
could reach the overworld - it cannot, and cannot in principle: the burrow is
its own 256-block box, not ground under the real surface (the correction is
argued in `BoltHoles`' javadoc). What it delivers is the escape-shaft fiction,
a vertical pocket worth digging, and a lit refuge off the corridor.

### 1d. The fortress mound

The colony core's overworld mound becomes visibly larger (a 2-3 block heap
instead of one). Marks the nest from above, gives the overworld a landmark per
colony. Pure overworld cosmetics, no burrow logic.

Skipped consciously: latrine chambers (real, but one mud alcove reads as a
bug, not a feature) and nurseries as separate rooms (the nest is the nursery -
that is also biologically right).

## 2. Creatures - what actually lives in soil, at mole scale

Vanilla spawning is the right machinery, which needs one prerequisite:

### 2a. A biome of our own

The dimension currently borrows `minecraft:deep_dark`. A custom
`moleverse:burrow` biome (JSON datapack entry, no code) gives us: our own
mob-spawn lists, our own ambience/mood sound hooks, fog/water tints, and a
place for every later tuning knob. This is the standard, data-driven way and
the single most load-bearing item in this plan - everything in 2b hangs off
it.

### 2b. The cast, in build order

| Creature | Role | Mechanics sketch |
|---|---|---|
| **Earthworm** (small) | ambient prey, the burrow's chicken | passive, wriggles, diggable out of walls, food item; spawn: burrow biome floor, common near ROOTY grain |
| **Great worm** | exists; stays the rare corridor event | keep manual stocking + rare natural spawn |
| **Soil beetle** (Käfer) | neutral scuttler, the burrow's silverfish-but-friendly | wall/ceiling walker, flees light, drops chitin flake; common |
| **Grub** | larder/nest vermin | slow, eats worm-larder blocks if unlit, squishable; the reason to light larders |
| **Shrew** (Spitzmaus) | the small hostile | fast, weak, hunts earthworms AND nips players, pack of 2-3; real moles' rival |
| **Weasel** (Wiesel) | the apex EVENT, not a spawn | playtest verdict 2026-08-29: a real weasel outsizes a mole and hunts it in its own runs, so at mole scale it must be corridor-filling - bigger than the giant mole. Reworked as an incursion: no natural spawn (removed from the biome list), instead a very rare guided traversal like the giant mole's (scale ~7, no pathfinding), entering through a chamber the way real weasels enter through molehills, announced by fleeing critters, survived in a bolt-hole niche or fought. The 3.0-scale walking entity stays registered for /summon until the incursion package builds |

Spawn rules per biome entry, all light-gated so lit stretches (glow pools,
player torches) are safe - light becomes the safety currency, which makes the
existing glow staging carry gameplay, not just looks.

Effort note: each entity is model + AI + spawn tuning (the mole pipeline
exists: Blockbench MCP, animation export, the docs). Earthworm and beetle are
small (crawler AI, no attack); shrew and weasel reuse vanilla melee-hunter AI
shapes. Realistic order: earthworm + beetle first (ambience), shrew next
(pressure), weasel last (event).

### 2c. Mole presence without moles

The design rule, in its precise form: no *twin* below - the same individual
simulated in two worlds is the synchronisation problem the whole design
avoids. Presence without a twin is fine: rare muffled scratching/rumble from
behind walls (positional sound, no entity), falling soil motes when it plays.
Built.

### 2d. The giant mole - a trip made visible

Approved 2026-08-29 in conversation. Not a twin: an *apparition* of a trip
that is already an abstraction. When an overworld mole travels a run (the
goal knows route and progress) and a player is below in range, a traversal
entity spawns - the mole model at SCALE, walking the link's own polyline at
matching speed. Three facts make it cheap and safe:

* **The path is data.** A link is a dead-straight horizontal line whose
  centre column the carver guarantees open - no pathfinding, no corners to
  cut, no wall to glitch through. Enter and exit are dug: it burrows out of
  and into the wall with particles and closing earth, which is fiction, not
  clipping. Attacked, it flees the same way; nothing diverges because the
  apparition carries no state.
* **Loose sync suffices.** Nobody can stand in both worlds at once, so
  plausible timing at the seams (trip start/end, mound activity) is all the
  synchrony that is observable.
* **Standing in the way costs.** A forward sweep box deals 2-3 damage plus a
  sideways knockback - rolled over, not crushed - and the scratching ambience
  announces it before it rounds into view, so the bolt-hole niches become the
  practised response. Warning first, damage second, always.

## 3. Atmosphere beyond blocks

* **Biome mood + additions sound** (via the custom biome): soil creaks, drips,
  faint underground rumble; occasional worm-slither accent near larders.
* **Particles**: falling soil motes from ceilings (tied to the scratching),
  spore-like motes near glow mycelium - both client, both cheap.
* **Music**: silence with rare sparse cues fits better than a loop; the biome
  can carry its own music entry later.
* **The dark is a place**: with lighting staged and creatures light-gated, the
  unlit stretches become the wild part of the colony. No change needed beyond
  what is planned - just the reason the staging matters.

## 4. Loot and what a descent is FOR

The overworld already has a worm economy - `earthworm`, `fat_worm`,
`glow_worm`, the exchange station's trades - and the burrow is where worms
actually live. So the loot answer is not new currencies; it is: **below is
where the worm economy is earned at its source, at some risk.**

* **Larder rooms are worm banks.** Harvesting a worm-larder block yields
  earthworms, sometimes a fat worm; glow worms only from larders in lit rooms.
  Breaking larders greedily collapses the alcove's stock (the blocks do not
  come back until moles re-dig - the ledger re-carves shape, not food), so
  careful harvesting beats strip-mining. Grubs (creature wave B) eat unlit
  larders: light is upkeep.
* **The nest holds the one treasure.** A single "warm hollow" per colony
  nest: a moss lid over a 2x2x2 trove of root nodules and worm larders. What
  makes it the treasure is not the pile - it is the only spot in a colony that
  reliably clears light 8, so it is the colony's one dependable glow-worm
  source, while unlit alcoves give none (block light falls one per block;
  glow mycelium at level 9 clears 8 only directly beside itself - the L-8
  rule, found the hard way). A richer trove needs a dedicated block; later
  wave, if at all. Reason to find the Hauptbereich, not just look at it.
* **Wall finds while digging.** Since walls are now diggable loose soil,
  digging is gameplay: position-hashed pockets inside the lining - root
  nodules (new simple item, brewing/food base), amethyst buds (already
  placed), the occasional worm. The shell is 2-3 deep, so pocket density is
  self-limiting; deep earth ends the dig.
* **Chitin (creature waves)** from beetles/shrews: the burrow's crafting
  material for later gear (dark, matte, mole-scale armour flavour). Listed so
  drops land somewhere; gear itself is a later plan.
* **Explicitly no chests.** Everything worth taking is in the world - larder
  blocks, the nest hollow, the walls. Chests read as dungeon, not as burrow.

## 5. Open technical points, tracked

* **Way-home refusal** ("mound gone"): instrumented; waiting for one
  reproduced log line. Fix follows the evidence, not a third guess.
* **Commits**: the whole session is uncommitted; module-wise commits once the
  playtest confirms the wave-4 look.
* **Cleanup**: worm_larder texture docstring claims "darker than deep_earth"
  (stale); noise-helper copies in three files could hoist into TunnelNoise.
* **Balance pass** on junction dressing rings and chamber floor mix - judged
  in play, tuned by constants.

## 6. What this plan deliberately defers

* The transit "mound gone" bug: instrumented this session; fixing waits for
  one reproduced log line rather than a third guess.
* Advancement/progression wiring for the new rooms and creatures (belongs in
  the advancement tree file once creatures exist).
* Trading/economy around worms and chitin - after the creatures walk.
* Sound *recording* pipeline (ffmpeg chain exists per project memory) - listed
  so it is not forgotten, sourced when the first creature needs a voice.

## 7. Build waves, if approved

1. **Anatomy wave** (pure worldgen, no new assets): NestFeature + spurs,
   LarderFeature, BoltHoleFeature, fortress mound, hash bump. One agent on the
   plan layer + one on dressing the new rooms. Testable same-day in game.
2. **Biome wave**: `moleverse:burrow` biome JSON, dimension points at it,
   ambience/mood moved onto it, spawn lists empty but ready. Small.
3. **Creature wave A**: earthworm + soil beetle (models via the Blockbench
   pipeline, crawler AI, spawn entries, drops).
4. **Creature wave B**: grub + shrew; larder-vermin loop; light-gating tuned
   in play.
5. **Creature wave C**: weasel event predator + mole-scratching ambience +
   particles.

Recommendation: approve waves 1-2 together (they are cheap and make the world
feel authored immediately), then judge creature waves one at a time in play -
each is independently shippable.

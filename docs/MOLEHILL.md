# Mole mounds and burrowing

Status: **built.** Decided in a planning round on 2026-08-28, revised the same
day after a design review, and implemented across four phases on the same day.
This document replaces the "Burrowing and molehills" section of
`docs/MOLE_DESIGN.md`, which was the proposal it grew out of.

| Phase | Commit | State |
|---|---|---|
| 1 - the block | `9d061c6` | Built. Verified in a running client: no missing models or textures, twelve blockstate variants |
| 2 - animations | `57f81b1` | Built. All six entity animations load |
| 3 - the mechanic | `b0f9c6a` | Built |
| 4 - natural spawning | `d849d2c` | Built. Biome tag and modifier generated |

Three review rounds ran against the code - one after phases 1, 2 and 4, one
against the mechanic, and one against the fixes those produced. All fifteen
findings are folded in; the ones deliberately left alone are listed under "Known
and left alone".

A round of tuning followed the first play test, and it is where the earthworm
came from: the mole needed a reason to be underground more than on top of it,
and the player needed a reason to open a mound rather than flatten it.

The plan below is kept in the present tense as the description of what the
feature does, not as a to-do list.

## The short version

A mole standing on soft ground digs itself in, leaves a mound behind, travels
underground and surfaces elsewhere leaving a second mound. Mounds it has already
made are reused rather than multiplied: they are taken to be connected into a
tunnel network, so an established mole moves through its own network instead of
carving new holes everywhere. A density cap keeps a meadow from turning into a
field of mounds.

## Decisions

| Question | Decision |
|---|---|
| Registry name | `moleverse:mole_mound`, class `MoleMound`, English "Mole Mound", German "Maulwurfshügel" |
| Shape | Rounded mound on a 1 px grid, generated from a radial height map. Two closed shapes plus an open crater |
| Model authoring | Blockbench over MCP, format `java_block`, sources in `art/mole_mound_{a,b,open}.bbmodel` |
| Texture | `art/mole_mound.png`: crumbly, freshly turned earth, darker and coarser than vanilla dirt. `art/mole_mound_shaft.png` for the floor of the open shaft |
| Collision | None. Walked through, not over |
| Hardness | Instant break |
| Block item | Exists and sits in the Moleverse creative tab, for placing by hand |
| Variants | A boolean `open` property. Closed picks at random between two shapes, open uses the crater; each with four Y rotations |
| Support | Only on `moleverse:mole_mound_placeable`; breaks when that block goes away, like a carpet |
| Material feel | `SoundType.GRAVEL`, dust particles when an entity walks through, `mineable/shovel`, `PushReaction.DESTROY` |
| Lifetime | Stays until broken. No decay for now |
| Player interaction | None beyond breaking it |
| Player-placed mounds | Count fully: they join the network and the density cap. Luring a mole, extending its network and putting it off digging are all things worth trying |
| Farmland | Stays in the tag. A mound in the wheat is exactly what real gardeners curse about, and it blocks one planting spot until knocked away |
| Burrow triggers | A player coming close, being struck, a boredom timer, and `/moleverse mole burrow`. Fright ignores the cooldown - a mole that cannot escape because it dug five seconds ago is the moment the mechanic looks broken |
| Shyness | Under 8 blocks a mole dives for cover. Sneaking closes that to under 3, which makes crouching the only way to watch one rather than watch it leave. Creative and spectator players scare nobody |
| Drop | Loose soil always, and an earthworm from about every fifth mound - the reason to dig a mound open rather than knock it flat |
| Earthworm | What a mole is actually after. Moles follow it, eat it, and breed for it, which is the only route to a juvenile other than a spawn egg |
| Underground travel | The entity stays alive, turns invisible, loses collision and moves under the surface |
| Mounds per trip | Two: entry and exit |
| New-dig distance | 12 to 16 blocks, tied to the minimum exit distance |
| Mound detection | The mound is a **point of interest**. `PoiManager` keeps the index; the blocks in the world remain the data |
| Density cap | At most 4 mounds within 16 blocks, counting everyone's |
| Reuse | Existing mounds serve as both entry and exit |
| At the cap | Walk out of the dense area and dig there |
| Network | Fiction for now: nothing is excavated. The route is kept as a waypoint list so 0.3 can carve the same path for real |
| Network membership | Chained neighbourhood: two mounds are linked at up to 16 blocks apart, and the chain continues from there |
| Network range | Along the chain to any connected mound, however far. Only fresh digs are limited to 8-16 blocks |
| Travel time | Proportional to distance; he really moves down there, at about 3 blocks per second |
| Surface trace | Dust particles and a muffled digging sound follow him along the way |
| Underground rules | Damage-immune while travelling, but never through the saved `Invulnerable` flag; breaking the target mound does not stop him; an interrupted trip always ends on the surface |
| Babies | A juvenile does not burrow on its own. It follows its mother into the network and comes out with her |
| Animations | `mole_dig` (loop), `mole_burrow`, `mole_emerge`, `mole_idle`, all authored in Blockbench |
| Body angle | Not a keyframe channel. A number in `setupAnim`, as with the rearing pose |
| Dig style | Alternating front paws, body shoving forward in small pushes, snout low |
| Dig sound | Played from code once per scoop, so it works while he is invisible |
| Natural spawning | Included, as the last phase: grassland and woodland, listed in a biome tag |

## Constants

All of these live as named constants in one place and are expected to move once
there is something to watch.

```
SEARCH_RADIUS         16 blocks    how far a mole looks for existing mounds
MAX_MOUNDS_IN_RADIUS   4           above this, no new mound is created
NEW_TRAVEL_RANGE      12-16 blocks distance of a freshly dug trip. The lower bound
                                   follows MIN_EXIT_DISTANCE - a shorter roll would be
                                   rejected after the fact and the attempt wasted
MIN_EXIT_DISTANCE     12 blocks    an exit closer than this is not worth the trip,
                                   and must exceed the scare distance or a flight
                                   would surface inside the radius it fled
NETWORK_LINK_MAX      16 blocks    two mounds count as connected up to this gap
NETWORK_SCAN_MAX      64 blocks    hard bound on how far a chain is followed
UNDERGROUND_SPEED      3 blocks/s  travel speed below the surface
NEW_HOLE_COOLDOWN     60 s         earliest a mole breaks ground for a NEW hole.
                                   Travelling the existing network is not rationed
                                   at all - that is what a mole does all day
SURFACE_DWELL          2-8 s       how long it stays up, drawn fresh each time so
                                   it does not surface on a metronome
REFUSAL_RETRY_DELAY    3 s         after a refusal, before planning again
EXPLORE_CHANCE        60%          preference for new ground when digging is allowed
FLEE_EXPLORE_CHANCE   75%          the same while fleeing - a known hole beside the
                                   pursuer is no escape
PLAYER_SCARE_DISTANCE  8 blocks    a player nearer than this sends it under
SNEAK_SCARE_FACTOR     1/3         how much of that a crouching player gets
FOOD_NOTICE_DISTANCE  10 blocks    how far an offered earthworm calms it - wider than
                                   the scare radius, or it would dig away mid-approach
APPROACH_TIMEOUT       5 s         give up walking to an entry mound and dig here
```

---

## Phase 1 - the block

Testable on its own: place it by hand and see whether it reads as a mound.

### Model and texture

Built already. Three models, all generated rather than clicked:

| File | Shape | Elements |
|---|---|---|
| `art/mole_mound_a.bbmodel` | Dome, 12 px across, 5 px tall | 27 |
| `art/mole_mound_b.bbmodel` | Flat patty, 14 px across, 3 px tall | 29 |
| `art/mole_mound_open.bbmodel` | Crater with a 4 px shaft, 4 px rim | 41 |

The shapes come from a **radial height map on a 1 px grid**: a cone or ring
profile with a little noise, smoothed so no cell towers over all its neighbours,
then each 1 px layer covered greedily with as few rectangles as possible. That
detour exists because hand-stacking boxes produced a stepped pyramid twice over -
it read as architecture, not as earth. A round falloff on a fine grid gives a
round outline for free. The generators are kept in `art/generators/`, so the
seed, the radius and the cell size stay available as tuning dials.

Two closed shapes rather than one, because both looked right and a field of a
single mound reads as stamped.

Textures, both 16x16 and generated:

* `art/mole_mound.png` - clods of a lighter tone with shadow along their lower
  edge, dark pockets between them, a few bright crumbs.
* `art/mole_mound_shaft.png` - the floor of the open shaft, several shades
  darker, so the hole reads as depth rather than as a dent.

On landing, the textures are copied to `assets/moleverse/textures/block/` and the
exported model JSON to `assets/moleverse/models/block/`. Those model files are
hand-managed, not generated: datagen templates only substitute textures into an
existing parent and cannot express a mound.

Roughly a hundred elements across three models is a lot for a world block. It is
acceptable here because the block has no collision and stays rare, but if it ever
shows up in a profile, the generator's cell size is the dial to turn.

### Code

`block/MoleMound.java`:

```java
public class MoleMound extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    // Outline only - there is no collision. One shape covers all variants.
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0);
}
```

* Properties: `mapColor(MapColor.DIRT)`, `instabreak()`, `sound(SoundType.GRAVEL)`,
  `noCollision()`, `pushReaction(PushReaction.DESTROY)`. `noCollision` clears
  occlusion too, so `noOcclusion` alongside it is redundant.
* `OPEN` is set when a mole is down the shaft and cleared when it comes back up.
  A player placing one by hand gets the closed form.
* `canSurvive` requires the block below to be in `mole_mound_placeable`.
* The neighbour-update hook drops the block when that support disappears. The
  method carrying this changed name and signature in recent versions - look it
  up in the decompiled sources before writing it, do not copy a tutorial.
* `entityInside` spawns a few `ParticleTypes.BLOCK` particles with this block's
  state. It runs on **both** sides: spawn them client side only, or server side
  through `sendParticles`, never both, or every step doubles up. Its signature
  also changed recently; verify it.

Registration follows the existing pattern: `ModBlocks.MOLE_MOUND` through
`DeferredRegister.Blocks`, `ModItems.MOLE_MOUND` through
`registerSimpleBlockItem`, and one more `output.accept(...)` in
`ModCreativeTabs.MAIN`.

### Point of interest

`ModPoi.MOLE_MOUND`, a `PoiType` over every state of the block, registered
through a `DeferredRegister` for `Registries.POINT_OF_INTEREST_TYPE`.

This is not decoration. Phase 3 counts mounds in a radius and then follows a
chain of them, which as a raw block scan means a 16-radius search around *every*
node - hundreds of thousands of `getBlockState` calls for a single decision, per
mole, repeated after every refusal. `PoiManager.getInRange` turns both into a
lookup over the mounds that exist. Vanilla indexes beds and workstations exactly
this way.

The type is registered in phase 1, with the block, even though nothing reads it
until phase 3. Adding it later would mean existing worlds carry unindexed
mounds.

### Tag

New tag `moleverse:mole_mound_placeable` in `ModTags.Blocks`, filled in
`ModBlockTagsProvider` with the same list `mole_diggable` currently holds, plus
farmland. Two separate tags on purpose: what a mound may rest on and what a mole
can dig through are different questions, and they will diverge as soon as stone
joins `mole_diggable`.

`mole_mound` also joins `minecraft:mineable/shovel`.

### Data generation

* Blockstate and item model through `ModModelProvider`, pointing at the
  hand-written block models: `open=false` picks at random between `mole_mound_a`
  and `mole_mound_b`, `open=true` uses `mole_mound_open`, each with four random
  Y rotations - twelve variants in all. The item model uses `mole_mound_a`. The
  model datagen API changed in 1.21.9; check how vanilla emits random and rotated
  variants in the decompiled sources rather than assuming `createRotatedVariant`
  still exists.
* Loot: `ModBlockLootProvider` adds `add(MOLE_MOUND, noDrop())`.
* Language: `addBlock(ModBlocks.MOLE_MOUND, "Mole Mound")` in the generated
  `en_us`, plus a hand-written line in `de_de.json` reading "Maulwurfshügel".

### Done when

A hand-placed mound sits on grass and reads as a mound from standing height,
four of them next to each other do not look stamped, walking through raises dust
once rather than twice, breaking it gives nothing, and removing the block
underneath makes it fall away.

---

## Phase 2 - animations

Authored in Blockbench into `assets/moleverse/neoforge/animations/entity/`, with
the asymmetric coordinate conversion documented in `docs/MODEL_WORKFLOW.md`
applied on export: rotations negate X and Y, positions negate X only, scale is
untouched.

The keyframes are **generated** rather than clicked, by a script that writes
them into the open Blockbench project through the MCP bridge - the cyclic ones
sampled from sine curves so the loop closes exactly. `art/mole.bbmodel` remains
the source of truth and the Blockbench preview remains the check; only the
authoring gesture changed. The export is likewise a dozen lines of conversion in
`risky_eval`, because the plugin's export action opens a native save dialog and a
modal dialog kills the MCP connection.

| Animation | Length | Content |
|---|---|---|
| `mole_dig` | ~0.8 s, loop | Alternating front paws scooping outward, body shoving forward in small pushes, snout low |
| `mole_burrow` | ~1.2 s, once | Nose down, a first scoop, body sinking |
| `mole_emerge` | ~0.8 s, once | The reverse, ending in the rearing pose |
| `mole_idle` | ~4 s, loop | Breathing, occasional snout twitch and ear flick. Restrained enough to run permanently |

`mole_dig` is built **direction-neutral**: level, with no body pitch. Pitch and
yaw come from code, applied to the `root` part in `setupAnim` before the keyframe
animation, exactly as the rearing pose does it. One cycle then serves digging
down, digging at an angle, and horizontal tunnelling in 0.3 - which is why no
animation is ever baked per direction.

### What has to be built alongside them

The animations cannot be judged without two small pieces of code, and this phase
includes them. Without them the phase ends untested and the work is only found
to be wrong in phase 3:

* **Dig pitch and yaw on the render state**, driven by two new sliders in the
  existing `/moleverse peek panel`, plus their constants in `MoleDebug`. This is
  the code half of the direction-neutral decision, and it is what proves the
  cycle still reads when the mole is pointed straight down.
* **A debug trigger** for the one-shot animations, so `mole_burrow` and
  `mole_emerge` can be played on a standing mole without the state machine.

`mole_emerge` ends where the rearing pose sits. That pose is *not* a keyframe -
it is the code-driven `peekAmount` blend - so the animation is authored against
`peekAmount = 1`, and phase 3 has to drive `peekAmount` to 1 while `EMERGING`.
Without that handoff the two do not chain, they jump.

### Done when

Each animation plays on a standing mole from the debug trigger and looks right
from the side and from above, and `mole_dig` still reads correctly with the
pitch slider at straight down.

---

## Phase 3 - the mechanic

### States

```
WANDERING   --(bored on diggable ground, or fleeing)--> APPROACHING or BURROWING
APPROACHING --(reached an existing mound)-------------> BURROWING
APPROACHING --(timeout or path exhausted)-------------> BURROWING here, logged
BURROWING   --(animation done)------------------------> UNDERGROUND   + mound if new, dig sound
UNDERGROUND --(route finished)------------------------> EMERGING
UNDERGROUND --(route invalid)-------------------------> EMERGING at the last valid point
EMERGING    --(animation done)------------------------> WANDERING     + mound if valid, surface sound
```

The state is a single enum on the entity, mirrored to the client through an
`EntityDataAccessor` and read in `extractRenderState` - rendering depends on it.
While `EMERGING`, the state machine drives `peekAmount` to 1 so the animation
hands over to the rearing pose without a jump.

### Choosing where to go

1. `PoiManager.getInRange` for mounds within `SEARCH_RADIUS`.
2. If one is close, walk to it and use it as the entry (`APPROACHING`).
   Otherwise dig where he stands and place a mound there.
3. From the entry mound, follow the chain: every mound within
   `NETWORK_LINK_MAX` of a known one joins the network, up to `NETWORK_SCAN_MAX`
   from the start. Pick a random member as the exit - the network is assumed to
   be connected underground, so distance does not matter here.
4. With no network, pick a random surface point `NEW_TRAVEL_RANGE` away and
   place a mound on arrival.
5. A fresh site is still tried when the area is crowded - the cap is asked again
   at each candidate, which is the only place it can judge whether another hole
   belongs there. When nothing is left, the trip is refused and the mole walks
   off to try from somewhere else. That refusal is the only thing that puts an
   established mole back on the surface: strolling is switched off while it has
   mounds within reach, so its default is to be in the network or looking out of
   it.

Rules on the exit, all of which have a failure mode behind them:

* **Never the entry mound, and never closer than `MIN_EXIT_DISTANCE`.** Random
  selection otherwise picks the mound he just dived into and he pops back out
  where he went in.
* **When fleeing, weight exits by distance from the threat.** A random member of
  the network is as likely to surface him next to the wolf as away from it,
  which turns the signature escape into a suicide.
* **A candidate site needs replaceable ground cover, not air.** Short grass,
  ferns and flowers are not air; testing for air rejects nearly every meadow a
  mole lives in and the mole would simply never dig. The test is
  `BlockState.canBeReplaced()`, and the plant is replaced when the mound goes in.

### Route validity and recovery

Four of the review's findings are the same problem seen from different angles:
the mole is moved by hand through a world that changes underneath him. They are
handled together rather than as scattered checks.

The route is a list of waypoints two or three blocks under the surface,
following the terrain. Nothing is excavated - the mole simply moves through
solid ground. Keeping it as a waypoint list rather than a straight jump is the
one concession to 0.3, which will carve these same paths for real.

**Every tick, before moving**, the next waypoint is checked for all of:

* **Entity ticking.** `ServerLevel.isPositionEntityTicking` - not merely
  "loaded". A mole that walks into a border chunk stops ticking and stays there
  invisible, immune and immobile forever. A single check at departure is the
  wrong predicate and is stale on arrival anyway.
* **Solid, non-liquid ground.** A player's roof lifts the heightmap, so a
  surface-relative route runs through their living room with dig sounds and dust
  indoors; a ravine or cave mouth drops the surface away and the invisible mole
  crosses open air. Water and lava columns count as impassable and are routed
  around or cut short.

Any failure ends the trip early: he surfaces at the last valid point. That is
always a better outcome than a mole stuck in a wall.

**On arrival** the exit is validated again - support present, cover replaceable,
no water flowed in. "He surfaces there anyway and places a new one" only holds
where placing one is still legal; otherwise he emerges without a mound.

**Damage immunity never touches the `Invulnerable` NBT flag.** That flag is
saved, so a chunk unload mid-trip would serialise a permanently invulnerable
mole. Immunity is decided from the state enum in the damage entry point instead.

**Recovery on load.** The state is not written to disk, so any mole comes back
`WANDERING`. The entity load path additionally clears `noPhysics` and
invisibility and pushes him out of solid ground to the surface. This covers a
plain chunk unload, not only a world reload.

### Guard clauses

Cheap conditions in the trigger, each closing a case that is otherwise
undefined:

* Not while leashed, riding, or carrying a passenger. `Mob` is `Leashable` in
  this version, so a leashed mole burrowing 64 blocks is reachable by accident.
* Not while a baby - see below.
* `BURROWING` is a 1.2 second window in which the mole stands still and is not
  yet immune, and the flee trigger makes being chased the common case. Immunity
  therefore starts at `BURROWING`, not at `UNDERGROUND`.
* Two moles can pass the density check in the same tick and overshoot the cap by
  one. That is left alone and logged; chasing exactness here costs more than the
  fifth mound does.

### Babies

A juvenile never decides to burrow on its own. It follows its mother: when she
enters, a baby within a few blocks enters with her, travels as her passenger in
the route rather than on a route of its own, and emerges with her.

This is the most expensive item in the phase because it couples two entities'
states, so it is built **last**, after the single-mole loop works end to end. If
it proves fiddly it degrades cleanly: the baby stays above ground and waits.

### Debug logging

Every decision the mechanic makes is logged, gated behind the existing
`MoleverseConfig.debugLogging` flag so a normal game stays quiet. Without it the
only evidence of a wrong choice is a mole standing around, which says nothing
about *why*.

One logger, `moleverse.mole`, one line per event, each prefixed with the entity
id and its position so several moles can be told apart:

| Event | Logged |
|---|---|
| State change | old state, new state, reason |
| Burrow wanted | trigger (flee or bored), ground block, whether it is diggable |
| Scan finished | mounds in radius, density cap hit or not |
| Network built | members, chain depth, distance to the farthest |
| Target chosen | entry, exit, reused or newly dug, route length |
| Refused | why no dig happened: cap reached, no valid exit, leashed, riding, baby, approach timed out |
| Mound placed | position, support block, what it replaced |
| Travel finished | ticks taken, distance covered, deviation from the estimate |
| Recovered | waypoint not entity-ticking, waypoint not solid, liquid on the route, target mound gone or invalid, loaded inside ground |

The refusal and recovery lines matter most. A mole that does not dig is the
failure mode this mechanic will spend its time in, and every refusal has a
namable cause.

### Debug tooling

* `/moleverse mole burrow` makes the nearest mole dig immediately. AI runs on
  the server, so unlike the existing pose command this one registers through
  `RegisterCommandsEvent`.
* `/moleverse mole log <on|off>` flips the debug logging without a config edit
  or a restart.
* `/moleverse network <on|off>` draws the mounds around the player, the links
  between them and the current route. The client has the blocks already, so the
  network is rebuilt there with the same chaining rule and needs no packets. It
  is rebuilt on a timer rather than per frame, and it is approximate on purpose:
  render distance is smaller than `NETWORK_SCAN_MAX`, so the client's picture can
  legitimately differ from the server's decision. With a chained network this is
  still the difference between following the logic and guessing at it - the same
  reasoning that produced the slider panel.

Both trees share the `moleverse` root even though one is registered on the
client and one on the server. That works because NeoForge hands a command the
client dispatcher cannot parse on to the server.

The pose commands from phase 2 remain, and are what the animations are judged
with:

```
/moleverse peek panel                sliders for both poses, without pausing
/moleverse dig force <true|false>    hold every mole in the digging pose
/moleverse dig pitch <degrees>       90 is straight down
/moleverse dig burrow                play mole_burrow once
/moleverse dig emerge                play mole_emerge once
```

### Done when

A mole digs in on command, the mound appears, the dust trail crosses the ground,
he surfaces at a second mound, and repeating it reuses those two mounds instead
of adding more. A fifth mound never appears within 16 blocks. Hitting him mid
trip does nothing; breaking his target mound does not strand him; walking him
into an unloaded chunk surfaces him instead of losing him. The log explains
every one of those outcomes without guessing.

---

## Phase 4 - natural spawning

Moles spawn in grassland and woodland - plains, meadow, the forests and the
taigas - listed in `moleverse:spawns_moles` so a data pack can change where
without touching the mod. Placement rules through
`RegisterSpawnPlacementsEvent`, biome assignment through a NeoForge
`add_spawns` biome modifier generated by the datapack registry provider, with a
biome tag rather than a list of biomes.

Weights start low - moles are meant to be a find, not scenery. Testable by
flying over fresh terrain and counting.

### Done when

New chunks contain moles without spawn eggs, a populated meadow grows a small
field of mounds over time, and the density cap holds across several moles.

---

## Still to judge by eye

Everything a machine can check has been checked: the client loads all six entity
animations and reports no missing model or texture, the generated blockstate
carries its twelve variants, the models declare only textures that exist. None
of that says whether it *looks* right, and three things can only be settled by
watching them:

* **The animation signs.** Rotations are mirrored on export, and a wrong sign
  shows up as a mole digging with its back or hovering, not as an error. The
  rearing pose cost several rounds over exactly this. Play each animation once
  through the debug trigger and look.
* **The dig aim at full pitch.** `mole_dig` is authored level; straight down is
  the extreme it has to survive. The slider is there for it.
* **A field of mounds.** Two shapes and four rotations are meant to stop a
  meadow looking stamped. Whether twelve variants are enough only shows with a
  dozen of them on the ground.

## Known and left alone

Three review rounds produced fifteen findings; all the ones that could change or
damage the world are fixed. These are the survivors, kept here rather than in a
tracker because they are small and because knowing they were seen is worth more
than fixing them:

* The **entry** mound is chosen from the point-of-interest index without the
  reachability filter the exit gets. Worst case is a trip that aborts on its
  first tick.
* `onAddedToLevel` can force a chunk load when it closes a shaft far from where
  the mole surfaced. A performance edge, once per load.
* A mole that changes dimension carries its open-shaft position along. Closing
  it is a no-op in the wrong world, and a mole underground cannot enter a portal
  anyway - `noPhysics` switches portal handling off.
* The escort goal checks its guards when it starts, not every tick. Leashing a
  juvenile mid-trip does not stop it following.
* An abort directly under a tree trunk would surface the mole on top of the
  tree: the trunk is a solid column, so the cavity test passes. It needs the
  abort to land on that one column, which the normal loop does not do.

One thing is **unverified** rather than accepted: whether NeoForge routes the
mixed mod-bus and game-bus handlers in `MoleverseClient` correctly by itself.
The annotation could not be found in either source archive. If the network
overlay draws nothing in game, look there first.

## Deliberately deferred

* **Real tunnels.** Excavated, walkable passages belong to 0.3 Digging. The
  waypoint route is the hook they will hang on.
* **Finds.** A mole surfacing with seeds, ore or Moleverse artefacts is the
  sniffer-like idea at the heart of the mod, and it needs a find table. 0.4.
* **Decay.** Mounds stay until broken. If a settled area does turn into a
  minefield of mounds, a random tick handler is a few lines - but the density
  cap is expected to make that unnecessary.
* **Interacting with a mound.** Digging one open, blocking an exit by standing
  on it, or using the network as a player all presuppose knowing what the
  network is *for*, which 0.3 and 0.4 decide.
* **When a mole really wants to dig.** Foraging, dusk, a player approaching.
  The real behaviour design, and it wants observation first.

## To verify before writing code

Recent versions moved or renamed several of these. Check the decompiled sources
under `~/.gradle/caches/neoformruntime/`, or the AE2 checkout, rather than a
tutorial:

* the neighbour-update method that breaks an unsupported block,
* `entityInside`, its parameters and which side it runs on,
* how the current model datagen API emits randomly rotated variants,
* the damage entry point used for the immunity check, and confirmation that
  `Invulnerable` is the saved flag to stay away from,
* `PoiType` registration and the `PoiManager` query used for the radius search,
* `ServerLevel.isPositionEntityTicking` or whatever it is called here,
* `RegisterSpawnPlacementsEvent` and the datapack registry provider for biome
  modifiers.

# The mole - open design questions

Status: proposed, not decided. Nothing here is implemented yet.

## What role does the mole play?

Three directions. They are not mutually exclusive, but one has to be the core.

**A) The mole is the tool.** You tame it, it digs tunnels on command. Tunnels
become your transport network. Dig deep enough and it breaks through into the
Moleverse. The mole is the verb of the mod.

**B) The mole is wildlife and a resource.** It lives in the world, you hunt or
watch it, you get pelt and claws and build digging gear from them. You dig, not
the mole. The mole is raw material and atmosphere.

**C) You become the mole.** A potion or artefact transforms you and you burrow
through blocks yourself. The mole is the player.

Proposal: **A as the core, B as the foundation, C as an endgame reward.** B is
needed anyway - the tameable moles have to come from somewhere.

## The idea worth keeping

The mole as a **finder**, in the spirit of the sniffer. It burrows, disappears
briefly and resurfaces with something: ores, strange seeds, artefacts from the
Moleverse, depending on biome and depth. That single mechanic serves three goals
at once - custom resources, a reason to keep a mole alive rather than kill it,
and a narrative thread towards the dimension.

## Proposed scope for 0.2

| | |
|---|---|
| Base class | `Animal` - leaves taming, breeding and babies open without deciding now |
| Spawning | plains, forest, taiga; small, passive, flees |
| Signature behaviour | burrows away when threatened, resurfaces nearby |
| Trace | leaves a molehill block |
| Drops | `mole_pelt`, spawn egg |
| Deliberately not yet | taming, tunnel digging, finds - those belong to 0.3 |

Burrowing and resurfacing is the one thing that separates the mole from a
recoloured pig. Everything else is a standard mob and can wait.

## Animations

Built so far, in `assets/moleverse/neoforge/animations/entity/`:

| Animation | Length | Purpose |
|---|---|---|
| `mole_walk` | 1.0 s, loop | Diagonal four-legged gait, body bobbing, snout twitching. |
| `mole_peek` | 6.0 s, loop | Body reared up out of a hole, head sweeping left and right, bursts of sniffing. |

Still to do:

* **Digging.** One scooping cycle: the front paws alternate, the body pushes
  forward, the snout stays low.
* **Idle** above ground, and a **burrow** transition that pulls the mole under.

Digging has to work in every direction, horizontally as well as straight down or
up. Do **not** bake one animation per direction. Bake a single scoop cycle and
drive the body angle procedurally: keep the dig pitch and yaw on the render state
and apply them to the `root` part in `setupAnim` before the keyframe animation is
applied. Any angle then works, not just the three that happened to be baked, and
there is one animation to maintain instead of many.

The `root` bone exists precisely for this: everything hangs under it, so rotating
it aims the whole mole.

## Burrowing and molehills

The first mechanic that is not decoration. A mole standing on soft ground digs
itself in, leaves a molehill behind, travels underground and surfaces somewhere
nearby leaving a second molehill.

### Pieces

**1. The molehill block** `moleverse:molehill`

A small mound that sits *on top of* the ground block, the way a snow layer or a
carpet does. Not a grass variant: replacing the grass block would break every
vanilla interaction that expects grass there.

| Property | Choice | Reason |
|---|---|---|
| Support | only on blocks in `moleverse:mole_diggable` | The tag already exists and already lists the soft blocks. |
| Collision | none | You walk over a molehill, you do not trip on it. Also avoids mobs pathing around it. |
| Hardness | breaks instantly | It is loose soil. |
| Drop | nothing | It is displaced earth, not a resource. |
| Blockstate | four Y rotations, chosen at random | A field of identical mounds looks stamped. |
| Shape | stepped mound, roughly 12x12 at the base tapering to 4x4 | Reads as a mound at Minecraft resolution. |

Breaks when the block below it goes away, like a carpet.

**2. Behaviour**

A state machine on the entity, not a pile of flags:

```
WANDERING --(idle, on diggable ground, air above)--> BURROWING
BURROWING --(animation done)--> UNDERGROUND    + molehill, dig sound
UNDERGROUND --(travelled, or timeout)--> EMERGING
EMERGING --(animation done)--> WANDERING       + molehill, surface sound
```

While underground the mole is invisible, has no collision and does not path
normally; it moves to a chosen target below the surface. The state has to be
synchronised to the client, because rendering depends on it: an
`EntityDataAccessor` on the entity, read in `extractRenderState`.

**3. Animations still missing**

| Animation | Length | Notes |
|---|---|---|
| `mole_burrow` | about 1.2 s, once | Nose down, paws scoop, body sinks. |
| `mole_emerge` | about 0.8 s, once | The reverse, ending in the peek pose. |

`mole_peek` already exists and is the natural end of `mole_emerge`.

**4. Data files**

Blockstate with four rotations, block model, item model, loot table (empty),
`minecraft:mineable/shovel`, localisation. All generated, no hand-written JSON.

### Scope for the first pass

Get the mechanic working, not the behaviour right. The trigger is deliberately
crude: standing on a diggable block for a few seconds is enough. Everything about
*when* a mole decides to dig is a separate design question, to be settled once
there is something to watch.

So for now: stand on grass a while, dig in, molehill, travel, come back up.

### Open questions, deferred

* When should a mole actually decide to burrow? Fleeing, foraging, at dusk,
  when a player gets close? This is the real design work and it comes later.
* Should molehills decay after a while, or stay until broken? Staying forever
  turns a populated area into a minefield of mounds; decaying costs a random
  tick handler.
* How far does it travel underground? Far enough to be interesting, near enough
  that the player connects the two molehills.

### Order of work

1. Molehill block, modelled in Blockbench as a Java block model, with datagen.
2. Burrow and emerge animations.
3. The state machine and the goal that triggers it.

Step 1 is testable on its own: place the block by hand and see whether it reads
as a molehill.

A Blockbench project for it is already open in the `java_block` format, empty.
Note that the block model needs a hand-written model JSON under
`src/main/resources/assets/moleverse/models/block/`, because datagen templates
only substitute textures into an existing parent and cannot express a stepped
mound. The blockstate and the item model are still generated:

```java
Identifier model = Moleverse.id("block/molehill");
blockModels.blockStateOutput.accept(
        BlockModelGenerators.createSimpleBlock(ModBlocks.MOLEHILL.get(),
                BlockModelGenerators.plainVariant(model)));
blockModels.registerSimpleItemModel(ModBlocks.MOLEHILL.get(), model);
```

The `moleverse:mole_diggable` tag already lists the blocks a molehill may sit on.

### Reusing what is there

The burrow and emerge animations should follow the pattern the rearing pose ended
up with: body angle as a number in `setupAnim`, keyframes only for the paws and
the snout. `MoleDebug` and the slider panel extend to those angles with a few
lines, which is the cheapest way to get them looking right.

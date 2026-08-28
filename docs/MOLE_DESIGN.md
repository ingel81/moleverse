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

| `mole_dig` | 0.8 s, loop | Alternating front paws scooping, body shoving forward, snout low. |
| `mole_burrow` | 1.2 s, once | Nose down, a first scoop, the tail flicking up last. |
| `mole_emerge` | 0.8 s, once | The reverse, ending where the rearing pose starts. |
| `mole_idle` | 4.0 s, loop | Breathing and the odd snout twitch. |

Digging works in every direction, horizontally as well as straight down or up,
and there is deliberately **no** animation per direction. A single scoop cycle is
baked level, and the body angle is driven procedurally: dig pitch and yaw live on
the render state and are applied to the `root` part in `setupAnim` before the
keyframe animation. Any angle then works, not just the three that happened to be
baked, and there is one animation to maintain instead of many.

The `root` bone exists precisely for this: everything hangs under it, so rotating
it aims the whole mole.

## Burrowing and molehills

Decided and built. The proposal that stood here has been replaced by
`docs/MOLEHILL.md`, which carries the settled design: the mound block, the
burrowing state machine, the mound network moles travel through, and natural
spawning.

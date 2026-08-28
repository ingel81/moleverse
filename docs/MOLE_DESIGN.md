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

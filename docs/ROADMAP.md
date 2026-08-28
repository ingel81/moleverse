# Roadmap

Rough direction, not a schedule. Ordered so that every stage ends in something
playable.

## 0.1 - Scaffold *(current)*
Project setup, one block, one item, a creative tab, a greeting. Data generators
for models, loot tables, tags and the source locale. Goal: `runClient` starts
and shows Moleverse content.

## 0.2 - The mole *(in progress)*
Done: entity, model, texture, walk and rearing animations, sounds, spawn egg,
drops, a tuning panel for poses, the mound block in a closed and an open form,
the dig, burrow, emerge and idle animations, and natural spawning in grassland
and woodland.
Open: the burrowing state machine and the mound network moles travel through -
planned in full in `docs/MOLEHILL.md`.

## 0.3 - Digging
A tool or ability for carving tunnels. The `mole_diggable` tag controls what is
passable. Tunnel blocks, supports, lighting.

## 0.4 - Resources
Custom ores or finds underground, processing, recipes. Add a recipe provider to
the existing data generators.

## 0.5 - Structures
Mole burrows in the overworld: chambers, storage, tunnel networks. Jigsaw
structures with templates.

## 1.0 - The Moleverse
A dimension of its own with a custom chunk generator, biomes and a transition
from the overworld. Everything before this is groundwork for it.

## Open
- Distribution: CurseForge, Modrinth, both?
- Mixins: introduce only once there is a concrete need.

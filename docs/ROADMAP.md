# Roadmap

Rough direction, not a schedule. Ordered so that every stage ends in something
playable.

## 0.1 - Scaffold *(current)*
Project setup, one block, one item, a creative tab, a greeting. Data generators
for models, loot tables, tags and the source locale. Goal: `runClient` starts
and shows Moleverse content.

## 0.2 - The mole
The mole as an entity: model, texture, animation, spawning in plains and
forests, behaviour (burrows away, surfaces again), drops.

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

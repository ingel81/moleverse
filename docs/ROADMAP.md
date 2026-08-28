# Roadmap

Rough direction, not a schedule. Ordered so that every stage ends in something
playable.

## 0.1 - Scaffold *(current)*
Project setup, one block, one item, a creative tab, a greeting. Data generators
for models, loot tables, tags and the source locale. Goal: `runClient` starts
and shows Moleverse content.

## 0.2 - The mole *(complete)*
Entity, model, texture, six animations, sounds, spawn egg, drops, natural
spawning, a tuning panel for poses, the mound block in a closed and an open
form, and the burrowing mechanic: moles travel underground between mounds they
treat as one connected network, extend it as they go, and flee into it.
The earthworm came with it - it drops from mounds, moles eat it, and it is what
makes breeding possible.
Planned and recorded in `docs/MOLEHILL.md`, built in four phases and tuned over
twelve rounds against play tests.

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

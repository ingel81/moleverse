# Roadmap

Rough direction, not a schedule. Ordered so that every stage ends in something
playable.

Replaces an earlier version that ran digging, resources, structures and a
dimension in that order. It was written before the mound network existed and
treated it as scenery. The network turned out to be the thing worth building on -
what a stage costs and what it is worth now depends mostly on how much of it that
stage spends. `IDEAS.md` holds the collection this ordering came out of, along
with the case against what was left out.

## 0.1 - Scaffold *(complete)*
Project setup, one block, one item, a creative tab, a greeting. Data generators
for models, loot tables, tags and the source locale. Goal: `runClient` starts and
shows Moleverse content.

## 0.2 - The mole *(complete)*
Entity, model, texture, six animations, sounds, spawn egg, drops, natural
spawning, a tuning panel for poses, the mound block in a closed and an open form,
and the burrowing mechanic: moles travel underground between mounds they treat as
one connected network, extend it as they go, and flee into it. The earthworm came
with it - it drops from mounds, moles eat it, and it is what makes breeding
possible. Planned and recorded in `MOLEHILL.md`, built in four phases and tuned
over twelve rounds against play tests.

## 0.3 - The network becomes tangible
The stage that turns a network you watch into one you deal with.

* **Persisted routes.** Nothing about a trip survives a chunk reload today. This
  is the smallest foundation with the most hanging off it: the burrow below needs
  the geometry, the exchange chest needs where a mole has been, and a mole caught
  mid-trip by a reload stops being a recovery case.
* **Mound attachments.** A fitting placed on `mole_mound` rather than a block of
  its own. The one early decision that everything later leans on - chest, trap
  and the entrance below are all the same socket - so its shape is settled before
  the first line of code.
* **Worm box.** A relative of the composter: soil and plant matter in, worms out,
  slowly. Worm production is the single rate knob for everything that follows.
* **Exchange chest.** Worms in, finds out, with the loot table picked by the
  route the delivering mole travelled. Extending the network changes what it
  yields, which is what makes it a mechanic rather than a vending machine.
* **Trap.** A worm as bait, and the next mole to surface there is caught. The
  non-violent way to get one, and the way a player starts a colony deliberately.

Ends in a closed loop with no new dimension: feed the box, stock the chest, read
what the ground gave back, extend the network to change it.

## 0.4 - The burrow below
A dimension that mirrors the tunnel network and nothing else. Solid to begin
with, corridors dug out along the persisted routes at a larger scale, chambers
where the mounds are, and no way to the surface - which is what keeps it from
being a second world to hold in sync. The player is shrunk to a quarter, which is
what turns a mole run into a gallery and the small life of the soil into the
large life down there.

Getting in at all is the midgame gate: the gift, which needs the breeding chain,
the exchange chest and a colony deep enough to yield a deep find - all three at
once, none of them skippable. The full argument, the layered limits and the open
questions are in `IDEAS.md`.

## 0.5 - Life below
Corridors alone get dull. Worm larders, root growth, the matriarch in a large
node, glow-worm light, and the burrow structure that was once planned for the
overworld. Soil finds and the processing that goes with them belong here too:
this is where a network reaching deep starts to pay differently from one that
stays in the meadow.

## 0.6 - Digging of one's own
Digging claws for soft ground, and a dig command for a tamed mole, fenced in
hard. Deliberately late: both are better once corridors exist and a player can
see what an order would produce.

## 1.0 - The Moleverse
A kingdom below, past the burrow and a dimension of its own. How it is reached is
open, but where its key lies is not: everything needed to get there exists only
in the burrow. That is what keeps 0.4 and 0.5 on the critical path instead of
beside it.

## Open
- How the kingdom is entered, and what the second gift is.
- Distribution: CurseForge, Modrinth, both?
- Mixins: introduce only once there is a concrete need.

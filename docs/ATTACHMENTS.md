# Prepared mounds and attachments

Status: planned. The infrastructure is what matters here - the exchange chest,
the trap and the way into the burrow below are all the same socket, and getting
the socket wrong means rebuilding three features rather than one.

## The chain

**mound → prepared mound → attachment.** Only the first step is an animal's
doing.

A molehill is what a mole leaves behind: a flat heap, one pixel of sixteen tall
at its highest element. Nothing can sit on it - a block placed above would hang
in the air with most of a block of nothing underneath, and a block replacing it
would have to carry every attachment's model in its own.

So the player shores one up. A **prepared mound** is a block with a rim and a
level top: still a molehill in every way the moles care about, but now something
can rest on it. That step is also the gate the sockets needed. Not every
molehill in a meadow is a trading post; preparing one costs material and is a
decision.

## Why an attachment is its own block

| Alternative | Why not |
|---|---|
| A block state property on the mound | The mound already has an open flag and two variants. An attachment enum multiplies that, and a chest needs a block entity regardless |
| A block entity on every mound | A colony grows two dozen mounds in an hour and hundreds over a world's life. Almost none of them will ever carry an attachment |
| **Its own block on a prepared mound** | Plain mounds stay dumb and cheap, each attachment gets its own model, behaviour and loot, and a block entity exists only where one is needed |

## What the family shares

* Placement requires a prepared mound below.
* It breaks when that support goes, like a torch.
* All of them answer one hook: *a mole surfaced at the mound under me*. The
  burrow goal already visits that position twice - `openEntryMound` when a mole
  goes down and `placeExitMound` when one comes up - so the hook costs a call,
  not a search.

## Consequences to handle

* **The point of interest.** `ModPoi` builds the mound type from
  `ModBlocks.MOLE_MOUND.get().getStateDefinition().getPossibleStates()`. A
  prepared mound is a different block and would silently drop out of the index,
  out of every colony, and out of every route. The type has to cover both blocks,
  and `MoleMound.isMound`, `setOpen`, `canPlaceAt` and `tryPlace` with it. This
  is the single most likely way to break the mod while adding a decoration.
* **The open flag has to survive preparation.** A mound can be prepared while a
  mole is down its shaft.
* **Collision.** The plain mound is `noCollision`, and a surfacing mole is put
  *inside* that block - `beginEmerging` snaps it to the mound position. A
  prepared mound has to stay `noCollision` for the same reason, however solid its
  rim looks. A ring-shaped collision box is not a substitute: the mole is 0.7
  wide and would be squeezed out of the hole in the middle.
* **Breaking a prepared mound is breaking a mound.** It drops what went into it,
  and the molehill is gone with it - which is already how a player removes a
  mound today, and needs no special case.

## Phases

### Phase A - the prepared mound

The block, its model, the point of interest covering both mound blocks, and the
recipe or interaction that turns one into the other. Nothing sits on it yet.

**Done when** a prepared mound can be made from a plain one, a mole treats it
exactly as before - travels to it, opens its shaft, comes up out of it - and
`/moleverse colony links` still shows runs ending there.

### Phase B - the socket

An abstract attachment block: support rules, breaking with its support, and the
`moleSurfaced` hook wired into the burrow goal. Plus one trivial attachment to
prove the mechanism, whose only job is to be visible and to react.

**Done when** an attachment placed on a prepared mound reacts to a mole coming
up, and pops off when the mound below it goes.

### Phase C - the first real attachment

Whichever of the three is wanted first. The exchange chest needs worm tiers to
trade against, the trap needs a mole in a sack to put the catch into, and the
entrance needs the burrow below. None of them is blocked by the socket once
phases A and B stand, which is the point of doing them in this order.

## Open

* What preparing a mound costs. It should come out of the mod's own economy
  rather than out of the vanilla one, which points at roots or worked soil rather
  than at planks.
* Whether an attachment is placed by hand or grows out of the preparation.
* Whether a prepared mound is worth anything to the moles themselves - a colony
  that prefers its prepared mounds would make the player's investment visible in
  the animals' behaviour rather than only in the interface.

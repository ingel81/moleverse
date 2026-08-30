# Open items - the working list

**Session handover 2026-08-30 midday:** two-day burst complete, everything
compiles, 12/12 game tests green, art round 2 (beetle rebuild, grub rebuild,
weasel trim) built but NOT yet judged in game. Next session starts with the
user's playtest verdicts and a fix round. FIRST ORDER OF BUSINESS AFTER THE
LOOK SETTLES: the commits - the entire two days is uncommitted, module-wise
per house rules. Fast-test client: `./gradlew runClient -PdevFastEvents`.


## Art: every secondary mob up to the Great Worm bar (2026-08-30 directive)

The great worm is the confirmed standard. Rework queue with artist-models:
GW UV-loss diagnosis (detail vanishes in-game, 128x256 atlas suspect), grub
de-squash, shrew ground-up rebuild, beetle rework + tripod gait, weasel (only
mob without an order until now), earthworm held against the bar, and the mole
hero pass (bones/pivots sacred, silhouette texel-stable, texture
upscale-then-refine to 128x64). Great worm presence fix runs separately:
population moves into BurrowLife's trickle so it no longer depends on
entering via the shrink post.

## Art, from the 2026-08-30 playtest ("klappt alles soweit" - these are the rest)

1. **Earthworm: much more detail.** Screenshot verdict: graphically poor, too
   short, badly textured. A comparison sheet of four body approaches is
   already being built (art/worm_variants.png) - the user picks, the winner
   gets the full workup for BOTH worms. "Too short" is a hard datum for every
   variant: real worm proportion is long-and-thin, the current 1.6 blocks
   reads as a dropped sausage.
2. **Shrink post: redesign.** Current model reads as a plain fitting. It must
   look like the thing that SHRINKS you - the fiction needs to be visible in
   the object (whatever that becomes: something grown, something ringed,
   something that dwarfs its own base... concept first, then Blockbench).
3. **Soil beetle: more detail, better leg animation.** The geometry pass
   added tarsi but the verdict stands: not detailed enough, and the leg cycle
   reads wrong. Legs are the beetle's whole silhouette - likely needs a real
   tripod-gait sine offset (alternating leg triples) instead of a uniform
   sweep.

## Standing

- Commits: the whole two-day burst is uncommitted; module-wise once the look
  settles.
- Traversal debug session with the new logging (rates, weave, rubber band).
- Bake panel-tuned constants when the user hands over "copy values" output.
- Hitboxes "a touch too big" on some critters - awaiting F3+B verdict on which.
- KINGDOM.md decision: is Passage the second gift?
- Fortress mound: confirm in game that colony cores wear it (no log line).
- Weasel incursion: first live sighting pending (fast-events flag helps).
- Parked with reasons: chitin gear line, trove block, weasel/mole scale
  constant into DevGate once a third reader appears.

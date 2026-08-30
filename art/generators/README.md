# Generators

The mound models, their textures and the mole's newer animations are not clicked
together by hand - they are produced by these scripts. Two hand-built attempts at
the mound came out as stepped pyramids that read as architecture rather than as
earth, which is what pushed the shapes onto a formula.

Nothing here runs as part of the build. They are kept because the seed, the
radius and the cell size are the tuning dials: regenerating a shape is cheaper
than editing a hundred boxes, and a second variant costs nothing.

| Script | Produces |
|---|---|
| `mound_shapes.py` | The mound geometry, as cube lists on a 1 px grid. Prints JSON. |
| `mound_texture.py` | `art/mole_mound.png` - crumbly turned earth. |
| `shaft_texture.py` | `art/mole_mound_shaft.png` - the dark floor of the open shaft. |
| `mole_animations.py` | Keyframe data for `mole_dig`, `mole_burrow`, `mole_emerge`, `mole_idle`. Prints JSON. |
| `cull_buried_faces.py` | Strips faces that are completely buried inside a finished model. |
| `great_worm_shape.py` | The great worm's segments, from a girth curve, plus the UV packing. Prints JSON. |
| `great_worm_texture.py` | `great_worm.png` - the worm's skin, one texel at a time. Writes the shipped file. |
| `texture_kit.py` | The shared ramps and the drawing primitives. Produces nothing on its own. |
| `burrow_textures.py` | The block textures for the burrow, the dimension, the mole trap and the shaft lantern. Writes the shipped files. |
| `worm_item_textures.py` | `fat_worm.png` and `glow_worm.png`. Writes the shipped files. |
| `mole_in_sack.py` | `mole_in_sack.png` - the bag from a profile curve, the snout by hand. |
| `predator_shapes.py` | The shrew and the weasel, from one quadruped builder and two tables of dials. Prints JSON, `--java`, or `--bb <name>` for the Blockbench bridge. |
| `predator_textures.py` | `shrew.png`, `weasel.png` and their two spawn eggs. Writes the shipped files. |

The critter wave's `critter_shapes.py` and `critter_textures.py` are not in the
table above and should be; so should `earthworm_texture.py` and
`root_nodule_item.py`. Left alone here rather than fixed, because the wave that
wrote them owns them.

## The two predators

Same two-script split as the great worm and the critters, and for the same
reason: `predator_textures.py` imports `predator_shapes.py` so the texture is
painted through the exact face rectangles the geometry was packed with.

What is new is `parent`. These are the first models in the mod with a real bone
tree - the weasel's head has to follow its chest through the spine wave - so a
bone carries the name of the one it hangs off, and `java()` subtracts the
parent's pivot on the way out because `PartPose.offset` is relative while
`addBox` is not.

`blockbench()` also negates X, which nothing before it did. The Modded Entity
exporter mirrors that axis, so a `.bbmodel` built the way the formula reads comes
back out of Blockbench with every `_l` part on the right. The flip is applied at
the bridge, and the check that it worked is that Blockbench's own Java export and
`--java` now agree line for line - which they did not before it was added.

## The great worm

Two scripts rather than one because the second imports the first: the texture is
painted by projecting every texel of every face back into model space and asking
what the animal looks like at that point, which needs the same cube list and the
same UV rectangles the geometry was built from. Sharing them is what keeps the
ring joints continuous across a segment boundary and the clitellum a straight
band instead of a stack of slightly offset ones.

`great_worm_texture.py` writes straight into `src/main/resources`, unlike the
older texture scripts, which write into `art/` and leave a second copy to be
synced by hand. There is no reason for the working copy - the script is the
source, and two PNGs that have to agree eventually will not.

The surface function returns a **position on a ramp, not a colour**. It first
mixed colours continuously and added per-texel grain, which came to 3965
distinct colours across the atlas - a photograph, next to blocks that run to
seven colours each. Now every effect - flank height, head and tail tint, ring
joints, the blood vessel - adds or subtracts levels on one scalar, and the
result is rounded once at the end. Rounding a smooth field is what a hand
painter does when they reach for the next darkest swatch: it puts a hard edge
where a mix put a fade. The ramp is `texture_kit.WORM` and it contains the four
colours `earthworm_texture.py` chose by hand, so the animal in the burrow and
the worm a mole carries off are literally the same colours. The atlas is nine.

### The geometry decides what the shading may assume

Both of the mistakes the quantised worm exposed were the same mistake, and it is
the one worth stating once. A pattern written as a function of model position
looks like it is independent of the boxes underneath it. It is not: the boxes
decide which positions actually occur, and how many.

* **A face may be a single value of the coordinate you are varying.** North and
  south are whole cube ends at one constant z, so a rule keyed on z makes each
  of them entirely on a ring or entirely off one - there is no in-between to
  average out. The soft falloff hid this by making every end faintly grey; a
  hard step turned half of them into pale bands around a joint, which is the one
  place on a worm that never has one. They are taken as joints outright now,
  because that is also what they physically are.
* **A feature sized in absolute units is sized relative to nothing.** The blood
  vessel was two units either side of the spine, which is a stripe on a
  twenty-two unit segment and the entire animal on a four unit tail segment. Its
  width is a fraction of the segment's now.

Neither showed up while the colours were mixed continuously, because a fade
degrades gracefully and a step does not. Anything keyed on position wants
checking against the narrowest and the flattest box in the model, not the one
that was in front of you when you wrote it.

## The burrow blocks

Nine blocks borrowed vanilla textures - rooted dirt, mangrove roots,
shroomlight, barrel staves, spruce planks - and together they made the mod look
like a pile of other mods. `burrow_textures.py` replaces them, and
`texture_kit.py` is what keeps them a set: one ramp for soil, one for root, one
for worked wood, one for mycelium, and no generator gets its own colours.

Two things were learnt the hard way and are worth not relearning.

**Curves, not walks.** Roots and mycelium threads started as random walks. A
4-connected walk can only turn ninety degrees, and however gently it is steered
the eye reads the corners: every attempt came out as a maze. They are sine
curves now (`texture_kit.wave`), with the frequency a whole number of periods
across the tile so the curve leaves one edge exactly where it re-enters the
opposite one.

**Structure above one pixel.** Per-pixel noise on its own fizzes at 16 px. The
soil textures get their body from clods - two and three pixel patches with a
shadow along the lower edge - exactly as `mound_texture.py` builds the mound.

`loose_soil` is here for that second reason and no other. It was already the
mod's own art, but it was 48 colours of per-pixel jitter - 232 of its 256 pixels
shared a colour with no neighbour at all - and it is the floor of every corridor,
so it is the texture a player looks at longest. Re-rendering it is the one case
in this file where the ramp was **measured rather than chosen**: the old file's
mean colour, luminance range and spread were taken first, and they landed on
`TURNED[1:6]` almost exactly. Same hue, same range, same weight, clods instead of
jitter. A player who has been looking at that block for weeks should not be able
to say what changed.

The mole trap and the shaft lantern followed the same way out of vanilla. The
trap runs its boards vertically because `worm_box` runs its slats horizontally
- the two are otherwise the same wooden box, and one shared texture direction
would have made them one block with two names. The lantern is a root cage with
`glow_mycelium`'s ramp shut inside it rather than iron and a torch, since the
mycelium is the only light this mod actually has.

`shrink_post`, `grunting_post`, `colony_board` and `shaft_lantern` are atlases: a post, its
collar and its end grain are different things at different sizes, and a 16x16
image holds all of them side by side. The `ATLASES` table in the script is the
authority on the layout and the `uv` arrays in the model JSONs are copies of it.
`python art/generators/burrow_textures.py --atlas` prints the rectangles so the
two can be checked against each other after either side moves.

`--preview PNG` writes a magnified contact sheet with the tiling textures shown
as a 3x3 wall. Judge them there, not at 16 px: a seam or a motif that repeats
too obviously is invisible on the single tile and glaring on a corridor wall.

## How the shapes are made

A radial height map: a cone or ring profile with a little noise, smoothed so no
cell towers over all its neighbours, then each 1 px layer covered greedily with
as few rectangles as possible. A round falloff on a fine grid gives a round
outline for free - which is exactly what stacking boxes by hand does not.

## Getting the output into Blockbench

The two scripts that print JSON are read by Blockbench through the MCP bridge,
which builds the cubes or the keyframes in the open project. The `.bbmodel` files
under `art/` remain the source of truth and the Blockbench preview remains the
check; only the authoring gesture changed.

The animation export applies the coordinate conversion documented in
`docs/MODEL_WORKFLOW.md` - rotations negate X and Y, positions negate X only.

## Culling

`cull_buried_faces.py` takes a glob of finished block model JSONs and removes
faces that neighbouring boxes tile over completely. It rasterises onto the same
1 px grid the shapes are built on, so the test is exact rather than approximate.
It removed roughly a quarter of the faces across the three mounds with no visible
difference. Re-run it after regenerating a shape:

```
python art/generators/cull_buried_faces.py "src/main/resources/assets/moleverse/models/block/mole_mound_*.json"
```

`critter_shapes.py` computes the segment/leg cuboids for the burrow critters;
`--bb <name>` emits them in the form the Blockbench MCP bridge takes, so the
`.bbmodel` in `art/` and the Java export stay two views of one formula.
`critter_textures.py` paints their skins (CHITIN and LARVA ramps live there,
not in `texture_kit.py`). `root_nodule_item.py` draws the nodule item icon.

`exchange_gui.py` draws `textures/gui/exchange_station.png` - the exchange
station's screen plus its two overlay sprites. Writes the shipped file.

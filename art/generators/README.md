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
| `burrow_textures.py` | The ten block textures for the burrow and the dimension. Writes the shipped files. |
| `worm_item_textures.py` | `fat_worm.png` and `glow_worm.png`. Writes the shipped files. |

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

`shrink_post`, `grunting_post` and `colony_board` are atlases: a post, its
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

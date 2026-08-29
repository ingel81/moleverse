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

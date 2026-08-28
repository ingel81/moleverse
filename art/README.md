# Art sources

Working files for models and textures. Nothing in here is shipped: `build.gradle`
excludes `*.bbmodel` from the jar, and this directory is not part of any source set.

| File | Purpose |
|---|---|
| `mole.bbmodel` | Blockbench project for the mole. Format: Java "Modded Entity". |
| `mole_blockbench_export.java.txt` | Raw Blockbench export, kept for reference only. |

The matching texture lives at
`src/main/resources/assets/moleverse/textures/entity/mole.png` (64x32) and *is*
shipped.

See `../docs/MODEL_WORKFLOW.md` for the full workflow.

## Why the export is not used verbatim

Blockbench emits a class header for "Minecraft version 1.17 or later", which no
longer matches 1.21.11: `ResourceLocation` is now `Identifier`, `EntityModel` is
bound to an `EntityRenderState`, and `renderToBuffer` has a different signature.

Only the geometry is taken from the export - the `createBodyLayer()` body with its
`CubeListBuilder` calls. The surrounding class is written by hand against the
current API. Re-export after every model change and copy the geometry across.

## Extension is .txt on purpose

So that no IDE tries to index or compile the reference export as project source.

# Model and animation workflow

How a model gets from Blockbench into the game. Read this before touching
anything under `art/`.

## Why there is a workflow at all

Blockbench's "Modded Entity" exporter only ships code templates for Minecraft
1.12, 1.14, 1.15 and 1.17. Nothing newer exists, because the templates are built
around obfuscation mappings that no longer apply to the deobfuscated codebase.
The upstream issue is open with no fix in sight:
<https://github.com/JannisX11/blockbench/issues/3514>

So the exported Java class does not compile against 1.21.11: `ResourceLocation`
is now `Identifier`, `EntityModel` is parameterised on an `EntityRenderState`,
and `renderToBuffer` has a different signature.

The split below works around that. Only the class frame is written by hand, and
it barely ever changes; geometry and animation both stay generated.

| Step | Tool | Result |
|---|---|---|
| Geometry | Blockbench, format "Modded Entity" | export Java, keep only `createBodyLayer()` |
| Class frame | hand-written against 1.21.11 | `EntityModel<S extends EntityRenderState>` |
| Animation | Blockbench + `animation_to_json` plugin | `assets/moleverse/neoforge/animations/entity/*.json` |
| Registration | `EntityRenderersEvent` | layer definition and renderer |

## Geometry

1. Open `art/<name>.bbmodel` in Blockbench.
2. Model in cuboids only. Keep every edge length an integer so UVs land on
   pixel boundaries.
3. Bones mirror the later `ModelPart` tree. Everything hangs under a single
   `root` bone, which lets the whole entity be moved at once (burrowing).
4. Beware: the exporter mirrors the X axis. A part at +X in Blockbench ends up at
   -X in Java, which is the entity's **right**. Name parts after the Java result.
5. Export as Java class, then copy the body of `createBodyLayer()` into the model
   class. Nothing else from the export is used.
6. Save the project back to `art/` and export the reference file next to it.

## Animation

NeoForge ships a JSON keyframe animation system and recommends Blockbench for it:
<https://docs.neoforged.net/docs/entities/renderer/>

* File location: `assets/<namespace>/neoforge/animations/entity/<path>.json`
* Keyframe targets: `minecraft:position`, `minecraft:rotation`, `minecraft:scale`
* Interpolations: `minecraft:linear`, `minecraft:catmullrom`

Export with the "Animation to JSON Converter" plugin by Gaming32, kept locally at
`D:\ai_local\minecraft_modding\_tools\animation_to_json.js`. Load it through
File > Plugins > Load Plugin from File. Never "from URL" - the firewall blocks
Blockbench's outbound traffic - and never rename the file, because Blockbench
requires the filename to match the id in `Plugin.register`.

Two things the plugin does on load: it flips `Formats.modded_entity.animation_mode`
to true, which is what makes the Animate tab usable for this format at all, and it
adds the action `export_animation_to_json` under File > Export.

Do not trigger that action over MCP. It opens a native save dialog, and a modal
dialog blocks the Blockbench event loop, which kills the MCP connection. Its
conversion is barely a dozen lines; replicate it in `risky_eval`, return the JSON,
and write the file from the shell instead.

### Coordinate conventions: the delta between Blockbench and Minecraft

This cost two wrong guesses and three test rounds. Read it before authoring or
exporting an animation.

Blockbench and Java model space differ by **two** mirrors: X is flipped
(Blockbench +X becomes Java -X) and Y is flipped (Blockbench is Y-up, Java model
space is Y-down). But the conversion is not applied in one place - it is spread
across three layers, and two of them are easy to miss.

**Layer 1 - the geometry exporter.** Applies the full mirror itself. Nothing to do.

**Layer 2 - the MCP tool `create_animation`.** Negates X rotations on the way *in*.
Pass `70` and the keyframe stores `-70`. `risky_eval` with
`keyframe.set('x', value)` bypasses this and writes what you give it.

**Layer 3 - NeoForge, when reading the JSON.** This is the one that gets
overlooked. From `net.neoforged.neoforge.client.entity.animation.AnimationTarget`:

```java
POSITION -> KeyframeAnimations::posVec     // posVec(x, y, z) -> (x, -y, z)
ROTATION -> KeyframeAnimations::degreeVec  // degrees to radians only, no sign change
SCALE    -> KeyframeAnimations::scaleVec   // value - 1
```

So NeoForge already negates Y **for positions only**. Rotations get no sign
handling at all, and the mirror has to come from us.

That leaves exactly one rule for the export, taking Blockbench values as input:

| Channel | Transform on export |
|---|---|
| `rotation` | negate X and Y, keep Z |
| `position` | negate X only, **keep Y** (NeoForge negates it later) |
| `scale` | unchanged |

Getting the rotation wrong produced a mole that reared up head first into the
ground and appeared to hover half a block above it: one wrong sign, two symptoms
that look unrelated. Then negating Y for position as well double-mirrored it and
moved the mole further up while trying to move it down.

A practical consequence for positions: to move a part **down** in the world, the
JSON needs a **negative** Y. In `mole_peek` the root sits at `y: -11`, which
`posVec` turns into +11 in model space, sinking the mole's rear into the ground
as it rears up.

Units: 16 model units are one block.

Verify a new animation in game once. The Blockbench viewport uses its own
convention and will happily show a pose that renders mirrored in Minecraft.

## Why not GeckoLib

GeckoLib loads model and animation entirely from `.geo.json` and
`.animation.json` at runtime and adds Molang, easings and animation layers. It is
more capable, but it is a hard dependency every user has to install.

NeoForge's built-in system covers keyframed position, rotation and scale per
bone, which is enough for walking, sniffing and digging. Applied Energistics 2,
the reference project, does not use GeckoLib either. Revisit this only if an
animation genuinely cannot be expressed with keyframes.

## Registration

```java
public static final ModelLayerLocation MOLE_LAYER =
        new ModelLayerLocation(Moleverse.id("mole"), "main");

@SubscribeEvent
static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.add(MOLE_LAYER, MoleModel::createBodyLayer);
}

@SubscribeEvent
static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.MOLE.get(), MoleRenderer::new);
}
```

## Audio

Minecraft only plays **Ogg Vorbis**. No WAV, no MP3, no Opus.

Positional sound must be **mono**. A stereo file is played non-positionally: no
direction, no distance falloff, equally loud across the whole world. Stereo is
only right for music and ambient tracks.

Raw material lives in `audio/raw/`, mirroring the target layout, and is converted
by `audio/convert.ps1`:

```
audio/raw/entity/mole/sniff1.mp3
  -> src/main/resources/assets/moleverse/sounds/entity/mole/sniff1.ogg
```

Numbered variants of one event (`sniff1`, `sniff2`, ...) let `sounds.json` pick at
random, which stops a repeated sound from sounding mechanical.

`sounds.json` is generated by NeoForge's `SoundDefinitionsProvider` rather than
hand-written, like every other data file here. Sound events are registered in
`registry/ModSounds`. Give every event a subtitle key so players with subtitles
enabled see something; those keys go through `ModLanguageProvider`.

Licensing is tracked in `audio/SOURCES.md`. A sound whose licence forbids
redistribution would make the whole mod undistributable, so check before adding.

## Files

| Path | Content |
|---|---|
| `art/*.bbmodel` | Blockbench projects. Not shipped. |
| `art/*_blockbench_export.java.txt` | Raw exports, reference only. |
| `src/main/resources/assets/moleverse/textures/entity/` | Entity textures. Shipped. |
| `src/main/resources/assets/moleverse/neoforge/animations/entity/` | Animations. Shipped. |

## Design at the largest scale, verify at the smallest

The scale attribute means one model serves every size it appears at - the
same mole is 1x in a meadow and 7x filling a corridor. So a creature is
DESIGNED for its largest appearance and CHECKED at its smallest: geometry
survives shrinking losslessly (no LOD, sub-pixel cubes render fine) but can
never be added by growing; texel density is budgeted for the big view and
mipmaps handle the way down. The one trap is aliasing - detail tuned for 7x
can turn mushy at 1x - and the rule that avoids it: silhouette from geometry
(reads at every size), fine detail from texture (shows big, vanishes small
gracefully).

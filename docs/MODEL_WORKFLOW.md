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

Beware: the MCP tool `create_animation` negates X rotations on the way *in*. Pass
`70` and the keyframe stores `-70`. The export itself is faithful and writes
whatever is stored, so always check the pose in the viewport after creating an
animation rather than trusting the number you passed. Setting a keyframe directly
through `risky_eval` (`keyframe.set('x', value)`) bypasses the negation.

Playing an animation from the model class:

```java
public static final AnimationHolder DIG = Model.getAnimation(Moleverse.id("mole_dig"));

private final KeyframeAnimation dig;

public MoleModel(ModelPart root) {
    this.dig = DIG.get().bake(root);
}

@Override
public void setupAnim(MoleRenderState state) {
    super.setupAnim(state);
    this.dig.apply(state.digAnimationState, state.ageInTicks);
    this.dig.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1, 1);
}
```

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

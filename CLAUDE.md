# Moleverse — working notes

NeoForge mod for Minecraft. Theme: moles, their tunnels, and eventually a
dimension of their own.

## Project rules

* **English everywhere.** Code, identifiers, comments, Javadoc, documentation,
  commit messages. The only exception is `assets/moleverse/lang/de_de.json`,
  which is the German localisation and is German by definition.
* **Commit messages are a single line.** No body, no bullet lists. Squeeze the
  reason into the subject or leave it out. Conventional Commits prefixes
  (`feat:`, `fix:`, `chore:`, `ci:`, `docs:`, `refactor:`) are used.
* **No `Co-Authored-By` trailers for Claude**, and no session links in commits.
* **Do not watch CI runs.** The local build is the signal that matters. Only
  look at GitHub Actions when something actually depends on it, such as a
  release build or a failure someone reports.
* Git identity is configured per repository, not globally:
  `ingel81 <ingel81@users.noreply.github.com>`.

## Facts

| | |
|---|---|
| Minecraft | 1.21.11 |
| NeoForge | 21.11.45 |
| Java | 21 (Temurin at `C:\Users\joerg\.jdks\jdk-21.0.12.1+1`) |
| Gradle | 9.2.1 via wrapper, plugin ModDevGradle 2.0.144 |
| Mod id | `moleverse` |
| Package | `net.sgeht.moleverse` |
| groupId | `net.sgeht` |
| Licence | LGPL-3.0-or-later (same as AE2) |

## Environment

* The Windows firewall runs with `DefaultOutboundAction = Block`. Every new Java
  binary needs its own outbound allow rule, otherwise all Gradle downloads fail
  with `SocketException: Permission denied: getsockopt`. Rules for the JDK above
  already exist. `ssh.exe` has none, so the git remote uses HTTPS.
* Gradle invocations need `dangerouslyDisableSandbox`, because the default
  sandbox blocks the JVM's outbound connections.
* `JAVA_HOME` is set at user scope; set it explicitly in freshly spawned shells.

## Reference project

This project takes **Applied Energistics 2** as its model. A checkout of the
matching branch (`1.21.11`, not `main` — that one is already on MC 26.1.2) sits
at `D:\ai_local\minecraft_modding\_reference\Applied-Energistics-2`.

Look there for questions about project layout, registry organisation, datagen,
worldgen and networking instead of hunting for tutorials. The folder lives
outside the mod repository on purpose and is not versioned with it.

## API specifics of this Minecraft version

* `ResourceLocation` is now called **`Identifier`** (`net.minecraft.resources.Identifier`).
* Data directories are singular: `data/<ns>/loot_table/`, `tags/block/`, `tags/item/`, `recipe/`.
* Item models come in two parts: the model under `assets/<ns>/models/item/<name>.json`
  plus an item model definition under `assets/<ns>/items/<name>.json`. Block items
  need no `models/item/` entry — their `items/<name>.json` points at the block model.

## Look the API up, do not guess

The source to read is `build/moddev/artifacts/neoforge-21.11.45-sources.jar`.
It carries Minecraft **and** NeoForge, with Parchment applied - real parameter
names, javadoc, and NeoForge's own patch comments, which frequently say why
something was changed. Parchment is configured in `gradle.properties`
(`parchment_minecraft_version`, `parchment_mappings_version`).

Do not read `~/.gradle/caches/neoformruntime/intermediate_results/decompile_*_output.jar`.
That is the raw decompile from before mappings are applied: same code, but every
parameter is called `p_401394_` and every patch comment is missing. It answers
signatures and nothing else, and it hides things that matter - it shows
`SavedDataType` demanding a `DataFixTypes` where the patched record accepts null
and offers a three-argument constructor for mod data.

Vanilla assets (blockstates, models, item model definitions) are in
`~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.11_client.jar`.

Check there whenever a signature or a JSON format is uncertain. Tutorials on the
web almost always target older versions and are frequently wrong for this one.

## Traps already hit

* `BlockBehaviour.Properties.noCollission` lost its double s in this version and
  is now `noCollision()`. It also clears occlusion, so `noOcclusion()` next to it
  is redundant.
* `BlockModelGenerators.blockStateOutput` and friends are package-private in the
  sources. They are usable only because NeoForge opens the whole class through
  its global access transformer - no mod-side transformer needed, but do not
  expect the same for an arbitrary vanilla field.
* A hand-written block model needs `"parent": "minecraft:block/block"`, otherwise
  the block item inherits no display transforms and lies flat and full-size in
  the hand and the creative tab.
* `entityInside` runs on both sides, but the client only simulates the local
  player - a mob walking through a block reaches it server side only.
* `Player` has no `sendSystemMessage`. Cast to `ServerPlayer` for chat, or use
  `displayClientMessage(Component, boolean)`.
* In `neoforge.mods.toml`, `loaderVersion` means the version of the **javafml
  language loader** (currently 10.0), not the NeoForge version. Setting it wrong
  aborts the client with "needs language provider javafml:<version> ... We have
  found 10.0". `modLoader` and `loaderVersion` are therefore omitted, exactly as
  the official MDK does.
* Gradle's `expand()` uses Groovy's SimpleTemplateEngine: a literal
  dollar-brace construct in a template — even inside a comment — is evaluated as
  an expression and breaks the build.
* `gradle.properties` is read as ISO-8859-1. Non-ASCII characters there only as
  `\uXXXX` escapes.
* Windows does not carry an executable bit, so `gradlew` landed in the index as
  `100644` and the Linux CI runner failed with `./gradlew: Permission denied`.
  Fixed with `git update-index --chmod=+x gradlew`.

## Tooling: Blockbench

Entity, block and item models are authored in Blockbench, not by hand.
Blender is only for concept studies and promotional renders - Minecraft entity
models are axis-aligned cuboids in a bone hierarchy and cannot come from a
free-form mesh.

Setup on this machine:

* Portable build and the MCP plugin live in `D:\ai_local\minecraft_modding\_tools`.
* The plugin file must be named `mcp.js` - Blockbench requires the filename to
  match the id in `Plugin.register`. Load it through File > Plugins > Load Plugin
  from File, never 'from URL': the firewall blocks Blockbench's outbound traffic.
* The MCP server runs inside Blockbench on `http://localhost:3000/bb-mcp`.
  Loopback is exempt from the firewall, so no extra rule is needed.
* Registered at **user** scope as server `blockbench`
  (`claude mcp add blockbench --transport http http://localhost:3000/bb-mcp --scope user`).
  Project scope does not work here: Claude Code runs from the parent directory
  `D:\ai_local\minecraft_modding`, so a `.mcp.json` inside the mod folder is never
  picked up and the approval prompt never appears.
* Blockbench must be running with a project open for most tools to work.

Useful tools: `create_project` (format `modded_entity` = "Java Class", the entity
format), `place_cube` (`from`/`to`/`origin`/`rotation` map one to one onto
`CubeListBuilder`), `add_group` for bones, `create_texture`, `set_camera_angle`,
`export_model` to write to disk, and `risky_eval` for everything the tools do not cover.

### Quirks worth knowing

* **`capture_screenshot` renders the viewport without textures.** Judging a texture
  from it is impossible - it always looks flat grey. Use `capture_app_screenshot`,
  which grabs the real window.
* **`add_group`'s `parent` parameter has no effect.** Groups end up as siblings at
  the top level. Reparent afterwards with `risky_eval`: `group.addTo(rootGroup)`.
* **The `modded_entity` export mirrors the X axis.** A part at +X in Blockbench comes
  out at -X in Java, which is the entity's *right* in Minecraft convention. Name parts
  according to the Java result, not the Blockbench view.
* **Animations need a coordinate conversion on export, and it is not symmetric.**
  Rotation: negate X and Y, keep Z. Position: negate X only, keep Y, because
  NeoForge applies `posVec` and negates Y itself when reading the JSON. Scale:
  unchanged. Getting this wrong shows up as a model that is mirrored, floating, or
  both. The full reasoning with the NeoForge source lines is in
  `docs/MODEL_WORKFLOW.md` under "Coordinate conventions".
* **`place_cube` fails without a texture** (`No texture found for "undefined"`).
  Create a texture first.
* **`create_texture` ignores `width`/`height` when `fill_color` is used** and produces
  16x16. The project resolution is separate again: set `Project.texture_width` and
  `Project.texture_height` via `risky_eval`, otherwise the export writes the wrong
  values into `LayerDefinition.create(...)`.
* **Auto UV assigns `[0, 0]` to every cube**, stacking them all in the same corner.
  Pack the layout by hand: in box UV mode a cube occupies `2*(depth+width)` by
  `depth+height` pixels, and `uv_offset` is its top-left corner.

### Export path

Blockbench's Modded Entity exporter only has templates up to Minecraft 1.17, so its
Java output does not compile against 1.21.11. Take only the `createBodyLayer()` body
from it and hand-write the class frame. Animations come from the
`animation_to_json` plugin into `assets/moleverse/neoforge/animations/entity/`,
using NeoForge's built-in keyframe system rather than GeckoLib.

The full procedure, including the reasoning and the registration snippets, is in
`docs/MODEL_WORKFLOW.md`. Read it before touching `art/`.

## Data generation

`./gradlew runData` writes to `src/generated/resources`, which is committed.
Entry point: `data/MoleverseDataGenerators`, bound to `Dist.CLIENT` because
model generation lives in client-only classes.

API shape in this version, verified against the AE2 checkout and the NeoForge
sources - most tutorials still show the pre-1.21.9 API and will not compile:

* The event is `GatherDataEvent.Client`, not plain `GatherDataEvent`.
* Providers are added through `event.getGenerator().getVanillaPack(true)`.
* Models: extend `net.minecraft.client.data.models.ModelProvider` and override
  `registerModels(BlockModelGenerators, ItemModelGenerators)`. The two-argument
  constructor `(PackOutput, String modId)` is a NeoForge addition and scopes
  validation to our namespace.
  `blockModels.createTrivialCube(block)` covers blockstate, block model and the
  block item; `itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM)` covers flat items.
* Loot: `net.minecraft.data.loot.LootTableProvider` with
  `SubProviderEntry(factory, LootContextParamSets.BLOCK)`; the sub provider extends
  `BlockLootSubProvider` and must narrow `getKnownBlocks()` to this mod's blocks.
* Tags: `net.neoforged.neoforge.common.data.BlockTagsProvider` / `ItemTagsProvider`,
  constructor `(PackOutput, CompletableFuture<HolderLookup.Provider>, String modId)`.
* Language: `net.neoforged.neoforge.common.data.LanguageProvider`.

Never hand-write a file a provider produces - both copies land in the jar.
Only the source locale is generated; `de_de.json` stays hand-written.

## Poses: code, not keyframes

The body angle of a pose belongs in `setupAnim`, driven by a blend factor on the
render state. Keyframe channels are for secondary motion only - head, snout,
limbs, tail.

The mole's rearing pose started as a keyframe channel on the `root` bone and cost
several rounds of guessing: a channel there has to survive three separate
coordinate conversions, so a wrong result gives no hint which layer produced it.
As a plain number in the model the value means exactly what it says, blends
smoothly, and generalises - aiming a digging mole in an arbitrary direction is the
same mechanism, which is why baking one animation per direction is the wrong plan.

Bone pivots matter as much as the angle. `root` sits at the hips, not at the
centre of the body, because that is what a mole rears around. With the pivot in
the middle the corrections needed to hide the error were an order of magnitude
larger.

## Tuning visual values

`/moleverse peek panel` opens a slider panel for the rearing and digging poses.
It does not pause the game and covers only a strip on the left, so the mole stays
visible while a slider is dragged. "Hold pose" freezes the mole - the entity, not
just its rendering - so a value can be judged without waiting for the timer.

Build this kind of instrument rather than iterating through config files or
guessed numbers. Anything that hides the subject while the value changes defeats
the purpose. Once a number is settled it is baked into a constant in
`debug/MoleDebug` and the panel stays as a check.

The same principle produced the rest of the debug surface, listed in
`docs/MOLEHILL.md`: `/moleverse dig burrow` and `/moleverse dig emerge` play the
one-shot animations on demand, `/moleverse mole burrow` makes a mole take a real
trip without waiting out its cooldown, `/moleverse network on` draws the mound
network and the current route, and `/moleverse mole log on` turns on a log line
for every decision the mechanic makes - above all for every refusal, because a
mole that does not dig is the failure mode with no visible cause.

Note the split: pose commands are client side (`RegisterClientCommandsEvent`),
anything that touches behaviour is server side (`RegisterCommandsEvent`), because
mob AI only exists there.

The panel currently ships in the jar. Before a release it needs a switch or an
exclusion from the release build.

## Conventions

* Versions live in `gradle.properties` only. Never hard-wire them in code or build script.
* `DeferredRegister` instances are attached to the bus solely in
  `registry/ModRegistries.register()`.
* Mind the two buses: the mod event bus for lifecycle and registration,
  `NeoForge.EVENT_BUS` for runtime events.
* Client-only code belongs under `client/` or in `MoleverseClient`. Never
  reference it from common code.
* Tag strings are not written inline; they go into `tag/ModTags`.
* `neoforge.mods.toml` is a template under `src/main/templates`. Never edit the
  generated copy in `build/`.

## Development environment

`runClient` brings quality-of-life mods along. They live in `localRuntime`, so
they never reach the jar or the mod metadata:

| Mod | Why |
|---|---|
| JEI | recipe lookup |
| Jade | block and entity tooltips - shows a mole's state at a glance |
| FTB Ultimine | mine a whole vein or surface at once |
| FTB Essentials | `/home`, `/tpa`, `/back` |

FTB is **not on Modrinth** - it runs its own Maven at `maven.ftb.dev/releases`,
and its builds are numbered after the Minecraft version (`2111.x` is 1.21.11).
The FTB mods need **Architectury**, which needs a third repository again, and
FTB Ranks is pulled in transitively by Essentials. All three repositories are in
`build.gradle` with `content { includeGroup ... }` so they are only consulted for
their own artifacts.

The dev client **opens itself to LAN with cheats** as soon as a single-player
world is entered - see `client/debug/DevWorldPublisher`. It goes through
`IntegratedServer.publishServer(gameType, allowCheats, port)`, the same call the
"Open to LAN" screen makes, whose middle argument is the cheats flag: commands
therefore work even in a world that was created without them, which matters
because every `runClient` tends to make a fresh one. It is gated behind the
`moleverse.devPublish` system property, set only by the Gradle run configuration.

## Commands

```bash
./gradlew build           # compile + jar
./gradlew runClient       # dev client (with JEI + Jade)
./gradlew runData         # data generators -> src/generated/resources
./gradlew runGameTestServer

tools/soak/soak.sh tools/soak/colonies.commands   # headless soak run
```

## Watching behaviour without playing

Every mechanic here fails the same way: an animal decides not to act, the
decision is invisible, and nothing happens. Two things exist for that.

* **The mole log is on from the first tick of any Gradle run**, through the
  `moleverse.devLogging` system property set in `configureEach` - the same
  pattern as `moleverse.devPublish`. A shipped game never sees the property and
  stays quiet, and `/moleverse mole log off` still wins over both. Turning it on
  by hand used to mean missing the colony founding, which happens seconds after
  a world is entered.
* **`tools/soak/soak.sh` runs a scenario against a headless server.** No client,
  no player, no window: two hours of game time in about two minutes, because
  `/tick sprint` runs ticks as fast as the CPU allows and nothing in this mod
  reads a wall clock. Scenarios are text files of vanilla console commands.

`docs/TESTING_AUTOMATION.md` has the mechanics and, more usefully, the traps -
including two early runs that produced healthy-looking output and meant nothing.
Read it before writing a scenario.

Note that `build.gradle` sets `standardInput = System.in` for `runServer`.
Without it Gradle hands a `JavaExec` task an empty standard input and every
console command is swallowed with no error whatsoever.

## Open decisions

* Distribution channel (CurseForge / Modrinth).
* Mixins: introduce only once there is a concrete need.

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

After the first build the decompiled Minecraft sources sit in
`~/.gradle/caches/neoformruntime/intermediate_results/decompile_*_output.jar`.
Vanilla assets (blockstates, models, item model definitions) are in
`~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.11_client.jar`.

Check there whenever a signature or a JSON format is uncertain. Tutorials on the
web almost always target older versions and are frequently wrong for this one.

## Traps already hit

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

## Commands

```bash
./gradlew build           # compile + jar
./gradlew runClient       # dev client (with JEI + Jade)
./gradlew runData         # data generators -> src/generated/resources
./gradlew runGameTestServer
```

## Open decisions

* Distribution channel (CurseForge / Modrinth).
* Mixins: introduce only once there is a concrete need.

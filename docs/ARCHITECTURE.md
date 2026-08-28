# Architecture

Guiding idea: the mod will grow into custom blocks, mobs, world generation,
structures and a dimension of its own. The scaffold is therefore cut along
responsibilities from the start, rather than piling everything into the main class.

## Layers

| Package | Responsibility |
|---|---|
| `net.sgeht.moleverse` | Entry points. They wire things up and hold no domain logic. |
| `config` | `ModConfigSpec` definitions. No access to registries. |
| `registry` | `DeferredRegister` instances and their entries, nothing else. |
| `block`, `item`, `entity` | Behaviour classes. Referenced from `registry`, never the other way round. |
| `worldgen`, `dimension` | Features, placed features, structures, dimension type. |
| `client` | Everything that must not exist on a dedicated server. |
| `network` | Payloads and their handlers. |
| `tag` | `TagKey` constants, so tag strings are not scattered through the code. |
| `data` | Data generators. Only runs under `runData`. |

## Registration

`ModRegistries.register(IEventBus)` is the only place where `DeferredRegister`
instances are attached to the mod event bus. New registry classes are added
there and nowhere else.

Order matters: blocks before items, so `registerSimpleBlockItem` can resolve its
block.

## Event buses

NeoForge separates two buses, and confusing them is the most common source of
errors:

* **Mod event bus** - lifecycle and registration (`FMLCommonSetupEvent`,
  `RegisterEvent`, `BuildCreativeModeTabContentsEvent`). Obtained through the
  `IEventBus` passed to the mod constructor.
* **Game event bus** (`NeoForge.EVENT_BUS`) - runtime events (`PlayerEvent`,
  `ServerStartingEvent`, tick events). Obtained through
  `@EventBusSubscriber(modid = Moleverse.MOD_ID)`.

## Side separation

Client-only code lives entirely under `client` or in `MoleverseClient`. A
dedicated server does not load those classes, so any reference to them from
common code causes a `NoClassDefFoundError` there.

## Resources: hand-written versus generated

`src/main/resources` holds what cannot sensibly be generated (textures, sounds,
language files). `src/generated/resources` is filled by `runData` (models,
blockstates, loot tables, recipes, tags) and is part of the jar as well.

While there are no data generators yet, loot tables and tags are also
hand-written under `src/main/resources/data`. They move to `data/` providers
once the generators arrive.

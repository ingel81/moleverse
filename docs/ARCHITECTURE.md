# Architektur

Leitgedanke: Die Mod wächst auf eigene Blöcke, Mobs, Weltgenerierung, Strukturen
und eine eigene Dimension zu. Das Gerüst ist deshalb von Anfang an nach
Zuständigkeiten geschnitten und nicht nach „alles in die Hauptklasse“.

## Schichten

| Paket | Zuständigkeit |
|---|---|
| `net.sgeht.moleverse` | Einstiegspunkte. Verdrahten nur, enthalten keine Fachlogik. |
| `config` | `ModConfigSpec`-Definitionen. Kein Zugriff auf Registries. |
| `registry` | Ausschließlich `DeferredRegister` und deren Einträge. |
| `block`, `item`, `entity` | Verhaltensklassen. Werden aus `registry` referenziert, nie umgekehrt. |
| `worldgen`, `dimension` | Features, Placed Features, Strukturen, Dimensionstyp. |
| `client` | Alles, was auf einem dedizierten Server nicht existieren darf. |
| `network` | Payloads und Handler. |
| `tag` | `TagKey`-Konstanten, damit Tag-Strings nicht im Code verstreut sind. |
| `data` | Data Generators. Läuft nur unter `runData`. |

## Registrierung

`ModRegistries.register(IEventBus)` ist die einzige Stelle, an der
`DeferredRegister` am Mod-Event-Bus angemeldet werden. Neue Registry-Klassen
werden dort eingetragen — sonst nirgends.

Reihenfolge zählt: Blöcke vor Items, damit `registerSimpleBlockItem` seinen Block
auflösen kann.

## Event-Busse

NeoForge trennt zwei Busse; die Verwechslung ist die häufigste Fehlerquelle:

* **Mod-Event-Bus** — Lifecycle und Registrierung (`FMLCommonSetupEvent`,
  `RegisterEvent`, `BuildCreativeModeTabContentsEvent`). Zugriff über den
  `IEventBus` im Mod-Konstruktor.
* **Game-Event-Bus** (`NeoForge.EVENT_BUS`) — Laufzeitgeschehen
  (`PlayerEvent`, `ServerStartingEvent`, Tick-Events). Zugriff über
  `@EventBusSubscriber(modid = Moleverse.MOD_ID)`.

## Seiten-Trennung

Client-only-Code liegt vollständig unter `client` bzw. in `MoleverseClient`.
Ein dedizierter Server lädt diese Klassen nicht — jeder Verweis darauf aus
gemeinsamem Code führt dort zu `NoClassDefFoundError`.

## Ressourcen: handgeschrieben vs. generiert

`src/main/resources` enthält, was sich nicht sinnvoll generieren lässt
(Texturen, Sounds, Sprachdateien). `src/generated/resources` wird von `runData`
befüllt (Modelle, Blockstates, Loot Tables, Rezepte, Tags) und ist ebenfalls
Teil des JARs.

Solange es noch keine Data Generators gibt, liegen auch Loot Tables und Tags
handgeschrieben unter `src/main/resources/data` — beim Umstieg wandern sie
nach `data/`-Providern.

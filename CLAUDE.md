# Moleverse — Arbeitsanweisungen

NeoForge-Mod für Minecraft. Thema: Maulwürfe, ihre Gänge, später eine eigene Dimension.

## Fakten

| | |
|---|---|
| Minecraft | 1.21.11 |
| NeoForge | 21.11.45 |
| Java | 21 (Temurin unter `C:\Users\joerg\.jdks\jdk-21.0.12.1+1`) |
| Gradle | 9.2.1 via Wrapper, Plugin ModDevGradle 2.0.144 |
| Mod-ID | `moleverse` |
| Package | `net.sgeht.moleverse` |
| groupId | `net.sgeht` |
| Lizenz | LGPL-3.0-or-later (wie AE2) |

## Umgebung

* Windows-Firewall steht auf `DefaultOutboundAction = Block`. Jedes neue Java-Binary
  braucht eine eigene Outbound-Allow-Regel, sonst schlagen alle Gradle-Downloads mit
  `SocketException: Permission denied: getsockopt` fehl. Für das oben genannte JDK
  existieren die Regeln bereits.
* Gradle-Aufrufe brauchen `dangerouslyDisableSandbox`, weil die Standard-Sandbox
  ausgehende Verbindungen der JVM unterbindet.
* `JAVA_HOME` ist im User-Scope gesetzt; in frisch gestarteten Shells ggf. explizit setzen.

## API-Besonderheiten dieser Minecraft-Version

* `ResourceLocation` heißt jetzt **`Identifier`** (`net.minecraft.resources.Identifier`).
* Datenverzeichnisse im Singular: `data/<ns>/loot_table/`, `tags/block/`, `tags/item/`, `recipe/`.
* Item-Modelle sind zweigeteilt: Modell unter `assets/<ns>/models/item/<name>.json`,
  zusätzlich eine Item-Model-Definition unter `assets/<ns>/items/<name>.json`.

## API nachschlagen statt raten

Die dekompilierten Minecraft-Quellen liegen nach dem ersten Build als JAR unter
`~/.gradle/caches/neoformruntime/intermediate_results/decompile_*_output.jar`.
Vanilla-Assets (Blockstates, Modelle, Item-Model-Definitionen) stecken in
`~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.11_client.jar`.

Bei jeder unsicheren Signatur oder jedem unsicheren JSON-Format dort nachsehen -
Tutorials im Netz beziehen sich meist auf aeltere Versionen und liegen haeufig daneben.

Bereits gestolpert: `Player` hat kein `sendSystemMessage`. Fuer Chat-Nachrichten auf
`ServerPlayer` casten, oder `displayClientMessage(Component, boolean)` verwenden.

## Bereits getretene Fallen

* `Player` hat kein `sendSystemMessage`. Fuer Chat auf `ServerPlayer` casten,
  alternativ `displayClientMessage(Component, boolean)`.
* In `neoforge.mods.toml` meint `loaderVersion` die Version des **javafml-Sprachladers**
  (aktuell 10.0), nicht die NeoForge-Version. Falsch belegt bricht der Client mit
  "needs language provider javafml:<version> ... We have found 10.0" ab. Deshalb werden
  `modLoader`/`loaderVersion` weggelassen - so macht es auch das offizielle MDK.
* Gradles `expand()` nutzt Groovys SimpleTemplateEngine: ein woertliches Dollar-Klammer-Konstrukt
  im Template - auch in einem Kommentar - wird als Ausdruck ausgewertet und laesst den Build platzen.
* `gradle.properties` wird als ISO-8859-1 gelesen. Umlaute dort nur als \uXXXX-Escape.

## Referenzprojekt

Vorbild dieses Projekts ist **Applied Energistics 2**. Ein Checkout des passenden
Branches (`1.21.11`, nicht `main` - der steht bereits auf MC 26.1.2) liegt unter
`D:\ai_local\minecraft_modding\_reference\Applied-Energistics-2`.

Dort nachsehen bei Fragen zu Projektaufbau, Registry-Organisation, Datagen,
Worldgen und Netzwerkcode - statt Tutorials zu suchen. Der Ordner liegt bewusst
ausserhalb des Mod-Repos und wird nicht mitversioniert.

## Konventionen

* Versionen ausschließlich in `gradle.properties`. Nichts hart im Code oder Build verdrahten.
* `DeferredRegister` werden nur in `registry/ModRegistries.register()` am Bus angemeldet.
* Bus-Trennung beachten: Mod-Event-Bus für Lifecycle/Registrierung, `NeoForge.EVENT_BUS`
  für Laufzeit-Events.
* Client-Code gehört unter `client/` oder in `MoleverseClient`. Nie aus gemeinsamem Code referenzieren.
* Tag-Strings nicht inline schreiben, sondern in `tag/ModTags` aufnehmen.
* Kommentare und Dokumentation auf Deutsch, Bezeichner im Code auf Englisch.
* `neoforge.mods.toml` liegt als Template unter `src/main/templates`. Die generierte
  Fassung in `build/` nie bearbeiten.

## Kommandos

```bash
./gradlew build           # kompilieren + JAR
./gradlew runClient       # Dev-Client (mit JEI + Jade)
./gradlew runData         # Data Generators -> src/generated/resources
./gradlew runGameTestServer
```

## Offene Entscheidungen

* Veröffentlichungskanal (CurseForge / Modrinth).
* Mixins erst einführen, wenn ein konkreter Bedarf besteht.

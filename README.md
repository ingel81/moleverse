# Moleverse

Minecraft-Mod über Maulwürfe: ihre Gänge, ihr Handwerk und eine eigene Welt unter der Welt.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Modloader | NeoForge 21.11.45 |
| Java | 21 |
| Build | Gradle 9.2.1 + ModDevGradle 2.0.144 |
| Mod-ID | `moleverse` |
| Package | `net.sgeht.moleverse` |

## Voraussetzungen

* JDK 21 (Temurin empfohlen). Prüfen mit `java -version`.
* Windows: die Firewall arbeitet auf diesem Rechner mit `DefaultOutboundAction = Block`.
  Jede neue Java-Installation braucht daher eine eigene Outbound-Allow-Regel,
  sonst schlagen alle Gradle-Downloads mit `SocketException: Permission denied: getsockopt` fehl.

## Entwickeln

Alle Kommandos im Projektverzeichnis, `gradlew` statt eines global installierten Gradle.

```bash
./gradlew build          # kompilieren + JAR bauen
./gradlew runClient      # Minecraft-Client mit der Mod starten
./gradlew runServer      # dedizierten Server starten
./gradlew runData        # Data Generators -> src/generated/resources
./gradlew runGameTestServer   # GameTests ausführen, danach beenden
```

Der erste Lauf dauert lange: Gradle-Distribution, NeoForge und Minecraft werden
geladen und dekompiliert.

### Dev-Umgebung

`runClient` startet mit zwei Komfort-Mods, die **nicht** im ausgelieferten JAR landen
(`localRuntime`, siehe `build.gradle`):

* **JEI** – Rezept-/Item-Browser, Nachfolger von NEI.
* **Jade** – Tooltip für anvisierte Blöcke und Entities, Nachfolger von Waila/Hwyla.

Versionen stehen in `gradle.properties` (`jei_version`, `jade_version_id`).
Weitere Dev-Mods lassen sich als `localRuntime "maven.modrinth:<slug>:<version-id>"`
ergänzen; die Version-ID steht in der Modrinth-URL bzw. in der API.

### IDE

IntelliJ IDEA: Projektverzeichnis als Gradle-Projekt öffnen, Import abwarten.
Die Run Configurations (`runClient` usw.) erzeugt ModDevGradle beim Sync automatisch.

## Die Mod im normalen Launcher testen

Für einen Test außerhalb der Dev-Umgebung – etwa in einer CurseForge-Instanz:

1. `./gradlew build`
2. Das Ergebnis liegt unter `build/libs/moleverse-1.21.11-<version>.jar`.
   Die Datei mit dem Suffix `-sources.jar` wird **nicht** gebraucht.
3. In CurseForge eine Instanz mit **Minecraft 1.21.11 / NeoForge** anlegen.
4. Instanz → *Open Folder* → JAR nach `mods/` kopieren.
5. Instanz starten.

Der Weg über `runClient` ist im Alltag schneller: kein JAR-Export, kein Launcher,
Änderungen an Ressourcen greifen teilweise ohne Neustart.

## Projektstruktur

```
src/main/java/net/sgeht/moleverse/
├── Moleverse.java          Einstiegspunkt (@Mod), Mod-ID, Logger, id()-Helper
├── MoleverseClient.java    Client-Einstiegspunkt (@Mod dist=CLIENT)
├── config/                 ModConfigSpec-Definitionen
├── registry/               DeferredRegister je Registry-Typ, gebündelt in ModRegistries
├── event/                  Handler am NeoForge-Game-Bus
├── tag/                    TagKey-Konstanten
├── block/ item/ entity/    Eigene Block-, Item- und Entity-Klassen
├── worldgen/ dimension/    Weltgenerierung und eigene Dimension
├── client/                 Renderer, Screens, clientseitige Events
├── network/                Netzwerk-Payloads
├── data/                   Data Generators
└── util/                   Hilfsklassen

src/main/resources/         handgepflegte Assets und Daten
src/generated/resources/    von runData erzeugt, wird mit eingepackt
src/main/templates/         neoforge.mods.toml mit ${...}-Platzhaltern
```

## Versionen anheben

Minecraft-, NeoForge- und Parchment-Version stehen ausschließlich in
`gradle.properties`. Ein Port beginnt dort und arbeitet sich dann durch die
Compilerfehler.

## Lizenz

[LGPL-3.0-or-later](LICENSE) - dieselbe Lizenz wie Applied Energistics 2.

Konkret: Der Quelltext ist offen. Wer die Mod forkt und weitergibt, muss den
Fork ebenfalls unter LGPL stellen und die Urheberschaft nennen. Andere Mods
duerfen Moleverse als Dependency einbinden, ohne selbst LGPL zu werden.

`LICENSE` enthaelt den LGPL-3.0-Text gefolgt vom GPL-3.0-Text, auf den die
LGPL verweist. Die Urheberangabe steht in `NOTICE`.

# Moleverse

A Minecraft mod about moles: their tunnels, their craft, and a world beneath the world.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Mod loader | NeoForge 21.11.45 |
| Java | 21 |
| Build | Gradle 9.2.1 + ModDevGradle 2.0.144 |
| Mod id | `moleverse` |
| Package | `net.sgeht.moleverse` |

## Requirements

* JDK 21 (Temurin recommended). Verify with `java -version`.
* Windows: if the firewall runs with `DefaultOutboundAction = Block`, every new
  Java installation needs its own outbound allow rule. Without it, all Gradle
  downloads fail with `SocketException: Permission denied: getsockopt`.

## Development

Run everything from the project directory using `gradlew` rather than a globally
installed Gradle.

```bash
./gradlew build          # compile and build the jar
./gradlew runClient      # start the Minecraft client with the mod
./gradlew runServer      # start a dedicated server
./gradlew runData        # run the data generators -> src/generated/resources
./gradlew runGameTestServer   # run the game tests, then exit
```

The first run takes a while: the Gradle distribution, NeoForge and Minecraft are
downloaded and decompiled.

### Development environment

`runClient` starts with two convenience mods that are **not** part of the shipped
jar (`localRuntime`, see `build.gradle`):

* **JEI** - recipe and item browser, successor to NEI.
* **Jade** - tooltip for the block or entity you are looking at, successor to Waila.

Their versions live in `gradle.properties` (`jei_version`, `jade_version_id`).
Further development mods can be added as
`localRuntime "maven.modrinth:<slug>:<version-id>"`; the version id is part of
the Modrinth URL and of the API response.

### IDE

IntelliJ IDEA: open the project directory as a Gradle project and let the import
finish. ModDevGradle creates the run configurations (`runClient` and friends)
during the sync.

## Testing the mod in a normal launcher

To test outside the development environment, for example in a CurseForge instance:

1. `./gradlew build`
2. The result is `build/libs/moleverse-1.21.11-<version>.jar`.
   The file ending in `-sources.jar` is not needed.
3. Create an instance with **Minecraft 1.21.11 / NeoForge**.
4. Instance -> *Open Folder* -> copy the jar into `mods/`.
5. Start the instance.

Day to day, `runClient` is faster: no jar export, no launcher, and some resource
changes apply without a restart.

## Project layout

```
src/main/java/net/sgeht/moleverse/
├── Moleverse.java          entry point (@Mod), mod id, logger, id() helper
├── MoleverseClient.java    client entry point (@Mod dist=CLIENT)
├── config/                 ModConfigSpec definitions
├── registry/               one DeferredRegister per registry type, bundled in ModRegistries
├── event/                  handlers on the NeoForge game bus
├── tag/                    TagKey constants
├── block/ item/ entity/    custom block, item and entity classes
├── worldgen/ dimension/    world generation and the mod's own dimension
├── client/                 renderers, screens, client-side events
├── network/                network payloads
├── data/                   data generators
└── util/                   helpers

src/main/resources/         hand-written assets and data
src/generated/resources/    produced by runData, shipped in the jar
src/main/templates/         neoforge.mods.toml with build-time placeholders
```

## Bumping versions

The Minecraft, NeoForge and Parchment versions live in `gradle.properties` and
nowhere else. A port starts there and then works through the compiler errors.

## Licence

[LGPL-3.0-or-later](LICENSE) - the same licence Applied Energistics 2 uses.

In practice: the source is open. Anyone who forks the mod and redistributes it
must place the fork under the LGPL as well and credit the authorship. Other mods
may depend on Moleverse without becoming LGPL themselves.

`LICENSE` contains the LGPL-3.0 text followed by the GPL-3.0 text it refers to.
The copyright notice is in `NOTICE`.

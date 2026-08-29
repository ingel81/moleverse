# Testing behaviour without playing

The colony mechanic's failure mode is nothing happening: an animal decides not
to act, the decision is invisible, and the world looks calm. Every mechanic this
mod has added has that shape, and the ones still to come will too - dispersal,
breeding, the exchange station's trades.

This describes the equipment for finding that class of bug without a person
watching. It exists and has found four real ones; the last section says what it
has not covered.

## The rule it is built under

**A test instrument may not change the game it measures.** Four tiers of risk,
and everything here sits in one of them deliberately:

| Risk | Shape | Examples |
|---|---|---|
| None | Gated behind a system property no shipped game sets | `moleverse.devPublish`, `moleverse.devLogging` |
| None | Outside the mod entirely - vanilla commands typed at a console | `/forceload`, `/summon`, `tools/soak` |
| Low | Code that ships but only runs when a player asks | the `/moleverse` debug tree |
| **Unacceptable** | Anything on a hot path that runs unasked in a played game | a counter incremented on every refusal |

The system property is the pattern to reach for first. `moleverse.devPublish`
already opens the dev client to LAN with cheats and a played game has never seen
it; `moleverse.devLogging` now does the same for the mole log. A property that is
absent costs one `Boolean.getBoolean` at class initialisation and nothing after.

**Extra mods are not a way round this.** FTB Chunks was considered for the forced
loading and rejected: vanilla `/forceload` already takes a ticket at the level
that ticks entities, so it buys nothing, and it hooks the very chunk loading a
soak run exists to exercise.

## Tier 1 and 2 - game tests

`./gradlew runGameTestServer` runs headless and finishes in ten seconds. Six
tests today, declared in `test/ModGameTests.java` with the bodies in
`test/BurrowGameTests.java`, all registered `REQUIRED` so a failure comes back as
a non-zero exit code.

Every one is a one-tick test of pure logic: geometry round trips, codec round
trips, the carver against a hand-built block of deep earth.

**A decision is testable this way. A journey is not.** The runner force loads
only the chunks its structure touches, and the structure is vanilla's 1x1x1
`empty`. Every carver write is guarded by `isLoaded`, so a write into an unloaded
chunk does not fail - it silently does nothing and the test passes for the wrong
reason. That rule is in the `BurrowGameTests` class doc and it is what caps this
tier.

Below that cap sits everything a mole *decides*, and it needs no new hooks:
`MoleBurrowGoal.forceBurrow(Consumer<String>)` is public and reports its outcome
as a string, because `/moleverse mole burrow` needs exactly that. A fixture can
place diggable ground, spawn a mole, found a colony in the store at a chosen
distance, force an attempt, and assert on the refusal.

*Not written yet.* The bugs so far came from tier 3.

## Tier 3 - a world left running

```
tools/soak/soak.sh tools/soak/colonies.commands
```

Wipes the world, writes `server.properties`, starts a headless dedicated server,
feeds it the scenario, saves the log to `run/logs/soak-<stamp>.log` and prints a
summary. About two minutes for two hours of game time.

A scenario is a text file of vanilla console commands. `@wait <marker>` blocks
until that text appears in the log.

Four facts make it work, each of which cost a failed run to learn:

**Forced chunks tick entities with nobody logged in.** `/forceload` takes a
`TicketType.FORCED` ticket at level 15; `ChunkLevel.ENTITY_TICKING_LEVEL` is 31
and `isEntityTicking` is `level <= 31`.

**An empty server otherwise stops dead.** `pause-when-empty-seconds` defaults to
60, and `MinecraftServer.tickServer` then returns before `tickChildren` - nothing
ticks at all. The script forces it to 0.

**`/tick sprint` really is equivalent to waiting.** It runs ticks as fast as the
CPU allows; measured at 3000-6000 per second, so an hour of game time takes ten
to twenty seconds. Nothing in this mod reads a clock - `currentTimeMillis`,
`nanoTime` and `Instant` do not appear in `src/main/java` at all, and every
duration is a multiple of `TICKS_PER_SECOND`. The day a wall-clock read is added,
this stops being true and only there.

**Nothing spawns by itself.** `NaturalSpawner` takes `getNearestPlayer` and puts
everything below it inside `if (player != null)`. Scenarios summon what they
need, which is the only way to get the same starting state twice anyway.

And one that is not about Minecraft: **Gradle does not connect stdin.** A
`JavaExec` task gets an empty standard input unless told otherwise, so every
console command was swallowed with no error at all. `build.gradle` sets
`standardInput = System.in` for `runServer`.

### Writing a scenario

* **Wipe the world, and check that it worked.** A dedicated server keeps its
  world in `run/<level-name>`, *not* `run/saves` - that is the client's path.
  Deleting the wrong one leaves the previous colonies in place, and the run then
  produces forty thousand plausible lines that mean nothing. The script deletes
  the right one and aborts if it is still there afterwards.
* **Check the starting state before summoning, not after.** Moles found a colony
  within seconds of being placed, so a `colony list` after the summons already
  shows colonies and proves nothing.
* **Load more ground than the run can cross.** A mole that walks out of the
  ticking area freezes where it stands. In the first run five of six did exactly
  that and piled up against the boundary at z=126; the log then describes the
  edge of the loaded square rather than the mod.
* **Use flat ground unless terrain is the subject.** The first run used a default
  world, put five moles on a mountainside where nothing is diggable, and measured
  geography.
* **Place animals at fixed coordinates chosen for the thresholds under test**,
  not with `spreadplayers`. The colony scenario puts group B 150 blocks from
  group A because `COLONY_EXTENT` is 64 and `COLONY_MIN_SEPARATION` is 224, so
  150 is squarely in the unclaimed band.

## What the runs found

* **A mole in the unclaimed band could not leave it.** `MoleEmigrateGoal.canUse`
  returned false when the mole belonged to no colony, and the band is exactly
  that ground. Measured before the fix: one mole, 114 refusals, seven and a half
  minutes of pacing. After: one refusal, then it walks.
* **An emigrating mole stopped on the line and drifted back.** Arrival was
  `isFreeGround`, true at exactly the minimum separation, which is a line rather
  than a place. Seventeen further refusals after "arriving".
* **The log said `leaving a full colony` for moles that were in no colony.**
* **`EMIGRATION_MARGIN` never applied.** Aiming at separation + 48, founding at
  separation, short by exactly 48 every time - because `ColonyStore.found`
  accepts at the lower threshold and `MoleBurrowGoal` asks it every three
  seconds, while arrival is checked only on the walk. Colony spacing comes from
  `COLONY_MIN_SEPARATION` alone.

## What is still open

* **A mole reaches a known mound in 5% of attempts.** 454 approaches, 22
  arrivals, 431 given up, with 430 `path to the entry mound exhausted`
  recoveries. Seen only in the first run, where mounds were packed together on
  uneven ground; flat terrain hides it because the mole always digs where it
  stands. Needs a scenario that reproduces the crowding.
* **`ground is not diggable` does not name the block.** 1373 refusals in one run
  and no way to tell what was underfoot: `passesGuards` refuses at
  `MoleBurrowGoal:290`, and `BurrowLog.wanted` writes the block name at 299.
* **Dispersal from a full colony is untested.** No colony has reached
  `NETWORK_MAX_MEMBERS`, so only the band half of `MoleEmigrateGoal` has run.
* **A refusal histogram.** The bugs above were found by counting log lines by
  hand; 245 of 520 attempts refused for one reason is the signal, and no
  assertion phrases it. `BurrowLog.refused` is the single choke point every
  refusal passes, and a counter placed *after* its `if (off()) return;` runs
  exactly when the log line beside it runs - no second switch, and nothing in a
  played game.

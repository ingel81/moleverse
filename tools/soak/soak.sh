#!/usr/bin/env bash
#
# Runs a scenario against a headless dedicated server and keeps the log.
#
#   tools/soak/soak.sh tools/soak/colonies.commands
#
# Why this works without a player:
#
#   * /forceload takes a TicketType.FORCED ticket at level 15, and
#     ChunkLevel.ENTITY_TICKING_LEVEL is 31, so forced chunks tick entities
#     with nobody logged in.
#   * pause-when-empty-seconds must be 0. The default of 60 stops the tick loop
#     before tickChildren, so an empty server simulates precisely nothing.
#   * /tick sprint runs ticks as fast as the CPU allows. Everything in this mod
#     counts ticks rather than wall clock - grep the sources for
#     currentTimeMillis and nanoTime, both are absent - so a sprint is
#     equivalent to waiting, only about three hundred times quicker.
#   * Natural spawning needs a player: NaturalSpawner puts everything under
#     `if (player != null)`. Scenarios summon what they need, which is also the
#     only way to get the same starting state twice.
#
# The server reads its console from stdin, and Gradle does not connect the
# standard input of a JavaExec task by default. build.gradle sets
# standardInput for runServer; without that line every command here is
# swallowed silently and the run looks fine.

set -euo pipefail

SCENARIO="${1:-tools/soak/colonies.commands}"
LEVEL="${SOAK_LEVEL:-soak}"

cd "$(dirname "$0")/../.."
[ -f "$SCENARIO" ] || { echo "no such scenario: $SCENARIO" >&2; exit 1; }

export JAVA_HOME="${JAVA_HOME:-C:/Users/joerg/.jdks/jdk-21.0.12.1+1}"

# A dedicated server keeps its world in run/<level-name>, not in run/saves -
# that is the client's path. Deleting the wrong one leaves the previous run's
# colonies in place and the next run silently builds on them.
rm -rf "run/$LEVEL"
[ -d "run/$LEVEL" ] && { echo "could not delete run/$LEVEL - is a server still running?" >&2; exit 1; }
rm -f run/logs/latest.log

mkdir -p run
python - "$LEVEL" <<'PY'
import sys, pathlib
level = sys.argv[1]
path = pathlib.Path("run/server.properties")
wanted = {
    "level-name": level,
    "level-type": "minecraft\\:flat",
    # Flat ground everywhere, so a run measures behaviour instead of geography.
    # Sixty blocks of dirt because moles burrow several blocks down.
    "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},'
                          '{"block":"minecraft:dirt","height":60},'
                          '{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
    "pause-when-empty-seconds": "0",
    "online-mode": "false",
    "spawn-monsters": "false",
    "spawn-protection": "0",
    "sync-chunk-writes": "false",
}
lines = path.read_text(encoding="latin-1").splitlines() if path.exists() else []
seen = set()
out = []
for line in lines:
    key = line.split("=", 1)[0]
    if key in wanted:
        out.append(f"{key}={wanted[key]}")
        seen.add(key)
    else:
        out.append(line)
out += [f"{k}={v}" for k, v in wanted.items() if k not in seen]
path.write_text("\n".join(out) + "\n", encoding="latin-1")
PY

printf 'eula=true\n' > run/eula.txt

feed () {
    # The server needs to be up before it will read anything.
    for _ in $(seq 1 150); do
        grep -q 'Done (' run/logs/latest.log 2>/dev/null && break
        sleep 2
    done
    sleep 3

    local seen=0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|'#'*) continue ;;
            '@wait '*)
                local marker="${line#@wait }"
                seen=$((seen + 1))
                for _ in $(seq 1 1200); do
                    [ "$(grep -c "$marker" run/logs/latest.log 2>/dev/null || echo 0)" -ge "$seen" ] && break
                    sleep 2
                done
                sleep 3
                ;;
            *)
                echo "$line"
                sleep 1
                ;;
        esac
    done < "$SCENARIO"

    sleep 5
    echo 'stop'
    sleep 25
}

feed | ./gradlew runServer 2>&1

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="run/logs/soak-$STAMP.log"
cp run/logs/latest.log "$OUT"
echo
echo "log: $OUT"
echo
grep -E 'moleverse\.mole/\]: .*(founded colony|unclaimed band|leaving a full colony|reached free ground)' "$OUT" \
    | sed -E 's/.*\]: //' || true
echo
echo "refusals:"
grep -oE 'refused: .*' "$OUT" | sort | uniq -c | sort -rn || echo "  none"

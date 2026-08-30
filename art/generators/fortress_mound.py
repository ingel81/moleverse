"""The mound models: the two field mounds and the fortress over a colony nest.

One recipe for all three - a radial height map on a 1 px grid, cone profile
plus a little hashed noise, smoothed so no cell steps more than one above its
lowest neighbour, then each 1 px layer covered greedily with as few boxes as
possible - because that is the recipe that made the first mounds read as earth
rather than as architecture, and three shapes from one recipe stay siblings.

This file originally built only the fortress; `mole_mound_a` and `_b` came out
of Blockbench via `mound_shapes.py` and were exactly what that pipeline makes:
a cone with its peak on the block's centre pixel. A real molehill has no centre
pixel. So every shape here is asymmetric twice over - the peak sits off centre,
and a second, lower lobe leans against the main cone where the mole kicked
spoil out unevenly - and the smoothing pass is the strict one, no cell more
than one above its *lowest* orthogonal neighbour, which is what breaks the
contour lines into 1 px steps instead of terraces.

The `art/mole_mound_{a,b}.bbmodel` files are historical from here on: the JSON
in `assets/` is written by this script and re-running it is the way to change
a mound. Run `cull_buried_faces.py` on the three outputs afterwards; the
greedy cover leaves every internal face in place and the culler is exact.
"""

import json
import math
import os

SIZE = 16
SEED = 20260829

OUT_DIR = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "..",
    "src", "main", "resources", "assets", "moleverse", "models", "block"))

#: The dials, per shape. `centre` and `lobe_centre` are deliberately never
#: (7.5, 7.5): the half-pixel offsets are the asymmetry, and pushing them
#: further apart than about two pixels makes the lobe read as a second mound
#: rather than as spoil kicked out to one side.
#:
#: `rim` above zero gives the skirt that reaches the block edge - the fortress
#: is built up over time and has buried its own footprint; the field mounds
#: are one night's work and stop short of theirs.
SHAPES = {
    "mole_mound_a": dict(   # the dome: one night's dig, leaning north-east
        peak=5.2, rim=0.0, radius=6.3, power=1.6,
        centre=(8.6, 7.2), lobe_centre=(5.4, 9.8), lobe_radius=3.6, lobe_peak=3.4,
        flat=1.0, noise=1.1, salt=101,
    ),
    "mole_mound_b": dict(   # the patty: rained on once, wider and lower
        peak=3.3, rim=0.0, radius=7.0, power=2.1,
        centre=(7.6, 8.3), lobe_centre=(10.6, 5.9), lobe_radius=4.1, lobe_peak=2.4,
        flat=1.0, noise=0.9, salt=202,
    ),
    "mole_mound_fortress": dict(  # the colony heap: full footprint, flat crown
        peak=9.0, rim=1.2, radius=7.5, power=1.6,
        centre=(8.4, 7.6), lobe_centre=(5.6, 9.7), lobe_radius=4.6, lobe_peak=5.4,
        flat=0.82, noise=1.4, salt=303,
    ),
}


def hash01(x, z, salt):
    h = (x * 374761393 + z * 668265263 + salt * 2246822519 + SEED) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def cone(d, radius, power, flat):
    """Height fraction at distance `d`. `flat` below 1 caps the top into a
    crown: everything inside `flat * radius` is full height."""
    if d >= radius:
        return 0.0
    return max(0.0, 1.0 - min(d / (flat * radius), 1.0) ** power)


def height_map(s):
    grid = [[0] * SIZE for _ in range(SIZE)]
    for z in range(SIZE):
        for x in range(SIZE):
            cx, cz = x + 0.5, z + 0.5
            main = cone(math.hypot(cx - s["centre"][0], cz - s["centre"][1]),
                        s["radius"], s["power"], s["flat"])
            lobe = cone(math.hypot(cx - s["lobe_centre"][0], cz - s["lobe_centre"][1]),
                        s["lobe_radius"], s["power"], 1.0)
            profile = max(main * s["peak"], lobe * s["lobe_peak"])
            if profile <= 0.0 and s["rim"] <= 0.0:
                continue
            h = max(s["rim"], profile)
            h += (hash01(x, z, s["salt"]) - 0.5) * s["noise"]
            grid[z][x] = max(0.0, h)
    # smooth: no cell more than 1 above the lowest orthogonal neighbour
    for _ in range(3):
        for z in range(SIZE):
            for x in range(SIZE):
                lows = [grid[z2][x2] for x2, z2 in ((x-1, z), (x+1, z), (x, z-1), (x, z+1))
                        if 0 <= x2 < SIZE and 0 <= z2 < SIZE]
                if lows:
                    grid[z][x] = min(grid[z][x], min(lows) + 1.0)
    return [[int(round(h)) for h in row] for row in grid]


def greedy_layers(grid):
    top = max(max(row) for row in grid)
    elements = []
    for y in range(top):
        covered = [[grid[z][x] > y for x in range(SIZE)] for z in range(SIZE)]
        for z in range(SIZE):
            x = 0
            while x < SIZE:
                if not covered[z][x]:
                    x += 1
                    continue
                x2 = x
                while x2 + 1 < SIZE and covered[z][x2 + 1]:
                    x2 += 1
                z2 = z
                while z2 + 1 < SIZE and all(covered[z3][x3] for z3 in (z2 + 1,) for x3 in range(x, x2 + 1)):
                    z2 += 1
                for z3 in range(z, z2 + 1):
                    for x3 in range(x, x2 + 1):
                        covered[z3][x3] = False
                elements.append(((x, y, z), (x2 + 1, y + 1, z2 + 1)))
                x = x2 + 1
    return elements


def face(u0, v0, u1, v1):
    return {"uv": [u0, v0, u1, v1], "texture": "#0"}


def element(frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {
            "north": face(16 - x1, 16 - y1, 16 - x0, 16 - y0),
            "south": face(x0, 16 - y1, x1, 16 - y0),
            "east": face(16 - z1, 16 - y1, 16 - z0, 16 - y0),
            "west": face(z0, 16 - y1, z1, 16 - y0),
            "up": face(x0, z0, x1, z1),
            "down": face(x0, 16 - z1, x1, 16 - z0),
        },
    }


def main():
    for name, shape in SHAPES.items():
        grid = height_map(shape)
        boxes = greedy_layers(grid)
        model = {
            "parent": "minecraft:block/block",
            "textures": {"0": "moleverse:block/mole_mound", "particle": "moleverse:block/mole_mound"},
            "elements": [element(f, t) for f, t in boxes],
        }
        out = os.path.join(OUT_DIR, f"{name}.json")
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f, indent=2)
        print(f"{name}: {len(boxes)} boxes, peak {max(max(r) for r in grid)} px -> {out}")


if __name__ == "__main__":
    main()

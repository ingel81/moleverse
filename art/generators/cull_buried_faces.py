"""Drop faces that are completely buried inside the mound.

The models are stacked axis-aligned boxes on a 1 px grid, so a face is invisible
exactly when the neighbouring boxes tile over all of it. Rasterising onto that
same 1 px grid makes the test exact rather than approximate.
"""

import glob
import json
import sys

# face name -> (axis it is perpendicular to, which side of the box it sits on)
FACES = {
    "west":  (0, "from"),
    "east":  (0, "to"),
    "down":  (1, "from"),
    "up":    (1, "to"),
    "north": (2, "from"),
    "south": (2, "to"),
}
OPPOSITE = {"west": "east", "east": "west", "down": "up",
            "up": "down", "north": "south", "south": "north"}


def cells(el, axis):
    """The 1 px cells this box covers on the two axes that are not `axis`."""
    u, v = [a for a in (0, 1, 2) if a != axis]
    out = set()
    for i in range(int(el["from"][u]), int(el["to"][u])):
        for j in range(int(el["from"][v]), int(el["to"][v])):
            out.add((i, j))
    return out


def integral(el):
    return all(float(c) == int(c) for c in el["from"] + el["to"])


def cull(path):
    model = json.load(open(path, encoding="utf-8"))
    elements = model["elements"]

    if not all(integral(el) for el in elements):
        print(f"{path}: not on a whole-pixel grid, left alone")
        return 0, 0

    before = sum(len(el["faces"]) for el in elements)

    for el in elements:
        for face in list(el["faces"]):
            axis, side = FACES[face]
            plane = el[side][axis]
            mine = cells(el, axis)

            # Neighbours whose opposing face sits in this same plane.
            covered = set()
            for other in elements:
                if other is el:
                    continue
                _, other_side = FACES[OPPOSITE[face]]
                if other[other_side][axis] == plane:
                    covered |= cells(other, axis)

            if mine <= covered:
                del el["faces"][face]

    after = sum(len(el["faces"]) for el in elements)
    json.dump(model, open(path, "w", encoding="utf-8"), indent=2)
    return before, after


total_before = total_after = 0
for path in sorted(glob.glob(sys.argv[1])):
    b, a = cull(path)
    total_before += b
    total_after += a
    if b:
        print(f"{path}: {b} -> {a} faces ({100 * (b - a) // b}% culled)")

if total_before:
    print(f"\ntotal: {total_before} -> {total_after} faces")

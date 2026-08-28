import json, math, random, sys

GRID = 16          # 1 px cells, so the steps are as fine as the reference
LIMIT = 8          # tallest allowed

def cone(d, r, h, power):
    if d >= r:
        return 0.0
    return h * (1.0 - (d / r) ** power)

def build(profile, seed, jitter=0.35):
    rng = random.Random(seed)
    hs = [[0] * GRID for _ in range(GRID)]
    for x in range(GRID):
        for z in range(GRID):
            v = profile(x + 0.5, z + 0.5)
            if v <= 0:
                continue
            v += rng.uniform(-jitter, jitter)
            hs[x][z] = max(0, min(LIMIT, int(round(v))))
    # No cell may tower over all of its neighbours.
    for _ in range(2):
        for x in range(GRID):
            for z in range(GRID):
                nb = [hs[x + dx][z + dz] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                      if 0 <= x + dx < GRID and 0 <= z + dz < GRID]
                if nb and hs[x][z] > max(nb) + 1:
                    hs[x][z] = max(nb) + 1
    return hs

def merge(mask):
    mask = [row[:] for row in mask]
    out = []
    for x in range(GRID):
        for z in range(GRID):
            if not mask[x][z]:
                continue
            w = 1
            while x + w < GRID and mask[x + w][z]:
                w += 1
            d = 1
            while z + d < GRID and all(mask[x + i][z + d] for i in range(w)):
                d += 1
            for i in range(w):
                for j in range(d):
                    mask[x + i][z + j] = False
            out.append((x, z, w, d))
    return out

def elements(hs, tag, dx):
    els = []
    top = max(max(row) for row in hs)
    for h in range(1, top + 1):
        mask = [[hs[x][z] >= h for z in range(GRID)] for x in range(GRID)]
        for i, (x, z, w, d) in enumerate(merge(mask)):
            els.append({"name": f"{tag}_{h}_{i}",
                        "from": [x + dx, h - 1, z],
                        "to": [x + w + dx, h, z + d],
                        "origin": [8 + dx, 0, 8]})
    return els

def dist(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)

profiles = [
    ("A_dome",   lambda x, z: cone(dist(x, z, 8, 8), 6.2, 5.0, 1.6), 11),
    ("B_flat",   lambda x, z: cone(dist(x, z, 8, 8), 7.2, 3.0, 1.9), 22),
    ("C_steep",  lambda x, z: cone(dist(x, z, 8, 8), 5.2, 6.5, 2.1), 33),
    ("D_twin",   lambda x, z: max(cone(dist(x, z, 6.5, 7.0), 4.6, 5.0, 1.7),
                                  cone(dist(x, z, 10.5, 9.5), 3.9, 3.5, 1.7)), 44),
]

def crater(x, z):
    d = dist(x, z, 8, 8)
    hole, r, h = 2.4, 6.6, 4.0
    if d < hole:
        return 0.0
    if d >= r:
        return 0.0
    t = (d - hole) / (r - hole)
    return h * (1.0 - t ** 1.7)

profiles.append(("E_crater", crater, 55))

all_els = {}
for idx, (tag, prof, seed) in enumerate(profiles):
    hs = build(prof, seed)
    els = elements(hs, tag, idx * 20)
    all_els[tag] = els
    print(f"{tag}: {len(els)} elements", file=sys.stderr)
    for row in hs:
        print("".join(str(v) if v else "." for v in row), file=sys.stderr)
    print(file=sys.stderr)

print(json.dumps([e for els in all_els.values() for e in els]))

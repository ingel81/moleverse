import random
from PIL import Image

# Freshly turned earth: darker and coarser than vanilla dirt, so a mound
# reads against the grass block it sits on.
PALETTE = [
    (0x33, 0x24, 0x18),  # deepest shadow between clods
    (0x3F, 0x2C, 0x1D),
    (0x4B, 0x36, 0x23),
    (0x58, 0x40, 0x2A),  # base tone
    (0x66, 0x4B, 0x32),
    (0x74, 0x57, 0x3A),  # lit face of a clod
    (0x85, 0x66, 0x47),  # dry crumb catching the light
]

SIZE = 16
rng = random.Random(20260828)

img = Image.new("RGBA", (SIZE, SIZE))
px = img.load()

# Start from a mid tone with per-pixel noise.
for y in range(SIZE):
    for x in range(SIZE):
        px[x, y] = PALETTE[rng.choice([2, 3, 3, 3, 4])] + (255,)

# Clods: small blobs of a lighter tone with a shadow along their lower edge.
for _ in range(14):
    cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
    w, h = rng.choice([(2, 2), (3, 2), (2, 3), (3, 3)])
    tone = rng.choice([4, 5, 5, 6])
    for dy in range(h):
        for dx in range(w):
            if rng.random() < 0.15:
                continue
            px[(cx + dx) % SIZE, (cy + dy) % SIZE] = PALETTE[tone] + (255,)
    for dx in range(w):
        px[(cx + dx) % SIZE, (cy + h) % SIZE] = PALETTE[rng.choice([0, 1])] + (255,)

# A few dark pockets: gaps between the clods.
for _ in range(10):
    cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
    px[cx, cy] = PALETTE[0] + (255,)
    if rng.random() < 0.5:
        px[(cx + 1) % SIZE, cy] = PALETTE[1] + (255,)

# Single bright crumbs on top, sparse enough not to read as noise.
for _ in range(6):
    px[rng.randrange(SIZE), rng.randrange(SIZE)] = PALETTE[6] + (255,)

img.save("D:/ai_local/minecraft_modding/moleverse/art/mole_mound.png")
print("written")

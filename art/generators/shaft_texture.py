import random
from PIL import Image

# The floor of the shaft: earth in shadow, several shades below the mound
# itself, so the hole reads as depth rather than as a dent.
PALETTE = [
    (0x0D, 0x09, 0x06),
    (0x14, 0x0E, 0x09),
    (0x1B, 0x13, 0x0C),
    (0x23, 0x19, 0x10),
    (0x2B, 0x1F, 0x14),
]

SIZE = 16
rng = random.Random(90210)

img = Image.new("RGBA", (SIZE, SIZE))
px = img.load()

for y in range(SIZE):
    for x in range(SIZE):
        px[x, y] = PALETTE[rng.choice([1, 2, 2, 3])] + (255,)

# A few faint clods so the floor is not a flat block of colour.
for _ in range(10):
    cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
    for dx, dy in ((0, 0), (1, 0), (0, 1)):
        px[(cx + dx) % SIZE, (cy + dy) % SIZE] = PALETTE[4] + (255,)

for _ in range(14):
    px[rng.randrange(SIZE), rng.randrange(SIZE)] = PALETTE[0] + (255,)

img.save("D:/ai_local/minecraft_modding/moleverse/art/mole_mound_shaft.png")
print("written")

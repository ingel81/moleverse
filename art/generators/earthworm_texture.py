"""The earthworm item texture, 16x16 on transparent ground.

The body is a hand-placed centre line rather than a formula: at sixteen pixels
a curve has to be chosen pixel by pixel to read as a worm, and a parametric arc
came out looking like a hook.

Shading is derived from the silhouette, not from offset copies of the line. A
body pixel with nothing above or to its left catches the light; one with nothing
below or to its right falls into shadow. That keeps the highlight on the outline
where it belongs instead of eating into the middle.
"""

from PIL import Image

OUT = "D:/ai_local/minecraft_modding/moleverse/art/earthworm.png"
SIZE = 16

SHADE = (0x5E, 0x30, 0x35, 255)
BODY = (0x9E, 0x5C, 0x5C, 255)
LIT = (0xC4, 0x81, 0x79, 255)
BAND = (0xE0, 0xA8, 0x9E, 255)

# Head top right, tail bottom left, in the loose S an earthworm falls into at
# rest. Four-connected on purpose - a diagonal step would leave a gap once the
# line is thickened.
SPINE = [
    (11, 3), (10, 3), (9, 3),
    (9, 4), (8, 4), (8, 5), (7, 5),
    (7, 6), (7, 7), (6, 7), (6, 8), (5, 8),
    (5, 9), (4, 9), (4, 10), (4, 11),
    (5, 11), (6, 11), (6, 12), (7, 12), (8, 12), (9, 12),
    (10, 12), (10, 11),
]

# The clitellum, about a third down from the head, as on a real worm.
BAND_FROM, BAND_TO = 6, 10

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
px = img.load()


def square(x, y):
    """The 2x2 block one spine pixel occupies."""
    return {(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)}


body = set()
for x, y in SPINE:
    body |= square(x, y)
body = {(x, y) for x, y in body if 0 <= x < SIZE and 0 <= y < SIZE}

band = set()
for x, y in SPINE[BAND_FROM:BAND_TO]:
    band |= square(x, y)

for x, y in sorted(body):
    lit = (x - 1, y) not in body or (x, y - 1) not in body
    shaded = (x + 1, y) not in body or (x, y + 1) not in body

    if (x, y) in band:
        colour = BAND
    elif shaded and not lit:
        colour = SHADE
    elif lit:
        colour = LIT
    else:
        colour = BODY
    px[x, y] = colour

img.save(OUT)
print("written", OUT, "-", len(body), "opaque pixels")

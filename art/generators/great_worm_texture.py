"""The great worm's entity texture, 128x128.

Painted per texel rather than per face. Every pixel of every face is projected
back into model space first, and the colour is then a function of where that
point sits on the animal: how far down the body, how high up its flank, which
way the surface points. That is what keeps the annuli continuous across a
segment boundary and the clitellum a straight band rather than a stack of
slightly offset ones - two things that are near impossible to keep aligned when
ten boxes are painted one at a time.

The face rectangles come from `great_worm_shape.box_faces`, the same function
that packs the atlas the model is built from, so the texture cannot be painted
for a layout the geometry no longer has. That rule was read out of the running
editor rather than guessed: the box unwrap reverses the `up` face on both axes
and `down` on one, and a wrong guess there mirrors the body gradient on the most
visible face of the animal.

Pigment only, no lighting. Minecraft shades the six directions itself; a baked
highlight on the back fights it. What is baked is what an earthworm is actually
coloured like: a dark red-brown dorsal side with the blood vessel showing
through as a line down the middle, a pale ventral side, the ring joints between
segments, and the smooth swollen clitellum a third of the way down.

Palette continues `earthworm_texture.py`, so the animal in the burrow and the
item a mole is after read as the same creature.
"""

import random

from PIL import Image

import great_worm_shape as shape

OUT = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/entity/great_worm.png"

# --- palette --------------------------------------------------------------

DORSAL = (0x6B, 0x36, 0x3B)
VESSEL = (0x46, 0x22, 0x2A)
FLANK = (0x9E, 0x5C, 0x5C)
VENTRAL = (0xC9, 0x94, 0x8C)
#: Warm buff, only a little lighter than the flank. The item texture's band
#: colour was tried here first and read as a bandage: on a 16 px item the band
#: has to shout to register at all, on a four block animal it is the brightest
#: thing on the model and the eye goes nowhere else.
CLITELLUM = (0xBE, 0x87, 0x77)
#: The head end is darker and browner, the tail end paler and greyer. Both are
#: mixed into the flank colour rather than replacing it.
HEAD_TINT = (0x5A, 0x2E, 0x30)
TAIL_TINT = (0xB6, 0x8E, 0x8A)

#: Distance between two ring joints, in model units.
RING_PITCH = 3

#: Where the clitellum sits along the body, as a fraction of the total length.
CLITELLUM_FROM, CLITELLUM_TO = 0.26, 0.40

CUBES, SIZE, TEXTURE_HEIGHT = shape.layout()

Z_FRONT = min(c["from"][2] for c in CUBES)
Z_BACK = max(c["to"][2] for c in CUBES)


# --- helpers --------------------------------------------------------------

def lerp(a, b, u):
    return a + (b - a) * u


def mix(c1, c2, u):
    u = max(0.0, min(1.0, u))
    return tuple(lerp(c1[i], c2[i], u) for i in range(3))


def shade(colour, factor):
    return tuple(max(0.0, min(255.0, c * factor)) for c in colour)


def smooth(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


def unproject(cube, face, px, py):
    """Model-space point a texel of one face stands for.

    `s` runs along the rectangle's u axis and `t` along its v, both taken with
    the rectangle's own sign so a flipped face maps back correctly. The six
    cases are the standard Minecraft box unwrap: walking east, north, west,
    south is one rotation about Y, and v always runs from the top of the box
    downwards.
    """
    u1, v1, u2, v2 = cube["faces"][face]
    s = (px + 0.5 - u1) / (u2 - u1)
    t = (py + 0.5 - v1) / (v2 - v1)
    x0, y0, z0 = cube["from"]
    x1, y1, z1 = cube["to"]

    if face == "north":
        return lerp(x1, x0, s), lerp(y1, y0, t), z0
    if face == "south":
        return lerp(x0, x1, s), lerp(y1, y0, t), z1
    if face == "east":
        return x1, lerp(y1, y0, t), lerp(z1, z0, s)
    if face == "west":
        return x0, lerp(y1, y0, t), lerp(z0, z1, s)
    if face == "up":
        return lerp(x0, x1, s), y1, lerp(z0, z1, t)
    return lerp(x0, x1, s), y0, lerp(z1, z0, t)


def surface(cube, face, x, y, z):
    """Colour of the animal's skin at one point in model space.

    `face` only decides how much of the point counts as back or belly. The
    pattern itself is a function of position, which is the whole reason the
    rings survive a segment boundary.
    """
    height = cube["to"][1] - cube["from"][1]
    along = (z - Z_FRONT) / (Z_BACK - Z_FRONT)

    # How far up the flank, 0 at the belly and 1 along the spine. The two
    # horizontal faces are the extremes outright; the sides read it off y.
    if face == "up":
        up = 1.0
    elif face == "down":
        up = 0.0
    else:
        up = (y - cube["from"][1]) / max(height, 1)

    colour = mix(VENTRAL, FLANK, smooth(up / 0.62))
    colour = mix(colour, DORSAL, smooth((up - 0.55) / 0.45))

    # Head and tail tints, both narrow enough to leave the middle alone.
    colour = mix(colour, HEAD_TINT, 0.55 * smooth((0.16 - along) / 0.16))
    colour = mix(colour, TAIL_TINT, 0.50 * smooth((along - 0.68) / 0.32))

    # The dorsal blood vessel, showing through as a line down the back. Only
    # on the upper surface, and it fades out over the clitellum like the rings.
    band = smooth((along - CLITELLUM_FROM) / 0.04) * smooth((CLITELLUM_TO - along) / 0.05)
    vessel = smooth((up - 0.80) / 0.20) * smooth((2.0 - abs(x)) / 1.5)
    colour = mix(colour, VESSEL, 0.75 * vessel * (1.0 - band))

    # The ring joints. A hard line on the ring itself, a slight lift just after
    # it, so each segment reads as rounded rather than as a stripe.
    phase = (z - Z_FRONT) % RING_PITCH
    ring = 1.0 - smooth(abs(phase - 0.5) / 0.9)
    lift = smooth((phase - 1.4) / 0.8) * smooth((2.6 - phase) / 0.8)
    colour = shade(colour, 1.0 - 0.16 * ring * (1.0 - 0.7 * band) + 0.07 * lift)

    # The clitellum: swollen, smooth and paler, the one part of a worm with no
    # visible ring joints at all. Strongest along the back and fading out down
    # the flanks, because a band of even weight all the way round reads as a
    # painted stripe rather than as a swelling.
    colour = mix(colour, CLITELLUM, 0.55 * band * (0.30 + 0.70 * up))

    return colour


def paint():
    img = Image.new("RGBA", (SIZE, TEXTURE_HEIGHT), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(20260829)

    for cube in CUBES:
        for face in ("north", "east", "south", "west", "up", "down"):
            u1, v1, u2, v2 = cube["faces"][face]
            for y in range(min(v1, v2), max(v1, v2)):
                for x in range(min(u1, u2), max(u1, u2)):
                    r, g, b = surface(cube, face, *unproject(cube, face, x, y))
                    # A little grain. Enough to break the flat fill, small
                    # enough not to read as noise at the distance the worm is
                    # actually seen from.
                    n = rng.uniform(-6.0, 6.0)
                    px[x, y] = (
                        int(max(0, min(255, r + n))),
                        int(max(0, min(255, g + n))),
                        int(max(0, min(255, b + n))),
                        255,
                    )
    return img


if __name__ == "__main__":
    paint().save(OUT)
    print("wrote", OUT)

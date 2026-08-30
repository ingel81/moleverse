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
visible face of the animal. Nothing in this file touches the layout - the UV
rectangles, the packing and the canvas size are `great_worm_shape`'s and stay
exactly as the Java model in `client/render/GreatWormModel.java` expects them.

## Colour is an index, not a mix

This started out mixing colours continuously and adding per-texel grain, and it
came out at 3965 distinct colours across the atlas - effectively a photograph.
Next to a mod whose blocks run to seven colours each, the worm read as if it
had come from a different game.

So the surface function returns a position on `texture_kit.WORM`, not a colour.
Every field - flank height, head and tail tint, ring joints, the blood vessel -
adds or subtracts levels on that one scalar, and the result is rounded once at
the end. Banding is the point: rounding a smooth field is exactly how a hand
painter picks the next darkest swatch, and it puts hard edges where a mix would
have put a fade. The grain is gone with it, because a random half-step per texel
turns a clean band edge back into a dither.

Two consequences worth knowing. The ramp is shared with the item textures, so
the animal in the burrow and the worm a mole carries off are literally the same
colours. And the clitellum is the one thing that replaces the colour outright
rather than shifting the level, because a swollen band is a different material
and not a lighter shade of skin.

Pigment only, no lighting. Minecraft shades the six directions itself; a baked
highlight on the back fights it.
"""

from PIL import Image

import great_worm_shape as shape
from texture_kit import WORM, WORM_CLITELLUM, smooth

OUT = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/entity/great_worm.png"

#: Distance between two ring joints, in model units.
RING_PITCH = 3

#: How wide the dark part of a joint is, in the same units. One unit of three
#: leaves two units of segment between rings, which is what makes the body read
#: as segmented rather than as striped.
RING_WIDTH = 1.0

#: Where the painted clitellum band sits along the body: exactly under the
#: geometry saddle on segment five. The band is buried by the saddle, but a
#: rule that quietly depends on another box hiding its output is a trap for
#: the next resize, so the two are kept aligned rather than one deleted.
CLITELLUM_FROM, CLITELLUM_TO = 0.35, 0.40

#: How far up the flank the clitellum reaches. A band of even weight all the
#: way round reads as a painted stripe rather than as a swelling.
CLITELLUM_FOOT = 0.28

#: The wet reflection: a hard band riding the upper flank, promoted from
#: variant C of the comparison sheet. Below the spine, not on it - the spine
#: faces the sky, the reflection faces the viewer - and hard-edged, because
#: at one texel per model unit a soft gleam is a smudge. It stacks on the
#: ramp like every other effect and rounds once at the end, so it costs no
#: new colours.
SHEEN_UP = (0.66, 0.84)
SHEEN = 1.4

CUBES, SIZE, TEXTURE_HEIGHT = shape.layout()

Z_FRONT = min(c["from"][2] for c in CUBES)
Z_BACK = max(c["to"][2] for c in CUBES)


# --- helpers --------------------------------------------------------------

def lerp(a, b, u):
    return a + (b - a) * u


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


def flank_height(cube, face, y):
    """How far up the flank a point sits, 0 at the belly and 1 along the spine.

    The two horizontal faces are the extremes outright; the sides read it off
    y. `face` is the only thing the pattern takes from the geometry - everything
    else is a function of position, which is the whole reason the rings survive
    a segment boundary.
    """
    if face == "up":
        return 1.0
    if face == "down":
        return 0.0
    height = cube["to"][1] - cube["from"][1]
    return (y - cube["from"][1]) / max(height, 1)


def surface(cube, face, x, y, z):
    """Colour of the skin at one point, as (ramp level, override or None).

    The level is a float and is rounded by the caller. Keeping it unrounded
    until the end is what lets four separate effects stack without any of them
    having to know about the others.
    """
    # The mouth is a cavity, not a surface, and a cavity is the bottom of the
    # ramp with nothing painted on it.
    if cube["name"] == "mouth":
        return 0.0, None

    along = (z - Z_FRONT) / (Z_BACK - Z_FRONT)
    up = flank_height(cube, face, y)
    on_clitellum = CLITELLUM_FROM <= along < CLITELLUM_TO

    # The saddle is painted off its own box, not off the band: clitellum
    # colour above the foot, skin below, because a real clitellum is open
    # beneath. The prostomium steps are darker working flesh all over.
    if cube["name"] == "clitellum":
        level = 7.0 - 4.4 * smooth(up)
        if up > CLITELLUM_FOOT:
            return level, WORM_CLITELLUM
        return level, None
    if cube["name"].startswith("prost"):
        return 7.0 - 4.4 * smooth(up) - 1.9, None

    # Pale ventral side at the top of the ramp, dark red-brown dorsal at the
    # bottom of it. Smoothstepped so the belly and the back each get a band of
    # their own rather than the whole flank being one long fade.
    #
    # The back stops two and a half levels short of the bottom rather than at
    # it. Taking the dorsal side all the way down leaves the rings and the
    # blood vessel nowhere to go - both subtract, both clamp, and the whole
    # back comes out as one flat darkest tone with the pattern gone.
    level = 7.0 - 4.4 * smooth(up)

    if SHEEN_UP[0] < up < SHEEN_UP[1]:
        level += SHEEN

    # The head end is darker and browner, the tail end paler and greyer. Both
    # narrow enough to leave the middle of the animal alone.
    level -= 1.1 * smooth((0.16 - along) / 0.16)
    level += 0.9 * smooth((along - 0.68) / 0.32)

    # The ring joints. A hard step rather than a falloff: at one texel per
    # model unit a soft ring is a two-pixel smudge, and the segmentation is the
    # one thing on this animal that has to survive being seen from across a
    # cavern. The clitellum has no rings at all, which is how you tell it from
    # a stripe.
    #
    # A north or south face is a whole cube end at one constant z, so it is
    # either entirely on a ring or entirely off one - and it is also exactly
    # where this segment meets the next. Taking it as a joint outright is both
    # the true answer and the safe one: off a ring it would come out as a pale
    # band around the joint, which is the one place a worm never has one.
    #
    # The raised annuli are the exception: their ends sit mid-bulge by
    # construction, between two joints, so only the z-keyed pattern applies to
    # them - which then runs unbroken from the segment onto the ring and back,
    # because both are functions of the same z.
    annulus = cube["name"].startswith("ring_")
    joint = (not annulus and face in ("north", "south")) \
        or (z - Z_FRONT) % RING_PITCH < RING_WIDTH
    if joint and not on_clitellum:
        level -= 1.6

    # The dorsal blood vessel showing through, along the spine only. Its width
    # is a fraction of the segment's, not a fixed number of units: the tail
    # segments are only four units across, and an absolute stripe swallowed
    # them whole.
    vessel_half = max(1.0, 0.12 * (cube["to"][0] - cube["from"][0]))
    if up > 0.82 and abs(x) < vessel_half and along < 0.85 and not on_clitellum:
        level -= 2.5

    if on_clitellum and up > CLITELLUM_FOOT:
        return level, WORM_CLITELLUM
    return level, None


def paint():
    img = Image.new("RGBA", (SIZE, TEXTURE_HEIGHT), (0, 0, 0, 0))
    px = img.load()

    for cube in CUBES:
        for face in ("north", "east", "south", "west", "up", "down"):
            u1, v1, u2, v2 = cube["faces"][face]
            for y in range(min(v1, v2), max(v1, v2)):
                for x in range(min(u1, u2), max(u1, u2)):
                    level, override = surface(cube, face, *unproject(cube, face, x, y))
                    if override is not None:
                        px[x, y] = override + (255,)
                    else:
                        index = max(0, min(len(WORM) - 1, int(round(level))))
                        px[x, y] = WORM[index] + (255,)
    return img


if __name__ == "__main__":
    paint().save(OUT)
    print("wrote", OUT)

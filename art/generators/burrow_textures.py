"""Block textures for the burrow and the dimension below it.

Nine blocks used to borrow vanilla textures - rooted dirt, mangrove roots,
shroomlight, barrel staves, spruce planks. Each one is a fine texture and
together they made the mod look like a pile of other mods, because none of
them share a palette with the mound the player dug through to get here.

Everything here is generated, not placed pixel by pixel, for the reason the
mound shapes are: the tuning dials are the seed, the ramp and the count, and
regenerating is cheaper than repainting. What is hand-placed is the *layout* -
which rectangle of the image a model face reads - because that is a decision
about the model, not about the picture.

Two kinds of texture come out of this file:

* Full 16x16 faces, for `cube_all` blocks and for the models whose faces carry
  block-shaped UVs (`exchange_station`, `worm_box`). These tile against copies
  of themselves, so every drawing operation wraps.
* Atlases, for the three worked-wood props. A post, its collar and its end
  grain are three different things at three different sizes, and a 16x16 image
  has room for all of them side by side. `ATLASES` below is the authority on
  where each one sits; the `uv` arrays in the model JSONs are copies of it, and
  `--atlas` prints them in JSON order so the two can be checked against each
  other.

Run with no arguments to write every PNG. `--preview` additionally writes a
magnified contact sheet, with the tiling textures shown as a 3x3 wall, because
a seam or a feature that repeats too obviously is invisible at 16 px and
glaring on a corridor wall.
"""

import argparse
import math
import random

from texture_kit import (
    GLOW,
    ROOT,
    SIZE,
    SOIL,
    WOOD,
    WORM_BODY,
    WORM_LIT,
    WORM_PALE,
    WORM_RIM,
    Canvas,
    scatter_points,
    silhouette,
    thicken,
    walk,
    wave,
)

BLOCKS = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/block"

#: Where each model face reads from, for the three atlas textures. Kept here
#: rather than in the drawing code so the model JSONs have one place to agree
#: with. Rectangles are (x0, y0, x1, y1), exactly the Minecraft `uv` order.
ATLASES = {
    "shrink_post": {
        "post_side": (0, 0, 4, 12),
        "post_end": (4, 0, 8, 4),
        "collar_side": (8, 0, 16, 3),
        "collar_top": (8, 4, 16, 12),
    },
    "grunting_post": {
        "stake_side": (0, 0, 4, 13),
        "stake_end": (4, 0, 8, 4),
        "rasp_top": (4, 4, 14, 6),
        "rasp_long": (0, 13, 10, 15),
        "rasp_end": (10, 13, 12, 15),
    },
    "colony_board": {
        "panel_face": (0, 0, 10, 8),
        "panel_edge_v": (10, 0, 12, 8),
        "panel_edge_h": (0, 8, 10, 10),
        "post_side": (12, 0, 14, 8),
        "post_end": (14, 0, 16, 2),
    },
}


# --- shared drawing -------------------------------------------------------

def soil_ground(canvas, rng, weights=(1, 2, 2, 2, 2, 3), clods=9, pockets=7, crumbs=4):
    """The packed earth every underground texture starts from.

    Per-pixel noise alone comes out as static: at 16 px the eye has nothing to
    hold on to and a wall of it fizzes. So the noise is only the ground, and
    the structure is clods - two and three pixel patches a step lighter, each
    with a shadow along its lower edge, exactly as `mound_texture.py` builds
    the mound above. Same earth, one ramp darker.
    """
    canvas.noise((0, 0, SIZE, SIZE), SOIL, list(weights), rng, wrap=True)
    for _ in range(clods):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        w, h = rng.choice([(2, 2), (3, 2), (2, 3), (3, 2)])
        tone = SOIL[rng.choice([3, 3, 4])]
        for dy in range(h):
            for dx in range(w):
                if rng.random() < 0.18:
                    continue
                canvas.put(cx + dx, cy + dy, tone)
        for dx in range(w):
            canvas.put(cx + dx, cy + h, SOIL[rng.choice([0, 1])])
    for _ in range(pockets):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        canvas.put(x, y, SOIL[0])
        if rng.random() < 0.4:
            canvas.put(x + rng.choice([-1, 1]), y, SOIL[1])
    for _ in range(crumbs):
        canvas.put(rng.randrange(SIZE), rng.randrange(SIZE), SOIL[4])


def paint_strand(canvas, body, ramp, rng, nick=0.10):
    """Lay one root down with a dark rim, shaded off its own outline.

    The rim is drawn first and one pixel wider than the root, so a strand
    crossing an earlier one cuts into it with a visible dark edge. That is what
    turns four overlapping walks into a tangle with depth instead of one blob.
    """
    for x, y in body:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if ((x + ox) % SIZE, (y + oy) % SIZE) not in body:
                canvas.put(x + ox, y + oy, ramp[0])
    lit, shaded, interior = silhouette(body)
    # The interior is mottled rather than flat: a three pixel wide root shaded
    # strictly lit / base / shadow comes out as a milled beam, and no amount of
    # bending the path fixes that - the bark has to be in the fill.
    #
    # The shaded side stays two ramp steps above the rim. A curved root is
    # nearly all edge - there is barely any interior left once it bends - so
    # putting the darkest tone on the shaded half leaves the root the same
    # value as the soil it is supposed to be growing through, and it vanishes.
    for x, y in interior:
        canvas.put(x, y, ramp[rng.choice([2, 3, 3, 3])])
    for x, y in shaded:
        canvas.put(x, y, ramp[rng.choice([1, 2, 2])])
    for x, y in lit:
        canvas.put(x, y, ramp[4] if rng.random() < nick else ramp[3])


def plank_grain(canvas, rect, ramp, rng, vertical=True, wrap=False):
    """A worked-wood surface: base tone, a few grain lines, lit and dark edge.

    The lit edge is the top and left of the rectangle and the dark edge the
    bottom and right - the same light direction the item textures use, which is
    why a post and the worm in front of it do not disagree about where the sun
    is.
    """
    x0, y0, x1, y1 = rect
    canvas.noise(rect, ramp, [1, 2, 2, 2, 3], rng, wrap)
    if vertical:
        for x in range(x0, x1):
            if rng.random() < 0.4:
                tone = ramp[rng.choice([1, 1, 3])]
                for y in range(y0, y1):
                    if rng.random() < 0.85:
                        canvas.put(x, y, tone, wrap)
        for y in range(y0, y1):
            canvas.put(x0, y, ramp[4], wrap)
            canvas.put(x1 - 1, y, ramp[0], wrap)
    else:
        for y in range(y0, y1):
            if rng.random() < 0.4:
                tone = ramp[rng.choice([1, 1, 3])]
                for x in range(x0, x1):
                    if rng.random() < 0.85:
                        canvas.put(x, y, tone, wrap)
        for x in range(x0, x1):
            canvas.put(x, y0, ramp[4], wrap)
            canvas.put(x, y1 - 1, ramp[0], wrap)


def end_grain(canvas, rect, core, rim, rng):
    """The cut end of a post: square rings, because at four pixels that is
    what a circle looks like once it is actually drawn.

    Only the corners get the bark rim. Ringing the whole rectangle was the
    first attempt and it left four pale pixels in a black box - on a 4x4 face
    the border *is* the drawing, and spending all of it on an outline leaves
    nothing to outline.
    """
    x0, y0, x1, y1 = rect
    cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
    for y in range(y0, y1):
        for x in range(x0, x1):
            ring = int(max(abs(x - cx), abs(y - cy)))
            index = [4, 3, 2, 1][min(ring, 3)]
            canvas.put(x, y, core[index], wrap=False)
    for x, y in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        canvas.put(x, y, rim, wrap=False)
    # One split running out from the middle: a gnawed end never comes off clean.
    for step in range(max(2, (x1 - x0) // 2)):
        canvas.put(int(cx), int(cy) - step, core[1], wrap=False)


def worm_end(canvas, at, rng, sunken=False):
    """A worm coming out of a wall: either a cut end or an elbow.

    The socket is drawn first - a ring of the darkest soil around wherever the
    worm is about to go - so the animal reads as coming out of the earth rather
    than as a bead stuck onto it. That is also most of where the unpleasantness
    comes from; a pale shape lying flat on soil looks like a petal.

    Half the ends are cut, a pale tube with the gut a dark dot in the middle,
    and half are elbows that bend away out of the wall. Two shapes rather than
    one, because eight copies of the same stamp read as a pattern.

    A `sunken` end is the same drawing one ramp step down, so it sits deeper in
    the wall. Without a couple of those every worm looks equally close and the
    block reads as spotted rather than as packed full of them.
    """
    x, y = at
    pale, lit_tone, mid, rim = WORM_PALE, WORM_LIT, WORM_BODY, WORM_RIM
    if sunken:
        pale, lit_tone, mid, rim = WORM_LIT, WORM_BODY, WORM_RIM, SOIL[1]

    if rng.random() < 0.5:
        body = {(x + ox, y + oy) for oy in range(3) for ox in range(3)}
        painted = {
            (x, y): pale, (x + 1, y): pale, (x + 2, y): lit_tone,
            (x, y + 1): pale, (x + 1, y + 1): rim, (x + 2, y + 1): mid,
            (x, y + 2): lit_tone, (x + 1, y + 2): mid, (x + 2, y + 2): rim,
        }
    else:
        # An elbow: two pixels down, then two across, thickened to two wide.
        spine = [(x, y), (x, y + 1), (x + 1, y + 1), (x + 2, y + 1)]
        body = thicken(spine, 2)
        lit, shaded, interior = silhouette(body)
        painted = {}
        for group, colour in ((interior, mid), (shaded, rim), (lit, lit_tone)):
            painted.update({p: colour for p in group})
        painted[(x % SIZE, y % SIZE)] = pale

    body = {(a % SIZE, b % SIZE) for a, b in body}
    for px, py in body:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1)):
            if ((px + ox) % SIZE, (py + oy) % SIZE) not in body:
                canvas.put(px + ox, py + oy, SOIL[0])
    for (px, py), colour in painted.items():
        canvas.put(px, py, colour)


# --- the tiling blocks ----------------------------------------------------

def deep_earth(rng):
    """The fill of the dimension. Dark, dense, subtly veined.

    Seen more than every other block down here put together, so it is built to
    be looked past: three tones of noise within one ramp step of each other,
    and the only features are three broken mineral seams and a handful of
    pockets. The seams are broken on purpose - an unbroken line reads as a
    crack, and a crack repeats visibly the moment two blocks sit side by side.
    """
    c = Canvas()
    soil_ground(c, rng, weights=(1, 2, 2, 2, 2, 3), clods=10, pockets=8, crumbs=5)
    for _ in range(2):
        start = (rng.randrange(SIZE), rng.randrange(SIZE))
        direction = rng.choice([(1, 0), (0, 1)])
        for i, (x, y) in enumerate(walk(rng, start, 14, direction, 0.3, min_run=3)):
            if rng.random() < 0.25:
                continue
            c.put(x, y, SOIL[5] if i % 3 else SOIL[4])
            c.put(x, y + 1, SOIL[1])  # the seam sits proud, so it casts a line
    return c


def root_beam(rng):
    """A woody root, and the same on all six sides because it is a tangle
    rather than a log - there is no grain direction to get wrong.

    One thick root crossing the block and three thinner ones over it, each on a
    single sine period so it leaves one edge exactly where it re-enters the
    other. Each is drawn complete with its own rim before the next starts, so
    the later ones pass in front.

    The thick one sweeps shallowly and the thin ones swing wider, which is the
    only hierarchy the block needs: a root under load stays put and the hair
    roots wander around it. It is also drawn last, so the hair roots pass
    behind it rather than cutting their dark rims through it.
    """
    c = Canvas()
    c.noise((0, 0, SIZE, SIZE), SOIL, [0, 0, 1, 1], rng, wrap=True)
    for centre, amplitude, frequency, axis, radius in (
        (13, 2.0, 2, "x", 1),
        (11, 3.0, 1, "x", 2),
        (5, 2.0, 1, "y", 3),
    ):
        phase = rng.uniform(0.0, 2.0 * math.pi)
        path = wave(centre, amplitude, frequency, phase, axis)
        paint_strand(c, thicken(path, radius), ROOT, rng)
    for _ in range(6):
        c.put(rng.randrange(SIZE), rng.randrange(SIZE), ROOT[0])
    return c


def glow_mycelium(rng):
    """Pale threads over dark earth: the only light in the burrow.

    The glow is carried by three things and none of them is a gradient. The
    ground is pushed a ramp step darker than `deep_earth`, so the threads have
    the widest contrast the palette allows; every soil pixel touching a thread
    is replaced by one flat tint, which reads as spill without becoming a
    falloff; and the brightest pixels sit on the nodes, where threads meet.

    Three threads on sine curves, each crossing the whole block, with short
    branches walked off them. One pixel wide throughout: the first attempt let
    the threads wander and they curled into three-pixel blobs, and at this size
    a thread is only a thread while it is going somewhere.
    """
    c = Canvas()
    c.noise((0, 0, SIZE, SIZE), SOIL, [0, 0, 1, 1, 2], rng, wrap=True)

    threads = set()
    trunks = []
    for centre, amplitude, frequency, axis in (
        (4, 2.5, 1, "x"), (12, 2.0, 2, "x"), (8, 3.0, 1, "y"),
    ):
        phase = rng.uniform(0.0, 2.0 * math.pi)
        path = [
            (x % SIZE, y % SIZE)
            for x, y in wave(centre, amplitude, frequency, phase, axis)
        ]
        trunks.append(path)
        threads |= set(path)
    for _ in range(3):
        start = rng.choice(rng.choice(trunks))
        direction = rng.choice([(1, 0), (0, 1), (-1, 0), (0, -1)])
        threads |= {
            (x % SIZE, y % SIZE)
            for x, y in walk(rng, start, rng.randint(2, 4), direction, 0.2, min_run=2)
        }

    for x, y in list(threads):
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if ((x + ox) % SIZE, (y + oy) % SIZE) not in threads:
                c.put(x + ox, y + oy, GLOW[0])
    for x, y in threads:
        c.put(x, y, GLOW[rng.choice([1, 1, 2, 2, 3])])
    for x, y in rng.sample(sorted(threads), min(3, len(threads))):
        c.put(x, y, GLOW[4])
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if ((x + ox) % SIZE, (y + oy) % SIZE) in threads:
                c.put(x + ox, y + oy, GLOW[3])
    return c


def worm_larder(rng):
    """Packed earth studded with worm ends. A mole's pantry: the worms are
    alive and put back in the wall head first.

    Damper and a shade darker than `deep_earth` so the pale ends carry. Spacing
    is rejection sampled around the seam, otherwise two ends meet across the
    tile edge and read as one large pale smear on a wall.
    """
    c = Canvas()
    soil_ground(c, rng, weights=(0, 1, 1, 2, 2, 2), clods=8, pockets=10, crumbs=3)
    for i, point in enumerate(scatter_points(rng, 6, 5.0)):
        worm_end(c, point, rng, sunken=i % 3 == 0)
    return c


# --- the atlases ----------------------------------------------------------

def shrink_post(rng):
    """A root driven into the floor with a worked collar around its top.

    Bark on the shaft, cut wood on the collar: the collar is the part a mole
    made, the post is the part it found.
    """
    c = Canvas(ground=ROOT[1] + (255,))
    rects = ATLASES["shrink_post"]

    plank_grain(c, rects["post_side"], ROOT, rng, vertical=True)
    for _ in range(3):
        x0, y0, x1, y1 = rects["post_side"]
        kx, ky = rng.randrange(x0 + 1, x1 - 1), rng.randrange(y0 + 1, y1 - 1)
        c.put(kx, ky, ROOT[1], wrap=False)
        c.put(kx, ky + 1, ROOT[0], wrap=False)

    end_grain(c, rects["post_end"], ROOT + [ROOT[4]], ROOT[0], rng)

    # The collar band is only three rows, so it is painted outright rather than
    # grained: a lit top, the groove, and the body. Running `plank_grain` over
    # it spends the top row on a highlight and the bottom row on a shadow, and
    # with the groove taking the middle there is no collar left.
    x0, y0, x1, y1 = rects["collar_side"]
    for x in range(x0, x1):
        c.put(x, y0, WOOD[4] if rng.random() < 0.75 else WOOD[3], wrap=False)
        c.put(x, y0 + 1, WOOD[0], wrap=False)  # the groove around the collar
        c.put(x, y0 + 2, WOOD[rng.choice([1, 2, 2, 3])], wrap=False)

    plank_grain(c, rects["collar_top"], WOOD, rng, vertical=False)
    x0, y0, x1, y1 = rects["collar_top"]
    for y in range(y0 + 2, y1 - 2):  # the hole the post comes through
        for x in range(x0 + 2, x1 - 2):
            c.put(x, y, ROOT[1], wrap=False)
    for x in range(x0 + 2, x1 - 2):
        c.put(x, y0 + 2, ROOT[0], wrap=False)
    return c


def grunting_post(rng):
    """A stripped stake with a notched rasp across the top.

    The notches are the whole point of the object - worm grunting is a stake
    rubbed with a rasp until the ground hums - so they are the one thing on
    this texture with full ramp contrast.
    """
    c = Canvas(ground=WOOD[1] + (255,))
    rects = ATLASES["grunting_post"]

    plank_grain(c, rects["stake_side"], WOOD, rng, vertical=True)
    end_grain(c, rects["stake_end"], WOOD, WOOD[1], rng)

    def notched(rect):
        """A comb: every other column a ridge, the ones between them cut away.

        Full ramp contrast, alternating column by column. Drawing the notches
        as dark columns over a grained bar - the first attempt - came out as a
        dotted line, because the bar was already dark and the notch had nothing
        to cut into.
        """
        x0, y0, x1, y1 = rect
        for i, x in enumerate(range(x0, x1)):
            for y in range(y0, y1):
                if i % 2:
                    c.put(x, y, WOOD[0], wrap=False)
                else:
                    c.put(x, y, WOOD[4] if y == y0 else WOOD[2], wrap=False)

    notched(rects["rasp_long"])
    notched(rects["rasp_top"])
    end_grain(c, rects["rasp_end"], WOOD, WOOD[1], rng)
    return c


def colony_board(rng):
    """A gnawed plank on a root post, scratched with claw marks.

    Same two materials as `shrink_post` in the same roles - found root below,
    worked wood above - which is what makes the three props read as one set.
    """
    c = Canvas(ground=WOOD[1] + (255,))
    rects = ATLASES["colony_board"]

    plank_grain(c, rects["panel_face"], WOOD, rng, vertical=False)
    x0, y0, x1, y1 = rects["panel_face"]
    for x in range(x0, x1):  # the seam between the two planks of the panel
        c.put(x, y0 + 4, WOOD[0], wrap=False)
    for group in range(3):  # claw marks, three strokes at a time
        gx = x0 + 1 + group * 3
        gy = y0 + 1 + rng.randrange(2)
        for stroke in range(3):
            for dy in range(rng.randint(2, 3)):
                c.put(gx + stroke, gy + dy, WOOD[0], wrap=False)
                c.put(gx + stroke, gy + dy + 1, WOOD[4], wrap=False)
    for peg in ((x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
        c.put(peg[0], peg[1], ROOT[1], wrap=False)

    plank_grain(c, rects["panel_edge_v"], WOOD, rng, vertical=True)
    plank_grain(c, rects["panel_edge_h"], WOOD, rng, vertical=False)
    plank_grain(c, rects["post_side"], ROOT, rng, vertical=True)
    end_grain(c, rects["post_end"], ROOT + [ROOT[4]], ROOT[0], rng)
    return c


# --- the block-shaped faces -----------------------------------------------

def exchange_station(rng):
    """The station's side, laid out as a block face because that is how the
    model reads it: rows 0-1 are the lid rim, rows 3-13 the body.

    Staves, two root hoops and a slot. A machine rather than a cupboard: the
    slot is where a mole pushes worms in, and it runs round all four sides
    because the block has no front.
    """
    c = Canvas(ground=WOOD[2] + (255,))
    plank_grain(c, (0, 0, SIZE, SIZE), WOOD, rng, vertical=True, wrap=True)

    x = 0
    while x < SIZE:  # staves of uneven width, so they do not read as a comb
        width = rng.choice([2, 3, 3, 4])
        for y in range(SIZE):
            c.put(x, y, WOOD[0])
        x += width

    for y0 in (0, 4, 12):  # the lid rim and the two hoops
        for x in range(SIZE):
            c.put(x, y0, ROOT[3])
            c.put(x, y0 + 1, ROOT[2])
        if y0:  # pegs holding the hoop down, on the hoop rather than the stave
            for x in (3, 11):
                c.put(x, y0, WOOD[5])

    for x in range(3, 13):  # the slot a mole pushes worms through
        c.put(x, 8, WOOD[0])
        c.put(x, 9, SOIL[0])
        c.put(x, 10, WOOD[3])  # the lip below it, catching what light there is
    return c


def exchange_station_top(rng):
    """The station's working surface: slats with the throat cut through them.

    A separate image rather than a region of the side, because both faces read
    almost the whole 16x16 through block-shaped UVs and there is no room to
    pack them together.
    """
    c = Canvas(ground=WOOD[2] + (255,))
    for band in range(3):
        y0 = band * 5
        plank_grain(c, (0, y0, SIZE, min(y0 + 5, SIZE)), WOOD, rng, vertical=False, wrap=True)

    for y in range(5, 11):
        for x in range(5, 11):
            c.put(x, y, SOIL[1])
    for x in range(5, 11):
        c.put(x, 5, ROOT[1])
        c.put(x, 10, ROOT[3])
    for y in range(5, 11):
        c.put(5, y, ROOT[1])
        c.put(10, y, ROOT[3])
    c.put(7, 8, SOIL[0])
    c.put(8, 7, SOIL[0])

    for _ in range(10):  # earth trodden into the wood
        c.put(rng.randrange(SIZE), rng.randrange(SIZE), SOIL[rng.choice([2, 3])])
    return c


def worm_box(rng):
    """Slats with a base rail along the bottom two rows, which is the strip
    the model's floor reads for its sides.

    Damp: the blotches are one ramp step down, not a wash, and they gather
    towards the bottom where a box of compost actually stays wet.
    """
    c = Canvas(ground=WOOD[2] + (255,))
    for y0 in (0, 5, 10):
        plank_grain(c, (0, y0, SIZE, y0 + 4), WOOD, rng, vertical=False, wrap=True)
        for x in range(SIZE):
            c.put(x, y0 + 4, WOOD[0])  # the gap between two slats
        for x in rng.sample(range(1, SIZE - 1), 2):
            c.put(x, y0 + 1, WOOD[4])  # pegs holding the slat to the frame

    for _ in range(9):
        x, y = rng.randrange(SIZE), rng.randrange(8, SIZE)
        for ox, oy in ((0, 0), (1, 0), (0, 1)):
            c.put(x + ox, y + oy, WOOD[1])

    for x in range(SIZE):  # the base rail
        c.put(x, 14, WOOD[1])
        c.put(x, 15, WOOD[0])
    return c


# --- driver ---------------------------------------------------------------

#: name -> (function, seed). The seeds are dials like every other number here:
#: a texture that comes out badly composed is reseeded, not repainted.
TEXTURES = [
    ("deep_earth", deep_earth, 20260829),
    ("root_beam", root_beam, 4471),
    ("glow_mycelium", glow_mycelium, 90112),
    ("worm_larder", worm_larder, 33107),
    ("shrink_post", shrink_post, 8802),
    ("grunting_post", grunting_post, 5410),
    ("colony_board", colony_board, 12244),
    ("exchange_station", exchange_station, 7719),
    ("exchange_station_top", exchange_station_top, 61503),
    ("worm_box", worm_box, 24680),
]

#: The ones that tile against copies of themselves, shown as a wall in the
#: preview sheet.
TILING = {"deep_earth", "root_beam", "glow_mycelium", "worm_larder"}


def paint_all():
    return [(name, fn(random.Random(seed))) for name, fn, seed in TEXTURES]


def contact_sheet(painted, scale=12, path=None):
    """A magnified sheet, tiling textures shown 3x3. Written for looking at,
    never shipped."""
    from PIL import Image

    cell = SIZE * 3 * scale
    cells = []
    for name, canvas in painted:
        repeat = 3 if name in TILING else 1
        tile = Image.new("RGBA", (SIZE * repeat, SIZE * repeat))
        for ty in range(repeat):
            for tx in range(repeat):
                tile.paste(canvas.img, (tx * SIZE, ty * SIZE))
        cells.append(tile.resize((cell, cell), Image.NEAREST))

    columns = 4
    rows = (len(cells) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * cell, rows * cell), (24, 24, 24, 255))
    for i, cell_img in enumerate(cells):
        sheet.paste(cell_img, ((i % columns) * cell, (i // columns) * cell))
    if path:
        sheet.save(path)
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG", help="also write a magnified contact sheet")
    parser.add_argument("--atlas", action="store_true", help="print the atlas rectangles")
    args = parser.parse_args()

    painted = paint_all()
    for name, canvas in painted:
        canvas.save(f"{BLOCKS}/{name}.png")
        print("wrote", f"{BLOCKS}/{name}.png")

    if args.atlas:
        for texture, rects in ATLASES.items():
            print(f"\n{texture}.png")
            for region, rect in rects.items():
                print(f"  {region:<14} uv {list(rect)}")

    if args.preview:
        contact_sheet(painted, path=args.preview)
        print("wrote", args.preview, "-", ", ".join(n for n, _ in painted))


if __name__ == "__main__":
    main()

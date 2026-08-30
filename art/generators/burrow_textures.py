"""Block textures for the burrow, the dimension below it, and the mole's kit.

Most of these replaced a vanilla stand-in - rooted dirt, mangrove roots,
shroomlight, barrel staves, spruce planks, oak, a lantern. Each one is a fine
texture and together they made the mod look like a pile of other mods, because
none of them share a palette with the mound the player dug through to get here.

`loose_soil` is the exception and is a re-render rather than a replacement: it
was already the mod's own art, but it was made of per-pixel jitter. Its entry
says how the ramp was measured off the file it replaces instead of chosen.

Everything here is generated, not placed pixel by pixel, for the reason the
mound shapes are: the tuning dials are the seed, the ramp and the count, and
regenerating is cheaper than repainting. What is hand-placed is the *layout* -
which rectangle of the image a model face reads - because that is a decision
about the model, not about the picture.

Two kinds of texture come out of this file:

* Full 16x16 faces, for `cube_all` blocks and for the models whose faces carry
  block-shaped UVs (`exchange_station`, `worm_box`). These tile against copies
  of themselves, so every drawing operation wraps.
* Atlases, for the worked-wood props and the lantern. A post, its collar and its end
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
    TURNED,
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
    "shaft_lantern": {
        "cage_side": (0, 0, 6, 7),
        "cage_end": (6, 0, 12, 6),
        "cap_side": (0, 8, 8, 10),
        "cap_top": (8, 6, 16, 14),
        "knot_side": (12, 0, 14, 2),
    },
}

#: `WOOD` shifted up a step, for anything that reads as stripped rather than
#: split. Same ramp, so a trap lid and a trap wall still agree about the wood.
PALE_WOOD = WOOD[1:]

#: The fill of the dimension, and the one ramp here that is not `SOIL`.
#:
#: `SOIL` runs #140E09 to #403325. That is brown by measurement and black by
#: eye, and the first person to walk down there said so: the walls read as
#: unlit geometry rather than as earth. A ramp that dark has no hue left to
#: carry once the dimension's ambient light is applied to it on top.
#:
#: So this is derived instead of picked: `TURNED[1:6]` - the mound's own soil,
#: which `loose_soil` is also cut from - multiplied by 0.66, with `ROOT[0]` on
#: the bottom for the pockets between clods. One scale factor apart from the
#: lining means deep earth and loose soil are the same soil at two depths and
#: cannot drift: same hue, same spacing, and the lining always the lighter of
#: the two by a fixed and visible margin.
#:
#: `SOIL` is left alone. It is the ground the roots, the mycelium and the
#: larder are drawn against, and those want the darkest thing in the palette
#: behind them.
DEEP = [
    (0x24, 0x19, 0x10),
    (0x2A, 0x1D, 0x13),
    (0x32, 0x24, 0x17),
    (0x3A, 0x2A, 0x1C),
    (0x43, 0x32, 0x21),
    (0x4D, 0x39, 0x26),
]


# --- shared drawing -------------------------------------------------------

def soil_ground(canvas, rng, ramp=SOIL, weights=(1, 2, 2, 2, 2, 3), clods=9, pockets=7, crumbs=4):
    """Earth: noise for the ground, clods for the structure.

    Per-pixel noise alone comes out as static: at 16 px the eye has nothing to
    hold on to and a wall of it fizzes. So the noise is only the ground, and
    the structure is clods - two and three pixel patches a step lighter, each
    with a shadow along its lower edge, exactly as `mound_texture.py` builds
    the mound above.

    `ramp` needs five entries in the roles this uses: 0 is a pocket between
    clods, 1 to 3 the ground, 3 and 4 the lit face of a clod. Passing a slice
    of a longer ramp is how a surface block and a burrow block come out the
    same hue at different values.
    """
    canvas.noise((0, 0, SIZE, SIZE), ramp, list(weights), rng, wrap=True)
    for _ in range(clods):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        w, h = rng.choice([(2, 2), (3, 2), (2, 3), (3, 2)])
        tone = ramp[rng.choice([3, 3, 4])]
        for dy in range(h):
            for dx in range(w):
                if rng.random() < 0.18:
                    continue
                canvas.put(cx + dx, cy + dy, tone)
        for dx in range(w):
            canvas.put(cx + dx, cy + h, ramp[rng.choice([0, 1])])
    for _ in range(pockets):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        canvas.put(x, y, ramp[0])
        if rng.random() < 0.4:
            canvas.put(x + rng.choice([-1, 1]), y, ramp[1])
    for _ in range(crumbs):
        canvas.put(rng.randrange(SIZE), rng.randrange(SIZE), ramp[4])


def hair_root(canvas, path, rng, nick=0.25):
    """A root one pixel wide, drawn as a seam rather than as a body.

    `paint_strand` is the wrong tool below about three pixels. It rims the whole
    outline in the darkest tone, so a one pixel path comes out as a three pixel
    black stripe with a brown thread inside it - and four of those on one tile
    read as cracks in the wall, which was the first version of `root_nodule`.

    So this borrows `deep_earth`'s mineral seam instead: the lit tone on the
    path, one darker pixel under it, and nothing else. The shadow is on the
    underside only because that is the light direction everything else here
    uses, and one-sided shading is what makes a single pixel read as standing
    proud of the soil rather than as a groove cut into it.

    Both halves have to be picked against the *soil*, not against each other.
    The root runs at the top of `ROOT` because the middle of that ramp is within
    a step of the earth it is drawn on and disappears into it; the shadow is
    broken, for `deep_earth`'s reason, because an unbroken dark line across a
    tile is a crack whatever is sitting on top of it.
    """
    for x, y in path:
        canvas.put(x, y, ROOT[3] if rng.random() < nick else ROOT[4])
        if rng.random() < 0.75:
            canvas.put(x, y + 1, ROOT[0])


def nodule(canvas, at, wide=False, keep=frozenset()):
    """One pale bead swelling off a root, with a socket cut around it.

    The socket is a four-connected ring of the darkest root tone. Ringing the
    diagonals too was the first attempt and it costs sixteen pixels to say what
    twelve already say; at this size the corners are most of the tile's budget.

    `keep` is the root the bead grew on, and leaving it out of the ring is the
    difference between a nodule and a bead glued to a wall. Ringing it too was
    the second attempt: three beads on one root black out a dozen of its sixteen
    pixels between them, and the root the whole texture is about disappears
    under its own sockets. A nodule is a swelling *on* something, so the
    something has to run into it and out the other side.

    `WOOD` rather than a pale ramp of its own. A nodule is the one thing down
    here with no colour in the kit already, and inventing a sixth ramp for a
    four pixel bead is exactly what `texture_kit` exists to prevent. The
    worked-wood ramp is the right hue and the right value range, and no texture
    carries both, so there is nothing for them to be confused with.
    """
    x, y = at
    w, h = (3, 2) if wide else (2, 2)
    body = {((x + ox) % SIZE, (y + oy) % SIZE) for oy in range(h) for ox in range(w)}
    for px, py in body:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ring = ((px + ox) % SIZE, (py + oy) % SIZE)
            if ring not in body and ring not in keep:
                canvas.put(*ring, ROOT[0])
    # Painted outright rather than through `silhouette`: a two by two blob has
    # no interior, so the split would put every pixel in the lit set and the
    # bead would come out as a flat pale square.
    for oy in range(h):
        for ox in range(w):
            far = ox + oy
            canvas.put(x + ox, y + oy, WOOD[5] if far == 0 else WOOD[4] if far < w else WOOD[2])


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


def board_run(canvas, ramp, rng, vertical=True, widths=(3, 4, 3, 3)):
    """Boards laid side by side across a whole 16x16 face.

    Each board gets a lit edge, a mottled middle and a seam, so the run reads
    as separate planks rather than as one grained surface. Uneven widths on
    purpose: four equal boards across sixteen pixels reads as a comb.
    """
    position, index = 0, 0
    while position < SIZE:
        width = widths[index % len(widths)]
        index += 1
        for a in range(position, min(position + width, SIZE)):
            for b in range(SIZE):
                if a == position:
                    tone = ramp[4]
                elif a == position + width - 1:
                    tone = ramp[0]
                else:
                    tone = ramp[rng.choice([1, 2, 2, 2, 3])]
                canvas.put(*((a, b) if vertical else (b, a)), tone)
        position += width


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

    Three shapes rather than one, because eight copies of the same stamp read
    as a pattern: cut ends, a pale tube with the gut a dark dot in the middle;
    elbows that bend away out of the wall, mirrored at random so no two point
    the same way; and the odd loop, a worm that came out and went straight
    back in. The mirroring is most of the "organic" - the first version bent
    every elbow down and right, and six identical hooks on one wall read as
    wallpaper however carefully each hook is shaded.

    A `sunken` end is the same drawing one ramp step down, so it sits deeper in
    the wall. Without a couple of those every worm looks equally close and the
    block reads as spotted rather than as packed full of them.
    """
    x, y = at
    pale, lit_tone, mid, rim = WORM_PALE, WORM_LIT, WORM_BODY, WORM_RIM
    if sunken:
        pale, lit_tone, mid, rim = WORM_LIT, WORM_BODY, WORM_RIM, SOIL[1]

    roll = rng.random()
    if roll < 0.4:
        body = {(x + ox, y + oy) for oy in range(3) for ox in range(3)}
        painted = {
            (x, y): pale, (x + 1, y): pale, (x + 2, y): lit_tone,
            (x, y + 1): pale, (x + 1, y + 1): rim, (x + 2, y + 1): mid,
            (x, y + 2): lit_tone, (x + 1, y + 2): mid, (x + 2, y + 2): rim,
        }
    else:
        if roll < 0.75:
            # An elbow: two pixels one way, then two across, mirrored at
            # random so the bends scatter instead of all hanging one way.
            sx, sy = rng.choice([1, -1]), rng.choice([1, -1])
            spine = [(x, y), (x, y + sy), (x + sx, y + sy), (x + 2 * sx, y + sy)]
        else:
            # A loop: out, along, and diving back - the longest shape here,
            # and the one that says the wall is full behind the surface.
            sx = rng.choice([1, -1])
            spine = [(x, y), (x + sx, y), (x + sx, y + 1),
                     (x + 2 * sx, y + 1), (x + 2 * sx, y + 2)]
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

def loose_soil(rng):
    """Dug earth on the surface. A re-render, not a redesign.

    The old file was 48 colours: 232 of its 256 pixels shared a colour with no
    neighbour at all, which is per-pixel jitter and nothing else. It is the
    floor of every corridor, so it is the texture a player looks at longest,
    and jitter at that dose fizzes.

    What it is not allowed to do is look like a different block. So the ramp is
    not chosen, it is measured off the file being replaced: mean colour #5A412F,
    luminance from 49 to 90 with the mean at 70. That is exactly `TURNED[1:6]` -
    the mound's ramp with its darkest and brightest ends left off - whose five
    steps run 48, 58, 69, 80, 92 and whose middle entry is #58402A. Same hue,
    same range, same weight. Only the jitter is gone, replaced by the clods
    that were the point of `soil_ground` in the first place.

    Calmer than `mole_mound` on the same ramp, which is right and was already
    true of the old file: a mound is turned clods, this is what was raked flat.
    """
    c = Canvas()
    soil_ground(c, rng, ramp=TURNED[1:6], weights=(1, 1, 2, 2, 2, 3),
                clods=12, pockets=5, crumbs=5)
    # Three dry crumbs from the top of the mound's own ramp, above the slice
    # the ground is cut from. The mound has them and this is the same earth a
    # few paces away, so their absence was the one measurable difference left
    # between the two files. Three, not five: this surface was raked flat.
    for _ in range(3):
        c.put(rng.randrange(SIZE), rng.randrange(SIZE), TURNED[6])
    return c



def deep_earth(rng):
    """The fill of the dimension. Dark, dense, subtly veined.

    Seen more than every other block down here put together, so it is built to
    be looked past: three tones of noise within one ramp step of each other,
    and the only features are three broken mineral seams and a handful of
    pockets. The seams are broken on purpose - an unbroken line reads as a
    crack, and a crack repeats visibly the moment two blocks sit side by side.

    Drawn on `DEEP` rather than on `SOIL`, which is the whole of the fix for a
    fill that came out black. The drawing itself is unchanged - the same clods,
    pockets and seams the mound is built from - because the composition was
    never the complaint.

    Reseeded with it. The two seams are the strongest thing on the tile and a
    seed whose seams happen to run the width of it puts a visible hook or bar
    on every block of a wall, which is exactly the failure the `--preview`
    sheet exists to catch.
    """
    c = Canvas()
    soil_ground(c, rng, ramp=DEEP, weights=(1, 2, 2, 2, 2, 3), clods=10, pockets=8, crumbs=5)
    for _ in range(2):
        start = (rng.randrange(SIZE), rng.randrange(SIZE))
        direction = rng.choice([(1, 0), (0, 1)])
        # The seam runs a step above the ground, not two: DEEP[4] with a rare
        # DEEP[5] glint. At full brightness the seams were the first thing on
        # the tile, and a fill's features must be findable, never found.
        for i, (x, y) in enumerate(walk(rng, start, 12, direction, 0.3, min_run=3)):
            if rng.random() < 0.3:
                continue
            c.put(x, y, DEEP[5] if i % 5 == 2 else DEEP[4])
            if rng.random() < 0.6:  # the seam sits proud, so it casts a line
                c.put(x, y + 1, DEEP[1])
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
    thick_path = None
    for centre, amplitude, frequency, axis, radius in (
        (13, 2.0, 2, "x", 1),
        (11, 3.0, 1, "x", 2),
        (5, 2.0, 1, "y", 3),
    ):
        phase = rng.uniform(0.0, 2.0 * math.pi)
        path = wave(centre, amplitude, frequency, phase, axis)
        paint_strand(c, thicken(path, radius), ROOT, rng)
        thick_path = (path, radius)

    # Bark girdles on the thick root only. A woody root is segmented where the
    # bark cracked as it grew, and the girdle is what says "wood under load"
    # rather than "hose": a one pixel line across the full width, dark at the
    # edges and half a step lighter in the middle so it reads as a crease in
    # the bark rather than as a cut through the root. The thin roots stay
    # smooth - hair roots have no bark to crack.
    path, radius = thick_path
    for i in range(4, len(path) - 3, 7):
        x, y = path[i]
        for o in range(radius):
            c.put(x + o, y, ROOT[2] if o == radius // 2 else ROOT[1])

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

    # The spill, in two steps and no gradient. Every soil pixel touching a
    # thread takes the full tint; the ring beyond that - diagonals and second
    # neighbours - takes it as a dither, roughly every other pixel. A stepped
    # dither is what falloff looks like inside a five colour budget: the eye
    # averages it into "dimmer" without a single new colour on the tile.
    ring = set()
    for x, y in threads:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            p = ((x + ox) % SIZE, (y + oy) % SIZE)
            if p not in threads:
                ring.add(p)
    fade = set()
    for x, y in threads:
        for ox, oy in ((-1, -1), (1, -1), (-1, 1), (1, 1),
                       (-2, 0), (2, 0), (0, -2), (0, 2)):
            p = ((x + ox) % SIZE, (y + oy) % SIZE)
            if p not in threads and p not in ring:
                fade.add(p)
    for p in ring:
        c.put(*p, GLOW[0])
    for p in fade:
        if rng.random() < 0.45:
            c.put(*p, GLOW[0])
    for x, y in threads:
        c.put(x, y, GLOW[rng.choice([1, 1, 2, 2, 3])])
    for x, y in rng.sample(sorted(threads), min(3, len(threads))):
        c.put(x, y, GLOW[4])
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if ((x + ox) % SIZE, (y + oy) % SIZE) in threads:
                c.put(x + ox, y + oy, GLOW[3])
    return c


def root_nodule(rng):
    """A pocket in the lining: hair roots through the soil, beaded with nodules.

    This block's whole job is to be spotted. It sits in a wall of `loose_soil`
    and it is the only thing in the burrow that says *dig here*, so it is drawn
    on `loose_soil`'s own ramp - same hue, same range - and everything that
    distinguishes it is laid over the top. A pocket in a different soil would
    read as a seam of another material and the player would learn to walk past
    it; a pocket in the same soil with something in it reads as a find.

    One root across the block and one branch off it, both thinner than
    `root_beam`'s: that block is a beam a corridor was cut around, this is hair
    root growing through undisturbed ground. Three nodules, placed on the root
    rather than scattered, because a nodule that is not attached to anything is
    a pebble.

    Weighted a step darker than `loose_soil` proper. The nodules are the palest
    thing on the tile by a wide margin and they need the ground to stay out of
    their way - which also means the block reads as a shadow in the wall from
    across a corridor and resolves into roots as you walk up to it.
    """
    c = Canvas()
    soil_ground(c, rng, ramp=TURNED[1:6], weights=(0, 1, 1, 2, 2, 3),
                clods=10, pockets=6, crumbs=3)

    # One root across the tile and one branch climbing off it. The amplitude is
    # deliberately under two: `wave` fills its steep runs in, so a taller curve
    # spends whole columns going vertically and the root arrives as a knot in
    # the middle of the tile rather than as something crossing it.
    phase = rng.uniform(0.0, 2.0 * math.pi)
    trunk = wave(9, 1.8, 1, phase, "x")
    hair_root(c, trunk, rng)

    fork = trunk[rng.randrange(4, 11)]
    hair_root(c, [(fork[0] + step // 2, fork[1] - step) for step in range(1, 7)], rng)

    # Placed by column rather than by index into the path. `wave` returns more
    # points than the tile is wide, so a fraction of the point list is not a
    # fraction of the way across - and three beads that land in one quadrant are
    # a lump, when the whole thing the count has to say is "several".
    columns = {x: y for x, y in trunk}
    on_root = {(x % SIZE, y % SIZE) for x, y in trunk}
    for i, column in enumerate((2, 7, 12)):
        column = (column + rng.randrange(2)) % SIZE
        nodule(c, (column, columns[column]), wide=i == 1, keep=on_root)
    return c


def root_ladder(rng):
    """Two roots braided into a rope with rungs lashed between them: the way up
    out of a chamber.

    The one cutout in this file. Every other block texture here fills its tile
    and is read as a face; this one is drawn on transparency and hung on a pair
    of crossed planes, so what is not rope has to be nothing at all rather than
    soil. It is also why the ladder is twelve pixels wide and not sixteen - the
    spare columns are the room seen past it, and a rope that filled its tile
    would read as a plank.

    **It has to tile in y and never meets a copy of itself in x.** Segments hang
    from the ceiling down to head height, so the braid and the rungs both run on
    a period of four, which divides sixteen: the twist carries unbroken down a
    column of seven blocks instead of restarting at every block boundary.

    The braid is the whole trick. A rope drawn in one flat tone with rungs on it
    reads as wire, and one with per-pixel noise in it reads as string; what makes
    it turn is a two pixel body with the highlight walking across it on a fixed
    four row cycle. The cycle is written out rather than computed so that the
    number of ramp steps it spends on each row is visible in the source.

    The rims are the outer column of each rope and they are not decoration.
    `ROOT` and the `loose_soil` the chamber is lined with are within a ramp step
    of each other at the top - the rope is drawn on the wall's own palette, from
    the ceiling of the wall's own room - so without a dark edge the lit side of a
    strand lands on the same value as the wall behind it and the ladder
    disappears from across the chamber. One column, on the outside only: this is
    `hair_root`'s lit-tone-and-a-shadow turned on its side, not the hard outline
    that eats a small shape.

    The lashings take the *inner* pixel of each rope at a rung row and leave the
    outer one to the braid, which is where a rung would actually be tied.
    Darkening the whole rope there was the first version and it costs the twist:
    three of every four rows carry it, and the cycle stops reading as rotation.

    Which row of the braid the rungs land on is a decision and not a free choice
    of phase. On the cycle's own lit row the lashing eats the one pixel that says
    which way the rope turns, and half the twist goes with it - so the rungs sit
    on a level row, where the pixel they take is the same tone as the one beside
    it and costs nothing.
    """
    #: The twist, as ramp indices for (outer, inner) over four rows. Both ropes
    #: run the same way round - mirroring them is symmetrical and reads as a
    #: pattern, where two lengths cut off one rope read as rope.
    braid = ((4, 2), (3, 3), (2, 4), (3, 3))

    #: Each rope as (rim column, direction the body runs in). The rim is the
    #: outer edge of the ladder, so the two ropes are laid inwards from 2 and 13
    #: and their inner pixels - 4 and 11 - are what the rungs tie to.
    ropes = ((2, 1), (13, -1))

    c = Canvas()
    for y in range(SIZE):
        outer, inner = braid[y % 4]
        for rim, step in ropes:
            c.put(rim, y, ROOT[0])
            c.put(rim + step, y, ROOT[outer])
            c.put(rim + 2 * step, y, ROOT[inner])

    # The rungs, and the knots holding them. The tone varies by a step from rung
    # to rung: four identical bars four pixels apart is the one place a
    # generated texture gives itself away.
    for y in range(1, SIZE, 4):
        tone = ROOT[rng.choice([2, 3, 3])]
        for x in range(5, 11):
            c.put(x, y, tone)
        for rim, step in ropes:
            c.put(rim + 2 * step, y, ROOT[0])

    # Fibres working loose. Five of them, just outside the rims where they break
    # the silhouette - a rope with two perfectly straight edges is a cable.
    for _ in range(5):
        c.put(rng.choice([1, 14]), rng.randrange(SIZE), ROOT[1])
    return c


def worm_larder(rng):
    """Packed earth studded with worm ends. A mole's pantry: the worms are
    alive and put back in the wall head first.

    Damp packed earth, dark enough that the pale ends carry. (It once claimed to
    be "a shade darker than deep_earth"; deep_earth has since gone browner in
    the fog rework and the comparison no longer holds - this draws on SOIL and
    stands on its own.) Spacing
    is rejection sampled around the seam, otherwise two ends meet across the
    tile edge and read as one large pale smear on a wall.
    """
    c = Canvas()
    soil_ground(c, rng, weights=(0, 1, 1, 2, 2, 2), clods=8, pockets=10, crumbs=3)
    # Minimum distance up half a pixel from 5.0: the mirrored elbows and the
    # loop reach further from their anchor than the old stamps did, and two
    # sockets fusing across a gap is the smear the sampling exists to prevent.
    for i, point in enumerate(scatter_points(rng, 6, 5.5)):
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
                    # The ridge tip takes the very top of the ramp: the rasp
                    # is the working edge of the whole object and its teeth
                    # should catch light before anything else on the post.
                    c.put(x, y, WOOD[5] if y == y0 else WOOD[2], wrap=False)

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
            c.put(x, y0 + 1, WOOD[5])  # pegs holding the slat to the frame

    for _ in range(9):
        x, y = rng.randrange(SIZE), rng.randrange(8, SIZE)
        for ox, oy in ((0, 0), (1, 0), (0, 1)):
            c.put(x + ox, y + oy, WOOD[1])

    for x in range(SIZE):  # the base rail
        c.put(x, 14, WOOD[1])
        c.put(x, 15, WOOD[0])
    return c


def mole_trap(rng):
    """The trap's walls and floor: solid boards, nailed, no gaps.

    Deliberately the other way up from `worm_box`. Both are plank boxes and
    both are made of the same wood, so if they also ran their boards the same
    way they would be one block with two names: the worm box is horizontal
    slats with daylight between them, the trap is vertical boards nailed shut.
    """
    c = Canvas(ground=WOOD[2] + (255,))
    board_run(c, WOOD, rng, vertical=True)
    # Nail heads at the top of the ramp, a step above the boards' own lit
    # edges, with a shadow pixel under each. The head is the one thing on the
    # wall that stands proud of it, and before the shadow it read as grain.
    for y in (4, 11):  # two rows of nails, one per board
        for x in range(1, SIZE, 3):
            c.put(x, y, WOOD[5])
            c.put(x, y + 1, WOOD[1])
    for _ in range(6):
        c.put(rng.randrange(SIZE), rng.randrange(SIZE), WOOD[1])
    return c


def mole_trap_frame(rng):
    """The lid and the door jambs: stripped wood, a step paler than the walls.

    The band inset two pixels from every edge is the lid's frame. The lid reads
    the middle twelve pixels of this image, so the band lands exactly on its
    rim; the jambs read narrow strips and come out as plain bars, which is what
    a jamb is.
    """
    c = Canvas(ground=PALE_WOOD[2] + (255,))
    board_run(c, PALE_WOOD, rng, vertical=False, widths=(4, 3, 4, 3))
    for i in (2, SIZE - 3):
        for j in range(2, SIZE - 2):
            c.put(i, j, WOOD[1])
            c.put(j, i, WOOD[1])
    # The frame band sits proud of the boards, so its inner edge casts a
    # broken shadow - broken, because an unbroken dark ring inset in a pale
    # face reads as a second frame rather than as depth.
    for j in range(3, SIZE - 3):
        if rng.random() < 0.6:
            c.put(3, j, WOOD[0])
            c.put(j, 3, WOOD[0])
    for corner in ((3, 3), (SIZE - 4, 3), (3, SIZE - 4), (SIZE - 4, SIZE - 4)):
        c.put(*corner, PALE_WOOD[4])  # pegs at the corners of the frame
    return c


def mole_trap_door(rng):
    """The drop door of a sprung trap: root heartwood, with a latch bar.

    Dark on purpose. The empty and baited traps show an open gap here, so the
    only thing this texture has to say at a glance is *shut* - which needs it
    to be a different value from the walls, not a different pattern.
    """
    c = Canvas(ground=ROOT[2] + (255,))
    board_run(c, ROOT, rng, vertical=True, widths=(3, 3, 4, 3))
    for x in range(SIZE):  # the latch bar across the middle
        c.put(x, 11, PALE_WOOD[4])
        c.put(x, 12, WOOD[1])
    for x in (5, 10):
        c.put(x, 11, WOOD[0])  # the two pins holding it
    return c


def shaft_lantern(rng):
    """A cage of root bars with glowing mycelium shut inside it.

    The vanilla lantern was standing in here, which put an iron-and-torch
    object in a mod whose only light source is a fungus. The cage is the same
    root the posts are made of and the light inside is `glow_mycelium`'s ramp,
    so the lantern reads as something a mole built out of what a mole has.
    """
    c = Canvas(ground=ROOT[1] + (255,))
    rects = ATLASES["shaft_lantern"]

    # The cage is mostly light. Only the two corner posts and one middle bar
    # are opaque: a cage drawn as bars with light between them ends up more bar
    # than light, and a lantern that is not the brightest thing in the corridor
    # is not a lantern.
    x0, y0, x1, y1 = rects["cage_side"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.put(x, y, GLOW[rng.choice([2, 3, 3, 4])], wrap=False)
    for x in (x0, x0 + 3, x1 - 1):
        for y in range(y0, y1):
            c.put(x, y, ROOT[rng.choice([1, 2, 2])], wrap=False)
    for x in range(x0, x1):  # the rims the bars are set into
        c.put(x, y0, ROOT[3], wrap=False)
        c.put(x, y1 - 1, ROOT[1], wrap=False)

    x0, y0, x1, y1 = rects["cage_end"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.put(x, y, ROOT[rng.choice([1, 2, 2, 3])], wrap=False)
    for y in range(y0 + 1, y1 - 1):  # light spilling through the woven base
        for x in range(x0 + 1, x1 - 1):
            c.put(x, y, GLOW[rng.choice([2, 3, 3])], wrap=False)
    for x in range(x0 + 1, x1 - 1):
        c.put(x, y0 + 3, ROOT[2], wrap=False)  # one strand across the weave

    x0, y0, x1, y1 = rects["cap_side"]
    for x in range(x0, x1):
        c.put(x, y0, PALE_WOOD[4], wrap=False)
        c.put(x, y0 + 1, WOOD[rng.choice([1, 2, 2])], wrap=False)

    end_grain(c, rects["cap_top"], PALE_WOOD, WOOD[1], rng)
    x0, y0, x1, y1 = rects["cap_top"]
    for y in (y0 + 3, y0 + 4):  # the loop the lantern hangs from
        for x in (x0 + 3, x0 + 4):
            c.put(x, y, ROOT[0], wrap=False)
    c.put(x0 + 3, y0 + 3, ROOT[3], wrap=False)

    # The knot the loop is tied off on: a stub of root standing on the cap,
    # which the model raises as its own little box. Lit corner up-left, dark
    # corner down-right, the kit's one light direction at its smallest.
    x0, y0, x1, y1 = rects["knot_side"]
    c.put(x0, y0, ROOT[3], wrap=False)
    c.put(x0 + 1, y0, ROOT[1], wrap=False)
    c.put(x0, y0 + 1, ROOT[1], wrap=False)
    c.put(x0 + 1, y0 + 1, ROOT[0], wrap=False)
    return c


# --- driver ---------------------------------------------------------------

#: name -> (function, seed). The seeds are dials like every other number here:
#: a texture that comes out badly composed is reseeded, not repainted.
TEXTURES = [
    ("loose_soil", loose_soil, 2318),
    ("deep_earth", deep_earth, 17320),
    ("root_beam", root_beam, 4471),
    ("glow_mycelium", glow_mycelium, 90112),
    ("worm_larder", worm_larder, 33107),
    ("root_nodule", root_nodule, 51236),
    ("root_ladder", root_ladder, 771),
    ("shrink_post", shrink_post, 8802),
    ("grunting_post", grunting_post, 5410),
    ("colony_board", colony_board, 12244),
    ("exchange_station", exchange_station, 7719),
    ("exchange_station_top", exchange_station_top, 61503),
    ("worm_box", worm_box, 24680),
    ("mole_trap", mole_trap, 31415),
    ("mole_trap_frame", mole_trap_frame, 27182),
    ("mole_trap_door", mole_trap_door, 16180),
    ("shaft_lantern", shaft_lantern, 14142),
]

#: The ones that tile against copies of themselves, shown as a wall in the
#: preview sheet. `root_ladder` tiles in y only - it hangs as a column and never
#: meets a copy sideways - and is in the set anyway, because the vertical seam is
#: the one thing about it worth checking and the sheet is where that shows.
TILING = {"loose_soil", "deep_earth", "root_beam", "glow_mycelium", "worm_larder",
          "root_nodule", "root_ladder"}


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

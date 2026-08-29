"""Shared ramps and drawing primitives for the hand-drawn textures.

Every texture in `burrow_textures.py` and `worm_item_textures.py` draws its
colours from the four ramps below and nothing else. That is the whole reason
this module exists: the posts, the board, the station and the worm box are
meant to read as made by the same hand, and the only reliable way to get that
is to deny each generator its own palette.

The ramps continue the ones already in the mod. `SOIL` is `mound_texture.py`'s
earth carried downwards - the mound is turned earth in daylight, the deep
blocks are the same earth under a few hundred metres of it - and the worm
colours are lifted verbatim from `earthworm_texture.py` so a worm end in a
larder wall and the item a mole carries away are the same animal.

Three primitives do the drawing:

* `walk` - a 4-connected path that mostly keeps going one way. Veins, roots
  and mycelium threads are all the same function with different lengths.
* `silhouette` - splits a blob into lit edge, shaded edge and interior. The
  rule is `earthworm_texture.py`'s: a pixel with nothing above or to its left
  catches the light, one with nothing below or to its right falls into shadow.
  Deriving shading from the outline keeps the highlight on the outline instead
  of eating into the middle, which is what offset copies of a shape do.
* `scatter_points` - rejection sampling with a minimum distance, measured on
  the torus so a tiling texture has no clumps across its own seam.

No gradients and no anti-aliasing anywhere: a colour is a ramp entry, and the
only softening allowed is picking a neighbouring entry at random.
"""

import math

SIZE = 16

# --- ramps ----------------------------------------------------------------

#: Packed soil, darkest first. Index 2 is the base tone, 0 is a pocket between
#: clods and 5 is a mineral seam. Deliberately short of the mound's brightest
#: crumb: nothing down here has ever seen the sun.
SOIL = [
    (0x14, 0x0E, 0x09),
    (0x1C, 0x14, 0x0D),
    (0x23, 0x1A, 0x11),
    (0x2B, 0x20, 0x16),
    (0x33, 0x27, 0x1B),
    (0x40, 0x33, 0x25),
]

#: Root and bark. Index 0 is the gap between two roots, which is nearly soil,
#: and index 4 is the ridge of a root catching what light there is.
ROOT = [
    (0x24, 0x19, 0x10),
    (0x3A, 0x28, 0x1A),
    (0x50, 0x39, 0x24),
    (0x66, 0x4A, 0x2E),
    (0x7E, 0x5E, 0x3B),
]

#: Wood a mole has worked: gnawed flat, not sawn. Warmer and paler than the
#: root it was cut from, because the bark is off. Index 0 doubles as the cut
#: line between two planks.
WOOD = [
    (0x33, 0x25, 0x17),
    (0x5C, 0x44, 0x2A),
    (0x7A, 0x5C, 0x39),
    (0x92, 0x71, 0x48),
    (0xAC, 0x89, 0x5C),
    (0xC4, 0xA4, 0x74),
]

#: Glowing mycelium. Index 0 is not thread at all but the tint the light
#: leaves on the soil around it - one flat step, not a falloff.
GLOW = [
    (0x2E, 0x35, 0x22),
    (0x6E, 0x8A, 0x55),
    (0x9C, 0xBE, 0x72),
    (0xCF, 0xE7, 0x9A),
    (0xEE, 0xF9, 0xC8),
]

#: The animal itself, read off `textures/entity/mole.png`. Nearly neutral but
#: not quite - it is a blue-grey, and matching that matters more than the exact
#: values, because a warm grey next to the soil ramps looks like dirty wood.
MOLE = [
    (0x2A, 0x27, 0x2C),
    (0x36, 0x33, 0x3B),
    (0x45, 0x41, 0x4B),
    (0x51, 0x4D, 0x57),
    (0x68, 0x64, 0x6E),
]

#: The mole's only two spots of colour, both sampled from the entity texture.
#: They are what makes a dark grey blob read as an animal at item size.
MOLE_NOSE = (0xBE, 0x7E, 0x86)
MOLE_CLAW = (0xDA, 0xD0, 0xBA)

#: Sackcloth: root fibre beaten out and woven. Paler and a good deal less red
#: than `WOOD`, on purpose - a sack in the plank colours reads as a crate.
SACK = [
    (0x3B, 0x31, 0x22),
    (0x5B, 0x4E, 0x36),
    (0x7C, 0x6D, 0x4C),
    (0x9A, 0x8A, 0x64),
    (0xB8, 0xA9, 0x83),
]

#: Straight out of `earthworm_texture.py`.
WORM_RIM = (0x5E, 0x30, 0x35)
WORM_BODY = (0x9E, 0x5C, 0x5C)
WORM_LIT = (0xC4, 0x81, 0x79)
WORM_PALE = (0xE0, 0xA8, 0x9E)


# --- canvas ---------------------------------------------------------------

class Canvas:
    """A square RGBA image addressed with optional wrap-around.

    `wrap` defaults to on because most of these textures are `cube_all` blocks
    that tile against copies of themselves in every direction. The atlas
    textures, which pack several unrelated rectangles into one image, pass
    `wrap=False` so a root growing off one region cannot appear inside another.
    """

    def __init__(self, size=SIZE, ground=(0, 0, 0, 0)):
        from PIL import Image

        self.size = size
        self.img = Image.new("RGBA", (size, size), ground)
        self.px = self.img.load()

    def put(self, x, y, colour, wrap=True):
        if wrap:
            x %= self.size
            y %= self.size
        elif not (0 <= x < self.size and 0 <= y < self.size):
            return
        self.px[x, y] = colour if len(colour) == 4 else colour + (255,)

    def fill(self, rect, colour, wrap=False):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, colour, wrap)

    def noise(self, rect, ramp, weights, rng, wrap=False):
        """Fill a rectangle by drawing a ramp index per pixel.

        `weights` is a list of indices with repeats rather than probabilities:
        `[1, 2, 2, 2, 3]` says the base tone four times as likely as either
        neighbour. Spelling it out this way keeps the bias visible in the
        source instead of hidden in a float.
        """
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, ramp[rng.choice(weights)], wrap)

    def save(self, path):
        self.img.save(path)


# --- primitives -----------------------------------------------------------

def walk(rng, start, length, direction, wander=0.3, min_run=1):
    """A 4-connected path that mostly keeps going one way.

    Turns are 90 degrees only, so the result stays 4-connected and survives
    being thickened without leaving gaps - the same reason
    `earthworm_texture.py` refuses diagonal steps in its spine. Coordinates
    are unbounded; wrapping is the caller's business.

    `min_run` is the number of steps that must pass before another turn is
    allowed, and it is the difference between a root and a scribble. Without
    it two turns land back to back, the path doubles back on itself, and a
    thickened walk comes out as a blob with a tail rather than as something
    growing through the block.
    """
    x, y = start
    dx, dy = direction
    path = [(x, y)]
    since_turn = min_run
    for _ in range(length):
        if since_turn >= min_run and rng.random() < wander:
            dx, dy = (-dy, dx) if rng.random() < 0.5 else (dy, -dx)
            since_turn = 0
        else:
            since_turn += 1
        x += dx
        y += dy
        path.append((x, y))
    return path


def smooth(u):
    """Smoothstep, clamped. Used to shape profiles, never to blend colours -
    the ramps are stepped and stay stepped."""
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


def wave(centre, amplitude, frequency, phase, axis="x", size=SIZE):
    """A sine curve sampled on the pixel grid, and a seamless one.

    `frequency` is a whole number of periods across the tile, so the curve
    leaves one edge at exactly the height it re-enters the opposite one - which
    is what a root or a thread has to do for a wall of these blocks to look
    like anything but a wall of blocks. Runs steeper than one pixel per step
    are filled in, so the result is 8-connected and survives thickening.

    This replaced `walk` for roots and mycelium. A path that can only turn 90
    degrees comes out as a maze however gently it is steered; the eye reads the
    corners, not the drift. A curve has no corners to read.
    """
    values = [
        centre + amplitude * math.sin(2.0 * math.pi * frequency * i / size + phase)
        for i in range(size + 1)
    ]
    points = []
    for i in range(size):
        a, b = int(round(values[i])), int(round(values[i + 1]))
        step = 1 if b >= a else -1
        for v in range(a, b + step, step):
            points.append((i, v) if axis == "x" else (v, i))
    return points


def thicken(path, radius, size=SIZE, wrap=True):
    """The set of pixels a path covers once it is `radius` px wide."""
    body = set()
    span = range(radius)
    for x, y in path:
        for oy in span:
            for ox in span:
                px, py = x + ox, y + oy
                if wrap:
                    body.add((px % size, py % size))
                elif 0 <= px < size and 0 <= py < size:
                    body.add((px, py))
    return body


def silhouette(body, size=SIZE, wrap=True):
    """Split a blob into (lit, shaded, interior) by its own outline.

    A pixel with nothing above or to its left is lit; one with nothing below
    or to its right is shaded; a pixel that is both - a one pixel wide neck -
    counts as lit, because losing the highlight there breaks the outline while
    losing the shadow does not.
    """

    def occupied(x, y):
        if wrap:
            return (x % size, y % size) in body
        return (x, y) in body

    lit, shaded, interior = set(), set(), set()
    for x, y in body:
        is_lit = not occupied(x - 1, y) or not occupied(x, y - 1)
        is_shaded = not occupied(x + 1, y) or not occupied(x, y + 1)
        if is_lit:
            lit.add((x, y))
        elif is_shaded:
            shaded.add((x, y))
        else:
            interior.add((x, y))
    return lit, shaded, interior


def torus_distance(a, b, size=SIZE):
    dx = abs(a[0] - b[0])
    dy = abs(a[1] - b[1])
    dx = min(dx, size - dx)
    dy = min(dy, size - dy)
    return math.hypot(dx, dy)


def scatter_points(rng, count, min_distance, size=SIZE, tries=600):
    """Points at least `min_distance` apart, measured around the seam.

    Rejection sampling rather than a grid: a grid at this resolution reads as
    a grid. Returns fewer than `count` if the tile is too small for them,
    which is the correct failure - a forced point would sit on top of another.
    """
    points = []
    for _ in range(tries):
        if len(points) == count:
            break
        p = (rng.randrange(size), rng.randrange(size))
        if all(torus_distance(p, q, size) >= min_distance for q in points):
            points.append(p)
    return points

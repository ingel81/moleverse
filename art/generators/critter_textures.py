"""Entity and item textures for the three small burrow animals.

Painted the way `great_worm_texture.py` paints the great worm: every texel of
every face is projected back into model space first, and the colour is then a
function of where that point sits on the animal. The face rectangles come from
`critter_shapes`, the same module the geometry is built from, so a texture
cannot be painted for a layout the model no longer has.

## Colour is an index, not a mix

Same rule as the worm, and it is the rule that keeps this mod from looking like
three mods. Every effect - flank height, segment creases, the beetle's grooves,
the grub's gut - adds or subtracts levels on one scalar, and the result is
rounded once at the end. Banding is the point. A material that is genuinely a
different material, rather than a lighter shade of the same one, replaces the
colour outright instead: the grub's head capsule and the worm's clitellum are
the only two that do.

The counts, which are the check that this worked: earthworm 8, soil beetle 5,
grub 6. Blocks in this mod run to about seven. The beetle and the grub each
lost one to the detail pass: the suture is a groove in the geometry now rather
than a painted level, and the grub's ring bulges likewise carry in silhouette
what a darker level used to fake.

## Where the ramps live

`WORM` comes from `texture_kit` verbatim, so the small worm, the great worm and
the item a mole carries off are literally the same colours - the strongest thing
tying the three together and worth more than any amount of shared code.

`CHITIN` and `LARVA` are new and are defined here rather than in `texture_kit`
only because this wave does not own that file. They belong there, next to the
other ramps, and moving them is the right follow-up: a fourth creature that
picks its own beetle brown is exactly what the kit exists to prevent.

Pigment only, no lighting. Minecraft shades the six directions itself and a
baked highlight on the back fights it.
"""

import math
import random

from PIL import Image

import critter_shapes as shapes
from texture_kit import SOIL, WORM, WORM_CLITELLUM, Canvas, silhouette, smooth

ENTITY_DIR = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/entity"
ITEM_DIR = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item"

FACES = ("north", "east", "south", "west", "up", "down")


# --- ramps ----------------------------------------------------------------

#: Beetle shell, darkest first. Olive-bronze, and both halves of that are load
#: bearing.
#:
#: **Bronze**, because the top of the ramp has to clear `SOIL`'s brightest entry
#: (0x40, 0x33, 0x25). A beetle darker than the floor it walks on is a beetle
#: nobody ever sees, and "it lives in the dark" is a reason to make the animal
#: legible, not an excuse to hide it. The first ramp here topped out below the
#: soil and the shell vanished into the corridor.
#:
#: **Olive**, because the obvious way to brighten it is to go warmer, and warm
#: brown at this value is `WOOD` - the mod already has a plank ramp and a beetle
#: painted in it reads as a bit of barrel. The tell is the gap between red and
#: green: `WOOD` runs about thirty apart, this runs under ten, and that is the
#: whole difference between chitin and timber.
CHITIN = [
    (0x18, 0x16, 0x12),
    (0x26, 0x23, 0x1A),
    (0x38, 0x33, 0x23),
    (0x4C, 0x46, 0x2D),
    (0x67, 0x5F, 0x3B),
    (0x8B, 0x82, 0x52),
]

#: The grub, palest last. Deliberately the brightest thing in the dimension:
#: everything down there is a brown between 0x14 and 0x85, and a grub is the
#: pale swollen thing that does not belong - which is what makes one in a larder
#: read at a glance as something gone wrong. Stops short of white, because a
#: pure white in this palette reads as a rendering error rather than as an
#: animal.
LARVA = [
    (0x6B, 0x5A, 0x46),
    (0x8E, 0x7A, 0x5E),
    (0xB0, 0x9B, 0x77),
    (0xC9, 0xB7, 0x92),
    (0xDE, 0xD0, 0xAF),
    (0xEE, 0xE4, 0xCA),
]

#: The head capsule: chestnut, sclerotised, and the one hard part of the animal.
#: Off the ramp for the same reason the clitellum is - it is a different
#: material, not a darker shade of skin.
LARVA_HEAD = (0x6A, 0x4A, 0x33)


# --- projection -----------------------------------------------------------

def lerp(a, b, u):
    return a + (b - a) * u


def unproject(cube, face, px, py):
    """Model-space point a texel of one face stands for.

    Lifted from `great_worm_texture.unproject`, and it has to be: the six cases
    are the box unwrap Minecraft actually performs, `up` comes out reversed on
    both axes and `down` on one, and a guess there mirrors the gradient on the
    most visible face of the animal.

    The point is in the cube's own frame - `critter_shapes` writes every box
    relative to its bone pivot - so zero is the part's own floor and centre line.
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


def flank(cube, face, y):
    """How far up a point sits on the part, 0 at the belly and 1 at the back.

    The two horizontal faces are the extremes outright. `face` is the only thing
    any pattern here takes from the geometry; everything else is a function of
    position, which is what lets a rule survive a part boundary.
    """
    if face == "up":
        return 1.0
    if face == "down":
        return 0.0
    height = cube["to"][1] - cube["from"][1]
    return (y - cube["from"][1]) / max(height, 1e-6)


def body_span(cubes):
    """Front and back of the whole animal, in the frame `along` is measured in.

    Pivot plus the box, so that a rule written as "the front fifth of the
    animal" means the same thing on the head box and on the tail box. The bones
    that carry a resting rotation are read unrotated, which is close enough for
    a gradient and exact for everything that matters.
    """
    front = min(c["pivot"][2] + c["from"][2] for c in cubes)
    back = max(c["pivot"][2] + c["to"][2] for c in cubes)
    return front, back


def paint(cubes, width, height, surface):
    """Runs `surface` over every texel of every face and writes the atlas."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for cube in cubes:
        for face in FACES:
            u1, v1, u2, v2 = cube["faces"][face]
            for y in range(min(v1, v2), max(v1, v2)):
                for x in range(min(u1, u2), max(u1, u2)):
                    point = unproject(cube, face, x, y)
                    ramp, level, override = surface(cube, face, *point)
                    if override is not None:
                        px[x, y] = override + (255,)
                    else:
                        index = max(0, min(len(ramp) - 1, int(round(level))))
                        px[x, y] = ramp[index] + (255,)
    return img


# --- earthworm ------------------------------------------------------------

#: Where the painted clitellum band sits, as a fraction of the body: exactly
#: under the geometry ring, which lives on the segment nearest a third of the
#: way back. The band is entirely buried by the ring, but a rule that quietly
#: depends on another box hiding its output is a trap for the next resize, so
#: the two are kept aligned rather than one deleted.
WORM_CLITELLUM_FROM, WORM_CLITELLUM_TO = 0.29, 0.33

#: How far up the flank the clitellum reaches. A band of even weight all the way
#: round reads as a painted stripe rather than as a swelling.
WORM_CLITELLUM_FOOT = 0.30

#: The wet reflection: a hard one-row band riding the upper flank, promoted
#: from variant C of the comparison sheet. It sits below the spine, not on
#: it - the spine faces the sky, the reflection faces the viewer - and it is
#: hard-edged because a soft gleam at three texels of flank is a smudge.
WORM_SHEEN_UP = (0.66, 0.84)
WORM_SHEEN = 1.2

#: The ring joints, on the great worm's own three unit grid, keyed on
#: absolute position so the pattern runs unbroken over every face.
#:
#: This exists because of an in-game lesson worth keeping: the first pass put
#: the segment shading only on the boxes' north and south ends - and on a
#: body whose boxes overlap a unit on either side, those ends are exactly the
#: faces the neighbouring segments bury. Every up face came out one flat
#: tone, and a player four times this animal's height sees almost nothing
#: but up faces. Detail an occluded face carries is detail the game never
#: shows; anything that must read from above has to be keyed on position and
#: painted across the faces that actually face the sky.
WORM_RING_PITCH = 3
WORM_RING_WIDTH = 1.0


def earthworm_surface(front, back):
    def surface(cube, face, x, y, z):
        along = (cube["pivot"][2] + z - front) / (back - front)
        up = flank(cube, face, y)
        on_band = WORM_CLITELLUM_FROM <= along < WORM_CLITELLUM_TO

        # Pale ventral at the top of the ramp, dark red-brown dorsal at the
        # bottom. The same numbers the great worm uses, because it is the same
        # animal and a second set fitted by eye would show as a different one.
        level = 7.0 - 4.4 * smooth(up)
        level -= 1.1 * smooth((0.18 - along) / 0.18)
        level += 0.9 * smooth((along - 0.70) / 0.30)

        if WORM_SHEEN_UP[0] < up < WORM_SHEEN_UP[1]:
            level += WORM_SHEEN

        # The prostomium is darker, working flesh; the paddle takes the tail
        # tint the `along` ramp already gave it.
        if cube["name"] == "prostomium":
            level -= 1.2

        # The ring joints, exactly as the great worm carries them: a north or
        # south face is a whole box end at one constant z and is taken as a
        # joint outright, and everything else darkens on the three unit grid
        # keyed to absolute position - which is what puts the segmentation on
        # the up faces a player actually looks at. See WORM_RING_PITCH.
        z_abs = cube["pivot"][2] + z
        joint = face in ("north", "south") \
            or (z_abs - front) % WORM_RING_PITCH < WORM_RING_WIDTH
        if joint and not on_band and cube["name"] != "clitellum":
            level -= 1.6

        # No dorsal blood vessel. It is two units wide on an animal whose widest
        # segment is four, so it would not read as a line down the spine - it
        # would read as the spine being a different colour. The great worm's
        # notes call this out as the trap: a feature sized in absolute units is
        # sized relative to nothing.

        # The raised ring the geometry now carries. Saddle-painted off its own
        # box: clitellum colour above the foot, skin below, because a real
        # clitellum is open beneath. The painted band on the segment under it
        # stays: it is entirely buried, but a layout rule that quietly depends
        # on another box hiding its output is a trap for the next resize.
        if cube["name"] == "clitellum":
            if up > WORM_CLITELLUM_FOOT:
                return WORM, level, WORM_CLITELLUM
            return WORM, level, None

        if on_band and up > WORM_CLITELLUM_FOOT:
            return WORM, level, WORM_CLITELLUM
        return WORM, level, None

    return surface


# --- soil beetle ----------------------------------------------------------

#: The elytral suture and the striae either side of it, as fractions of the
#: half-width of whatever box they are being drawn on.
#:
#: Fractions and not units, and that is the whole of what went wrong on the
#: first pass. Written as absolute units the seam was two texels wide on both
#: the shell and the crown - which is a third of the shell and the entire crown,
#: so the crown came out as seam and groove with no shell colour left in it and
#: the beetle rendered in three colours. It is the trap `great_worm_texture.py`
#: writes down: a feature sized in absolute units is sized relative to nothing.
#: Check any rule keyed on position against the narrowest box in the model, not
#: the one that was in front of you when you wrote it.
#:
#: The seam is tight enough to catch one texel on a seven wide back. That is why
#: the shell is seven wide and not six - see `critter_shapes.BEETLE_BODY`. The
#: numbers here and the width there are one decision and have to move together.
#:
#: There are no striae. A real ground beetle has grooves either side of the
#: suture and they were drawn twice, and both times the back came out as a set of
#: stripes rather than as a shell with a seam. The reason is the ramp, not the
#: layout: `CHITIN` steps about half again in brightness per level, so even a
#: one-level groove is a hard band, and on seven texels the pattern lands as
#: dark-light-dark-light across the whole width. Marks that must alternate need
#: either more texels to alternate over or a finer ramp to alternate on, and this
#: animal has neither. What survives is the one mark that carries the animal.
BEETLE_SEAM = 0.20

def beetle_surface(_front, _back):
    def surface(cube, face, x, y, z):
        name = cube["name"]

        # Legs, feelers and mouth-parts: a step LIGHTER than the shell's
        # sides. The first pass put them at the bottom of the ramp and in
        # game they vanished into the shell's shadow. The mandibles go a
        # half-step brighter still - they are the two texels that make the
        # front of the animal a face.
        if name.startswith(("coxa_", "tibia_", "foot_", "scape_", "flagellum_", "club_")):
            return CHITIN, 2.6, None
        if name.startswith("mandible_"):
            return CHITIN, 3.4, None

        if name == "head":
            return CHITIN, 3.0 if face == "up" else 2.0, None

        if name == "pronotum":
            # The shield carries the animal's one reliable highlight, with a
            # lit rear rim where its edge steps down onto the wing cases.
            if face == "up":
                depth = max(cube["to"][2] - cube["from"][2], 1e-6)
                rim = (cube["to"][2] - z) < 1.0
                return CHITIN, 5.0 if rim else 4.4, None
            return CHITIN, 2.0, None

        if face == "down":
            # The underside, lifted off the ramp's floor so the animal has a
            # visible belly between its visible legs.
            return CHITIN, 2.0, None

        if name.startswith("elytron"):
            if face in ("north", "south"):
                return CHITIN, 1.4, None
            if face in ("east", "west"):
                # The outer wall of the wing case, with a rim-light row along
                # its top edge: the one bright line that draws the whole
                # silhouette in a dark corridor.
                top = (y - cube["from"][1]) > (cube["to"][1] - cube["from"][1]) - 1.0
                return CHITIN, 3.2 if top else 1.4, None
            # The top: directional ridging running the body's length, done
            # with ADJACENT ramp steps (the full-step attempt striped), plus
            # punctation - the dot rows a ground beetle's elytra carry - as
            # sparse single texels in the raised columns, offset per row so
            # they read as stippling rather than as a grid.
            level = 4.0
            ridge = int(math.floor(abs(x))) % 2 == 1
            if ridge:
                level -= 0.6
            elif (int(math.floor(z)) * 2 + int(math.floor(abs(x)))) % 4 == 0:
                level -= 0.6
            return CHITIN, level, None

        # The shell: dark rim walls, and a top that is mostly hidden under
        # the plates - except the suture groove's floor, the lit centre
        # column the odd shell width exists to provide.
        if face == "up":
            half = max((cube["to"][0] - cube["from"][0]) / 2.0, 1e-6)
            if abs(x) / half < BEETLE_SEAM:
                return CHITIN, 5.0, None
            return CHITIN, 3.0, None
        return CHITIN, 1.4, None

    return surface


# --- grub -----------------------------------------------------------------

#: The gut LINE: the dark of a full digestive tract showing through
#: translucent cuticle, which the in-game verdict named as THE grub identity
#: mark. Two pieces, both position-keyed so they run unbroken across segment
#: and ring boundaries: a band along each flank at mid-height, and a stripe
#: down the middle of the back where the tract sits closest to the skin.
#: Confined to the mid-body - head and tail ends stay clean fat.
GRUB_GUT_ALONG = (0.22, 0.85)
GRUB_GUT_UP = (0.35, 0.62)
GRUB_GUT_DEPTH = 1.6

#: The spiracle row: one dark texel per segment along each flank. Real, small,
#: and the reason to look twice at a thing that is otherwise a blob.
GRUB_SPIRACLE_UP = (0.40, 0.64)
GRUB_SPIRACLE_Z = 0.7


def grub_surface(front, back):
    def surface(cube, face, x, y, z):
        name = cube["name"]
        if name in ("head", "face"):
            # The amber capsule, both boxes: a different material, not a
            # darker skin, exactly as the worm's clitellum is.
            return LARVA, 0.0, LARVA_HEAD
        if name.startswith("leg_"):
            # The folded true legs: capsule-toned, because they are the same
            # sclerotised stuff, and one flat tone at one texel each.
            return LARVA, 0.0, LARVA_HEAD

        along = (cube["pivot"][2] + z - front) / (back - front)
        up = flank(cube, face, y)

        # Pale all over. A grub has no counter-shading worth the name; what it
        # has is folds and a gut, and both are below.
        level = 4.0
        if face == "down":
            level = 3.0

        # The creases between segments. On an animal this fat they are deep, and
        # they are the whole reason the silhouette reads as segmented rather
        # than as one lump. The rings are exempt: their ends sit mid-segment,
        # where a fold's shadow would say "joint" in the one place there is
        # none - the bulge is left to Minecraft's own face shading.
        if face in ("north", "south") and not name.startswith("ring"):
            level -= 2.0

        # The gut line. See GRUB_GUT_ALONG.
        if GRUB_GUT_ALONG[0] < along < GRUB_GUT_ALONG[1]:
            if face in ("east", "west") and GRUB_GUT_UP[0] < up < GRUB_GUT_UP[1]:
                level -= GRUB_GUT_DEPTH
            if face == "up":
                centre = (cube["from"][0] + cube["to"][0]) / 2.0
                half = max((cube["to"][0] - cube["from"][0]) / 2.0, 1e-6)
                if abs(x - centre) < half * 0.3:
                    level -= GRUB_GUT_DEPTH

        low, high = GRUB_SPIRACLE_UP
        if face in ("east", "west") and low < up < high and abs(z) < GRUB_SPIRACLE_Z:
            level -= 2.5

        return LARVA, level, None

    return surface


# --- item textures --------------------------------------------------------

SIZE = 16


def egg_body(width, height, centre_y):
    """The pixels of a spawn egg, from a profile rather than a drawn outline.

    An egg is narrower at the top than the bottom, and the half-width at each
    row is a circle stretched by a factor that varies down the egg. Generated
    because three eggs drawn by hand come out as three different eggs, and the
    one thing a set of spawn eggs has to be is a set.
    """
    body = set()
    top = centre_y - height / 2.0
    for row in range(SIZE):
        t = (row + 0.5 - top) / height
        if not 0.0 <= t <= 1.0:
            continue
        # A half-ellipse for the crown and a fuller one for the base, joined at
        # the widest point a third of the way down.
        radius = math.sqrt(max(0.0, 1.0 - ((t - 0.62) / 0.62) ** 2)) if t > 0.62 \
            else math.sqrt(max(0.0, 1.0 - ((0.62 - t) / 0.62) ** 2)) * (0.62 + 0.38 * t)
        half = radius * width / 2.0
        for column in range(SIZE):
            if abs(column + 0.5 - SIZE / 2.0) <= half:
                body.add((column, row))
    return body


def spawn_egg(ramp, spot, seed):
    """One spawn egg: shell from the ramp, spots from the creature's own colour.

    Shaded off its own outline with `texture_kit.silhouette`, the rule the whole
    mod's item art uses - a pixel with nothing above or to its left catches the
    light. Deriving the shading from the outline is what keeps the highlight on
    the edge instead of eating into the middle.
    """
    rng = random.Random(seed)
    canvas = Canvas(SIZE)
    body = egg_body(width=10.0, height=13.0, centre_y=8.0)
    lit, shaded, interior = silhouette(body, SIZE, wrap=False)

    # Seeded from the interior rather than from a rectangle over the whole tile.
    # A blind rectangle put most of the spots on the lit and shaded rim, where
    # they are discarded to keep the outline intact, and an egg came out with
    # two spots on it instead of six.
    field = sorted(interior)
    spots = set()
    for anchor in rng.sample(field, min(6, len(field))):
        x, y = anchor
        spots |= {(x, y), (x + 1, y)} if rng.random() < 0.5 else {(x, y), (x, y + 1)}

    for x, y in sorted(body):
        if (x, y) in spots and (x, y) in interior:
            colour = spot
        elif (x, y) in lit:
            colour = ramp[-1]
        elif (x, y) in shaded:
            colour = ramp[0]
        else:
            colour = ramp[len(ramp) // 2]
        canvas.put(x, y, colour, wrap=False)
    return canvas


def chitin_flake():
    """A shard of elytra, snapped off along a groove.

    The outline is a lens - two arcs meeting at points - because a beetle's wing
    case is curved in both directions and a rectangle reads as leather. The
    grooves are the same striae the entity texture carries, so the flake is
    recognisably a piece of the animal it came off.
    """
    canvas = Canvas(SIZE)
    body = set()
    for y in range(SIZE):
        t = (y - 2.0) / 12.0
        if not 0.0 <= t <= 1.0:
            continue
        half = 4.2 * math.sin(math.pi * t) ** 0.7
        centre = 8.0 - 1.6 * math.sin(math.pi * t)
        for x in range(SIZE):
            if abs(x + 0.5 - centre) <= half:
                body.add((x, y))

    lit, shaded, interior = silhouette(body, SIZE, wrap=False)
    for x, y in sorted(body):
        t = (y - 2.0) / 12.0
        centre = 8.0 - 1.6 * math.sin(math.pi * t)
        groove = int(math.floor(abs(x + 0.5 - centre))) % 2 == 1
        # A level brighter throughout than the animal it comes off. An item is
        # judged at sixteen pixels against the bright ground of an inventory
        # slot, not on a corridor wall, and the entity's near-black shell there
        # is a hole in the grid.
        if (x, y) in lit:
            level = 5
        elif (x, y) in shaded:
            level = 2
        else:
            level = 3 if groove else 4
        canvas.put(x, y, CHITIN[level], wrap=False)
    return canvas


# --- entry points ---------------------------------------------------------

ENTITIES = {
    "earthworm": earthworm_surface,
    "soil_beetle": beetle_surface,
    "grub": grub_surface,
}

#: Shell ramp and spot colour per egg. Each takes its two colours from the
#: animal it hatches, so the egg is readable before the tooltip is.
EGGS = {
    "earthworm_spawn_egg": (WORM[1:5], WORM_CLITELLUM, 11),
    "soil_beetle_spawn_egg": (CHITIN[:5], CHITIN[5], 22),
    "grub_spawn_egg": (LARVA[1:5], LARVA_HEAD, 33),
    # Not a wave-A animal, and here for one reason: the item exists, its model
    # asks for this file, and nothing has ever written it - so a great worm
    # spawn egg is a magenta cube in the creative tab today. Two colours off the
    # ramp the animal is already painted from cost nothing.
    "great_worm_spawn_egg": (WORM[1:5], SOIL[2], 44),
}


def write_all():
    written = []
    for name, factory in ENTITIES.items():
        cubes, width, height = shapes.build(name)
        front, back = body_span(cubes)
        image = paint(cubes, width, height, factory(front, back))
        path = "%s/%s.png" % (ENTITY_DIR, name)
        image.save(path)
        written.append((path, len(set(image.get_flattened_data()) - {(0, 0, 0, 0)})))

    for name, (ramp, spot, seed) in EGGS.items():
        path = "%s/%s.png" % (ITEM_DIR, name)
        spawn_egg(ramp, spot, seed).save(path)
        written.append((path, None))

    path = "%s/chitin_flake.png" % ITEM_DIR
    chitin_flake().save(path)
    written.append((path, None))
    return written


def preview(path, scale=10):
    """A magnified contact sheet of everything this script writes.

    Judge the textures here, not at 32 px. A gradient that runs the wrong way
    across one face, a groove that has landed between two texels, a spot of
    ramp that never got used - none of them are visible at native size and all
    of them are obvious at ten times it.
    """
    tiles = [(name, Image.open("%s/%s.png" % (ENTITY_DIR, name))) for name in ENTITIES]
    tiles += [(name, Image.open("%s/%s.png" % (ITEM_DIR, name))) for name in EGGS]
    tiles.append(("chitin_flake", Image.open("%s/chitin_flake.png" % ITEM_DIR)))

    pad = 8
    width = sum(tile.width * scale + pad for _, tile in tiles) + pad
    height = max(tile.height * scale for _, tile in tiles) + 2 * pad
    sheet = Image.new("RGBA", (width, height), (0x18, 0x18, 0x18, 255))

    x = pad
    for _, tile in tiles:
        scaled = tile.resize((tile.width * scale, tile.height * scale), Image.NEAREST)
        sheet.alpha_composite(scaled, (x, pad))
        x += scaled.width + pad
    sheet.save(path)
    return path


if __name__ == "__main__":
    import sys

    for path, colours in write_all():
        print("wrote", path, "" if colours is None else "- %d colours" % colours)

    if "--preview" in sys.argv:
        print("preview", preview(sys.argv[sys.argv.index("--preview") + 1]))

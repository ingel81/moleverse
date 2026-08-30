"""Entity and item textures for the burrow's two predators.

Painted the way `critter_textures.py` paints the small animals, and importing its
projection outright: every texel of every face is unprojected into model space
first, and the colour is then a function of where that point sits on the animal.
The face rectangles come from `predator_shapes`, the same module the geometry is
built from, so a texture cannot be painted for a layout the model no longer has.

## Colour is an index, not a mix

The mod's rule, unchanged. Every effect adds or subtracts levels on one scalar
and the result is rounded once at the end; banding is the point. A material that
is genuinely a different material replaces the colour outright instead, and here
there are two of those: the weasel's belly and the black of its tail tip. Both
have to be hard-edged - a stoat's flank line is the sharpest boundary on any
animal in this dimension, and a smoothed one would read as a dirty weasel.

The counts, which are the check that this worked: shrew 7, weasel 8. Earthworm
is 8, grub 6, beetle 5.

## What separates these two from everything already down here

Both are mammals in a palette full of soil, root and chitin, so both were checked
against the ramps they could be confused with rather than picked in isolation:

* **Shrew against `MOLE`.** The mole is a blue-grey - `texture_kit` says so and
  says why. The shrew is the same value and warm, so the two read as different
  animals at the same distance without either becoming a colour the dimension
  does not own.
* **Weasel against `ROOT`.** This is the near miss. A chestnut ramp at bark
  values *is* the bark ramp, and the first pass came out as an animal-shaped
  piece of root. The fix is saturation, not value: `ROOT` runs about thirty
  between red and green at its brightest, this runs sixty-six. Fur is pigment and
  bark is not.
* **Weasel belly against `LARVA`.** The grub is the brightest thing in the
  burrow and that is load-bearing, so the belly stops short of it and goes cool
  where the grub is warm. Two pale things are fine as long as only one of them is
  the anomaly.

Pigment only, no lighting. Minecraft shades the six directions itself and a baked
highlight on the back fights it.
"""

import math

from PIL import Image

import predator_shapes as shapes
from critter_textures import ENTITY_DIR, ITEM_DIR, body_span, flank, paint, spawn_egg


# --- ramps ----------------------------------------------------------------

#: The shrew, darkest first. Grey-brown velvet: dark above, pale below, and
#: nothing else, because that is the whole of a shrew's colouring.
#:
#: The ramp is pitched so the *back* sits at `SOIL`'s brightest entry
#: (0x40, 0x33, 0x25) rather than under it, and that is a correction, not the
#: first guess. The first ramp put the dorsum four levels below the floor -
#: honest counter-shading, and in the viewport a black blob. The reason it fails
#: here and not on a real shrew is the camera: the player is four times this
#: animal's height and therefore sees almost nothing but its top faces, so
#: whatever colour the back is, is the colour of the animal. The pale belly still
#: does its work on the flank line, which is what actually names the shape when
#: one runs past.
#:
#: `CHITIN` records the same fix in the same words. Two animals into this
#: dimension it is a rule: nothing that walks the corridor floor may be darker
#: than the corridor floor.
SHREW = [
    (0x2A, 0x25, 0x21),
    (0x39, 0x33, 0x2C),
    (0x4B, 0x43, 0x3A),
    (0x60, 0x57, 0x4B),
    (0x7A, 0x70, 0x62),
    (0x99, 0x91, 0x82),
]

#: The bare skin of the snout tip and the ear rims. Off the ramp, because it is
#: skin rather than fur.
#:
#: Deliberately in the same family as `texture_kit.MOLE_NOSE` and deliberately
#: duller. Moles and shrews are close relatives and the shared pink is worth
#: having; an exact match would read as a copied constant.
SHREW_NOSE = (0xB0, 0x82, 0x7E)

#: The weasel, darkest first. Chestnut, and saturated enough not to be `ROOT` -
#: see the module note, this is the ramp that had to be fixed twice.
WEASEL = [
    (0x2B, 0x19, 0x0F),
    (0x40, 0x25, 0x14),
    (0x5C, 0x33, 0x1B),
    (0x7B, 0x45, 0x23),
    (0x9B, 0x5C, 0x30),
    (0xBA, 0x78, 0x44),
]

#: The belly, throat and chin: one colour, no shading, hard edge.
#:
#: One and not two on purpose. A gradient here would be a gradient across the one
#: boundary on this animal that is genuinely abrupt - the pelage changes species
#: of hair at that line - and it would also push the palette to nine colours for
#: a step nobody can see. Cool where `LARVA` is warm, so the burrow still has
#: exactly one thing in it that is pale and wrong.
WEASEL_BELLY = (0xD2, 0xCE, 0xC0)

#: The tail tip, and the nose pad, and the eyes. Darker than the ramp's darkest,
#: because black on a chestnut animal has to actually be black.
#:
#: One colour doing three jobs is what a tight palette looks like. The tail tip is
#: the mark that names the animal at any distance; the nose and the eye are the
#: two texels that turn the front of it into a face.
WEASEL_TIP = (0x17, 0x11, 0x0D)


# --- shrew ----------------------------------------------------------------

#: How much of the animal, measured from the nose, is nose pad.
SHREW_NOSE_TIP = 0.05

#: Where the face pales, as a fraction from the nose back. Shrews carry a lighter
#: muzzle, and it is what stops the long snout from reading as a shadow.
SHREW_MUZZLE = 0.22

#: The eye: a dark texel on each side of the head. Small, real, and the difference
#: between an animal and a lump - the beetle's spiracle trick on a mammal.
SHREW_EYE_UP = (0.45, 0.80)
SHREW_EYE_Z = (0.55, 0.95)


def shrew_surface(front, back):
    def surface(cube, face, x, y, z):
        name = cube["name"]
        up = flank(cube, face, y)
        along = (cube["pivot"][2] + z - front) / (back - front)

        if name == "snout_tip":
            # The bare nose, all of it: the last unit of the wedge is skin,
            # and one solid pink texel-cluster at the point is what makes the
            # whole face read as a shrew's from across a corridor.
            return SHREW, 0.0, SHREW_NOSE

        if name.startswith("ear_"):
            # Bare, dark skin. An ear one texel square carries no pattern.
            return SHREW, 1.5, None

        if name.startswith("leg_"):
            return SHREW, 2.0, None

        if name.startswith("tail"):
            # Bicoloured like the body but flatter. A shrew's tail is scaly and
            # short-haired, so it never reaches either end of the ramp.
            return SHREW, 3.5 - 1.5 * up, None

        # Counter-shading first: dark dorsum, mid flank. The belly is taken
        # OUT of the fade and set hard instead - velvet-grey fur above, pale
        # fur below, and on a real shrew that boundary is abrupt enough to
        # read at a glance. The fade alone was the old texture, and in game
        # it read as one flat tone per face.
        level = 5.0 - 3.4 * smoothed(up)
        if face == "down" or up < 0.22:
            level = 5.2

        # The fur direction. Short strokes running down the body: every third
        # texel column drops a level, and each row of flank shifts the phase,
        # so the strokes lie diagonally the way brushed fur does. Clumps, not
        # noise - the stroke is two texels of the three, keyed on position so
        # it runs unbroken across segment boundaries, and it lives on the
        # faces a player actually sees (flanks and back, not the buried box
        # ends). The face chain is exempt: a shrew's head fur is too short to
        # show direction, and strokes there would fight the eye and the nose.
        if face in ("east", "west", "up") \
                and name not in ("head", "muzzle", "snout"):
            z_abs = cube["pivot"][2] + z
            if (int(math.floor(z_abs)) + 2 * int(math.floor(y))) % 3 == 0:
                level -= 0.8

        if name == "snout":
            if face == "north" or along < SHREW_NOSE_TIP:
                return SHREW, 0.0, SHREW_NOSE
            level += 0.8

        # The muzzle pales towards the front, on the head as well as the snout,
        # so the two parts read as one face rather than as a nose stuck on.
        level += 0.8 * (1.0 - min(1.0, along / SHREW_MUZZLE))

        if name == "head" and face in ("east", "west") \
                and SHREW_EYE_UP[0] < up < SHREW_EYE_UP[1] \
                and SHREW_EYE_Z[0] < along_box(cube, z) < SHREW_EYE_Z[1]:
            level -= 3.0

        return SHREW, level, None

    return surface


# --- weasel ---------------------------------------------------------------

#: How far up the flank the belly reaches. Just over a third, which on a five
#: unit trunk is the bottom two texels - and two is the fewest that still reads
#: as a field rather than as a line.
WEASEL_BELLY_LINE = 0.42

#: How far that line wanders, and how many times along the animal.
#:
#: The line is sharp and it is not straight, and the second half is the one worth
#: coding. Drawn level it came out as a painted waterline on a boat; a real
#: stoat's boundary rises over the shoulder, dips at the waist and rises again at
#: the haunch. The amplitude is set against the texel grid rather than by eye - a
#: five unit trunk puts texel centres at 0.1, 0.3, 0.5, 0.7 and 0.9, so anything
#: under about 0.13 moves the line by less than one texel and is a number that
#: changes nothing.
WEASEL_LINE_WANDER = 0.15
WEASEL_LINE_PERIODS = 1.5

#: The eye, in the same two windows the shrew's uses.
WEASEL_EYE_UP = (0.50, 0.85)
WEASEL_EYE_Z = (0.55, 0.95)

#: How much darker the head is than the trunk. Stoats carry a darker mask than
#: back, and on an animal this plain it is one of only two marks above the line.
WEASEL_MASK = 1.2


def weasel_surface(front, back):
    def surface(cube, face, x, y, z):
        name = cube["name"]
        up = flank(cube, face, y)
        along = (cube["pivot"][2] + z - front) / (back - front)

        # The tip. Everything about this animal that a player remembers is this
        # box, so it is not shaded, not ramped, and not softened at the joint.
        if name == "tail1":
            return WEASEL, 0.0, WEASEL_TIP

        if name == "tail0":
            return WEASEL, 3.0, None

        if name.startswith("ear_"):
            return WEASEL, 4.0 if face == "up" else 1.5, None

        if name.startswith(("leg_", "paw_")):
            # Dark, and no white on them, paws included. A stoat's white stops
            # at the shoulder; legs that carried it would read as socks and
            # turn the animal into a ferret.
            return WEASEL, 2.0, None

        if name == "snout" and face == "north":
            # The nose pad, and only the nose pad.
            #
            # The first version took the bridge of the muzzle as well, on the
            # theory that a stoat's nose is not a flat disc. On a snout two units
            # high that clause claimed the whole top face - `flank` returns 1.0
            # for `up` outright - and the animal came out with a black muzzle
            # stuck on the front of a black mask. Everything below falls through
            # to the ordinary rules, which give it the white chin and the brown
            # bridge a stoat actually has.
            return WEASEL, 0.0, WEASEL_TIP

        # The line. Hard, because on this animal it is - see the ramp note - and
        # not level, because on this animal it is not.
        line = WEASEL_BELLY_LINE + WEASEL_LINE_WANDER * math.sin(
            math.pi * WEASEL_LINE_PERIODS * along)
        if face == "down" or up < line:
            return WEASEL, 0.0, WEASEL_BELLY

        if name == "head" and face in ("east", "west") \
                and WEASEL_EYE_UP[0] < up < WEASEL_EYE_UP[1] \
                and WEASEL_EYE_Z[0] < along_box(cube, z) < WEASEL_EYE_Z[1]:
            return WEASEL, 0.0, WEASEL_TIP

        # Above the line: mid-chestnut at the flank, darkening to the spine. The
        # span is wide on purpose. A narrower one left the whole back on two ramp
        # entries, and a two-tone animal painted in two tones is a cut-out.
        level = 4.8 - 3.2 * smoothed((up - line) / (1.0 - line))

        # Fur direction: short diagonal strokes on the FLANKS only, above the
        # belly line, position-keyed so they cross segment boundaries
        # unbroken. The back is deliberately exempt, and that is the weasel
        # departing from the shrew's rule for a measured reason: WEASEL's
        # ramp steps half again in brightness per level and the spine fade
        # crosses a rounding boundary across most of the back, so any
        # constant stroke offset there tipped whole columns and the animal
        # came out tiger-striped. On the flanks the same offset lands as
        # ticking against the white line, which is where brushed fur actually
        # shows on a stoat.
        if face in ("east", "west") \
                and name not in ("head", "muzzle", "snout"):
            z_abs = cube["pivot"][2] + z
            if (int(math.floor(z_abs)) + 2 * int(math.floor(y))) % 3 == 0:
                level -= 0.6

        if name in ("head", "muzzle"):
            # The mask covers the muzzle too: a pale step between two dark
            # boxes would read as a bandage.
            level -= WEASEL_MASK
        return WEASEL, level, None

    return surface


# --- helpers --------------------------------------------------------------

def along_box(cube, z):
    """How far back a point sits on its own box, 0 at the front and 1 at the back.

    Measured across the box and not across the animal, which is the trap
    `critter_textures` writes down twice: taken over the whole animal a window
    like "the front third of the head" is stretched by the snout in front of it
    and the tail behind it, and lands somewhere else entirely.
    """
    depth = max(cube["to"][2] - cube["from"][2], 1e-6)
    return (z - cube["from"][2]) / depth


def smoothed(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


# --- entry points ---------------------------------------------------------

ENTITIES = {
    "shrew": shrew_surface,
    "weasel": weasel_surface,
}

#: Shell ramp and spot colour per egg, each taken from the animal it hatches, so
#: the egg is readable before the tooltip is. Through `critter_textures.spawn_egg`
#: rather than a copy of it - five spawn eggs drawn by five hands are five eggs,
#: and the one thing a set of them has to be is a set.
EGGS = {
    "shrew_spawn_egg": (SHREW[1:5], SHREW_NOSE, 55),
    "weasel_spawn_egg": (WEASEL[1:5], WEASEL_BELLY, 66),
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
    return written


def preview(path, scale=10):
    """A magnified contact sheet of everything this script writes.

    Judge the textures here, not at 64 px. A gradient running the wrong way
    across one face, a belly line that has landed one texel too high, a spot of
    ramp that never got used - none of them are visible at native size and all of
    them are obvious at ten times it.
    """
    tiles = [Image.open("%s/%s.png" % (ENTITY_DIR, name)) for name in ENTITIES]
    tiles += [Image.open("%s/%s.png" % (ITEM_DIR, name)) for name in EGGS]

    pad = 8
    width = sum(tile.width * scale + pad for tile in tiles) + pad
    height = max(tile.height * scale for tile in tiles) + 2 * pad
    sheet = Image.new("RGBA", (width, height), (0x18, 0x18, 0x18, 255))

    x = pad
    for tile in tiles:
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

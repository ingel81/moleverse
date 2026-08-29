"""The mole-in-a-sack item icon, 16x16 on transparent ground.

A live mole carried in a tied sack. It shipped as a byte-for-byte copy of the
earthworm, which meant the two most different things a mole hunter can be
holding looked identical in the hotbar.

The one thing this icon has to do is not be `mole_pelt.png`. The pelt is a
round dark grey disc on a stretching rod - so the sack is the opposite on every
axis the eye checks first: tall rather than round, warm buff rather than cold
grey, and with a hard horizontal accent low at the neck rather than high across
the top. Two icons that differ only in detail are the same icon at hotbar size.

The bag comes off a profile curve rather than placed pixel by pixel: half-width
as a function of depth, swelling from the neck to the belly and tucking back in
at the floor, with a hump added on one side where the animal is shoving. That
is also the only honest way to get the bulge - a hand-drawn one either reads as
a lopsided bag or disappears.

The mole's head is the exception and is a hand-placed pixel map. At four by
three pixels a formula has nothing to work with, and the difference between a
snout and a smudge is one pixel in the right place.
"""

import argparse
import math
import random

from texture_kit import (
    MOLE,
    MOLE_CLAW,
    MOLE_NOSE,
    ROOT,
    SACK,
    SIZE,
    Canvas,
    silhouette,
    smooth,
)

OUT = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item/mole_in_sack.png"

#: The cord sits at `CORD_Y`, the bag hangs from `NECK_Y` to `FLOOR_Y`, and the
#: two rows above the cord are the cloth gathered over the tie.
CORD_Y, NECK_Y, FLOOR_Y = 4, 5, 14
GATHER_ROWS = (2, 3)

#: Centre of the bag. The fraction is deliberate: a bag on a whole pixel is
#: symmetrical, and a sack with a live mole in it should not be.
CX = 8.0

#: The mole's snout, forced out sideways where the cord does not quite close.
#: Hand placed - see the module docstring. Values pick the tone off `MOLE`.
SNOUT = {
    (3, 3): 3, (4, 3): 0, (5, 3): 2,
    (2, 4): 2, (3, 4): 2, (4, 4): 2, (5, 4): 1,
}
NOSE = (2, 4)
#: One claw over the rim on the other side. A single pale pixel: two read as
#: a pair of eyes and the whole thing turns into a face.
PAW = (10, 3)


def half_width(t):
    """Half-width of the bag at depth `t`, 0 at the neck and 1 at the floor.

    Swells out of the neck to a belly a little past halfway, then draws back in
    towards the floor. The taper is a factor on the whole width rather than a
    subtraction, which is what rounds the bottom corners off: subtracting a
    constant narrows the bag but leaves its sides vertical, and a sack with
    vertical sides is a crate.
    """
    return 1.1 + 4.7 * smooth(t / 0.55) * (1.0 - 0.30 * smooth((t - 0.70) / 0.30))


def bulge(t):
    """How far the animal is shoving the right-hand wall out at depth `t`."""
    return 1.0 * smooth((t - 0.16) / 0.16) * smooth((0.64 - t) / 0.18)


def build_body():
    """The bag, plus the cloth gathered over the tie and its loose corner."""
    body = set()
    for y in range(NECK_Y, FLOOR_Y + 1):
        t = (y - NECK_Y) / (FLOOR_Y - NECK_Y)
        left, right = half_width(t), half_width(t) + bulge(t)
        for x in range(SIZE):
            if -left <= x + 0.5 - CX <= right:
                body.add((x, y))

    for y, half in zip(GATHER_ROWS, (1.8, 2.6)):
        for x in range(SIZE):
            if abs(x + 0.5 - CX) <= half:
                body.add((x, y))
    body |= {(9, 1)}  # one corner of cloth standing up out of the knot
    return body


def creases(body, rng):
    """Folds running down from the tie, as rays rather than stripes.

    A sack is cloth because of what the tie does to it, so every crease starts
    at the same point. Pixels are dropped at random along each ray - an unbroken
    line at this size reads as a seam, and three seams read as a barrel.
    """
    marks = set()
    for angle in (-0.62, -0.22, 0.16, 0.54):
        for step in range(2, 12):
            x = int(round(CX + math.sin(angle) * step))
            y = NECK_Y + int(round(math.cos(angle) * step))
            if (x, y) in body and rng.random() < 0.62:
                marks.add((x, y))
    return marks


def paint(rng):
    c = Canvas()
    body = build_body()

    # Shaded off the outline, the same rule as the worms. A hard outline all
    # the way round was tried first and it eats the small shapes: the gathered
    # top and the snout are only two or three pixels thick, so every one of
    # their pixels is outline and they come out as one black blob on a hat.
    lit, shaded, interior = silhouette(body, wrap=False)
    for group, index in ((interior, 2), (lit, 4), (shaded, 0)):
        for x, y in group:
            c.put(x, y, SACK[index], wrap=False)
    for x, y in interior:  # a little weave, one ramp step either way
        if rng.random() < 0.24:
            c.put(x, y, SACK[rng.choice([1, 3, 3])], wrap=False)
    for x, y in creases(body, rng) & (interior | shaded):
        c.put(x, y, SACK[1], wrap=False)

    # The cord. `CORD_Y` is the pinch itself and carries no cloth of its own -
    # the row is the cord - so its width comes from the neck below it, widened
    # by a pixel on each side so it reads as tied round rather than painted on.
    span = [x for x, y in body if y == NECK_Y]
    for x in range(min(span) - 1, max(span) + 2):
        c.put(x, CORD_Y, ROOT[1], wrap=False)
        c.put(x, CORD_Y + 1, SACK[0], wrap=False)  # the shadow the tie casts
    c.put(max(span) + 1, CORD_Y + 1, ROOT[2], wrap=False)  # the loose end

    for (x, y), index in SNOUT.items():
        c.put(x, y, MOLE[index], wrap=False)
    c.put(*NOSE, MOLE_NOSE, wrap=False)
    c.put(*PAW, MOLE_CLAW, wrap=False)
    return c


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG", help="write a magnified sheet next to the pelt")
    parser.add_argument("--seed", type=int, default=740215)
    args = parser.parse_args()

    canvas = paint(random.Random(args.seed))
    canvas.save(OUT)
    print("wrote", OUT)

    if args.preview:
        from PIL import Image

        items = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item"
        scale = 20
        images = [canvas.img] + [
            Image.open(f"{items}/{n}.png").convert("RGBA")
            for n in ("mole_pelt", "earthworm", "fat_worm")
        ]
        sheet = Image.new(
            "RGBA", (SIZE * scale * len(images), SIZE * scale), (40, 38, 40, 255)
        )
        for i, image in enumerate(images):
            sheet.alpha_composite(
                image.resize((SIZE * scale, SIZE * scale), Image.NEAREST), (i * SIZE * scale, 0)
            )
        sheet.save(args.preview)
        print("wrote", args.preview, "- mole_in_sack, mole_pelt, earthworm, fat_worm")


if __name__ == "__main__":
    main()

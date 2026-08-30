"""The mole's own two item icons: the pelt and the spawn egg.

Both predate the texture kit and showed it. The pelt was 27 colours of
per-pixel jitter - the exact dose of noise `loose_soil` was re-rendered to get
rid of - and the spawn egg was 20 colours of hand-blended shading in a drawer
full of five colour eggs stamped by `critter_textures.spawn_egg`. Every other
item in the mod is built from the shared ramps and shaded off its own outline;
these two are brought onto the same footing rather than repainted in place.

The pelt is a splayed hide seen from above: one oval with four stubs where the
legs were, in the `MOLE` ramp read off the entity texture. Its job in the
inventory is to be the *flat grey* thing - the sack is the tall buff thing, the
worms are the red curls - so the drawing stays quiet: outline-derived shading,
a few strokes of fur lying in one direction, and nothing else. The strokes run
down the pelt the way fur lies on the animal, and they are one ramp step deep,
because fur on a dead-flat hide catches no more light than that.

The egg goes through `critter_textures.spawn_egg` unchanged, with the `MOLE`
ramp for the shell and the nose pink for the spots - the same two decisions
every other egg in the drawer made, which is the entire point.
"""

import argparse
import math
import random

from critter_textures import spawn_egg
from texture_kit import MOLE, MOLE_NOSE, SIZE, Canvas, silhouette

ITEMS = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item"


def pelt_body():
    """The hide as a set of pixels: an oval with four leg stubs.

    A formula rather than placed pixels, unlike the nodule: a pelt really is a
    round thing with lumps on it, so a formula has something to work with, and
    the stubs landing a half pixel outside the oval is what keeps them reading
    as legs rather than as a lumpy outline.
    """
    body = set()
    for y in range(SIZE):
        for x in range(SIZE):
            cx, cy = x + 0.5, y + 0.5
            if ((cx - 8.0) / 4.4) ** 2 + ((cy - 8.6) / 5.2) ** 2 <= 1.0:
                body.add((x, y))
                continue
            for sx, sy in ((3.6, 4.4), (12.4, 4.4), (3.6, 13.0), (12.4, 13.0)):
                if math.hypot(cx - sx, cy - sy) <= 1.5:
                    body.add((x, y))
    return body


def pelt(rng):
    canvas = Canvas()
    body = pelt_body()
    lit, shaded, interior = silhouette(body, wrap=False)
    for group, colour in ((interior, MOLE[2]), (shaded, MOLE[0]), (lit, MOLE[3])):
        for x, y in group:
            canvas.put(x, y, colour, wrap=False)

    # Fur, lying down the hide: short two pixel strokes, a step either side of
    # the base tone, never touching the outline. Sparse on purpose - this is
    # texture, and the moment it is countable it is pattern.
    strokes = sorted(interior)
    for x, y in rng.sample(strokes, min(9, len(strokes))):
        tone = MOLE[1] if rng.random() < 0.6 else MOLE[3]
        for step in range(2):
            if (x, y + step) in interior:
                canvas.put(x, y + step, tone, wrap=False)

    # The sheen: fur catches one line of light across the shoulders, where the
    # hide was curved before it was flat. One row, broken, top of the ramp.
    for x, y in strokes:
        if y == 5 and rng.random() < 0.6:
            canvas.put(x, y, MOLE[4], wrap=False)
    return canvas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG",
                        help="write a magnified sheet next to the sack and the other eggs")
    args = parser.parse_args()

    painted = [
        ("mole_pelt", pelt(random.Random(2603))),
        ("mole_spawn_egg", spawn_egg(MOLE, MOLE_NOSE, 88)),
    ]
    for name, canvas in painted:
        canvas.save(f"{ITEMS}/{name}.png")
        print("wrote", f"{ITEMS}/{name}.png")

    if args.preview:
        from PIL import Image

        scale = 16
        neighbours = ("mole_in_sack", "earthworm_spawn_egg", "shrew_spawn_egg")
        images = [c.img for _, c in painted] + [
            Image.open(f"{ITEMS}/{n}.png").convert("RGBA") for n in neighbours
        ]
        sheet = Image.new("RGBA", (SIZE * scale * len(images), SIZE * scale), (40, 38, 40, 255))
        for i, image in enumerate(images):
            sheet.alpha_composite(
                image.resize((SIZE * scale, SIZE * scale), Image.NEAREST),
                (i * SIZE * scale, 0),
            )
        sheet.save(args.preview)
        print("wrote", args.preview, "- mole_pelt, mole_spawn_egg, " + ", ".join(neighbours))


if __name__ == "__main__":
    main()

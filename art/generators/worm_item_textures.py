"""The fat worm and the glow worm, siblings of `earthworm.png`.

Both shipped as byte-for-byte copies of the earthworm, which is worse than a
placeholder: three items that are supposed to be three different things looked
identical in the hotbar.

They stay siblings on purpose. The spine below is the earthworm's, verbatim -
the same loose S, chosen pixel by pixel because a parametric arc came out
looking like a hook at this size - and the shading is the same rule off the
same outline. What differs is one thing per item, so the three read as a set:

* `fat_worm` - girth. The spine is thickened by a curve rather than a constant,
  three pixels through the middle and two at either end, which is what makes it
  look well fed rather than merely scaled up. A scaled copy would also lose the
  silhouette, since the whole S only just fits in sixteen pixels.
* `glow_worm` - light. The palette is washed out towards the mycelium's greens
  at the lit edge, and one flat ring of half-transparent green sits around the
  body. One ring, one alpha: a falloff would be a gradient, and the item would
  stop matching everything else in the mod.

The spine is duplicated here rather than imported because
`earthworm_texture.py` does its work at module level - importing it would
rewrite the earthworm as a side effect.
"""

import argparse

from texture_kit import (
    GLOW,
    SIZE,
    WORM_BODY,
    WORM_LIT,
    WORM_PALE,
    WORM_RIM,
    Canvas,
    silhouette,
)

ITEMS = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item"

#: The earthworm's spine, head top right and tail bottom left. Four-connected,
#: so thickening it leaves no gaps.
SPINE = [
    (11, 3), (10, 3), (9, 3),
    (9, 4), (8, 4), (8, 5), (7, 5),
    (7, 6), (7, 7), (6, 7), (6, 8), (5, 8),
    (5, 9), (4, 9), (4, 10), (4, 11),
    (5, 11), (6, 11), (6, 12), (7, 12), (8, 12), (9, 12),
    (10, 12), (10, 11),
]

#: The clitellum, about a third down from the head, as on a real worm.
BAND_FROM, BAND_TO = 6, 10

#: The glow worm's own ramp. Same four roles as the earthworm's, drained of
#: red and lifted at the top end towards `GLOW`, so the light on the animal and
#: the light on the mycelium it lives in are the same colour.
PALE_RIM = (0x7A, 0x62, 0x60)
PALE_BODY = (0xBE, 0xA2, 0x99)
PALE_LIT = (0xE0, 0xD6, 0xB6)
PALE_CORE = (0xF1, 0xF8, 0xD4)

#: The halo. Alpha rather than a lighter opaque colour, because the item is
#: drawn over whatever inventory slot it happens to sit in.
HALO = GLOW[2] + (96,)


def stamp(spine, girth):
    """The pixels a spine covers when `girth(i)` decides its width at step i.

    Anchored so the extra width grows down and right, the direction the shading
    rule already treats as away from the light.
    """
    body = set()
    for i, (x, y) in enumerate(spine):
        radius = girth(i)
        for oy in range(radius):
            for ox in range(radius):
                if 0 <= x + ox < SIZE and 0 <= y + oy < SIZE:
                    body.add((x + ox, y + oy))
    return body


def band_pixels(spine, girth):
    return stamp(spine[BAND_FROM:BAND_TO], lambda i: girth(i + BAND_FROM))


def draw(body, band, palette, extras=()):
    """Paint a worm from its outline. `palette` is (rim, body, lit, band)."""
    rim, mid, lit_colour, band_colour = palette
    canvas = Canvas()
    for x, y in extras:
        canvas.put(x, y, HALO, wrap=False)
    lit, shaded, interior = silhouette(body, wrap=False)
    for group, colour in ((interior, mid), (shaded, rim), (lit, lit_colour)):
        for x, y in group:
            canvas.put(x, y, colour, wrap=False)
    for x, y in band:
        canvas.put(x, y, band_colour, wrap=False)
    return canvas


def fat_worm():
    """Three pixels through the middle two thirds, two at head and tail.

    The taper is the whole difference. Thickening the entire spine to three
    turns the S into a blob, and leaving it at two is the earthworm again.
    """
    def girth(i):
        return 3 if 3 <= i < len(SPINE) - 4 else 2

    body = stamp(SPINE, girth)
    band = band_pixels(SPINE, girth) & body
    return draw(body, band, (WORM_RIM, WORM_BODY, WORM_LIT, WORM_PALE))


def glow_worm():
    """The earthworm's exact silhouette, drained and lit.

    Same girth as the earthworm on purpose: with the palette and the halo
    already carrying the difference, changing the shape too would make it read
    as a third species rather than as the same worm holding a light.
    """
    def girth(_):
        return 2

    body = stamp(SPINE, girth)
    band = band_pixels(SPINE, girth) & body
    halo = {
        (x + ox, y + oy)
        for x, y in body
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1))
    } - body
    halo = {(x, y) for x, y in halo if 0 <= x < SIZE and 0 <= y < SIZE}
    return draw(body, band, (PALE_RIM, PALE_BODY, PALE_LIT, PALE_CORE), extras=halo)


TEXTURES = [("fat_worm", fat_worm), ("glow_worm", glow_worm)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG", help="write a magnified sheet next to the earthworm")
    args = parser.parse_args()

    painted = [(name, fn()) for name, fn in TEXTURES]
    for name, canvas in painted:
        canvas.save(f"{ITEMS}/{name}.png")
        print("wrote", f"{ITEMS}/{name}.png")

    if args.preview:
        from PIL import Image

        scale = 16
        images = [Image.open(f"{ITEMS}/earthworm.png").convert("RGBA")]
        images += [c.img for _, c in painted]
        sheet = Image.new("RGBA", (SIZE * scale * len(images), SIZE * scale), (36, 32, 30, 255))
        for i, image in enumerate(images):
            up = image.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(up, (i * SIZE * scale, 0))
        sheet.save(args.preview)
        print("wrote", args.preview, "- earthworm, fat_worm, glow_worm")


if __name__ == "__main__":
    main()

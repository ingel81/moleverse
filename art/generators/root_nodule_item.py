"""The root nodule item icon, 16x16 on transparent ground.

What comes out of a pocket in a corridor wall: a knot of root with the pale
swellings still on it. The block texture (`burrow_textures.root_nodule`) is the
same object seen edge on in the earth; this is the same object in the hand, so
the two share the palette exactly - `ROOT` for the wood, `WOOD` for the beads -
and differ only in that the item has no soil behind it and can therefore afford
to be a silhouette.

The silhouette is the whole job. At hotbar size an icon is a shape and two
colours, and this one has to be told apart from four things it will sit next to
in the same inventory: the earthworm's red S, the fat worm's thicker version of
it, the pelt's grey disc and the sack's tall buff bag. So the nodule is the one
shape none of those is - a *cluster*, three round lobes bunched at the top with
a root trailing away below them to one corner. Nothing else in the mod is
lumpy, and lumpy is legible at twelve pixels.

Hand-placed rather than generated, for `worm_item_textures.SPINE`'s reason: a
formula has nothing to work with at this size, and the difference between a
cluster and a smudge is one pixel in the right place. Only the shading is
derived, off the same outline rule as everything else here.
"""

import argparse

from texture_kit import (
    ROOT,
    SIZE,
    WOOD,
    Canvas,
    silhouette,
    thicken,
)

OUT = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item/root_nodule.png"

#: Top-left corner of each bead. Three, bunched but not concentric - two across
#: the top and one dropped below and between them, which is the arrangement that
#: reads as a bunch rather than as a row or a triangle sign.
BEADS = [(2, 2), (7, 1), (5, 6)]

#: A bead is four across with its corners knocked off. Three would be a square
#: at this scale - there is no corner to lose - and five leaves no room for
#: three of them plus a stem.
BEAD_CORNERS = {(0, 0), (3, 0), (0, 3), (3, 3)}

#: The root the cluster hangs from, trailing to the bottom right. Four-connected
#: so it survives being thickened, and running corner to corner so the icon uses
#: the diagonal - a stem straight down would read as a lollipop.
STEM = [
    (7, 9), (7, 10), (8, 10), (8, 11), (9, 11),
    (9, 12), (10, 12), (10, 13), (11, 13), (11, 14),
]

#: Hair roots: one linking the two upper beads, one wisp off the stem. Both one
#: pixel wide and both there to break the outline - a cluster with a perfectly
#: clean edge reads as fruit.
HAIRS = [
    [(6, 3), (6, 4), (6, 5)],
    [(9, 13), (8, 13), (7, 14)],
]


def bead(canvas, at):
    """One pale lobe, lit from the top left like everything else in the kit."""
    x, y = at
    body = {
        (x + ox, y + oy)
        for oy in range(4)
        for ox in range(4)
        if (ox, oy) not in BEAD_CORNERS
    }
    for px, py in body:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if (px + ox, py + oy) not in body:
                canvas.put(px + ox, py + oy, ROOT[0], wrap=False)
    for px, py in body:
        far = (px - x) + (py - y)
        canvas.put(px, py, WOOD[5] if far <= 1 else WOOD[4] if far <= 3 else WOOD[2],
                   wrap=False)
    return body


def paint():
    c = Canvas()

    # The root first and the beads over it, so a bead's socket cannot eat the
    # wood it grew on - the same order, and the same reason, as the block.
    for hair in HAIRS:
        for x, y in hair:
            c.put(x, y, ROOT[1], wrap=False)
            c.put(x + 1, y, ROOT[0], wrap=False)

    stem = thicken(STEM, 2, wrap=False)
    lit, shaded, interior = silhouette(stem, wrap=False)
    for group, tone in ((interior, ROOT[2]), (shaded, ROOT[0]), (lit, ROOT[3])):
        for x, y in group:
            c.put(x, y, tone, wrap=False)

    for at in BEADS:
        bead(c, at)
    return c


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG",
                        help="write a magnified sheet next to the items it must not look like")
    args = parser.parse_args()

    canvas = paint()
    canvas.save(OUT)
    print("wrote", OUT)

    if args.preview:
        from PIL import Image

        items = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/item"
        scale = 20
        neighbours = ("earthworm", "fat_worm", "glow_worm", "mole_pelt", "mole_in_sack")
        images = [canvas.img] + [
            Image.open(f"{items}/{n}.png").convert("RGBA") for n in neighbours
        ]
        sheet = Image.new(
            "RGBA", (SIZE * scale * len(images), SIZE * scale), (40, 38, 40, 255)
        )
        for i, image in enumerate(images):
            sheet.alpha_composite(
                image.resize((SIZE * scale, SIZE * scale), Image.NEAREST), (i * SIZE * scale, 0)
            )
        sheet.save(args.preview)
        print("wrote", args.preview, "- root_nodule, " + ", ".join(neighbours))


if __name__ == "__main__":
    main()

"""The mole's hero-pass texture refinement. Reads the hand-painted original,
never repaints it.

The mole's 64x32 atlas is the one hand-painted texture in the mod and the
identity of its main character, so this script is built around a hard rule:
**every texel of the original survives byte-identical.** The canvas grows
DOWNWARD to 64x64 - which leaves every existing texOffs valid - and all new
paint lands either in the new lower half (the strips for the hero-pass detail
cubes) or on a handful of individually listed texels of the original (the
nostrils and the paw pads), each documented below with its reason.

Why not the doubled 128x64 that was first proposed: the model uses box UV,
and box UV allots exactly one texel per model unit regardless of the declared
atlas size - a doubled canvas would not give any existing face a single texel
more, it would only shrink every strip's normalized footprint and tear the
paint off the boxes. Growing downward is the version of "more texels for
detail" that box UV actually permits: the new cubes get fresh strips, the old
ones keep their exact pixels.

Every colour used here is sampled from the original atlas, so the palette
cannot drift by even one entry.

Run: python art/generators/mole_refine.py
Reads  art/backup_mole/mole.png (the untouched original)
Writes src/main/resources/assets/moleverse/textures/entity/mole.png (64x64)
"""

import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "backup_mole", "mole.png")
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets",
                   "moleverse", "textures", "entity", "mole.png")

#: The new cubes' strips, all in the fresh lower half (v >= 32). These
#: offsets are the single source the additions in MoleModel.createBodyLayer
#: copy their texOffs from - if either side moves, move both.
CLAW_STRIPS = [(0, 32), (8, 32), (16, 32), (24, 32)]     # [1,1,2] each
WHISKER_STRIPS = [(32, 32), (38, 32), (44, 32), (50, 32)]  # [1,1,1] each
EAR_STRIPS = [(32, 36), (38, 36)]                          # [1,1,1] each
TUFT_STRIPS = [(0, 38), (8, 38), (16, 38), (24, 38)]       # [2,1,1] each
TAIL_TUFT_STRIP = (44, 36)                                 # [1,1,1]


def box_strip(img, colour, u, v, w, h, d, top=None):
    """Fills one box-UV strip with a flat colour (top face optionally its own).

    Flat on purpose: these are one-texel-wide details - claws, studs, tufts -
    and at that size a single well-chosen swatch IS the texture.
    """
    px = img.load()
    for y in range(v, v + d + h):
        for x in range(u, u + 2 * (d + w)):
            px[x, y] = colour
    if top is not None:
        for y in range(v, v + d):
            for x in range(u + d, u + d + w):
                px[x, y] = top


def main():
    src = Image.open(SRC).convert("RGBA")
    assert src.size == (64, 32), "expected the original 64x32 atlas"

    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.paste(src, (0, 0))
    px = out.load()

    # The swatches, sampled from the animal itself. Each coordinate is the
    # middle of a known box-UV face of the original layout - never a corner,
    # which in box UV is empty.
    claw = px[28, 8]        # the middle claw strip's north face, texOffs(26,7)
    fur_dark = px[9, 3]     # the body's up face, texOffs(0,0), [7,6,6]
    fur_mid = px[3, 9]      # the body's east face, same strip
    nose = px[54, 14]       # the nose tip's north face, texOffs(52,12)
    dark = px[26, 17]       # the head's north face, texOffs(20,12)

    # New strips: five-ridge claws (two extra per paw), whisker studs, ear
    # nubs, fur tufts along the back, a tail tuft.
    for u, v in CLAW_STRIPS:
        box_strip(out, claw, u, v, 1, 1, 2)
    for u, v in WHISKER_STRIPS:
        box_strip(out, dark, u, v, 1, 1, 1)
    for u, v in EAR_STRIPS:
        box_strip(out, dark, u, v, 1, 1, 1, top=fur_mid)
    for u, v in TUFT_STRIPS:
        box_strip(out, fur_dark, u, v, 2, 1, 1)
    box_strip(out, fur_dark, TAIL_TUFT_STRIP[0], TAIL_TUFT_STRIP[1], 1, 1, 1)

    # Refinements on the ORIGINAL texels, each listed singly. The snout box
    # is [3,2,4] at texOffs(38,12): its north face spans (42..45, 16..18), and
    # the two outer texels of its lower row become the nostrils - the darkest
    # head tone, not black, because a nostril is a shadow and not a hole.
    px[42, 17] = dark
    px[44, 17] = dark

    # The paw pads. The right paw palm is [4,2,5] at texOffs(26,0), its down
    # face spans (35..39, 0..5); the left palm mirrors it at texOffs(44,0),
    # down face (53..57, 0..5). The central 2x3 of each palm goes the nose's
    # pink: bare working skin, the one place on a mole that shows it.
    for x in range(36, 38):
        for y in range(1, 4):
            px[x, y] = nose
    for x in range(54, 56):
        for y in range(1, 4):
            px[x, y] = nose

    out.save(OUT)
    print("wrote", os.path.normpath(OUT), out.size)


if __name__ == "__main__":
    main()

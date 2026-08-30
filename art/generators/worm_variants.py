"""Four worm body approaches side by side, for the comparison sheet.

DECIDED: variant C won ("C ist gut... aber eben viel zu kurz") and its dials
are promoted - girth profile into `great_worm_shape.profile`, the prostomium,
clitellum saddle, tail paddle, sheen and ring work into both worm generators,
and the length pushed well past the sheet's, per the verdict. This file stays
as the record of the choice and as the machinery for the next sheet.

The worms were rejected twice, which by the project's own rule means the next
round is not another guess - it is a sheet the user picks from. Each variant
here is a dial set fed through one builder, so the winner is not a sketch to
be redone but a spec to be promoted into `great_worm_shape` (and scaled down
into the earthworm) as-is.

The four approaches, left to right on the sheet, one label dot per letter:

* **A - many-segment chain.** Ten short segments with narrower connector
  boxes bridging real gaps between them: the grooves are concave geometry,
  not paint, and the undulation gets a joint every six units.
* **B - smooth tube, texture-carried.** Five long segments wearing three
  raised annuli each, with a glossy dorsal highlight band and lit ring
  crests. Detail from paint and thin rings; the silhouette stays calm. The
  baked highlight deliberately breaks the mod's pigment-only rule - that is
  part of what the sheet is asking.
* **C - realistic profile.** A pointed three-step prostomium, a fat clitellum
  saddle a third of the way back, a flattened tail paddle, and a girth that
  visibly peaks mid-front. The current worm's language, pushed further.
* **D - chunky-stylized.** Five fat segments with one-unit gaps showing a
  dark core through them, bold alternating two-tone bands, a blunt head.
  Built to read across a cavern, not up close.

Everything is at great-worm scale, because detail is what is being judged;
the earthworm is the same formula sampled shorter, as it always was.

Run:

    python art/generators/worm_variants.py          # writes the variant PNGs,
                                                    # prints one JSON per variant
                                                    # for the Blockbench bridge

Textures land next to the script in `build/` (gitignored); the sheet itself is
a Blockbench screenshot saved to `art/worm_variants.png` by hand. No Java is
emitted on purpose - the sheet is the deliverable, the winner gets the workup.
"""

import json
import math
import os

from great_worm_shape import box_faces, even, pack, smooth
from texture_kit import WORM, WORM_CLITELLUM

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build")

FACES = ("north", "east", "south", "west", "up", "down")

#: Sheet layout: variant centres along X, in model units. Wide enough that no
#: two silhouettes touch from any sensible camera.
SPACING = 48

#: The label dots' colour. Off every ramp on purpose - it must read as
#: annotation, not as part of any animal.
LABEL = (0xF2, 0xEE, 0xE4)


# --- the shared builder -----------------------------------------------------

def girth(t, spec):
    """Girth fraction along the body, from the spec's four profile dials."""
    head = spec["head_base"] + (1.0 - spec["head_base"]) * smooth(t / spec["head_ramp"])
    tail = 1.0 - spec["tail_drop"] * smooth((t - spec["tail_start"]) / (1.0 - spec["tail_start"]))
    return head * tail


def segments(spec):
    """The main segment boxes: name, size, centre. Local coordinates, x = 0."""
    count = spec["segments"]
    out = []
    for i in range(count):
        t = i / (count - 1)
        r = girth(t, spec)
        width = even(2 * spec["half_width"] * r, 6)
        height = max(5, round(spec["height"] * r))
        centre = (i - (count - 1) / 2) * spec["pitch"]
        out.append({
            "name": "seg%d" % i,
            "size": [width, height, spec["depth"]],
            "from": [-width / 2, 0, centre - spec["depth"] / 2],
            "to": [width / 2, height, centre + spec["depth"] / 2],
            "centre": centre,
            "girth": r,
        })
    return out


def cube(name, from_, to_):
    w = to_[0] - from_[0]
    h = to_[1] - from_[1]
    d = to_[2] - from_[2]
    return {"name": name, "size": [round(w), round(h), round(d)],
            "from": list(from_), "to": list(to_)}


def build(spec):
    """All boxes of one variant, local coordinates, plus its label dots."""
    segs = segments(spec)
    out = [cube(s["name"], s["from"], s["to"]) for s in segs]

    if spec.get("connectors"):
        # A narrower box bridging each gap: the groove as concave geometry.
        for a, b in zip(segs, segs[1:]):
            w = min(a["size"][0], b["size"][0]) - spec["groove_bite"]
            h = min(a["size"][1], b["size"][1]) - spec["groove_bite"]
            mid = (a["centre"] + b["centre"]) / 2
            out.append(cube("joint_%s" % b["name"],
                            [-w / 2, 0, mid - 1.5], [w / 2, h, mid + 1.5]))

    if spec.get("annuli"):
        # Thin raised rings riding each segment, spec-many per segment.
        offs = spec["annuli"]
        for s in segs:
            w, h, d = s["size"]
            for k, dz in enumerate(offs):
                out.append(cube("ring_%s_%d" % (s["name"], k),
                                [-w / 2 - 1, 0, s["centre"] + dz - 1],
                                [w / 2 + 1, h + 1, s["centre"] + dz + 1]))

    if spec.get("core"):
        # The dark inner body the chunky variant's gaps open onto.
        first, last = segs[0], segs[-1]
        w = min(s["size"][0] for s in segs) - 6
        h = min(s["size"][1] for s in segs) - 4
        out.append(cube("core", [-w / 2, 1, first["from"][2] + 2],
                        [w / 2, 1 + h, last["to"][2] - 2]))

    front = segs[0]["from"][2]
    fw, fh, _ = segs[0]["size"]

    if spec.get("prostomium"):
        # A three-step point: the closest a box model comes to a cone.
        steps = [(even(fw - 6, 4), max(4, fh - 6), 4), (even(fw - 12, 4), 3, 3), (2, 2, 2)]
        z = front
        base = 2
        for k, (w, h, d) in enumerate(steps):
            out.append(cube("prost%d" % k, [-w / 2, base, z - d], [w / 2, base + h, z]))
            z -= d
            base += 1

    if spec.get("blunt_head"):
        w = even(fw - 4, 6)
        h = fh - 2
        out.append(cube("snub", [-w / 2, 0, front - 4], [w / 2, h, front + 2]))

    if spec.get("mouth"):
        out.append(cube("mouth", [-4, 0, front - 2.5], [4, 3, front - 0.5]))

    if spec.get("clitellum"):
        # The saddle: markedly wider and taller than its segment, several deep.
        t = spec["clitellum"]
        z = round((t - 0.5) * (spec["segments"] - 1) * spec["pitch"] * 2.0) / 2.0
        host = min(segs, key=lambda s: abs(s["centre"] - z))
        w, h, d = host["size"]
        # One unit proud, not two: at two the saddle read as a cork driven
        # through the animal rather than as a swelling of it.
        out.append(cube("clitellum", [-w / 2 - 1, 0, z - 3], [w / 2 + 1, h + 1, z + 3]))

    if spec.get("tail_paddle"):
        back = segs[-1]["to"][2]
        w, h, _ = segs[-1]["size"]
        out.append(cube("paddle", [-w / 2 - 1, 0, back - 1],
                        [w / 2 + 1, max(4, round(h * 0.45)), back + 6]))

    # Label dots, one per letter, in front of the head on the floor.
    n = spec["index"] + 1
    z = front - 12
    for j in range(n):
        x = (j - (n - 1) / 2) * 6
        out.append(cube("label%d" % j, [x - 1, 0, z - 2], [x + 1, 2, z]))

    return out


# --- the four surfaces ------------------------------------------------------

def lerp(a, b, u):
    return a + (b - a) * u


def unproject(c, face, px, py):
    u1, v1, u2, v2 = c["faces"][face]
    s = (px + 0.5 - u1) / (u2 - u1)
    t = (py + 0.5 - v1) / (v2 - v1)
    x0, y0, z0 = c["from"]
    x1, y1, z1 = c["to"]
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


def flank(c, face, y):
    if face == "up":
        return 1.0
    if face == "down":
        return 0.0
    height = c["to"][1] - c["from"][1]
    return (y - c["from"][1]) / max(height, 1e-6)


def base_level(up, along):
    """The counter-shade every variant starts from: the shipped worm's."""
    level = 7.0 - 4.4 * smooth(up)
    level -= 1.1 * smooth((0.16 - along) / 0.16)
    level += 0.9 * smooth((along - 0.68) / 0.32)
    return level


def sheen(up, strength):
    """The wet reflection: a hard narrow band riding the upper flank.

    A worm is the one animal in this mod that is genuinely wet, and a wet
    body's tell is a highlight that sits below the spine, not on it - the
    spine faces the sky, the reflection faces the viewer. Hard-edged like
    every other band here: at these resolutions a soft gleam is a smudge.
    """
    return strength if 0.66 < up < 0.84 else 0.0


def rounding(c, z, depth):
    """Ring shading: how much darker a point is towards its segment's ends.

    This is what stops a segment from being a flat single-tone slab: the
    barrel of each segment stays bright and the last fifth to either end
    falls away, so every segment reads as a rounded annulus of its own even
    before the geometry says anything.
    """
    t = (z - c["from"][2]) / max(depth, 1e-6)
    return 1.4 * smooth((abs(t - 0.5) - 0.22) / 0.28)


def surface_a(c, face, x, y, z, along):
    up = flank(c, face, y)
    level = base_level(up, along) + sheen(up, 1.2)
    if c["name"].startswith("joint"):
        # The connector is the floor of a groove: two levels down, flat.
        return level - 2.2, None
    level -= rounding(c, z, c["to"][2] - c["from"][2])
    if face in ("north", "south"):
        level -= 1.8
    return level, None


def surface_b(c, face, x, y, z, along):
    up = flank(c, face, y)
    level = base_level(up, along)
    # The gloss: a hard highlight band riding high on the flank, and ring
    # crests one level brighter still. Baked light, on purpose - variant B is
    # the "paint carries it" thesis and this is what that costs and buys.
    level += sheen(up, 2.2)
    if c["name"].startswith("ring"):
        if face == "up":
            level += 1.5
    elif face in ("north", "south"):
        level -= 1.4
    if (z % 4.0) < 1.0 and not c["name"].startswith("ring"):
        level -= 0.8
    if up < 0.2:
        level -= 0.8
    return level, None


def surface_c(c, face, x, y, z, along, span):
    up = flank(c, face, y)
    level = base_level(up, along) + sheen(up, 1.2)
    name = c["name"]
    if name == "mouth":
        return 0.0, None
    if name == "clitellum":
        return level, WORM_CLITELLUM if up > 0.25 else None
    if name.startswith("prost"):
        return level - 1.2, None
    joint = face in ("north", "south") or (z - span[0]) % 3.0 < 1.0
    if joint:
        level -= 1.6
    half = max(1.0, 0.12 * (c["to"][0] - c["from"][0]))
    if up > 0.82 and abs(x) < half and along < 0.85:
        level -= 2.5
    return level, None


def surface_d(c, face, x, y, z, along, spec):
    up = flank(c, face, y)
    name = c["name"]
    if name == "core":
        return 1.0, None
    # Three flat tones per segment - lit crown, body colour, dark belly -
    # alternating light and dark down the animal: poster colouring, but with
    # enough modelling that a segment is not one flat slab. The bands land on
    # whole segments so the gaps separate them.
    seg = round((z / spec["pitch"]) + (spec["segments"] - 1) / 2)
    tone = 6.0 if seg % 2 == 0 else 3.5
    if up > 0.8:
        tone += 1.0
    if up < 0.25:
        tone = 2.0
    if face in ("north", "south"):
        tone -= 1.0
    return tone, None


# --- the dial sets ----------------------------------------------------------

#: Every variant is deliberately long and thin. The shipped worms were judged
#: in game as "zu kurz" - a dropped sausage - and the numbers agree: the old
#: earthworm ran three times as long as wide, and a real earthworm runs ten
#: or more. Boxes cannot go that far without starving the texture, but every
#: dial set here sits between four and six, half again to double the shipped
#: proportion, and the length comes from MORE SEGMENTS rather than deeper
#: ones, because the undulation chain gets better with count.
VARIANTS = [
    {
        "index": 0, "name": "a_chain",
        "segments": 14, "pitch": 6, "depth": 5,
        "half_width": 8, "height": 13,
        "head_base": 0.70, "head_ramp": 0.12, "tail_start": 0.50, "tail_drop": 0.50,
        "connectors": True, "groove_bite": 5,
        "mouth": True,
    },
    {
        "index": 1, "name": "b_tube",
        "segments": 7, "pitch": 13, "depth": 15,
        "half_width": 9, "height": 14,
        "head_base": 0.85, "head_ramp": 0.15, "tail_start": 0.60, "tail_drop": 0.35,
        "annuli": (-4.5, 0.0, 4.5),
        "mouth": True,
    },
    {
        "index": 2, "name": "c_realistic",
        "segments": 12, "pitch": 7, "depth": 9,
        "half_width": 9, "height": 14,
        "head_base": 0.62, "head_ramp": 0.25, "tail_start": 0.45, "tail_drop": 0.58,
        "prostomium": True, "clitellum": 0.33, "tail_paddle": True, "mouth": True,
    },
    {
        "index": 3, "name": "d_chunky",
        "segments": 7, "pitch": 12, "depth": 11,
        "half_width": 10, "height": 16,
        "head_base": 0.80, "head_ramp": 0.20, "tail_start": 0.55, "tail_drop": 0.42,
        "core": True, "blunt_head": True,
    },
]


def paint(spec, cubes, width, height):
    span = (min(c["from"][2] for c in cubes), max(c["to"][2] for c in cubes))
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for c in cubes:
        for face in FACES:
            u1, v1, u2, v2 = c["faces"][face]
            for py in range(min(v1, v2), max(v1, v2)):
                for px_ in range(min(u1, u2), max(u1, u2)):
                    x, y, z = unproject(c, face, px_, py)
                    if c["name"].startswith("label"):
                        px[px_, py] = LABEL + (255,)
                        continue
                    along = (z - span[0]) / (span[1] - span[0])
                    if spec["name"] == "a_chain":
                        level, override = surface_a(c, face, x, y, z, along)
                    elif spec["name"] == "b_tube":
                        level, override = surface_b(c, face, x, y, z, along)
                    elif spec["name"] == "c_realistic":
                        level, override = surface_c(c, face, x, y, z, along, span)
                    else:
                        level, override = surface_d(c, face, x, y, z, along, spec)
                    if override is not None:
                        px[px_, py] = override + (255,)
                    else:
                        index = max(0, min(len(WORM) - 1, int(round(level))))
                        px[px_, py] = WORM[index] + (255,)
    return img


# --- the sheet renderer -----------------------------------------------------
#
# The sheet is rendered here rather than screenshotted out of Blockbench, and
# not by preference: the editor build on this machine mis-scales per-texture
# UVs (fresh textures come in with uv_width 16 whatever their pixels say), so
# cubes whose strips sit low in a 256-row atlas lose their side faces in the
# viewport. These orthographic views sample the exact texels through the same
# face rectangles the geometry was packed with - which makes them, if
# anything, more faithful to what the game would draw than that viewport is.

def render_view(cubes, atlas, view, scale=5):
    """One flat orthographic view of one variant, texel-exact.

    `view` picks the axis: "side" sees the west faces (profile, head left),
    "top" the up faces, "front" the north faces (head on). Painter's
    algorithm: cubes sorted far-to-near along the viewing axis, so a proud
    ring simply covers the segment behind it, exactly as a depth buffer
    would.
    """
    px_at = atlas.load()

    if view == "side":
        face, key = "west", lambda c: -c["from"][0]
        span = lambda c: (c["from"][2], c["to"][2], c["from"][1], c["to"][1])
        lo = min(c["from"][2] for c in cubes), min(c["from"][1] for c in cubes)
        hi = max(c["to"][2] for c in cubes), max(c["to"][1] for c in cubes)
    elif view == "top":
        face, key = "up", lambda c: c["to"][1]
        span = lambda c: (c["from"][2], c["to"][2], c["from"][0], c["to"][0])
        lo = min(c["from"][2] for c in cubes), min(c["from"][0] for c in cubes)
        hi = max(c["to"][2] for c in cubes), max(c["to"][0] for c in cubes)
    else:
        face, key = "north", lambda c: -c["from"][2]
        span = lambda c: (c["from"][0], c["to"][0], c["from"][1], c["to"][1])
        lo = min(c["from"][0] for c in cubes), min(c["from"][1] for c in cubes)
        hi = max(c["to"][0] for c in cubes), max(c["to"][1] for c in cubes)

    width = int(math.ceil((hi[0] - lo[0]) * scale)) + 2
    height = int(math.ceil((hi[1] - lo[1]) * scale)) + 2
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out = img.load()

    for c in sorted(cubes, key=key):
        u1, v1, u2, v2 = c["faces"][face]
        a1, a2, b1, b2 = span(c)
        for py in range(min(v1, v2), max(v1, v2)):
            for px in range(min(u1, u2), max(u1, u2)):
                colour = px_at[px, py]
                if colour[3] == 0:
                    continue
                x, y, z = unproject(c, face, px, py)
                if view == "side":
                    ha, va = z, y
                elif view == "top":
                    ha, va = z, x
                else:
                    ha, va = x, y
                sx = int((ha - 0.5 - lo[0]) * scale) + 1
                sy = int((hi[1] - va - 0.5) * scale) + 1
                for dy in range(scale):
                    for dx in range(scale):
                        ix, iy = sx + dx, sy + dy
                        if 0 <= ix < width and 0 <= iy < height:
                            out[ix, iy] = colour
    return img


CAPTIONS = {
    "a_chain": "A  many-segment chain: 14 segments, grooves as geometry, ring shading + sheen",
    "b_tube": "B  smooth tube: 7 long segments, 21 raised rings, wet gloss band",
    "c_realistic": "C  realistic profile: prostomium, fat clitellum, tail paddle, sheen + vessel",
    "d_chunky": "D  chunky-stylized: 7 fat segments, open gaps, 3-tone poster bands",
}


def sheet(out_path):
    from PIL import ImageDraw

    rows = []
    for spec in VARIANTS:
        cubes = [c for c in build(spec) if not c["name"].startswith("label")]
        offsets, used = pack(cubes, 256)
        for c, off in zip(cubes, offsets):
            c["uv_offset"] = off
            c["faces"] = box_faces(off, c["size"])
        height = 1 << max(4, math.ceil(math.log2(max(used, 1))))
        atlas = paint(spec, cubes, 256, height)
        views = [render_view(cubes, atlas, v) for v in ("side", "top", "front")]
        rows.append((spec["name"], views))

    pad, caption_h = 24, 26
    col_w = [max(v[i].width for _, v in rows) for i in range(3)]
    row_h = [max(view.height for view in views) + caption_h for _, views in rows]
    width = sum(col_w) + pad * 4
    height = sum(row_h) + pad * (len(rows) + 1)
    img = Image.new("RGBA", (width, height), (0x1E, 0x1A, 0x16, 255))
    draw = ImageDraw.Draw(img)

    y = pad
    for (name, views), rh in zip(rows, row_h):
        draw.text((pad, y), CAPTIONS[name], fill=(0xE8, 0xDF, 0xD0, 255))
        x = pad
        for view, cw in zip(views, col_w):
            img.alpha_composite(view, (x + (cw - view.width) // 2,
                                       y + caption_h + (rh - caption_h - view.height) // 2))
            x += cw + pad
        y += rh + pad
    img.save(out_path)
    return out_path


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    sheet = []
    for spec in VARIANTS:
        cubes = build(spec)
        offsets, used = pack(cubes, 256)
        for c, off in zip(cubes, offsets):
            c["uv_offset"] = off
            c["faces"] = box_faces(off, c["size"])
        height = 1 << max(4, math.ceil(math.log2(max(used, 1))))
        png = os.path.join(OUT_DIR, "%s.png" % spec["name"])
        paint(spec, cubes, 256, height).save(png)

        # Variants sit side by side along X. On the sheet each variant's GROUP
        # is then rotated 90 degrees about Y at the world origin, which turns
        # the whole row so the bodies lie across the camera - done as a group
        # rotation rather than by rotating the boxes, because box UV derives
        # its face rectangles from a cube's own dimensions and swapping width
        # for depth would tear every face off its painted rectangle.
        shift = (spec["index"] - (len(VARIANTS) - 1) / 2) * SPACING
        sheet.append({
            "name": spec["name"],
            "texture": png,
            "texture_size": [256, height],
            "cubes": [{
                "name": c["name"],
                "from": [c["from"][0] + shift, c["from"][1], c["from"][2]],
                "to": [c["to"][0] + shift, c["to"][1], c["to"][2]],
                "uv_offset": c["uv_offset"],
            } for c in cubes],
        })
    print(json.dumps(sheet, separators=(",", ":")))


if __name__ == "__main__":
    import sys

    if "--sheet" in sys.argv:
        print("wrote", sheet(sys.argv[sys.argv.index("--sheet") + 1]))
    else:
        main()

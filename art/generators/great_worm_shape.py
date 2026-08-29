"""The great worm's geometry: eight segments on a girth profile, plus a UV
layout that packs them without overlap.

Hand-placing eight tapered boxes and then finding eight non-overlapping UV
rectangles for them is the kind of bookkeeping that goes wrong quietly - a
segment one unit too wide only shows up in game as a seam. The girth comes from
a curve instead, and the UV layout from a shelf packer, so changing the worm's
length or width is one constant rather than a rewrite.

The profile is asymmetric on purpose. An earthworm is blunt at the mouth, at its
fattest just behind the clitellum, and tapers to a flat point at the tail; a
symmetric spindle reads as a maggot.

Printed as JSON, this is fed into Blockbench through the MCP bridge. The
`.bbmodel` under `art/` stays the source of truth for the model, and
`great_worm_texture.py` imports this module so texture and geometry cannot drift
apart.
"""

import json
import math

# --- dials ----------------------------------------------------------------

#: Head, six body segments, tail.
SEGMENTS = 8

#: Distance between two segment centres, in model units. 16 units is one block.
#: Eight of these plus the two end lobes make the worm 66 units, just over four
#: blocks long.
PITCH = 7

#: How far each cube reaches into its neighbours. Peristalsis scales segments
#: along Z, and without an overlap a contracting segment tears a gap open.
OVERLAP = 2

#: Half of the widest segment. 22 units across is a touch under 1.4 blocks.
#: One unit narrower than the eye would pick was worth it: at 24 the two fattest
#: segments no longer sit side by side in the UV atlas and the texture needs
#: 128x256 instead of 128x128, for a difference nobody can see in game.
MAX_HALF_WIDTH = 11

#: Height of the widest segment. A worm on the ground is a little wider than tall.
MAX_HEIGHT = 18

#: Names in the order the model uses them, head first.
NAMES = ["head", "body1", "body2", "body3", "body4", "body5", "body6", "tail"]


def smooth(u: float) -> float:
    """Smoothstep, clamped."""
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


def profile(t: float) -> float:
    """Girth at position `t` along the body, as a fraction of the maximum.

    `t` runs 0 at the mouth to 1 at the tail tip. Two clamped ramps multiplied:
    a short one that thickens the head end, and a long one that thins the tail
    half. Their product peaks around a quarter of the way down, where a worm's
    clitellum sits.
    """
    head = 0.66 + 0.34 * smooth(t / 0.22)
    tail = 1.0 - 0.52 * smooth((t - 0.50) / 0.50)
    return head * tail


def even(value: float, minimum: int) -> int:
    """Rounds to an even integer, so a box stays symmetric about x = 0."""
    return max(minimum, 2 * round(value / 2))


def segments():
    """The eight body segments, front to back.

    Blockbench coordinates: Y up with the floor at 0, -Z forward. The exporter
    mirrors X, which costs nothing here because the worm is symmetric.
    """
    depth = PITCH + OVERLAP
    out = []
    for i in range(SEGMENTS):
        t = i / (SEGMENTS - 1)
        r = profile(t)
        width = even(2 * MAX_HALF_WIDTH * r, 6)
        height = max(5, round(MAX_HEIGHT * r))
        centre = (i - (SEGMENTS - 1) / 2) * PITCH
        out.append({
            "name": NAMES[i],
            "index": i,
            "centre_z": centre,
            "size": [width, height, depth],
            "from": [-width / 2, 0, centre - depth / 2],
            "to": [width / 2, height, centre + depth / 2],
            # Pivot on the floor at the segment's own centre: a vertical scale
            # pulse then swells the segment upwards instead of sinking it into
            # the ground, and a Z pulse stays centred on the segment.
            "origin": [0, 0, centre],
        })
    return out


def cubes():
    """Every cube, tagged with the bone it belongs to.

    Two cubes carry no body segment of their own: the prostomium, the lobe an
    earthworm noses along the ground with, and the flattened tail tip. Both are
    what stops the silhouette from ending in a blunt cut.
    """
    body = segments()
    out = []
    for seg in body:
        out.append({
            "bone": seg["name"],
            "name": seg["name"],
            "from": seg["from"],
            "to": seg["to"],
            "origin": seg["origin"],
            "size": seg["size"],
        })

    head, tail = body[0], body[-1]

    hw, hh, _ = head["size"]
    nose = [even(hw - 6, 4), max(3, hh - 4), 5]
    front = head["from"][2]
    out.append({
        "bone": "head",
        "name": "prostomium",
        "from": [-nose[0] / 2, 1, front - nose[2] + 1],
        "to": [nose[0] / 2, 1 + nose[1], front + 1],
        "origin": head["origin"],
        "size": nose,
    })

    # Flattened rather than merely smaller. An earthworm's anterior end is a
    # cone it burrows with, but the posterior is pressed flat, and a tip that
    # only shrinks in every direction makes both ends of the animal look alike.
    tw, th, _ = tail["size"]
    tip = [even(tw - 2, 4), max(3, th - 3), 5]
    back = tail["to"][2]
    out.append({
        "bone": "tail",
        "name": "tail_tip",
        "from": [-tip[0] / 2, 0, back - 1],
        "to": [tip[0] / 2, tip[1], back - 1 + tip[2]],
        "origin": tail["origin"],
        "size": tip,
    })
    return out


def box_uv_size(size):
    """Pixels a box UV occupies: 2*(depth+width) by depth+height."""
    w, h, d = size
    return 2 * (d + w), d + h


def pack(items, canvas_width):
    """Shelf packer. Returns the UV offsets and the height used.

    Tallest first, so a row of thin cubes cannot strand a fat one. Nothing
    clever - the layout only has to be gap-free enough to fit a power-of-two
    canvas, and auto UV would put every cube in the same corner.
    """
    order = sorted(range(len(items)), key=lambda i: -box_uv_size(items[i]["size"])[1])
    offsets = [None] * len(items)
    x = y = shelf_height = 0
    for i in order:
        w, h = box_uv_size(items[i]["size"])
        if x + w > canvas_width:
            x = 0
            y += shelf_height
            shelf_height = 0
        offsets[i] = [x, y]
        x += w
        shelf_height = max(shelf_height, h)
    return offsets, y + shelf_height


def box_faces(uv_offset, size):
    """The six face rectangles Blockbench derives from one box UV offset.

    Transcribed from what Blockbench actually produced for this model, not from
    the layout diagram - the diagram does not say that `up` comes out reversed
    on both axes and `down` on one, and a wrong guess there mirrors the body
    gradient on the most visible face of the animal. `great_worm_texture.py`
    projects texels back into model space through these, so they have to be the
    real ones. `verify()` re-checks them against the read-back.

    Each rectangle is [u1, v1, u2, v2], and u1 > u2 means the face is mirrored.
    """
    u, v = uv_offset
    w, h, d = size
    return {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }


def layout(canvas_width=128):
    """Cubes with their UV offsets and face rectangles, plus the canvas needed."""
    items = cubes()
    offsets, used = pack(items, canvas_width)
    for item, offset in zip(items, offsets):
        item["uv_offset"] = offset
        item["faces"] = box_faces(offset, item["size"])
    height = 1 << max(4, math.ceil(math.log2(max(used, 1))))
    return items, canvas_width, height


def verify():
    """Checks `box_faces` against one cube Blockbench built, and the packing.

    The sample is the head as it stood when the rule was read out of the running
    editor. It is a canary for a Blockbench version that lays box UVs out
    differently - which would otherwise show up only as a texture that is subtly
    wrong on the top face.
    """
    expected = {
        "north": [59, 62, 71, 72], "east": [50, 62, 59, 72],
        "south": [80, 62, 92, 72], "west": [71, 62, 80, 72],
        "up": [71, 62, 59, 53], "down": [83, 53, 71, 62],
    }
    assert box_faces([50, 53], [12, 10, 9]) == expected, "box UV layout changed"

    items, width, height = layout()
    used = set()
    for item in items:
        for rect in item["faces"].values():
            u1, v1, u2, v2 = rect
            for y in range(min(v1, v2), max(v1, v2)):
                for x in range(min(u1, u2), max(u1, u2)):
                    assert (x, y) not in used, "UV layout overlaps at %d,%d" % (x, y)
                    assert 0 <= x < width and 0 <= y < height, "UV outside the canvas"
                    used.add((x, y))
    return len(used), width * height


if __name__ == "__main__":
    used, total = verify()
    items, width, height = layout()
    print(json.dumps({
        "texture": [width, height],
        "uv_fill": round(used / total, 3),
        "cubes": items,
    }, indent=1))

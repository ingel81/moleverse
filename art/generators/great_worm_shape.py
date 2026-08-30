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

#: Head, fourteen body segments, tail. Doubled from eight when the sheet's
#: variant C won with the note "aber viel zu kurz": the old worm ran three
#: times as long as wide, and a worm is not a shape that stops there. The
#: length comes from count rather than pitch because every segment is a joint
#: of the crawl, and the chain reads better the more joints it has.
SEGMENTS = 16

#: Distance between two segment centres, in model units. 16 units is one
#: block. Sixteen of these plus the nose and the paddle make the worm about
#: 122 units - seven and a half blocks of animal winding down a corridor.
PITCH = 7

#: How far each cube reaches into its neighbours. Peristalsis scales segments
#: along Z, and without an overlap a contracting segment tears a gap open.
OVERLAP = 2

#: Half of the widest segment. Narrowed from 11 as the body doubled: eighteen
#: units across against 122 long is roughly seven to one, worm proportion,
#: where the old three to one read as a dropped sausage.
MAX_HALF_WIDTH = 9

#: Height of the widest segment. A worm on the ground is a little wider than tall.
MAX_HEIGHT = 15

#: Names in the order the model uses them, head first.
NAMES = ["head"] + ["body%d" % i for i in range(1, SEGMENTS - 1)] + ["tail"]

#: Which segment carries the clitellum saddle: index 5 of 15 is a third of
#: the way back, where a worm's clitellum sits. Deliberately an odd index so
#: the even-indexed annuli never collide with it.
CLITELLUM_SEGMENT = 5

#: The raised annuli: one ring per body segment, a unit prouder each side and
#: above, two units deep, on the segment's own bone.
#:
#: The texture has painted ring joints on a three unit pitch since the worm was
#: quantised, and at a distance they carry the segmentation - but up close the
#: silhouette stayed a smooth loaf. Each geometry ring sits on that same grid,
#: centred on the bulge *between* two painted joints, which is what an annulus
#: physically is: the swollen part, not the groove. `great_worm_texture`
#: paints position-keyed, so the joint pattern simply continues across the
#: raised box and the two layers cannot disagree. The head and tail lobes get
#: no ring - the prostomium and the flattened tip are shapes of their own.
#:
#: Every *other* segment, not every one - half the annuli read as
#: segmentation just as well, and the strips they save pay for the doubled
#: body. Even indices only, so they never collide with the clitellum saddle
#: on segment five.
RING_SEGMENTS = (2, 4, 6, 8, 10, 12, 14)
RING_FLARE = 1
RING_DEPTH = 2

#: The clitellum saddle: markedly wider and taller than its host segment and
#: six units deep - variant C's signature feature, the swelling as geometry
#: where a painted band of even weight reads as a stripe. Saddle-painted in
#: `great_worm_texture` through its name: clitellum colour above the foot,
#: skin below, because a real clitellum is open beneath.
CLITELLUM_FLARE = 1
CLITELLUM_DEPTH = 6

#: The mouth: a dark box tucked under the prostomium, half its height, one and
#: a half units proud of the head's front face. An earthworm's mouth is exactly
#: there - the prostomium is the lip that hangs over it - and without it the
#: front of a four block animal is a wall with a bump on it.
MOUTH = [8, 3, 2]


def smooth(u: float) -> float:
    """Smoothstep, clamped."""
    u = max(0.0, min(1.0, u))
    return u * u * (3.0 - 2.0 * u)


def profile(t: float) -> float:
    """Girth at position `t` along the body, as a fraction of the maximum.

    `t` runs 0 at the mouth to 1 at the tail tip. Two clamped ramps multiplied:
    a short one that thickens the head end, and a long one that thins the tail
    half. Their product peaks around a third of the way down, where a worm's
    clitellum sits.

    These are variant C's dials from the comparison sheet, promoted verbatim:
    a slimmer head, a girth that visibly peaks mid-front, and a harder taper
    to the tail than the first worm carried. The earthworm samples the same
    curve, as it always did.
    """
    head = 0.62 + 0.38 * smooth(t / 0.25)
    tail = 1.0 - 0.58 * smooth((t - 0.45) / 0.55)
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

    # The prostomium as a three-step point, variant C's nose: the closest a
    # box model comes to the cone an earthworm actually burrows with. Each
    # step is narrower, shorter and a little higher than the one behind it,
    # and all three ride the head bone.
    hw, hh, _ = head["size"]
    front = head["from"][2]
    steps = [(even(hw - 6, 4), max(4, hh - 5), 4), (even(hw - 10, 4), 3, 3), (2, 2, 2)]
    z = front + 1
    base = 1
    for index, (w, h, d) in enumerate(steps):
        out.append({
            "bone": "head",
            "name": "prost%d" % index,
            "from": [-w / 2, base, z - d],
            "to": [w / 2, base + h, z],
            "origin": head["origin"],
            "size": [w, h, d],
        })
        z -= d
        base += 1

    # The clitellum saddle. See CLITELLUM_FLARE.
    host = body[CLITELLUM_SEGMENT]
    cw, ch, _ = host["size"]
    out.append({
        "bone": host["name"],
        "name": "clitellum",
        "from": [-cw / 2 - CLITELLUM_FLARE, 0, host["centre_z"] - CLITELLUM_DEPTH / 2],
        "to": [cw / 2 + CLITELLUM_FLARE, ch + CLITELLUM_FLARE,
               host["centre_z"] + CLITELLUM_DEPTH / 2],
        "origin": host["origin"],
        "size": [cw + 2 * CLITELLUM_FLARE, ch + CLITELLUM_FLARE, CLITELLUM_DEPTH],
    })

    # Flattened rather than merely smaller, and a little wider than the
    # segment it follows: an earthworm's anterior end is a cone it burrows
    # with, but the posterior is pressed flat into a paddle, and a tip that
    # only shrinks in every direction makes both ends of the animal look
    # alike.
    tw, th, _ = tail["size"]
    tip = [even(tw + 2, 4), max(3, round(th * 0.45)), 6]
    back = tail["to"][2]
    out.append({
        "bone": "tail",
        "name": "tail_tip",
        "from": [-tip[0] / 2, 0, back - 1],
        "to": [tip[0] / 2, tip[1], back - 1 + tip[2]],
        "origin": tail["origin"],
        "size": tip,
    })

    # The mouth, under the prostomium's overhang. See MOUTH.
    mw, mh, md = MOUTH
    mouth_front = head["from"][2] - md + 0.5
    out.append({
        "bone": "head",
        "name": "mouth",
        "from": [-mw / 2, 0, mouth_front],
        "to": [mw / 2, mh, mouth_front + md],
        "origin": head["origin"],
        "size": MOUTH,
    })

    # The annuli, one per body segment, centred on the bulge between two
    # painted ring joints so texture and geometry stay one pattern. The grid
    # is anchored where the texture anchors it: the front of the whole animal,
    # which is the prostomium's tip. See RING_FLARE.
    z_front = min(item["from"][2] for item in out)
    ring_grid = 3  # mirrors RING_PITCH in great_worm_texture.py
    for seg in (body[i] for i in RING_SEGMENTS):
        w, h, d = seg["size"]
        k = round((seg["centre_z"] - z_front - 2.0) / ring_grid)
        ring_z = z_front + ring_grid * k + 2.0
        out.append({
            "bone": seg["name"],
            "name": "ring_%s" % seg["name"],
            "from": [-w / 2 - RING_FLARE, 0, ring_z - RING_DEPTH / 2],
            "to": [w / 2 + RING_FLARE, h + RING_FLARE, ring_z + RING_DEPTH / 2],
            "origin": seg["origin"],
            "size": [w + 2 * RING_FLARE, h + RING_FLARE, RING_DEPTH],
        })
    return out


def box_uv_size(size):
    """Pixels a box UV occupies: 2*(depth+width) by depth+height."""
    w, h, d = size
    return 2 * (d + w), d + h


def pack(items, canvas_width):
    """Shelf packer, first-fit decreasing. Returns the UV offsets and the height used.

    Tallest first, so a row of thin cubes cannot strand a fat one, and each item
    goes onto the first open shelf with room rather than only the newest one.
    The difference matters once a model grows small parts: closing a shelf the
    moment one item overflows leaves every earlier remainder empty, and the
    critter wave's feet and feelers pushed the beetle over a power-of-two
    boundary on exactly that waste. An item is never taller than a shelf it
    revisits, because the shelves were opened in falling height order.
    """
    order = sorted(range(len(items)),
                   key=lambda i: tuple(-v for v in reversed(box_uv_size(items[i]["size"]))))
    offsets = [None] * len(items)
    shelves = []  # [y, height, x used]
    for i in order:
        w, h = box_uv_size(items[i]["size"])
        for shelf in shelves:
            if shelf[2] + w <= canvas_width:
                offsets[i] = [shelf[2], shelf[0]]
                shelf[2] += w
                break
        else:
            y = shelves[-1][0] + shelves[-1][1] if shelves else 0
            shelves.append([y, h, w])
            offsets[i] = [0, y]
    return offsets, shelves[-1][0] + shelves[-1][1] if shelves else 0


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


# --- Java emitter -----------------------------------------------------------

def _f(value):
    # The `+ 0.0` turns -0.0 into 0.0 before printing, exactly as the sister
    # emitters in critter_shapes and predator_shapes do.
    return "%.1FF" % (value + 0.0)


def java():
    """The body of `createBodyLayer()`, in the house multi-line format.

    Written here rather than round-tripped through Blockbench's exporter for
    the same reason the critters' emitter exists: transcribing a hundred-odd
    numbers by hand is how a texture ends up subtly wrong on one face of one
    box. The Blockbench export remains the cross-check, not the source.
    """
    items, width, height = layout()

    bones = {}
    for item in items:
        bones.setdefault(item["bone"], []).append(item)

    lines = [
        "    public static LayerDefinition createBodyLayer() {",
        "        MeshDefinition meshdefinition = new MeshDefinition();",
        "        PartDefinition partdefinition = meshdefinition.getRoot();",
        "",
        "        PartDefinition root = partdefinition.addOrReplaceChild(\"root\", CubeListBuilder.create(),",
        "                PartPose.offset(0.0F, 24.0F, 0.0F));",
        "",
    ]
    for bone, cubes_of in bones.items():
        origin = cubes_of[0]["origin"]
        out = ["        root.addOrReplaceChild(\"%s\", CubeListBuilder.create()" % bone]
        for item in cubes_of:
            u, v = item["uv_offset"]
            w, h, d = item["size"]
            bx = item["from"][0] - origin[0]
            by = item["from"][1] - origin[1]
            bz = item["from"][2] - origin[2]
            # Natural space stands on `by`; the model hangs from `-by - h`.
            out.append(
                "                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(0.0F))"
                % (u, v, _f(bx), _f(-by - h), _f(bz), _f(w), _f(h), _f(d)))
        out[-1] += ","
        out.append("                PartPose.offset(%s, %s, %s));"
                   % (_f(origin[0]), _f(-origin[1]), _f(origin[2])))
        lines.extend(out)
        lines.append("")
    lines.append("        return LayerDefinition.create(meshdefinition, %d, %d);" % (width, height))
    lines.append("    }")
    return "\n".join(lines)


if __name__ == "__main__":
    import sys

    used, total = verify()
    if "--java" in sys.argv:
        print(java())
    else:
        items, width, height = layout()
        print(json.dumps({
            "texture": [width, height],
            "uv_fill": round(used / total, 3),
            "cubes": items,
        }, indent=1))

"""Geometry and UV layout for the burrow's two predators.

The shrew and the weasel are the same animal twice: a spine of tapered boxes, a
head with a snout on the front of it, four legs hanging off the ends of the
spine, and a tail off the back. Nothing else. So there is one builder and two
tables of dials, which is the only way two mammals authored a day apart come out
looking like they live in the same dimension - the earthworm and the great worm
share `profile` for exactly this reason.

`predator_textures.py` imports this module and paints through the same face
rectangles the geometry was packed with, as every other creature in this mod
does. Geometry and texture therefore cannot drift apart.

## Coordinates

Natural space, the same one `critter_shapes` uses: Y up with the floor at 0, -Z
forward, X across, every box written relative to the bone it hangs off. `java()`
applies the one conversion at the end, negating Y, because a Minecraft entity
model hangs down from a root at y = 24.

The export mirrors X, so a part at +X here arrives at -X in Java, which is the
entity's right. Both animals are symmetric, so only the *names* care: the bone
called `leg_fl` is built at +X and is the front left leg in game. See
`docs/MODEL_WORKFLOW.md` step 4 - the critter wave paid for this one already.

## Why these two have a bone tree and the critters do not

The earthworm, the beetle and the grub hang every bone straight off `root`. That
works while nothing has to follow anything: a beetle's antenna twitches, and the
head it is attached to does not move.

Here it does. The weasel's spine carries a travelling wave, and a head that
stayed behind at the root would swing off the front of the neck. So `parent` is
part of a bone's definition and the Java emitter nests the calls, subtracting the
parent's pivot from the child's - `PartPose.offset` is relative, `addBox` is not.
Blockbench needs no such conversion: its group origins are absolute whatever the
outliner looks like, so `blockbench()` emits the same absolute corners it always
did and the nesting is applied to the outliner afterwards.

The spine segments themselves stay siblings. Chaining them would be more correct
and buys nothing here - the wave is a few degrees per joint and the boxes carry
`OVERLAP` units of slack precisely so a joint of that size cannot open a gap. A
chain would also make every segment's amplitude depend on the one in front of it,
which is a thing to tune rather than a thing to write down.

Run it for the numbers:

    python art/generators/predator_shapes.py            # JSON, both animals
    python art/generators/predator_shapes.py --java     # createBodyLayer bodies
    python art/generators/predator_shapes.py --bb shrew # for the MCP bridge
"""

import json
import math
import sys

from great_worm_shape import box_faces, pack
from texture_kit import smooth

#: Both atlases are this wide. Wide enough that the weasel's chest - the widest
#: single box here at 26 px of UV - shares a shelf with two more boxes, which is
#: what keeps the canvas at 32 rows instead of 64.
CANVAS_WIDTH = 64


# --- the two girth profiles -----------------------------------------------

def shrew_profile(t):
    """Girth along a shrew, 0 at the shoulders and 1 at the rump.

    Rebuilt from the ground up after the in-game verdict "too little detail":
    the first shrew was a torpedo, fattest behind the shoulder, and at seven
    units of trunk it read as a pellet with a nose. A shrew is the opposite
    shape - a pointed wedge that is nearly all snout, widening steadily from
    the shoulders to a round rump. So the ramp now RISES the whole way back
    and only rounds off at the very end, and the wedge continues past the
    shoulders through muzzle, snout and snout tip: six wide at the rump down
    to one at the nose, over twenty units of animal.
    """
    front = 0.60 + 0.40 * smooth(t / 0.70)
    back = 1.0 - 0.10 * smooth((t - 0.80) / 0.20)
    return front * back


def weasel_profile(t):
    """Girth along a weasel, 0 at the chest and 1 at the haunches.

    Nearly a tube, and that is the animal. What little variation there is has to
    be a dip and not a taper: a weasel is a deep chest, a waist it can follow a
    vole down a hole with, and haunches. A profile that only narrowed towards the
    back would read as a ferret-shaped cat.

    The numbers are picked so the dip lands on the height and not on the width -
    at five segments the heights round to 5, 5, 4, 4, 5 against widths of
    8, 8, 7, 7, 8, which from the side is a visible waist through the middle
    of the animal and from above is a nearly straight one. That is the right
    way round: nobody looks at a weasel from above except the player, who is a
    quarter of their usual size and standing next to it.
    """
    return 1.0 - 0.14 * smooth((t - 0.10) / 0.45) + 0.12 * smooth((t - 0.62) / 0.38)


# --- the dials ------------------------------------------------------------

#: Everything that distinguishes the two animals.
#:
#: `stance` is the leg length and therefore the height of the trunk's floor, and
#: it is the single number that carries most of the difference: a shrew at 2 is
#: pressed against the ground and a weasel at 4 stands over it. `spine_pitch`
#: plus `spine_overlap` is each segment's depth, and the overlap is not padding -
#: it is the slack a rotating joint hides its shear in, the great worm's reason
#: exactly.
#:
#: The two hitboxes these have to sit in are 0.5 x 0.45 and 0.9 x 0.7 blocks,
#: which is 8 x 7.2 and 14.4 x 11.2 units. Both models overrun their box lengthwise
#: by about two thirds, which is what the mole already does (18 units of animal in
#: an 11.2 unit box) and is the accepted trade for animals that are longer than
#: they are wide. Height and width stay inside it.
ANIMALS = {
    "shrew": {
        "profile": shrew_profile,
        "stance": 2,
        "spine_count": 3,
        # Three units of pitch, not two: the trunk is ten units now instead of
        # seven, and with the face chain in front of it the animal runs about
        # twenty against six wide - a wedge, not a pellet.
        "spine_pitch": 3,
        "spine_overlap": 1,
        "half_width": 3,
        "trunk_height": 5,
        # A shrew is mostly snout, and the wedge says so in four steps: head
        # four wide, muzzle three, snout two, tip one. Half the animal's
        # length is in front of its shoulders.
        "head": [4, 3, 3],
        "muzzle": [3, 2, 3],
        "snout": [2, 2, 4],
        # The last unit of the wedge, on the snout's own bone so it sniffs
        # with it. Painted as the bare nose it is.
        "snout_tip": [1, 1, 2],
        # Barely there, because a shrew's are. One texel of pinna is the
        # smallest thing that still reads as an ear rather than as an
        # artefact, and a shrew earns exactly that much ear.
        "ear": [1, 1, 1],
        "ear_x": 1.5,
        "leg": [1, 2, 1],
        # A haunch on each hind leg's bone: the one place a body this plain
        # buys silhouette during the trot, because the thigh swings with the
        # stride while the rump above it holds still.
        "thigh": [1, 2, 2],
        # Long, thin and two-boned. The first tail was two fat stubs; a
        # shrew's tail is a wire about half its body length.
        "tail": [[1, 1, 4], [1, 1, 4]],
        "tail_pitch": 4,
    },
    "weasel": {
        "profile": weasel_profile,
        "stance": 4,
        # Five spine segments on a three and a half unit pitch. Five and not
        # three because the great worm's lesson is that a travelling wave
        # reads by its joint count; three and a half and not four because the
        # four unit trunk came back from the game as a touch too long - the
        # in-game verdict trimmed half a unit per segment, twenty-two units
        # down to nineteen, and the tail stayed, because it was the body that
        # overshot. Half-unit pivots are fine; only box SIZES owe the texel
        # grid whole numbers, which is why the overlap dropped to 1.5 - it
        # keeps the box depth at a whole five.
        "spine_count": 5,
        "spine_pitch": 3.5,
        "spine_overlap": 1.5,
        "half_width": 4,
        "trunk_height": 5,
        # Small, flat and set low. A weasel's head is barely wider than its neck,
        # which is the whole trick that lets it follow prey underground - and at
        # this scale a head any larger turns the animal into an otter.
        "head": [5, 4, 4],
        # The muzzle step returns: it was cut when the atlas had to stay at
        # 64x32, and the five segment body has moved that boundary anyway.
        # Five into four into three is the jaw a predator's face needs.
        "muzzle": [4, 3, 3],
        "snout": [3, 2, 3],
        "ear": [2, 2, 1],
        "ear_x": 1.5,
        "leg": [2, 4, 2],
        # A forward-jutting foot at the bottom of each leg. A mustelid stands
        # on paws, not on the ends of four posts, and at this scale the one
        # unit of toe past the leg's front face is the whole of that
        # statement.
        "paw": [2, 1, 2],
        # Thinner and longer than the first tail, because the dark tip is the
        # mark that names the animal and a wire of a tail is what makes the
        # tip read as a tip - a third of the animal, half of it black.
        "tail": [[2, 2, 6], [2, 2, 5]],
        "tail_pitch": 5,
    },
}


# --- the builder ----------------------------------------------------------

def build_animal(spec):
    """Spine, head, muzzle, snout, ears, four legs and paws, tail. In that
    order and no other.

    The legs, the paws and the ears come out of loops rather than out of a
    list, so the two sides cannot end up asymmetric - the one error on an
    animal like this that is instantly visible in game and invisible in the
    source. `muzzle` and `paw` are optional dials: the muzzle rides the head
    bone and the paws ride their leg bones, so neither adds anything for
    `setupAnim` to know about.
    """
    out = []

    # --- spine ---------------------------------------------------------
    depth = spec["spine_pitch"] + spec["spine_overlap"]
    count = spec["spine_count"]
    floor = spec["stance"]
    widths = []
    for i in range(count):
        t = i / (count - 1)
        r = spec["profile"](t)
        width = max(3, round(2 * spec["half_width"] * r))
        height = max(3, round(spec["trunk_height"] * r))
        widths.append(width)
        centre = (i - (count - 1) / 2) * spec["spine_pitch"]
        out.append(cube(
            bone="spine%d" % i,
            parent="root",
            name="spine%d" % i,
            # On the floor of the trunk at the segment's own centre. A rotation
            # about a pivot in the middle of the box swings the belly through
            # the ground; about this one the segment turns like a rib.
            pivot=[0, floor, centre],
            box=[-width / 2, 0, -depth / 2],
            size=[width, height, depth],
        ))

    front = -(count - 1) / 2 * spec["spine_pitch"] - depth / 2
    back = (count - 1) / 2 * spec["spine_pitch"] + depth / 2
    chest, rump = "spine0", "spine%d" % (count - 1)

    # --- head and snout ------------------------------------------------
    head_w, head_h, head_d = spec["head"]
    # Bottom of the head flush with the bottom of the trunk, not centred on it.
    # Both of these carry their heads level with their bellies; a head hung from
    # the middle of the chest reads as a dog looking up.
    out.append(cube(
        bone="head", parent=chest, name="head",
        pivot=[0, floor, front],
        box=[-head_w / 2, 0, -head_d], size=spec["head"],
    ))

    if "muzzle" in spec:
        muzzle_w, muzzle_h, muzzle_d = spec["muzzle"]
        # On the head bone, not a bone of its own: it is the front of the
        # skull, and it has nothing to do that the head does not already do.
        # It reaches one unit past the head's front face and buries the rest of
        # its depth inside it, so the snout's twitch disappears into a socket
        # rather than into a flat wall.
        out.append(cube(
            bone="head", parent=chest, name="muzzle",
            pivot=[0, floor, front],
            box=[-muzzle_w / 2, 0, -head_d - 1], size=spec["muzzle"],
        ))

    snout_w, snout_h, snout_d = spec["snout"]
    out.append(cube(
        bone="snout", parent="head", name="snout",
        pivot=[0, floor, front - head_d],
        # Along the bottom of the head. A snout on the centre line leaves a step
        # under the chin, and the underside of a long muzzle is the one part of
        # it a player at this scale is looking straight at.
        box=[-snout_w / 2, 0, -snout_d], size=spec["snout"],
    ))

    if "snout_tip" in spec:
        tip_w, tip_h, tip_d = spec["snout_tip"]
        # On the snout's bone, half a unit buried in its end, so the twitch
        # in setupAnim carries the very point of the wedge.
        out.append(cube(
            bone="snout", parent="head", name="snout_tip",
            pivot=[0, floor, front - head_d],
            box=[-tip_w / 2, 0.5, -snout_d - tip_d + 0.5], size=spec["snout_tip"],
        ))

    ear_w, ear_h, ear_d = spec["ear"]
    for side, sign in (("l", 1.0), ("r", -1.0)):
        out.append(cube(
            bone="ear_%s" % side, parent="head", name="ear_%s" % side,
            # On the skull, a third of the way back, which is where an ear sits
            # on anything that hunts by hearing.
            pivot=[sign * spec["ear_x"], floor + head_h, front - head_d / 2],
            box=[-ear_w / 2, 0, -ear_d / 2], size=spec["ear"],
        ))

    # --- legs ----------------------------------------------------------
    leg_w, leg_h, leg_d = spec["leg"]
    hip_x = spec["half_width"] - leg_w / 2.0
    for side, sign in (("l", 1.0), ("r", -1.0)):
        for tag, parent, z in (("f", chest, front + leg_d), ("h", rump, back - leg_d)):
            bone = "leg_%s%s" % (tag, side)
            out.append(cube(
                bone=bone, parent=parent, name=bone,
                # Hip at the trunk's floor, so the leg swings from where it meets
                # the body and the foot reaches the ground exactly.
                pivot=[sign * hip_x, floor, z],
                box=[-leg_w / 2, -leg_h, -leg_d / 2], size=spec["leg"],
            ))
            if "paw" in spec:
                paw_w, paw_h, paw_d = spec["paw"]
                # On the sole, anchored one unit of toe past the leg's front
                # face and reaching back from there. Same bone: the leg swing
                # carries it.
                out.append(cube(
                    bone=bone, parent=parent, name="paw_%s%s" % (tag, side),
                    pivot=[sign * hip_x, floor, z],
                    box=[-paw_w / 2, -leg_h, -leg_d / 2 - 1], size=spec["paw"],
                ))
            if "thigh" in spec and tag == "h":
                thigh_w, thigh_h, thigh_d = spec["thigh"]
                # The haunch, at the top of the hind leg on the leg's own
                # bone, reaching up into the trunk: it swings with the stride
                # while the rump above holds still, which is where a small
                # quadruped's silhouette earns its motion.
                out.append(cube(
                    bone=bone, parent=parent, name="thigh_%s" % side,
                    pivot=[sign * hip_x, floor, z],
                    box=[-thigh_w / 2, 0, -thigh_d / 2], size=spec["thigh"],
                ))

    # --- tail ----------------------------------------------------------
    # Chained rather than flat, so the far half of a long tail follows the near
    # half instead of pivoting independently off the rump.
    parent = rump
    z = back
    # Read off the rump segment rather than off `trunk_height`, which is the
    # maximum and belongs to whichever segment the profile made fattest. A tail
    # centred on that number leaves a tapered animal above the middle of its own
    # backside.
    rump_height = out[count - 1]["size"][1]
    for index, size in enumerate(spec["tail"]):
        tail_w, tail_h, tail_d = size
        z += spec["tail_pitch"] if index else tail_d / 2
        out.append(cube(
            bone="tail%d" % index, parent=parent, name="tail%d" % index,
            # Level with the middle of the rump: a tail leaving at floor height
            # drags, and one leaving at the top of the back is a squirrel.
            pivot=[0, floor + rump_height / 2.0 - tail_h / 2.0, z],
            box=[-tail_w / 2, 0, -tail_d / 2], size=size,
        ))
        parent = "tail%d" % index

    return out


# --- plumbing -------------------------------------------------------------

def cube(bone, parent, name, pivot, box, size, rest=(0.0, 0.0, 0.0)):
    """One box, in natural space, with the bone it hangs off and that bone's parent.

    `box` is the box's minimum corner **relative to the bone pivot**, which is
    both the form `addBox` wants and the frame a surface function wants: zero is
    the part's own floor and its own centre line, so a rule like "how far up the
    flank" means the same thing on every box without knowing where the part sits.
    """
    w, h, d = size
    return {
        "bone": bone,
        "parent": parent,
        "name": name,
        "pivot": [float(v) for v in pivot],
        "rest": [float(v) for v in rest],
        "size": [int(w), int(h), int(d)],
        "from": [float(box[0]), float(box[1]), float(box[2])],
        "to": [float(box[0] + w), float(box[1] + h), float(box[2] + d)],
    }


def layout(cubes, canvas_width=CANVAS_WIDTH):
    """Cubes with their UV offsets and face rectangles, plus the canvas needed."""
    offsets, used = pack(cubes, canvas_width)
    for item, offset in zip(cubes, offsets):
        item["uv_offset"] = offset
        item["faces"] = box_faces(offset, item["size"])
    height = 1 << max(4, math.ceil(math.log2(max(used, 1))))
    return cubes, canvas_width, height


def build(name):
    return layout(build_animal(ANIMALS[name]))


def bones(cubes):
    """Bone name -> its cubes, in the order the bones were first mentioned."""
    out = {}
    for item in cubes:
        out.setdefault(item["bone"], []).append(item)
    return out


def verify():
    """Checks the packing, the bone tree, and that a box's six faces tile.

    The face check is the one worth having: `box_faces` promises a box takes
    `2*(d+w)` by `d+h` pixels of which two corners stay empty, and if it ever
    disagreed with that the texture would be painted through a layout the
    geometry does not have. The tree check catches a parent named before it is
    defined, which the Java emitter would turn into an undeclared variable and
    the Blockbench one into a silently flat outliner.
    """
    for name in ANIMALS:
        cubes, width, height = build(name)

        seen = {"root"}
        for bone, items in bones(cubes).items():
            assert items[0]["parent"] in seen, \
                "%s: bone %s hangs off %s, which comes later" % (name, bone, items[0]["parent"])
            seen.add(bone)

        used = set()
        for item in cubes:
            w, h, d = item["size"]
            own = set()
            for rect in item["faces"].values():
                u1, v1, u2, v2 = rect
                for y in range(min(v1, v2), max(v1, v2)):
                    for x in range(min(u1, u2), max(u1, u2)):
                        assert (x, y) not in used, "%s: UV overlap at %d,%d" % (name, x, y)
                        assert 0 <= x < width and 0 <= y < height, "%s: UV off canvas" % name
                        own.add((x, y))
                        used.add((x, y))
            expected = 2 * (d * w + d * h + w * h)
            assert len(own) == expected, \
                "%s/%s: faces cover %d px, six sides are %d" % (name, item["name"], len(own), expected)
        yield name, width, height, len(used) / (width * height)


# --- Java emitter ---------------------------------------------------------

def _f(value):
    # The `+ 0.0` is not decoration: without it a value of -0.0 prints as
    # "-0.0F", which compiles and means nothing and looks like a mistake.
    return "%.1FF" % (value + 0.0)


def java(name):
    """The body of `createBodyLayer()` for one animal.

    Written out rather than transcribed because a transcription error here is a
    texture that is subtly wrong on one face of one box, which is the kind of
    thing that survives a dozen looks.

    Every bone is assigned to a local, whether or not anything hangs off it. The
    alternative is deciding per bone, and a leaf that later grows a child then
    fails to compile for a reason that has nothing to do with the change.
    """
    cubes, width, height = build(name)
    tree = bones(cubes)
    pivots = {"root": [0.0, 0.0, 0.0]}

    lines = [
        "    public static LayerDefinition createBodyLayer() {",
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot()",
        "                .addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));",
        "",
    ]

    for bone, items in tree.items():
        parent = items[0]["parent"]
        pivot = items[0]["pivot"]
        pivots[bone] = pivot
        # PartPose is relative to the parent bone; addBox is relative to this one.
        base = pivots[parent]
        offset = [pivot[i] - base[i] for i in range(3)]

        out = ["        PartDefinition %s = %s.addOrReplaceChild(\"%s\", CubeListBuilder.create()"
               % (bone, parent, bone)]
        for item in items:
            u, v = item["uv_offset"]
            bx, by, bz = item["from"]
            w, h, d = item["size"]
            # The one conversion: a box standing on `by` with height `h` in
            # natural space hangs from `-by - h` in a model the renderer flips.
            out.append(
                "                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)"
                % (u, v, _f(bx), _f(-by - h), _f(bz), _f(w), _f(h), _f(d)))
        out[-1] += ","
        out.append("                PartPose.offset(%s, %s, %s));"
                   % (_f(offset[0]), _f(-offset[1]), _f(offset[2])))
        lines.extend(out)
        lines.append("")

    lines.append("        return LayerDefinition.create(mesh, %d, %d);" % (width, height))
    lines.append("    }")
    return "\n".join(lines)


def blockbench(name):
    """The bones and cubes as the MCP bridge wants them.

    Blockbench works in the same space this module does - Y up, floor at 0, -Z
    forward - and its group origins are absolute however the outliner is nested.
    Two differences from the JSON dump: `place_cube` wants absolute corners rather
    than pivot-relative ones, and the `parent` field is a name for the
    reparenting pass rather than a coordinate frame.

    And one conversion, which is the whole reason this function is not four lines:
    **X is negated.** The Modded Entity exporter mirrors X on the way out, so a
    bone authored at +X here arrives at -X in Java, which is the entity's *right*.
    Everything in this module is named for the Java result - `leg_fl` is the front
    left leg in game - so the `.bbmodel` has to be built with those parts on the
    other side. Blockbench's own export then lands on exactly the signs `java()`
    emits, and the saved project stays a source of truth rather than a mirrored
    copy of one.

    This was found rather than remembered: the shrew was built without the flip,
    and Blockbench's export came back with every `_l` part at negative X. The
    beetle's `.bbmodel` has the same flip baked in, which is what
    `SoilBeetleModel` means by "they were authored the other way round".
    `docs/MODEL_WORKFLOW.md` step 4 is the rule; this is the place that applies it.

    This exists so the models are still built from the formula after they move
    into Blockbench. The editor is the authoring surface and the `.bbmodel` is
    what a person opens later, but nobody types thirty cube corners into it by
    hand - and feeding the bridge from here is what keeps the UV offsets
    identical to the ones the texture is painted through.
    """
    cubes, width, height = build(name)
    flip = [-1.0, 1.0, 1.0]

    def point(pivot, corner):
        return [flip[i] * (pivot[i] + corner[i]) for i in range(3)]

    out = []
    for bone, items in bones(cubes).items():
        pivot = items[0]["pivot"]
        out.append({
            "name": bone,
            "parent": items[0]["parent"],
            "origin": [flip[i] * pivot[i] for i in range(3)],
            "rotation": [math.degrees(a) for a in items[0]["rest"]],
            "cubes": [{
                "name": item["name"],
                # Negating X swaps which corner is the minimum, and Blockbench
                # requires `from` to be the lower one on every axis. Sorting the
                # pair is cheaper than knowing which one it was.
                "from": [min(a, b) for a, b in
                         zip(point(pivot, item["from"]), point(pivot, item["to"]))],
                "to": [max(a, b) for a, b in
                       zip(point(pivot, item["from"]), point(pivot, item["to"]))],
                "origin": [flip[i] * pivot[i] for i in range(3)],
                "uv_offset": item["uv_offset"],
            } for item in items],
        })
    return {"texture": [width, height], "bones": out}


if __name__ == "__main__":
    for animal, width, height, fill in verify():
        print("// %s: %dx%d atlas, %.0f%% covered" % (animal, width, height, fill * 100),
              file=sys.stderr)

    if "--bb" in sys.argv:
        print(json.dumps(blockbench(sys.argv[sys.argv.index("--bb") + 1]), indent=1))
    elif "--java" in sys.argv:
        for animal in ANIMALS:
            print("// --- %s ---" % animal)
            print(java(animal))
            print()
    else:
        print(json.dumps({
            animal: dict(zip(("cubes", "width", "height"), build(animal)))
            for animal in ANIMALS
        }, indent=1))

"""Geometry and UV layout for the three small burrow animals.

The earthworm, the soil beetle and the grub are between five and eleven boxes
each - small enough that hand-placing them looks tempting and just large enough
that it goes wrong. What actually goes wrong is never the geometry, it is the
bookkeeping around it: a UV rectangle overlapping its neighbour, a `texOffs`
that no longer matches the box it belongs to, a leg at a position nobody can
justify. So the boxes come out of a table of dials, the UV layout out of
`great_worm_shape`'s shelf packer, and the Java out of this file.

`critter_textures.py` imports this module and paints through the same face
rectangles the geometry was packed with, exactly as the great worm's two scripts
do. Geometry and texture therefore cannot drift apart, which is the only reason
either of them is generated rather than typed.

## Coordinates

Natural space throughout, the same one `great_worm_shape` uses: Y up with the
floor at 0, -Z forward, X across, and every box written relative to the bone it
hangs off. `java()` applies the one conversion at the end, negating Y, because a
Minecraft entity model is authored with its root at y = 24 and its boxes hanging
*down* from there - the renderer flips Y before drawing. A box that stands from
0 to 5 in natural space is emitted as `addBox(x, -5, z, w, 5, d)`.

That flip is also the answer to the trap in `box_faces`. The rectangle it calls
"up" is the one Minecraft's own `ModelPart.Cube` hands to `Direction.DOWN`, and
the two are not in conflict: `Direction` there is named in model space, before
the flip, so model-space DOWN is the surface a player sees on *top* of the
animal. The names in `box_faces` are world names and are the useful ones. Read
`ModelPart.Cube`'s constructor before doubting this - it is six explicit
rectangles and it settles the question in a way an editor read-back cannot.

The same flip mirrors X, so the whole model is handed to the game left-right
reversed. Two of these three animals are symmetric and do not care. The grub's
curl is not, and does not care either: its sideways offsets and the angles that
follow them are mirrored together, so the shape survives - it simply curls the
other way, and nobody knows which way a grub is supposed to curl.

## What is a dial and what is not

Every length below is a dial. The rules that turn them into boxes - the girth
profile, the leg sockets spaced evenly along the body, the grub's curl - are
formulas, so that changing "how long is a grub" is one number rather than a
rewrite of five boxes and five UV offsets.

Run it for the numbers:

    python art/generators/critter_shapes.py            # JSON, all three
    python art/generators/critter_shapes.py --java     # createBodyLayer bodies
"""

import json
import math
import sys

from great_worm_shape import box_faces, even, pack, profile
from texture_kit import smooth

#: Every atlas is this wide. Small enough that the three animals stay cheap,
#: wide enough that the beetle's body - the widest single box here at 28 px of
#: UV - fits on a shelf with room to spare.
CANVAS_WIDTH = 32


# --- earthworm ------------------------------------------------------------

#: Head, sixteen body segments, tail. The first worm had five and was judged
#: in game as too short, twice - a dropped sausage at under three times as
#: long as wide, where a real earthworm runs ten. Eighteen segments on the
#: same two unit pitch make the body thirty-seven units against four wide,
#: nine to one, and the length comes from count rather than from deeper
#: boxes on purpose: every segment is a joint of the peristalsis chain, and
#: the chain reads better the more joints it has. The lateral S stays one
#: period over the whole body (its phase step is TWO_PI over the count), so
#: the yaw per joint FALLS as the count rises - more segments make the curve
#: smoother, never springier.
WORM_SEGMENTS = 18

#: Distance between two segment pivots, and how deep each box is. The one unit
#: of overlap is what stops a contracting segment from tearing a gap open, the
#: same reason the great worm carries two.
WORM_PITCH = 2
WORM_OVERLAP = 1

#: Half the widest segment, and the height of it. Four units across and three
#: high against a 0.4 x 0.2 hitbox: the back pokes a little above the box, which
#: is normal for an animal this flat and is what the great worm does on a far
#: larger scale.
WORM_MAX_HALF_WIDTH = 2
WORM_MAX_HEIGHT = 3

WORM_NAMES = ["head"] + ["body%d" % i for i in range(1, WORM_SEGMENTS - 1)] + ["tail"]

#: The prostomium: the lobe an earthworm noses along the ground with, as a
#: small box riding the head bone half a unit up and reaching past the front.
#: At this scale it is one step, not the great worm's three - a two unit nose
#: is the whole of what four units of width can afford.
WORM_NOSE = [2, 2, 2]

#: The tail paddle: flat and a little wider than the segment it follows,
#: because an earthworm's posterior is pressed flat and a tip that only
#: shrinks makes both ends of the animal look alike. Rides the tail bone.
WORM_PADDLE = [4, 1, 3]

#: Where the clitellum saddle sits, as a fraction of the body. A third of the
#: way back; the ring lands on whichever segment is nearest.
WORM_CLITELLUM_AT = 0.33

#: The clitellum, as geometry: a one-deep box riding its host segment's bone,
#: half a unit wider each side and a unit taller than the segment it wraps.
#:
#: The painted band alone read as a stripe, because at this scale a stripe is
#: all a flat band can be - the great worm's notes say a band of even weight
#: reads as painted, and on an animal four units wide there is no room for
#: the weighting trick. Geometry says "swelling" the way no pigment can. It
#: rides its segment, so the peristaltic pulse swells it with its host, and
#: the saddle is painted through the name check in `critter_textures`: the
#: ring's upper flank is clitellum colour, its underside stays skin, because
#: a real clitellum is open beneath. One deep, because a clitellum one unit
#: of depth narrower is not a thing anyone can see at this scale and the
#: atlas rows are better spent on sixteen body segments.
WORM_CLITELLUM_SIZE = [5, 4, 1]


def earthworm():
    """The small worm: the great worm's girth profile, sampled shorter.

    Deliberately the same `profile` function - variant C's dials, promoted
    from the comparison sheet. It is the same animal at a different age, and
    a second curve fitted by eye would have been a different species drawn by
    a different hand.
    """
    depth = WORM_PITCH + WORM_OVERLAP
    out = []
    for i in range(WORM_SEGMENTS):
        t = i / (WORM_SEGMENTS - 1)
        r = profile(t)
        # Odd widths allowed, the grub's lesson: at four units of maximum
        # girth `even` leaves only 2 and 4, and the head taper becomes one
        # hard step. A three wide box is still symmetric about x = 0.
        width = max(2, round(2 * WORM_MAX_HALF_WIDTH * r))
        height = max(2, round(WORM_MAX_HEIGHT * r))
        centre = (i - (WORM_SEGMENTS - 1) / 2) * WORM_PITCH
        out.append(cube(
            bone=WORM_NAMES[i],
            name=WORM_NAMES[i],
            # Pivot on the floor at the segment's own centre, so a vertical
            # swell grows upwards out of the ground rather than sinking through
            # it and a lengthwise pulse stays centred.
            pivot=[0, 0, centre],
            box=[-width / 2, 0, -depth / 2],
            size=[width, height, depth],
        ))

    # The clitellum ring, centred on whichever segment sits nearest a third
    # of the way back. See WORM_CLITELLUM_SIZE.
    span = (WORM_SEGMENTS - 1) * WORM_PITCH + depth
    target = -span / 2 + WORM_CLITELLUM_AT * span
    host = min(out, key=lambda c: abs(c["pivot"][2] - target))
    ring_w, ring_h, ring_d = WORM_CLITELLUM_SIZE
    out.append(cube(
        bone=host["bone"],
        name="clitellum",
        pivot=host["pivot"],
        box=[-ring_w / 2, 0, -ring_d / 2],
        size=WORM_CLITELLUM_SIZE,
    ))

    # The prostomium, half a unit up on the head bone, reaching one unit past
    # the front. See WORM_NOSE.
    head = out[0]
    nose_w, nose_h, nose_d = WORM_NOSE
    out.append(cube(
        bone="head",
        name="prostomium",
        pivot=head["pivot"],
        box=[-nose_w / 2, 0.5, -depth / 2 - nose_d + 1],
        size=WORM_NOSE,
    ))

    # The tail paddle, flat on the floor past the last segment. See WORM_PADDLE.
    tail = out[WORM_SEGMENTS - 1]
    pad_w, pad_h, pad_d = WORM_PADDLE
    out.append(cube(
        bone="tail",
        name="paddle",
        pivot=tail["pivot"],
        box=[-pad_w / 2, 0, depth / 2 - 1],
        size=WORM_PADDLE,
    ))
    return out


# --- soil beetle ----------------------------------------------------------

#: The belly shell: the box everything else stands on. Eight deep by seven
#: wide is a beetle rather than a woodlouse, and the odd width exists to give
#: the elytral suture a one-unit centre column. See BEETLE_SUTURE_GAP.
BEETLE_BODY = [7, 3, 8]

#: The pronotum: its own raised shield over the front third of the shell,
#: flush with the shell's front edge. This is the rebuild's core change - the
#: first crown was one plate, and a beetle's most recognisable break, the
#: straight line where pronotum ends and wing cases begin, was paint. Now it
#: is a real step in the geometry, half a unit of height and a full joint
#: line, and the head tucks under the shield's front edge the way a ground
#: beetle's actually does.
BEETLE_PRONOTUM = [6, 2, 3]

#: One wing case. The pair sits behind the pronotum with the suture gap
#: between them, and reaches one unit past the shell's rear - elytra overhang
#: the abdomen, and that overhang is most of a beetle's rear silhouette.
BEETLE_ELYTRON = [3, 2, 6]

#: The gap between the two wing cases: the suture as a groove, its floor the
#: lit centre column of the shell top.
BEETLE_SUTURE_GAP = 1

#: The head, small and tucked under the pronotum's edge, with a mandible
#: either side of the mouth - the pincers that make the front of the animal
#: a FACE at this scale.
BEETLE_HEAD = [3, 2, 2]
BEETLE_MANDIBLE = [1, 1, 2]

#: One leg, three real segments on one bone: the coxa reaches out and up from
#: under the shell's edge, the tibia drops from the elbow to the floor wholly
#: outside the shell, and the tarsus is a tiny foot-box lying forward on the
#: ground. Elbow-up, the way a ground beetle stands, and the reason the legs
#: frame the animal instead of tangling under it.
BEETLE_COXA = [4, 1, 1]
BEETLE_TIBIA = [1, 3, 1]
BEETLE_TARSUS_FOOT = [1, 1, 2]

#: How far under the shell a coxa starts, measured in from its edge.
BEETLE_LEG_INSET = 1

#: The antenna, three segments with a bend: scape straight forward, flagellum
#: kinked outward and on, club at the tip. Segmented feelers are half of what
#: separates an insect from a toy at reading distance.
BEETLE_SCAPE = [1, 1, 2]
BEETLE_FLAGELLUM = [1, 1, 3]
BEETLE_CLUB = [1, 1, 1]

#: How far the shell floats above the floor. Two units: one for the legs and
#: one of shadow underneath, which is what stops the animal from looking
#: printed on the ground.
BEETLE_CLEARANCE = 2

#: Where along the body the three leg sockets sit, as a fraction from the
#: front of the shell to the back. The whole flank, front socket under the
#: pronotum's edge and the rear one in the shell's back third: the in-game
#: verdict on the 2.5-unit cluster was that it still bunched, and a real
#: beetle at this body size carries six or seven units between its first and
#: last hip. 6.5 here.
BEETLE_SOCKETS = [0.06, 0.5, 0.9]

#: Resting sweep of each leg pair, in degrees, front pair first. The middle
#: pair points straight out and the other two brace forward and back, which is
#: the stance that makes a six-legged thing look planted rather than stuck on.
BEETLE_SWEEP = [-24.0, 0.0, 26.0]


def soil_beetle():
    """Shell, pronotum, two elytra, head with mandibles, six three-piece legs,
    two three-piece feelers.

    The legs, the mandibles and the feelers come out of loops rather than a
    list of boxes so the two sides cannot end up asymmetric - which is the one
    error on an animal like this that is instantly visible and impossible to
    see in the source.
    """
    body_w, body_h, body_d = BEETLE_BODY
    floor = BEETLE_CLEARANCE
    front = -body_d / 2

    out = [
        cube(bone="body", name="shell", pivot=[0, floor, 0],
             box=[-body_w / 2, 0, front], size=BEETLE_BODY),
        # The pronotum shield, front-flush, half a unit up off the shell.
        cube(bone="body", name="pronotum", pivot=[0, floor, 0],
             box=[-BEETLE_PRONOTUM[0] / 2, body_h - 0.5, front],
             size=BEETLE_PRONOTUM),
    ]

    # The wing cases, from the pronotum's rear edge to one unit past the
    # shell's, with the suture groove between them.
    ely_w, ely_h, ely_d = BEETLE_ELYTRON
    ely_z = front + BEETLE_PRONOTUM[2]
    for side, sign in (("l", 1.0), ("r", -1.0)):
        inner = sign * BEETLE_SUTURE_GAP / 2
        out.append(cube(
            bone="body", name="elytron_%s" % side, pivot=[0, floor, 0],
            box=[min(inner, inner + sign * ely_w), body_h - 0.5, ely_z],
            size=BEETLE_ELYTRON,
        ))

    # The head, tucked under the pronotum's front edge, mouth-parts first.
    head_w, head_h, head_d = BEETLE_HEAD
    out.append(cube(bone="head", name="head", pivot=[0, floor, front],
                    box=[-head_w / 2, 0.5, -head_d], size=BEETLE_HEAD))
    man_w, man_h, man_d = BEETLE_MANDIBLE
    for side, sign in (("l", 1.0), ("r", -1.0)):
        out.append(cube(
            bone="head", name="mandible_%s" % side, pivot=[0, floor, front],
            box=[(0.25 if sign > 0 else -0.25 - man_w), 0.5,
                 -head_d - man_d + 0.5],
            size=BEETLE_MANDIBLE,
        ))

    coxa_w, coxa_h, coxa_d = BEETLE_COXA
    tibia_w, tibia_h, tibia_d = BEETLE_TIBIA
    foot_w, foot_h, foot_d = BEETLE_TARSUS_FOOT
    for side, sign in (("l", 1.0), ("r", -1.0)):
        for index, along in enumerate(BEETLE_SOCKETS):
            z = round((front + along * body_d) * 2.0) / 2.0
            bone = "leg_%s%d" % (side, index)
            pivot = [sign * body_w / 2, floor, z]
            rest = [0.0, math.radians(sign * BEETLE_SWEEP[index]), 0.0]
            elbow = sign * (coxa_w - BEETLE_LEG_INSET)
            out.append(cube(
                bone=bone, name="coxa_%s%d" % (side, index), pivot=pivot,
                box=[-BEETLE_LEG_INSET if sign > 0 else BEETLE_LEG_INSET - coxa_w,
                     0.5, -coxa_d / 2],
                size=BEETLE_COXA, rest=rest,
            ))
            out.append(cube(
                bone=bone, name="tibia_%s%d" % (side, index), pivot=pivot,
                box=[elbow - tibia_w if sign > 0 else elbow, -floor, -tibia_d / 2],
                size=BEETLE_TIBIA, rest=rest,
            ))
            # The tarsus: a foot-box lying forward on the ground at the
            # tibia's base. Tiny, and the thing that makes the leg END rather
            # than stop.
            out.append(cube(
                bone=bone, name="foot_%s%d" % (side, index), pivot=pivot,
                box=[elbow - tibia_w if sign > 0 else elbow, -floor,
                     -tibia_d / 2 - foot_d + 0.5],
                size=BEETLE_TARSUS_FOOT, rest=rest,
            ))

        # The feeler: scape, kinked flagellum, club, all on one bone whose
        # pivot sits at the head's upper front corner.
        antenna = "antenna_%s" % side
        pivot = [sign * 1.0, floor + head_h, front - head_d + 0.5]
        rest = [0.0, math.radians(sign * 18.0), 0.0]
        sc_w, sc_h, sc_d = BEETLE_SCAPE
        fl_w, fl_h, fl_d = BEETLE_FLAGELLUM
        cl_w, cl_h, cl_d = BEETLE_CLUB
        out.append(cube(
            bone=antenna, name="scape_%s" % side, pivot=pivot,
            box=[-sc_w / 2, 0, -sc_d], size=BEETLE_SCAPE, rest=rest,
        ))
        out.append(cube(
            bone=antenna, name="flagellum_%s" % side, pivot=pivot,
            box=[-fl_w / 2 + sign * 0.5, 0.25, -sc_d - fl_d + 0.5],
            size=BEETLE_FLAGELLUM, rest=rest,
        ))
        out.append(cube(
            bone=antenna, name="club_%s" % side, pivot=pivot,
            box=[-cl_w / 2 + sign * 1.0, 0.5, -sc_d - fl_d - cl_d + 1.0],
            size=BEETLE_CLUB, rest=rest,
        ))

    return out


# --- grub -----------------------------------------------------------------

#: Four segments and a head capsule. Fewer than the worm because a grub is a fat
#: comma rather than a long line, and the fatness has to come from somewhere.
#:
#: The pitch went from two to three after an in-game verdict of "sehr
#: gestaucht": at two, four fat segments with proud rings between them stood
#: shoulder to shoulder, and the bow pressed the rings into a shingled crumple.
#: The fat silhouette has to come from girth, not from compression - at three
#: units of pitch each ring keeps a unit of air to its neighbour even with the
#: bow applied, and the animal reads as segments rather than as a block.
GRUB_SEGMENTS = 6
GRUB_PITCH = 3
GRUB_OVERLAP = 1

GRUB_MAX_HALF_WIDTH = 3
GRUB_MAX_HEIGHT = 6

#: The head capsule, two boxes: the amber dome and a smaller face-plate in
#: front of it. Small, and the only hard part of the animal - it rides
#: body0's bone (this module's emitter keeps all bones flat under root, and
#: the capsule has nothing to do that the segment it is set into does not),
#: but it is its own box GROUP with its own harder tone, which is what makes
#: the front of a curl-grub read as a head rather than as a darker segment.
GRUB_HEAD = [3, 3, 2]
GRUB_FACE = [2, 2, 1]

#: The six true legs, three tiny stubs a side under the thoracic segments -
#: the one anatomical mark that separates a chafer grub from a maggot at a
#: glance. Half a unit proud of the flank, low, on the front two segments'
#: bones so the pulse carries them.
GRUB_LEG = [1, 1, 1]

#: How far the middle of the body bows sideways, in units.
#:
#: A scarab larva at rest is a comma seen from above and this is the whole of
#: that shape. Sideways rather than a vertical arch on purpose: a curl in the
#: vertical plane lifts the middle segments off the floor and opens a gap under
#: the animal that reads as a broken model rather than as a posture.
#:
#: Small, and it has to be. The boxes are four deep on a three unit pitch, so a
#: joint has one unit of overlap to hide a shear in; a bow of this size turns the
#: end segments by about ten degrees, which that unit covers. The first
#: attempt at 2.2 was a hairpin - the ends stood at fifty degrees and the corners
#: of the boxes came apart.
#:
#: The second attempt at 0.9 came apart differently, and only in the viewport
#: from above: the bow steps the body sideways at the same joints where the girth
#: profile steps it wider, the two add on one flank, and a comma came out as a
#: staircase. The fix was both halves - a gentler profile below and less bow
#: here. A curve built from four boxes can afford one abrupt change per joint,
#: not two.
#:
#: Raised again to 1.1 when the body went to six segments: with five joints
#: instead of three the same C costs only twelve degrees at the steepest
#: joint, inside what one unit of overlap hides, and the resting silhouette
#: finally reads as the C-shape a curl-grub is named for.
GRUB_CURL = 1.1

GRUB_NAMES = ["body%d" % i for i in range(GRUB_SEGMENTS)]

#: The annular bulge on each segment: one unit of extra width, half a unit a
#: side, standing half a unit proud of the back as well.
#:
#: This is what makes the segments read as rings rather than as a taper. The
#: creases at the box ends say "jointed"; the bulges between them say "fat",
#: and a grub is the second thing. Each ring is one box deep at its segment's
#: own centre, on the segment's own bone, so the bow, the pulse and the
#: fattening all carry it for free.
#:
#: A ring is one unit shorter than its segment and starts a unit and a half up,
#: which keeps it half a unit proud of the back while the bottom edge hides
#: behind the flank of an animal whose belly drags. The lost unit is a packing
#: decision: at full height the four ring strips pushed the atlas three rows
#: over a power-of-two boundary, and the rows they gave up are rows nobody can
#: see. The beetle's crown and the weasel's tail record the same trade.
GRUB_RING_FLARE = 0.5
#: Two deep on the three unit pitch: wider rings, further apart, which is the
#: half of the de-crumpling the flare alone could not do.
GRUB_RING_DEPTH = 2
GRUB_RING_TRIM = 1


def grub_profile(t):
    """Girth along a grub, 0 at the head and 1 at the tail tip.

    Fattest a third of the way back, where the thoracic segments carry the legs
    a chafer larva barely uses, then tapering. Same two-clamped-ramps shape as
    the worm's profile and for the same reason: a symmetric spindle reads as a
    maggot, and a grub is not one.

    The head ramp is deliberately slack. At `0.72 + 0.28 * smooth(t / 0.30)` it
    was at full girth by the second segment, so the front joint gained forty per
    cent of width in one step and the animal had a shoulder. Over four segments
    every ramp has to be slower than looks right on the curve, because four
    samples of a smooth function are not a smooth shape.
    """
    head = 0.82 + 0.18 * smooth(t / 0.45)
    tail = 1.0 - 0.34 * smooth((t - 0.45) / 0.55)
    return head * tail


def grub_bow(t):
    """Sideways offset of the body at position `t` along it.

    Half a period of a sine: both ends on the axis, the middle pushed out. A
    bow, and the shape a fat larva actually falls into when it is not fully
    curled - a full C would need twice the segments to describe without the
    corners of the boxes parting.
    """
    return GRUB_CURL * math.sin(math.pi * t)


def grub_angle(i):
    """The angle segment `i` sits at, from the chord between its neighbours.

    Read off the two segments either side rather than from the derivative of
    `grub_bow`. The derivative is the slope of the ideal curve; what must not
    shear is the joint between two boxes that actually exist, and at four
    segments those are not the same number. The ends use the one neighbour they
    have.
    """
    last = GRUB_SEGMENTS - 1
    before = max(0, i - 1)
    after = min(last, i + 1)
    run = (after - before) * GRUB_PITCH
    rise = grub_bow(after / last) - grub_bow(before / last)
    return math.atan2(rise, run)


def grub():
    depth = GRUB_PITCH + GRUB_OVERLAP
    out = []
    for i in range(GRUB_SEGMENTS):
        t = i / (GRUB_SEGMENTS - 1)
        r = grub_profile(t)
        # Odd widths allowed, unlike the worm's. `even` exists to keep a box
        # symmetric about x = 0, and the bow has already taken that away - so
        # forcing even widths here bought nothing and cost the taper its middle
        # step, leaving a four unit segment butted against a six unit one.
        width = max(4, round(2 * GRUB_MAX_HALF_WIDTH * r))
        height = max(3, round(GRUB_MAX_HEIGHT * r))
        centre = (i - (GRUB_SEGMENTS - 1) / 2) * GRUB_PITCH
        out.append(cube(
            bone=GRUB_NAMES[i],
            name=GRUB_NAMES[i],
            pivot=[grub_bow(t), 0, centre],
            box=[-width / 2, 0, -depth / 2],
            size=[width, height, depth],
            # The bow is a rest pose and never changes, so it belongs in the
            # geometry rather than in setupAnim. What setupAnim adds on top is
            # the pulse, and the two superpose: the bow is the constant term.
            rest=[0.0, grub_angle(i), 0.0],
        ))
        # The segment's ring. See GRUB_RING_FLARE.
        out.append(cube(
            bone=GRUB_NAMES[i],
            name="ring%d" % i,
            pivot=[grub_bow(t), 0, centre],
            box=[-width / 2 - GRUB_RING_FLARE,
                 GRUB_RING_TRIM + GRUB_RING_FLARE, -GRUB_RING_DEPTH / 2],
            size=[width + 2 * GRUB_RING_FLARE, height - GRUB_RING_TRIM, GRUB_RING_DEPTH],
            rest=[0.0, grub_angle(i), 0.0],
        ))

    head_w, head_h, head_d = GRUB_HEAD
    # On the front segment's own bone, so the head follows the body's pulse
    # instead of hanging in the air where the body used to be.
    front = out[0]
    out.append(cube(
        bone=GRUB_NAMES[0],
        name="head",
        pivot=front["pivot"],
        # Just clear of the floor: a grub's head capsule is tucked under the
        # first segment, not stuck on the end of it like a nose.
        box=[-head_w / 2, 1, -depth / 2 - head_d],
        size=GRUB_HEAD,
        rest=front["rest"],
    ))
    # The face-plate, the capsule's second box: a smaller step in front, which
    # is what turns the amber lump into a face with a jawline.
    face_w, face_h, face_d = GRUB_FACE
    out.append(cube(
        bone=GRUB_NAMES[0],
        name="face",
        pivot=front["pivot"],
        box=[-face_w / 2, 1, -depth / 2 - head_d - face_d + 0.5],
        size=GRUB_FACE,
        rest=front["rest"],
    ))

    # The six true legs. See GRUB_LEG. Two pairs on the first segment, one on
    # the second; sideways stubs low on the flank, because a curl-grub's legs
    # are folded useless things it carries, not stands on.
    leg_w, leg_h, leg_d = GRUB_LEG
    for bone_index, dzs in ((0, (-1.0, 0.5)), (1, (-0.5,))):
        host = out_segment = None
        for item in out:
            if item["name"] == GRUB_NAMES[bone_index]:
                host = item
                break
        width = host["size"][0]
        for side, sign in (("l", 1.0), ("r", -1.0)):
            for k, dz in enumerate(dzs):
                out.append(cube(
                    bone=GRUB_NAMES[bone_index],
                    name="leg_%s%d_%d" % (side, bone_index, k),
                    pivot=host["pivot"],
                    box=[sign * (width / 2 - 0.5) - leg_w / 2, 0.25, dz - leg_d / 2],
                    size=GRUB_LEG,
                    rest=host["rest"],
                ))
    return out


# --- plumbing -------------------------------------------------------------

def cube(bone, name, pivot, box, size, rest=(0.0, 0.0, 0.0)):
    """One box, in natural space, with the bone it hangs off.

    `box` is the box's minimum corner **relative to the bone pivot**, which is
    both the form `addBox` wants and the frame a surface function wants: zero is
    the part's own floor and its own centre line, so a rule like "along the
    spine" or "how far up the flank" means the same thing on every box without
    knowing where the part sits on the animal. `rest` is the bone's resting
    rotation in radians; only the grub and the beetle's legs have one.
    """
    w, h, d = size
    return {
        "bone": bone,
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


CREATURES = {
    "earthworm": earthworm,
    "soil_beetle": soil_beetle,
    "grub": grub,
}

#: Per-creature canvas widths where the shared default does not fit. The
#: eighteen-segment worm's strips stack past sixty-four rows on a 32 wide
#: canvas but pack four to a shelf on 64, which is the difference between a
#: 32x128 atlas and a 64x32 one.
CANVAS_WIDTHS = {"earthworm": 64, "grub": 64, "soil_beetle": 64}


def build(name):
    return layout(CREATURES[name](), CANVAS_WIDTHS.get(name, CANVAS_WIDTH))


def verify():
    """Checks the packing, and that a box's six faces tile what they should.

    The second check is the one worth having. `box_uv_size` promises a box takes
    `2*(d+w)` by `d+h` pixels, of which two `d` by `w` corners stay empty; if
    `box_faces` ever disagreed with that - a swapped rectangle, an off-by-one -
    the covered area would change and the texture would be painted through a
    layout the geometry does not have.
    """
    for name in CREATURES:
        cubes, width, height = build(name)
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
    """The body of `createBodyLayer()` for one creature.

    Written out rather than described because the alternative is transcribing
    thirty-odd numbers by hand, and a transcription error here is a texture that
    is subtly wrong on one face of one box - the kind of thing that survives a
    dozen looks.
    """
    cubes, width, height = build(name)
    lines = [
        "    public static LayerDefinition createBodyLayer() {",
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot()",
        "                .addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));",
        "",
    ]

    bones = {}
    for item in cubes:
        bones.setdefault(item["bone"], []).append(item)

    for bone, items in bones.items():
        pivot = items[0]["pivot"]
        rest = items[0]["rest"]
        builder = ["        root.addOrReplaceChild(\"%s\", CubeListBuilder.create()" % bone]
        for item in items:
            u, v = item["uv_offset"]
            bx, by, bz = item["from"]
            w, h, d = item["size"]
            # The one conversion: a box standing on `by` with height `h` in
            # natural space hangs from `-by - h` in a model the renderer flips.
            builder.append(
                "                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)"
                % (u, v, _f(bx), _f(-by - h), _f(bz), _f(w), _f(h), _f(d)))
        builder[-1] += ","
        if any(abs(angle) > 1e-6 for angle in rest):
            builder.append(
                "                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
                % (_f(pivot[0]), _f(-pivot[1]), _f(pivot[2]),
                   "%.4FF" % rest[0], "%.4FF" % rest[1], "%.4FF" % rest[2]))
        else:
            builder.append("                PartPose.offset(%s, %s, %s));"
                           % (_f(pivot[0]), _f(-pivot[1]), _f(pivot[2])))
        lines.extend(builder)
        lines.append("")

    lines.append("        return LayerDefinition.create(mesh, %d, %d);" % (width, height))
    lines.append("    }")
    return "\n".join(lines)


def blockbench(name):
    """The bones and cubes as the MCP bridge wants them.

    Blockbench works in the same space this module does - Y up, floor at 0, -Z
    forward - so nothing is converted here. Two differences from the JSON dump
    only: `place_cube` wants absolute corners rather than pivot-relative ones,
    and group rotations are in degrees.

    This exists so the models are still built from the formula after they moved
    into Blockbench. The editor is the authoring surface and the `.bbmodel` is
    what a person opens later, but nobody types thirty-one cube corners into it
    by hand - the bridge is fed from here, which is also what keeps the UV
    offsets identical to the ones the texture was painted through.
    """
    cubes, width, height = build(name)

    bones = {}
    for item in cubes:
        bone = bones.setdefault(item["bone"], {
            "name": item["bone"],
            "origin": item["pivot"],
            "rotation": [math.degrees(a) for a in item["rest"]],
            "cubes": [],
        })
        pivot = item["pivot"]
        bone["cubes"].append({
            "name": item["name"],
            "from": [pivot[i] + item["from"][i] for i in range(3)],
            "to": [pivot[i] + item["to"][i] for i in range(3)],
            "origin": pivot,
            "uv_offset": item["uv_offset"],
        })

    return {"texture": [width, height], "bones": list(bones.values())}


if __name__ == "__main__":
    for creature, width, height, fill in verify():
        print("// %s: %dx%d atlas, %.0f%% covered" % (creature, width, height, fill * 100),
              file=sys.stderr)

    if "--bb" in sys.argv:
        print(json.dumps(blockbench(sys.argv[sys.argv.index("--bb") + 1]), indent=1))
    elif "--java" in sys.argv:
        for creature in CREATURES:
            print("// --- %s ---" % creature)
            print(java(creature))
            print()
    else:
        print(json.dumps({
            creature: dict(zip(("cubes", "width", "height"), build(creature)))
            for creature in CREATURES
        }, indent=1))

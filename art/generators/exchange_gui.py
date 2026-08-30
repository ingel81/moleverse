"""The exchange station's screen: a board with holes cut in it.

The station shipped with the dispenser's background, which was defensible while
its nine slots were a three-by-three grid and nothing else was on the screen. It
stopped being defensible when the station learnt to grade its input - a gauge, a
shaft and two kinds of slot have nowhere to live on a borrowed texture, and a
vanilla window in the middle of a mod's own blocks looks exactly like what it
is.

The picture is the block, opened. A crate of gnawed boards sits in the mound's
crater with a shaft under it: so the panel is `WOOD`, the framing is `ROOT`, and
everything a player can put something into is a hole cut through the boards into
the earth below. There is no metal, no glass and no dial. What a mole is doing
down the shaft is not shown, because nobody up here can see it - the station's
whole fiction is that the trade happens out of sight.

Two things are drawn here that a background texture normally would not carry:

* **The gauge plate** below the feed slots, with four sockets. Which socket is
  lit says which of the four feed tiers the box would be paid at, and the screen
  draws the feed's own item icon and the rate beside it. Sockets rather than a
  needle, because the tiers are four discrete things and a needle would promise
  a continuum that does not exist.
* **The shaft**, dead centre, with its own overlay sprite. A trade is one mole
  and one trip, and until now the only sign it happened at all was a sound. The
  overlay is freshly moved earth lit from above, faded out over half a second by
  the screen - not a flash of light, because nothing down there glows and the
  mod has exactly one light source, which is a fungus.

`REGIONS` and `LAYOUT` below are the authority on where everything sits.
`ExchangeStationScreen` and `ExchangeStationMenu` carry copies of the numbers
they need; `--layout` prints them so the two can be checked against each other
after either side moves.
"""

import argparse
import random

from texture_kit import GLOW, ROOT, SOIL, TURNED, WOOD, Canvas

GUI = "D:/ai_local/minecraft_modding/moleverse/src/main/resources/assets/moleverse/textures/gui"

#: Sheet size. The window is the usual 176x166 in the top-left corner; the two
#: overlay sprites live to the right of it, where a 256 sheet has room to spare.
SHEET = 256
WIDTH, HEIGHT = 176, 166

#: Rectangles on the sheet, as (x0, y0, x1, y1) - the same order the Java blit
#: calls take as u, v, width, height.
REGIONS = {
    "window": (0, 0, WIDTH, HEIGHT),
    "shaft_active": (176, 0, 210, 52),
    "socket_lit": (176, 56, 181, 61),
}

#: Where everything sits inside the window. Slot positions are the top-left of
#: the 16x16 contents, which is what `Slot` takes; the well drawn around one is
#: a pixel wider on every side.
LAYOUT = {
    "feed_slots": [(16, 17), (34, 17), (52, 17)],
    "find_slots": [(107, 26), (125, 26), (143, 26), (107, 44), (125, 44), (143, 44)],
    "shaft": (71, 16, 105, 68),
    "plate": (15, 38, 69, 68),
    "sockets": [(20, 42), (28, 42), (36, 42), (44, 42)],
    "gauge_icon": (18, 49),
    "gauge_price": (40, 52),
    "gauge_payout": (58, 52),
    "rail": (3, 70, 173, 74),
    #: The screen moves the inventory label down to clear the rail; vanilla's
    #: default of 72 would print it straight through the beam.
    "inventory_label": (8, 75),
    "player_inventory": (8, 84),
}

SLOT = 16

#: Board heights across the panel. Uneven on purpose, the same reason
#: `board_run` gives for the block textures: equal boards read as a comb.
BOARDS = (13, 11, 15, 12, 14, 11, 13, 15, 12, 14, 11, 15, 13, 12)


# --- primitives -----------------------------------------------------------

def dot(canvas, x, y, colour):
    """One pixel, never wrapped. A sheet is not a tiling texture: a root that
    runs off the panel must be clipped, not reappear on the far side."""
    canvas.put(x, y, colour, wrap=False)


def boards(canvas, rect, rng):
    """Gnawed planks, laid across the panel.

    Deliberately quiet: base tone with a scattering of grain lines one ramp
    step either way, and a seam under each board. A panel is the one texture in
    this mod that has text on top of it, so anything with more contrast than
    this eats the labels.
    """
    x0, y0, x1, y1 = rect
    y = y0
    index = 0
    while y < y1:
        height = min(BOARDS[index % len(BOARDS)], y1 - y)
        index += 1
        canvas.noise((x0, y, x1, y + height), WOOD, [2, 2, 2, 3], rng)
        for _ in range((x1 - x0) // 12):  # grain, running with the board
            gy = y + 1 + rng.randrange(max(1, height - 2))
            gx = x0 + rng.randrange(x1 - x0)
            tone = WOOD[rng.choice([1, 1, 3])]
            for step in range(rng.randint(6, 20)):
                if rng.random() < 0.85:
                    dot(canvas, gx + step, gy, tone)
        # One knot per third board or so: two dark pixels and the grain's own
        # lit tone beside them. The single mark of wear the boards can afford -
        # the labels sit on this surface, so wear has to stay countable on one
        # hand across the whole sheet.
        if rng.random() < 0.35 and height > 4:
            kx = x0 + 4 + rng.randrange(max(1, x1 - x0 - 8))
            ky = y + 2 + rng.randrange(height - 4)
            dot(canvas, kx, ky, WOOD[0])
            dot(canvas, kx + 1, ky, WOOD[1])
            dot(canvas, kx + 1, ky + 1, WOOD[4])
        for x in range(x0, x1):  # lit top edge, seam below
            # The top edge is gnawed, not milled: the highlight drops out for
            # a pixel every so often, which is all "worn" needs to say.
            dot(canvas, x, y, WOOD[4] if rng.random() < 0.85 else WOOD[2])
            dot(canvas, x, y + height - 1, WOOD[0])
        y += height


def frame(canvas, rect, thickness=3):
    """The crate's timber, round the outside of the panel.

    Light from the top left, which is where every other texture in this mod has
    it: the top and left runs are the lit face of the beam, the bottom and right
    are its shadowed one.
    """
    x0, y0, x1, y1 = rect
    for step in range(thickness):
        tone_lit = ROOT[3] if step == 0 else ROOT[2]
        tone_dark = ROOT[1] if step == 0 else ROOT[2]
        for x in range(x0 + step, x1 - step):
            dot(canvas, x, y0 + step, tone_lit)
            dot(canvas, x, y1 - 1 - step, tone_dark)
        for y in range(y0 + step, y1 - step):
            dot(canvas, x0 + step, y, tone_lit)
            dot(canvas, x1 - 1 - step, y, tone_dark)
    for x in range(x0 + thickness, x1 - thickness):  # the shadow the beam casts
        dot(canvas, x, y0 + thickness, WOOD[0])
    for y in range(y0 + thickness, y1 - thickness):
        dot(canvas, x0 + thickness, y, WOOD[0])


def rail(canvas, rect, rng):
    """A cross beam. The station's half of the screen and the player's are
    different places, and a line of pixels says so more cheaply than a gap."""
    x0, y0, x1, y1 = rect
    canvas.noise(rect, ROOT, [1, 2, 2, 3], rng)
    for x in range(x0, x1):
        dot(canvas, x, y0, ROOT[3])
        dot(canvas, x, y1 - 1, ROOT[0])
    for x in range(x0 + 6, x1 - 6, 23):  # pegs, at the spacing the hoops use
        dot(canvas, x, y0 + 1, WOOD[3])
        dot(canvas, x, y0 + 2, WOOD[1])


def well(canvas, at, rng, size=SLOT):
    """A slot: a hole cut through the boards, with earth at the bottom of it.

    Dark along the top and left, lit along the bottom and right - vanilla's
    convention for a sunken slot, and worth keeping to the pixel. A player reads
    "I can put something here" off that shading before they read anything else
    on the screen, and inverting it to be interesting would cost exactly that.

    The floor is `TURNED` and not `SOIL`, which is the first thing that had to
    be corrected: the burrow ramp is nearly black, and forty slots of it turned
    the screen into a pegboard of holes with no item in any of them legible. A
    slot is a background for an icon before it is a picture of earth.
    """
    x, y = at
    x0, y0 = x - 1, y - 1
    x1, y1 = x + size + 1, y + size + 1
    canvas.noise((x0, y0, x1, y1), TURNED, [0, 1, 1, 1, 2], rng)
    for step in range(x0, x1):
        dot(canvas, step, y0, SOIL[0])
        dot(canvas, step, y1 - 1, WOOD[4])
    for step in range(y0, y1):
        dot(canvas, x0, step, SOIL[0])
        dot(canvas, x1 - 1, step, WOOD[4])
    # A second, broken step of shadow inside the dark edges. One ring reads as
    # a line drawn around a square; the half ring under it is what makes the
    # hole a hole - and it stays broken so the earth floor shows through and
    # the well does not gain a second border.
    for step in range(x0 + 1, x1 - 1):
        if rng.random() < 0.7:
            dot(canvas, step, y0 + 1, TURNED[0])
    for step in range(y0 + 1, y1 - 1):
        if rng.random() < 0.7:
            dot(canvas, x0 + 1, step, TURNED[0])
    dot(canvas, x0, y1 - 1, TURNED[0])  # the two corners the light does neither to
    dot(canvas, x1 - 1, y0, TURNED[0])


def recess(canvas, rect, rng):
    """A panel let into the boards, for the gauge to sit in.

    Same light direction as a well and one step shallower - it holds a reading,
    not an item. Light enough for the sockets cut into it to be visible as
    holes: a dark plate with darker holes in it is a plate with nothing on it.
    """
    x0, y0, x1, y1 = rect
    canvas.noise(rect, TURNED, [0, 0, 1, 1, 2], rng)
    for x in range(x0, x1):
        dot(canvas, x, y0, SOIL[0])
        dot(canvas, x, y1 - 1, WOOD[3])
    for y in range(y0, y1):
        dot(canvas, x0, y, SOIL[0])
        dot(canvas, x1 - 1, y, WOOD[3])


def socket(canvas, at):
    """One unlit step of the gauge: a 5x5 hole with a lip under it.

    The lip runs along the bottom and up the right side - the two edges light
    from the top left reaches inside a hole - so an unlit socket reads as a
    cut recess rather than as a painted black square. That distinction is the
    gauge's legibility: four holes read as one scale, four squares as dirt.
    """
    x, y = at
    for oy in range(5):
        for ox in range(5):
            dot(canvas, x + ox, y + oy, SOIL[0])
    for ox in range(5):
        dot(canvas, x + ox, y + 4, WOOD[3])
    for oy in range(1, 4):
        dot(canvas, x + 4, y + oy, WOOD[2])
    dot(canvas, x + 4, y + 4, WOOD[4])


def arrow(canvas, at, colour):
    """The rate's arrow, painted rather than typed.

    A right arrow is one glyph in Unicode and several fonts' worth of trouble
    at 8 px. Two numbers and a picture between them say the same thing and say
    it the same way in every language the mod is translated into.
    """
    x, y = at
    for step in range(9):
        dot(canvas, x + step, y + 4, colour)
    for step in range(4):  # the head converges on the tip, which is the point
        dot(canvas, x + 8 - step, y + 4 - step, colour)
        dot(canvas, x + 8 - step, y + 4 + step, colour)


def shaft_hole(rect):
    """The set of pixels inside the shaft's opening.

    An ellipse rather than a rectangle: everything else on the screen is square
    because a mole did not cut it, and this is the one thing a mole did cut.
    """
    x0, y0, x1, y1 = rect
    cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
    rx, ry = (x1 - x0) / 2 - 6, (y1 - y0) / 2 - 9
    hole = set()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                hole.add((x, y))
    return hole


def shaft(canvas, rect, rng):
    """The hole the moles come up, and the middle of the whole screen.

    Three layers: the earth the boards are laid over, the opening cut through
    it, and what little can be seen down the opening. The depth is stepped
    rather than faded - the ramps are stepped everywhere else in this mod and a
    smooth falloff here would be the one gradient in the set.
    """
    x0, y0, x1, y1 = rect
    canvas.noise(rect, SOIL, [2, 3, 3, 4], rng)
    for step in range(2):  # the collar: boards cut back round the hole
        tone_lit = ROOT[3] if step == 0 else ROOT[2]
        tone_dark = ROOT[1] if step == 0 else ROOT[2]
        for x in range(x0 + step, x1 - step):
            dot(canvas, x, y0 + step, tone_lit)
            dot(canvas, x, y1 - 1 - step, tone_dark)
        for y in range(y0 + step, y1 - step):
            dot(canvas, x0 + step, y, tone_lit)
            dot(canvas, x1 - 1 - step, y, tone_dark)

    hole = shaft_hole(rect)
    for x, y in hole:
        depth = min(
            sum(1 for step in range(1, 6) if (x + step, y) in hole),
            sum(1 for step in range(1, 6) if (x - step, y) in hole),
            sum(1 for step in range(1, 6) if (x, y + step) in hole),
            sum(1 for step in range(1, 6) if (x, y - step) in hole),
        )
        dot(canvas, x, y, SOIL[max(0, 3 - depth // 2)])
    for x, y in hole:
        # Looking down a hole from in front of it: the far wall is the part
        # anything above can light, and the near lip is what casts the shadow.
        if (x, y - 1) not in hole:
            dot(canvas, x, y, TURNED[2])
        if (x, y + 1) not in hole and (x, y - 1) in hole:
            dot(canvas, x, y, SOIL[0])

    # Roots hanging into the opening, and the one thing in the shaft that is
    # allowed to be pale: they are the only feature inside the hole, and a root
    # in the root ramp's dark end against earth in the soil ramp's dark end is
    # a root nobody sees.
    for _ in range(6):
        rx = rng.randrange(x0 + 6, x1 - 6)
        ry = min(y for x, y in hole if x == rx) if any(x == rx for x, _ in hole) else y0 + 2
        for step in range(rng.randint(2, 7)):
            if (rx, ry + step) in hole:
                dot(canvas, rx, ry + step, ROOT[rng.choice([3, 4])])
    for _ in range(14):  # spoil trodden onto the boards round the hole
        cx, cy = rng.randrange(x0 + 2, x1 - 2), rng.randrange(y0 + 2, y1 - 2)
        if (cx, cy) not in hole:
            dot(canvas, cx, cy, TURNED[rng.choice([1, 2, 3])])


# --- the sheet ------------------------------------------------------------

def window(canvas, rng):
    """The 176x166 background, in the order the block would be built."""
    boards(canvas, (0, 0, WIDTH, HEIGHT), rng)
    frame(canvas, (0, 0, WIDTH, HEIGHT))
    rail(canvas, LAYOUT["rail"], rng)

    shaft(canvas, LAYOUT["shaft"], rng)
    for at in LAYOUT["feed_slots"] + LAYOUT["find_slots"]:
        well(canvas, at, rng)

    recess(canvas, LAYOUT["plate"], rng)
    # The groove the sockets are cut along, drawn first so they punch through
    # it. Four holes on a plate are four holes; four holes strung on one line
    # are a scale, and the line is the whole difference.
    first, last = LAYOUT["sockets"][0], LAYOUT["sockets"][-1]
    for x in range(first[0] - 1, last[0] + 6):
        dot(canvas, x, first[1] + 2, SOIL[0])
    for at in LAYOUT["sockets"]:
        socket(canvas, at)
    arrow(canvas, (46, 52), WOOD[4])

    px, py = LAYOUT["player_inventory"]
    for row in range(3):
        for column in range(9):
            well(canvas, (px + column * 18, py + row * 18), rng)
    for column in range(9):
        well(canvas, (px + column * 18, py + 58), rng)


def shaft_active(canvas, rng):
    """The overlay a trade lights up: the same opening with fresh earth in it.

    Only the hole is painted and everything else stays transparent, so the
    sprite lands exactly on the shaft the background already drew. The screen
    fades it out over ten ticks; at full strength it should read as earth that
    has just been pushed up, which is why it is `TURNED` - the ramp the molehill
    above ground is made of - and not a light.
    """
    x0, y0, x1, y1 = REGIONS["shaft_active"]
    hole = shaft_hole((0, 0, x1 - x0, y1 - y0))
    for x, y in hole:
        depth = min(
            sum(1 for step in range(1, 6) if (x + step, y) in hole),
            sum(1 for step in range(1, 6) if (x - step, y) in hole),
            sum(1 for step in range(1, 6) if (x, y + step) in hole),
            sum(1 for step in range(1, 6) if (x, y - step) in hole),
        )
        tone = TURNED[min(len(TURNED) - 1, 2 + depth // 2)]
        dot(canvas, x0 + x, y0 + y, tone)
    for x, y in hole:  # crumbs sitting proud of the rest
        if rng.random() < 0.12:
            dot(canvas, x0 + x, y0 + y, TURNED[6])
    for x, y in hole:  # the rim, thrown up over the boards
        if (x, y - 1) not in hole:
            dot(canvas, x0 + x, y0 + y - 1, TURNED[5])


def socket_lit(canvas):
    """The gauge's lit step. Mycelium green, because it is the only colour in
    this mod that means "on" - the shaft lantern uses the same ramp."""
    x0, y0, _, _ = REGIONS["socket_lit"]
    for oy in range(5):
        for ox in range(5):
            ring = max(abs(ox - 2), abs(oy - 2))
            dot(canvas, x0 + ox, y0 + oy, GLOW[[4, 3, 2][ring]])


def paint(seed=20260829):
    rng = random.Random(seed)
    canvas = Canvas(size=SHEET)
    window(canvas, rng)
    shaft_active(canvas, rng)
    socket_lit(canvas)
    return canvas


def preview(canvas, path, scale=3):
    """A magnified copy on a flat ground, because the sheet is transparent
    everywhere the window is not and a viewer shows that as whatever it likes."""
    from PIL import Image

    ground = Image.new("RGBA", (SHEET, SHEET), (0x18, 0x18, 0x1C, 255))
    ground.alpha_composite(canvas.img)
    ground.resize((SHEET * scale, SHEET * scale), Image.NEAREST).save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", metavar="PNG", help="also write a magnified copy")
    parser.add_argument("--layout", action="store_true", help="print the regions and slot positions")
    args = parser.parse_args()

    canvas = paint()
    path = f"{GUI}/exchange_station.png"
    canvas.save(path)
    print("wrote", path)

    if args.layout:
        print("\nregions (u, v, x1, y1)")
        for name, rect in REGIONS.items():
            print(f"  {name:<14} {list(rect)}")
        print("\nlayout")
        for name, value in LAYOUT.items():
            print(f"  {name:<18} {value}")

    if args.preview:
        preview(canvas, args.preview)
        print("wrote", args.preview)


if __name__ == "__main__":
    main()

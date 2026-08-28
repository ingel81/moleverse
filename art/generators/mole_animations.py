import json, math

OUT = "C:/Users/joerg/AppData/Local/Temp/claude/D--ai-local-minecraft-modding/a9380116-d96c-44f3-aa8f-db3d5fd68acb/scratchpad/anims.json"


def keys(pairs):
    """pairs: list of (time, x, y, z)"""
    return [[round(t, 3)] + [round(v, 3) for v in xyz] for t, *xyz in pairs]


def cycle(length, steps, fn, phase=0.0):
    """Sample fn(u) over one loop, u in [0,1), and close the loop."""
    out = []
    for i in range(steps + 1):
        u = i / steps
        out.append((u * length,) + tuple(fn((u + phase) % 1.0)))
    return out


anims = {}

# ---------------------------------------------------------------- idle
# Breathing, the odd snout twitch. Restrained enough to run permanently.
anims["animation.mole_idle"] = {
    "loop": "loop", "length": 4.0,
    "channels": [
        {"bone": "body", "channel": "position", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (1.3, 0, 0.22, 0), (2.6, 0, 0.05, 0), (4, 0, 0, 0)])},
        {"bone": "head", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (1.0, -2.5, 6, 0), (2.2, 1.5, -5, 0), (3.2, -1, 2, 0), (4, 0, 0, 0)])},
        {"bone": "snout", "channel": "rotation", "interpolation": "linear",
         "keys": keys([(0, 0, 0, 0), (1.05, 0, 0, 0), (1.15, -9, 0, 0), (1.3, 0, 0, 0),
                       (2.85, 0, 0, 0), (2.95, -11, 0, 0), (3.05, -2, 0, 0), (3.15, -8, 0, 0),
                       (3.3, 0, 0, 0), (4, 0, 0, 0)])},
        {"bone": "tail", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (1.6, 0, 7, 0), (3.1, 0, -6, 0), (4, 0, 0, 0)])},
    ],
}

# ---------------------------------------------------------------- dig
# Alternating front paws, body shoving forward in small pushes, snout low.
# Built level and direction-neutral: pitch and yaw come from code.
DIG = 0.8


def scoop(u):
    """Front leg: reach forward, pull back through the soil, lift, return."""
    return (-45 * math.cos(2 * math.pi * u) + 5, 0, 0)


def paw_twist(u):
    """The paw turns outward as the leg pulls back, flinging soil aside."""
    return (0, 18 * math.sin(2 * math.pi * u), 0)


anims["animation.mole_dig"] = {
    "loop": "loop", "length": DIG,
    "channels": [
        {"bone": "front_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, scoop))},
        {"bone": "front_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, scoop, phase=0.5))},
        {"bone": "front_paw_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, paw_twist))},
        {"bone": "front_paw_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (0, -18 * math.sin(2 * math.pi * ((u + 0.5) % 1.0)), 0)))},
        # Two shoves per cycle, one per paw stroke.
        {"bone": "body", "channel": "position", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (0, 0.18 * math.sin(4 * math.pi * u),
                                               -0.45 * abs(math.sin(2 * math.pi * u)))))},
        # Snout stays low and nods with the effort.
        {"bone": "head", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (-13 + 4 * math.sin(4 * math.pi * u), 0, 0)))},
        {"bone": "hind_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (9 * math.sin(2 * math.pi * u + math.pi), 0, 0)))},
        {"bone": "hind_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (9 * math.sin(2 * math.pi * u), 0, 0)))},
        {"bone": "tail", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys(cycle(DIG, 8, lambda u: (7, 5 * math.sin(2 * math.pi * u), 0)))},
    ],
}

# ---------------------------------------------------------------- burrow
# Secondary motion only. The body sinking and tipping is a number in the code,
# not a keyframe channel - see docs/MOLEHILL.md.
anims["animation.mole_burrow"] = {
    "loop": "once", "length": 1.2,
    "channels": [
        {"bone": "head", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.25, -30, 0, 0), (0.6, -34, 3, 0), (1.2, -30, 0, 0)])},
        {"bone": "snout", "channel": "rotation", "interpolation": "linear",
         "keys": keys([(0, 0, 0, 0), (0.2, -12, 0, 0), (0.5, -4, 0, 0), (0.8, -12, 0, 0), (1.2, -8, 0, 0)])},
        # One deliberate scoop with each paw, then both dig in.
        {"bone": "front_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.2, -52, 0, 0), (0.45, 30, 0, 0), (0.8, -20, 0, 0), (1.2, 25, 0, 0)])},
        {"bone": "front_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.35, -48, 0, 0), (0.6, 28, 0, 0), (0.95, -18, 0, 0), (1.2, 24, 0, 0)])},
        {"bone": "front_paw_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.45, 0, 22, 0), (1.2, 0, 8, 0)])},
        {"bone": "front_paw_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.6, 0, -22, 0), (1.2, 0, -8, 0)])},
        {"bone": "hind_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.7, 18, 0, 0), (1.2, 26, 0, 0)])},
        {"bone": "hind_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.7, 18, 0, 0), (1.2, 26, 0, 0)])},
        # The tail is the last thing above ground, so it flicks up.
        {"bone": "tail", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 0, 0), (0.8, 14, 0, 0), (1.2, 34, 0, 0)])},
    ],
}

# ---------------------------------------------------------------- emerge
# The reverse, authored against peekAmount = 1 so it hands over to the
# rearing pose without a jump.
anims["animation.mole_emerge"] = {
    "loop": "once", "length": 0.8,
    "channels": [
        {"bone": "head", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, -28, 0, 0), (0.35, -6, 0, 0), (0.6, 4, 0, 0), (0.8, 0, 0, 0)])},
        {"bone": "snout", "channel": "rotation", "interpolation": "linear",
         "keys": keys([(0, -10, 0, 0), (0.45, -2, 0, 0), (0.55, -9, 0, 0), (0.65, -1, 0, 0), (0.8, -5, 0, 0)])},
        # Paws push down and outward against the rim to haul the body up.
        {"bone": "front_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 30, 0, 0), (0.3, -25, 0, 0), (0.8, -8, 0, 0)])},
        {"bone": "front_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 28, 0, 0), (0.35, -22, 0, 0), (0.8, -8, 0, 0)])},
        {"bone": "front_paw_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, 12, 0), (0.4, 0, -14, 0), (0.8, 0, -4, 0)])},
        {"bone": "front_paw_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 0, -12, 0), (0.4, 0, 14, 0), (0.8, 0, 4, 0)])},
        {"bone": "hind_leg_right", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 24, 0, 0), (0.5, 6, 0, 0), (0.8, 0, 0, 0)])},
        {"bone": "hind_leg_left", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 24, 0, 0), (0.5, 6, 0, 0), (0.8, 0, 0, 0)])},
        {"bone": "tail", "channel": "rotation", "interpolation": "catmullrom",
         "keys": keys([(0, 30, 0, 0), (0.5, 8, 0, 0), (0.8, 0, 0, 0)])},
    ],
}

json.dump(anims, open(OUT, "w"), indent=1)
for name, a in anims.items():
    print(name, a["length"], "s,", len(a["channels"]), "channels,",
          sum(len(c["keys"]) for c in a["channels"]), "keyframes")

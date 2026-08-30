"""Generate the burrow's own sounds through the ElevenLabs sound-effects API.

One entry per sound: a text prompt, a duration, and how many variants. The raw
MP3s land in `art/audio_raw/`, then ffmpeg turns them into the mono OGGs the
game wants under `assets/moleverse/sounds/`. Re-running skips files that
already exist, so a single bad sound can be deleted and regenerated alone.

Needs `ELEVENLABS_API_KEY` in the environment and an outbound firewall rule
for python (the box denies outbound by default). ffmpeg must be on PATH.

The prompts are the design: written for an animal heard through earth at mole
scale, not for a nature documentary. Sounds are requested WITHOUT music or
ambience mixed in - layering happens in game, where distance and position do
the mixing.
"""

import json
import os
import subprocess
import sys
import urllib.request

API = "https://api.elevenlabs.io/v1/sound-generation"
RAW = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "audio_raw"))
OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..",
                                    "src", "main", "resources", "assets", "moleverse", "sounds"))

# name -> (subdir, prompt, seconds, variants)
SOUNDS = {
    # The apex predator. Its own voice, not a borrowed fox.
    "weasel_hiss": ("entity/weasel",
                    "A small fierce mustelid hissing a sharp warning, close and dry, "
                    "no echo, no background", 2.0, 3),
    "weasel_chitter": ("entity/weasel",
                       "An excited weasel chittering rapidly while hunting, short "
                       "staccato animal chatter, dry close recording, no background", 2.5, 3),
    "weasel_hurt": ("entity/weasel",
                    "A weasel yelping in pain, one short sharp animal cry, "
                    "dry, no background", 1.2, 2),

    # The harasser.
    "shrew_squeak": ("entity/shrew",
                     "A tiny shrew squeaking aggressively, very high pitched short "
                     "mammal squeaks, dry close recording, no background", 1.5, 3),
    "shrew_hurt": ("entity/shrew",
                   "A small rodent squeal of pain, single very short high squeak, "
                   "dry, no background", 0.8, 2),

    # The three that are not predators. None of them has a voice in the usual
    # sense - a worm has no mouth to speak with and a beetle has no lungs - so
    # these are the sounds their bodies make going about their business. Asked
    # for wet where the animal is wet and dry where it is armoured, because at
    # mole scale that difference is most of what tells them apart in the dark.
    "worm_slither": ("entity/great_worm",
                     "A large worm sliding through wet soil, a thick body dragging "
                     "over damp earth, slow and wet, close dry recording, "
                     "no background", 2.0, 3),
    "beetle_click": ("entity/soil_beetle",
                     "A beetle clicking its chitin plates, two or three dry sharp "
                     "ticks, hard and hollow, close recording, no background", 1.0, 2),
    "grub_munch": ("entity/grub",
                   "A fat grub chewing, soft wet muffled munching on damp matter, "
                   "small and close, no background", 1.5, 2),

    # Not an animal. Roots braided into a rope, taking a player's weight.
    "ladder_rustle": ("block/root_ladder",
                      "Dry roots creaking and rustling as a rope of them takes "
                      "weight, one short pull, close and dry, no background", 1.0, 2),

    # The dimension's signature: something digging behind the wall.
    "scratch": ("ambient/burrow",
                "Claws scratching and digging through packed earth heard through a "
                "wall, muffled dry scraping soil, low and close, rhythmic, "
                "no background", 3.0, 3),

    # Ambient bed: a slow loopable underground room tone. Kept quiet on purpose;
    # the mood/additions system plays on top of it.
    "underearth_loop": ("ambient/burrow",
                        "Deep underground ambience, faint earth settling, distant "
                        "soil trickles, very low rumble, no music, no animals, "
                        "seamless loop", 12.0, 1),

    # Music: one sparse cue, not a loop - the burrow's silence is a feature.
    "burrow_theme": ("music",
                     "Sparse mysterious underground music cue, soft low woodwinds "
                     "and deep marimba, slow, warm and earthy, fades in and out, "
                     "minimal, no percussion", 20.0, 1),
}


def generate(name, prompt, seconds, index):
    os.makedirs(RAW, exist_ok=True)
    raw = os.path.join(RAW, f"{name}{index}.mp3")
    if os.path.exists(raw):
        return raw
    body = json.dumps({
        "text": prompt,
        "duration_seconds": seconds,
        "prompt_influence": 0.4,
    }).encode()
    req = urllib.request.Request(API, data=body, headers={
        "xi-api-key": os.environ["ELEVENLABS_API_KEY"],
        "Content-Type": "application/json",
    })
    with urllib.request.urlopen(req, timeout=120) as resp:
        audio = resp.read()
    with open(raw, "wb") as f:
        f.write(audio)
    print(f"generated {raw} ({len(audio)} bytes)")
    return raw


def to_ogg(raw, subdir, name, index):
    dest_dir = os.path.join(OUT, subdir.replace("/", os.sep))
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, f"{name}{index}.ogg")
    if os.path.exists(dest):
        return dest
    # Mono, normalised, 44.1k - what every vanilla sound is. Music keeps stereo.
    channels = [] if subdir == "music" else ["-ac", "1"]
    subprocess.run(["ffmpeg", "-y", "-i", raw, *channels, "-ar", "44100",
                    "-af", "loudnorm=I=-18", "-c:a", "libvorbis", "-q:a", "4", dest],
                   check=True, capture_output=True)
    print(f"encoded  {dest}")
    return dest


def main():
    if "ELEVENLABS_API_KEY" not in os.environ:
        sys.exit("ELEVENLABS_API_KEY is not set")
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for name, (subdir, prompt, seconds, variants) in SOUNDS.items():
        if only and only != name:
            continue
        for i in range(1, variants + 1):
            raw = generate(name, prompt, seconds, i)
            to_ogg(raw, subdir, name, i)


if __name__ == "__main__":
    main()

# Audio sources

Raw, unprocessed audio goes here. Nothing in this directory is shipped: it sits
outside every source set, so Gradle never sees it.

```
audio/raw/<same path as under sounds/>.<mp3|wav|flac>
        |
        |  convert.ps1
        v
src/main/resources/assets/moleverse/sounds/<same path>.ogg
```

The raw tree mirrors the target tree. Drop a file at
`audio/raw/entity/mole/sniff1.mp3` and it converts to
`src/main/resources/assets/moleverse/sounds/entity/mole/sniff1.ogg`.

## Naming

`<category>/<subject>/<event><n>.<ext>`, for example:

```
audio/raw/entity/mole/sniff1.mp3
audio/raw/entity/mole/sniff2.mp3
audio/raw/entity/mole/dig1.mp3
audio/raw/block/loose_soil/break1.mp3
```

Numbered variants of the same event let `sounds.json` pick one at random, which
is what keeps a repeated sound from turning mechanical.

## Converting

```powershell
.\audio\convert.ps1            # convert everything that is missing or outdated
.\audio\convert.ps1 -Force     # reconvert everything
```

The script requires `ffmpeg` on PATH.

## Why mono matters

Minecraft plays stereo files as non-positional audio: no direction, no distance
falloff, equally loud everywhere in the world. Anything emitted by an entity or a
block has to be single channel. The script forces `-ac 1` for exactly that
reason. Stereo is only correct for music and ambient tracks.

## Licensing

Every raw file must be listed in `SOURCES.md` with its origin and licence.
Moleverse is distributed under LGPL-3.0-or-later, and shipping a sound whose
licence forbids redistribution would make the whole download undistributable.
Check before adding, not afterwards.

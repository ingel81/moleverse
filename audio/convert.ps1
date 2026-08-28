<#
.SYNOPSIS
    Converts raw audio under audio/raw into mono Ogg Vorbis in the resource tree.

.DESCRIPTION
    Mirrors the directory layout: audio/raw/<path>.<ext> becomes
    src/main/resources/assets/moleverse/sounds/<path>.ogg

    Mono is forced. Minecraft plays stereo files as non-positional audio, so a
    stereo entity sound would be heard everywhere at equal volume.

.PARAMETER Force
    Reconvert even when the .ogg is newer than its source.
#>
[CmdletBinding()]
param([switch]$Force)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg not found on PATH."
}

$root   = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'audio\raw'
$target = Join-Path $root 'src\main\resources\assets\moleverse\sounds'

if (-not (Test-Path $source)) { throw "Missing source directory: $source" }

$files = Get-ChildItem -Path $source -Recurse -File -Include *.mp3, *.wav, *.flac, *.aiff
if (-not $files) {
    Write-Output "Nothing to convert - audio/raw is empty."
    return
}

$converted = 0
$skipped   = 0

foreach ($file in $files) {
    $relative = $file.FullName.Substring($source.Length).TrimStart('\')
    $out      = Join-Path $target ([IO.Path]::ChangeExtension($relative, '.ogg'))
    $outDir   = Split-Path -Parent $out

    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

    if (-not $Force -and (Test-Path $out) -and ((Get-Item $out).LastWriteTime -ge $file.LastWriteTime)) {
        $skipped++
        continue
    }

    # -ac 1  mono, required for positional audio
    # -ar 44100  standard sample rate
    # -q:a 5  Vorbis quality, a good size to fidelity trade for short effects
    ffmpeg -hide_banner -loglevel error -y -i $file.FullName -ac 1 -ar 44100 -c:a libvorbis -q:a 5 $out
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed on $($file.FullName)" }

    $size = [Math]::Round((Get-Item $out).Length / 1KB, 1)
    Write-Output ("  {0,-50} {1,7} KB" -f $relative, $size)
    $converted++
}

Write-Output ""
Write-Output "$converted converted, $skipped up to date."

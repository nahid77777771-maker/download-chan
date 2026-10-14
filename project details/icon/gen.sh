#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
RES="$HOME/Downloads/download-chan/project files/downloadchan/android/app/src/main/res"

# --- rasterize masters at 1024 ---
rsvg-convert -w 1024 -h 1024 art.svg -o art.png
rsvg-convert -w 1024 -h 1024 bg.svg  -o bg.png

# --- square icon (art over gradient) ---
magick bg.png art.png -composite square.png

# --- round icon (circular mask) ---
magick -size 1024x1024 xc:none -fill white -draw "circle 512,512 512,24" mask.png
magick square.png mask.png -alpha off -compose CopyOpacity -composite round.png

# --- adaptive foreground: art scaled to ~66% centered on transparent 1024 ---
magick art.png -resize 683x683 art_small.png
magick -size 1024x1024 xc:none art_small.png -gravity center -composite fg.png

# --- emit densities ---
# legacy launcher: mdpi48 hdpi72 xhdpi96 xxhdpi144 xxxhdpi192
# adaptive fg (108dp): mdpi108 hdpi162 xhdpi216 xxhdpi324 xxxhdpi432
emit() { # src dp_px size dir prefix
  magick "$1" -resize "$3"x"$3" "$RES/$4/$5.png"
}
for spec in "mdpi 48 108" "hdpi 72 162" "xhdpi 96 216" "xxhdpi 144 324" "xxxhdpi 192 432"; do
  set -- $spec
  d="$1"; leg="$2"; fg="$3"
  emit square.png x "$leg" "mipmap-$d" ic_launcher
  emit round.png  x "$leg" "mipmap-$d" ic_launcher_round
  emit fg.png     x "$fg"  "mipmap-$d" ic_launcher_foreground
done

echo "generated:"; ls -1 "$RES"/mipmap-mdpi
identify "$RES/mipmap-xxxhdpi/ic_launcher.png" "$RES/mipmap-xxxhdpi/ic_launcher_foreground.png" 2>/dev/null || true

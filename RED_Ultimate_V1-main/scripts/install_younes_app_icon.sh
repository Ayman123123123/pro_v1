#!/bin/bash
# Install the official YOUNES Q/Y square launcher from the improved master.
set -euo pipefail

ROOT="/home/user/pro_v1"
MASTER="$ROOT/icon-work/younes_master_1024.png"
WORK="$ROOT/icon-work"
APP_RES="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/app/src/main/res"
RED_RES="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/res"
ADMIN="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/public"

if [[ ! -f "$MASTER" ]]; then
  echo "Missing master icon: $MASTER" >&2
  exit 1
fi

export_png() {
  local src="$1" size="$2" dest="$3"
  convert "$src" -resize "${size}x${size}" -strip -define png:compression-level=9 "$dest"
}

# Adaptive foreground: keep the monogram inside the 66% safe zone.
convert "$MASTER" -resize 82% -background '#000C1C' -gravity center -extent 1024x1024 \
  -strip -define png:compression-level=9 "$WORK/younes_adaptive_fg_1024.png"

# Circular legacy round icon
convert -size 1024x1024 xc:none -fill white -draw "circle 512,512 512,511" "$WORK/circle_mask.png"
convert "$MASTER" "$WORK/circle_mask.png" -compose CopyOpacity -composite \
  -background '#000C1C' -alpha remove -alpha off \
  -strip -define png:compression-level=9 "$WORK/younes_round_1024.png"

install_mipmaps() {
  local base="$1"
  local -A sizes=([mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192)
  local density size dir
  for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    size="${sizes[$density]}"
    dir="$base/mipmap-$density"
    mkdir -p "$dir"
    export_png "$MASTER" "$size" "$dir/ic_launcher.png"
    export_png "$WORK/younes_round_1024.png" "$size" "$dir/ic_launcher_round.png"
    echo "  $density ${size}x${size}"
  done
}

echo "Installing YOUNES launcher icons"
echo "— app module"
install_mipmaps "$APP_RES"
export_png "$WORK/younes_adaptive_fg_1024.png" 432 "$APP_RES/drawable/ic_launcher_full.png"
export_png "$MASTER" 1024 "$APP_RES/drawable/younes_icon_ultimate_clean.png"

echo "— red-app module"
install_mipmaps "$RED_RES"
export_png "$WORK/younes_adaptive_fg_1024.png" 432 "$RED_RES/drawable/ic_launcher_full.png"
export_png "$MASTER" 512 "$RED_RES/drawable/younes_icon_master.png"
export_png "$MASTER" 512 "$RED_RES/drawable/ic_launcher_pro.png"
export_png "$MASTER" 1024 "$RED_RES/drawable/younes_icon_ultimate_clean.png"

echo "— admin dashboard"
export_png "$MASTER" 48 "$ADMIN/favicon.png"
export_png "$MASTER" 192 "$ADMIN/icon-192.png"
export_png "$MASTER" 512 "$ADMIN/icon-512.png"
export_png "$MASTER" 512 "$ADMIN/admin-icon.png"

echo "— repo master ICON1.png"
export_png "$MASTER" 1024 "$ROOT/ICON1.png"

echo "Done."

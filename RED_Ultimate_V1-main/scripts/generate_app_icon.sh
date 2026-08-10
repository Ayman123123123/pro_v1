#!/bin/bash
# YOUNES Sovereign — App Icon Generator
# Generates professional PNG icons in all required densities

set -e

SIZES=(
  "mdpi:48"
  "hdpi:72"
  "xhdpi:96"
  "xxhdpi:144"
  "xxxhdpi:192"
)

OUTPUT_BASE="/home/user/pro_v1/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/res"

generate_icon() {
  local size=$1
  local density=$2
  local output_dir="$OUTPUT_BASE/mipmap-$density"

  mkdir -p "$output_dir"

  # Generate icon with ImageMagick
  convert -size ${size}x${size} xc:'#071A2E' \
    -fill '#1E3A5F' -draw "circle $((size/2)),$((size/2)) $((size/2)),$((size/3))" \
    -fill '#0A1628' -draw "circle $((size/2)),$((size/2)) $((size/2)),$((size/4))" \
    -fill '#E8B84A' -draw "circle $((size/2)),$((size/2)) $((size/2)),$((size*7/16))" \
    -fill '#FFE27A' -draw "circle $((size/2)),$((size/2)) $((size/2)),$((size*6/16))" \
    -fill '#0A1628' -draw "circle $((size/2)),$((size/2)) $((size/2)),$((size*5/16))" \
    -fill '#E8B84A' -font 'DejaVu-Sans-Bold' -pointsize $((size/4)) -gravity center -annotate +0+$((size/30)) 'Y' \
    -fill '#FFE27A' -font 'DejaVu-Sans-Bold' -pointsize $((size/5)) -gravity center -annotate +0+$((size/40)) 'Y' \
    "$output_dir/ic_launcher.png"

  # Round version (same content, different filename)
  cp "$output_dir/ic_launcher.png" "$output_dir/ic_launcher_round.png"

  echo "✅ Generated: $density (${size}x${size})"
}

for entry in "${SIZES[@]}"; do
  IFS=':' read -ra PARTS <<< "$entry"
  generate_icon "${PARTS[1]}" "${PARTS[0]}"
done

echo ""
echo "🎨 All app icons generated successfully!"

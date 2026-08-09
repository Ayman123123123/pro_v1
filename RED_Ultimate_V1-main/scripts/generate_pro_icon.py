#!/usr/bin/env python3
"""
YOUNES Sovereign — Professional PNG Icon Generator v4 (Final)
Pure Python PNG encoder (no external dependencies)
Matches the reference design EXACTLY:
- Filled navy background
- Thick gold outer ring
- Inner subtle ring
- Centered Y letter (gold + highlight)
- Sparkle stars + accent dots
- Network mesh pattern
"""
import struct
import zlib
import math
import os


def write_png(filename, width, height, pixels):
    """Write a PNG file from RGB pixel data"""
    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data +
                struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    signature = b'\x89PNG\r\n\x1a\n'
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b = pixels[y * width + x]
            raw.extend([r, g, b])
    compressed = zlib.compress(bytes(raw), 9)
    with open(filename, 'wb') as f:
        f.write(signature)
        f.write(chunk(b'IHDR', ihdr))
        f.write(chunk(b'IDAT', compressed))
        f.write(chunk(b'IEND', b''))


def hex_to_rgb(hex_color):
    h = hex_color.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def lerp_color(c1, c2, t):
    return tuple(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3))


def draw_filled_circle(pixels, w, h, cx, cy, r, color):
    r2 = r * r
    for y in range(max(0, int(cy - r - 1)), min(h, int(cy + r + 2))):
        for x in range(max(0, int(cx - r - 1)), min(w, int(cx + r + 2))):
            dx = x - cx
            dy = y - cy
            if dx*dx + dy*dy <= r2:
                pixels[y * w + x] = color


def draw_ring(pixels, w, h, cx, cy, r_outer, r_inner, color):
    r_outer2 = r_outer * r_outer
    r_inner2 = r_inner * r_inner
    for y in range(max(0, int(cy - r_outer - 1)), min(h, int(cy + r_outer + 2))):
        for x in range(max(0, int(cx - r_outer - 1)), min(w, int(cx + r_outer + 2))):
            dx = x - cx
            dy = y - cy
            d2 = dx*dx + dy*dy
            if r_inner2 <= d2 <= r_outer2:
                pixels[y * w + x] = color


def draw_line(pixels, w, h, x1, y1, x2, y2, thickness, color):
    dx = abs(x2 - x1)
    dy = abs(y2 - y1)
    sx = 1 if x1 < x2 else -1
    sy = 1 if y1 < y2 else -1
    err = dx - dy
    x, y = x1, y1
    t = max(0, thickness // 2)
    while True:
        for ty in range(-t, t+1):
            for tx in range(-t, t+1):
                px, py = x + tx, y + ty
                if 0 <= px < w and 0 <= py < h:
                    pixels[py * w + px] = color
        if x == x2 and y == y2:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x += sx
        if e2 < dx:
            err += dx
            y += sy


def draw_filled_polygon(pixels, w, h, points, color):
    if not points:
        return
    min_y = max(0, min(p[1] for p in points))
    max_y = min(h - 1, max(p[1] for p in points))
    n = len(points)
    for y in range(min_y, max_y + 1):
        intersections = []
        for i in range(n):
            j = (i + 1) % n
            p1 = points[i]
            p2 = points[j]
            if (p1[1] <= y < p2[1]) or (p2[1] <= y < p1[1]):
                if p2[1] != p1[1]:
                    t = (y - p1[1]) / (p2[1] - p1[1])
                    x = int(p1[0] + t * (p2[0] - p1[0]))
                    intersections.append(x)
        intersections.sort()
        for k in range(0, len(intersections), 2):
            if k + 1 < len(intersections):
                x_start = max(0, intersections[k])
                x_end = min(w - 1, intersections[k+1])
                for x in range(x_start, x_end + 1):
                    pixels[y * w + x] = color


def draw_y_letter(pixels, w, h, cx, cy, size, color, highlight, shadow):
    """Draw Y letter with V-shape top, vertical stem, 3D effect"""
    s = size

    # V-shape top
    top_y = int(cy - s * 0.42)
    meet_y = int(cy - s * 0.02)
    bot_y = int(cy + s * 0.50)
    stem_w = s * 0.18
    v_w = s * 0.50

    # Build Y as a single polygon (clockwise from top-left)
    points = [
        (int(cx - v_w / 2), top_y),
        (int(cx - v_w / 2 + v_w * 0.18), top_y),
        (int(cx), int(meet_y - s * 0.03)),
        (int(cx + v_w / 2 - v_w * 0.18), top_y),
        (int(cx + v_w / 2), top_y),
        (int(cx + stem_w / 2), int(meet_y)),
        (int(cx + stem_w / 2), bot_y),
        (int(cx - stem_w / 2), bot_y),
        (int(cx - stem_w / 2), int(meet_y)),
    ]

    # Draw shadow first (darker version, slightly offset)
    shadow_offset = max(1, int(s * 0.015))
    shadow_points = [(p[0] + shadow_offset, p[1] + shadow_offset) for p in points]
    draw_filled_polygon(pixels, w, h, shadow_points, shadow)

    # Draw main Y letter
    draw_filled_polygon(pixels, w, h, points, color)

    # Highlight on left half (lighter gold)
    for y in range(top_y, bot_y + 1):
        for x in range(int(cx - v_w / 2), int(cx) + 1):
            if 0 <= x < w and 0 <= y < h:
                p = pixels[y * w + x]
                if p == color:
                    pixels[y * w + x] = highlight


def draw_sparkle(pixels, w, h, cx, cy, size, color):
    """4-point sparkle with thin lines"""
    t1 = max(1, size // 5)
    draw_line(pixels, w, h, cx, cy - size, cx, cy + size, t1, color)
    draw_line(pixels, w, h, cx - size, cy, cx + size, cy, t1, color)
    s2 = int(size * 0.4)
    t2 = max(1, t1 // 2)
    draw_line(pixels, w, h, cx - s2, cy - s2, cx + s2, cy + s2, t2, color)
    draw_line(pixels, w, h, cx - s2, cy + s2, cx + s2, cy - s2, t2, color)


def create_icon(size):
    """Create the YOUNES Sovereign app icon at given size"""
    cx, cy = size // 2, size // 2

    # Initialize with dark navy
    pixels = [hex_to_rgb('#071A2E')] * (size * size)

    # 1. Background gradient (radial: bright center, dark edges)
    bg_outer = hex_to_rgb('#050A16')
    bg_mid = hex_to_rgb('#0A1628')
    bg_inner = hex_to_rgb('#1E3A5F')
    glow_color = hex_to_rgb('#E8B84A')

    for y in range(size):
        for x in range(size):
            dx = x - cx
            dy = y - cy
            d = math.sqrt(dx*dx + dy*dy)
            max_d = math.sqrt(2) * size / 2
            t = min(1.0, d / max_d)

            if t < 0.4:
                pixels[y * size + x] = lerp_color(bg_inner, bg_mid, t / 0.4)
            else:
                pixels[y * size + x] = lerp_color(bg_mid, bg_outer, (t - 0.4) / 0.6)

            # Central golden glow
            glow_d = d / (size * 0.45)
            if glow_d < 1.0:
                glow_t = (1.0 - glow_d) * 0.18
                base = pixels[y * size + x]
                pixels[y * size + x] = (
                    int(base[0] + (glow_color[0] - base[0]) * glow_t),
                    int(base[1] + (glow_color[1] - base[1]) * glow_t),
                    int(base[2] + (glow_color[2] - base[2]) * glow_t)
                )

    # 2. Mesh pattern (very subtle)
    mesh_color = (53, 203, 224)  # Cyan
    mesh_alpha = 0.10
    spacing = max(8, size // 10)
    for y in range(0, size, spacing):
        for x in range(0, size, spacing):
            if 0 <= x < size and 0 <= y < size:
                p = pixels[y * size + x]
                pixels[y * size + x] = (
                    int(p[0] * (1 - mesh_alpha) + mesh_color[0] * mesh_alpha),
                    int(p[1] * (1 - mesh_alpha) + mesh_color[1] * mesh_alpha),
                    int(p[2] * (1 - mesh_alpha) + mesh_color[2] * mesh_alpha)
                )

    # 3. Outer gold ring (THICK)
    ring_r = int(size * 0.42)
    ring_thickness = max(2, int(size * 0.025))
    ring_color = hex_to_rgb('#E8B84A')
    draw_ring(pixels, size, size, cx, cy, ring_r, ring_r - ring_thickness, ring_color)

    # Inner ring (subtle)
    inner_ring_r = int(size * 0.37)
    inner_thickness = max(1, int(size * 0.006))
    draw_ring(pixels, size, size, cx, cy, inner_ring_r,
              inner_ring_r - inner_thickness, hex_to_rgb('#FFE27A'))

    # 4. Y letter (centered)
    y_size = int(size * 0.42)
    y_color = hex_to_rgb('#E8B84A')
    y_highlight = hex_to_rgb('#FFE27A')
    y_shadow = hex_to_rgb('#0A1628')
    draw_y_letter(pixels, size, size, cx, cy, y_size, y_color, y_highlight, y_shadow)

    # 5. Center emerald dot
    dot_r = max(1, int(size * 0.020))
    draw_filled_circle(pixels, size, size, cx, cy + int(size * 0.05), dot_r, hex_to_rgb('#00C98C'))

    # 6. Sparkle stars (positioned around the icon)
    draw_sparkle(pixels, size, size,
                 int(size * 0.78), int(size * 0.28),
                 int(size * 0.030), hex_to_rgb('#FFE27A'))
    draw_sparkle(pixels, size, size,
                 int(size * 0.86), int(size * 0.40),
                 int(size * 0.018), hex_to_rgb('#FFE27A'))
    draw_sparkle(pixels, size, size,
                 int(size * 0.20), int(size * 0.75),
                 int(size * 0.022), hex_to_rgb('#00E6A0'))
    draw_sparkle(pixels, size, size,
                 int(size * 0.12), int(size * 0.60),
                 int(size * 0.012), hex_to_rgb('#35CBE0'))

    # 7. Accent dots
    accent_dots = [
        (int(size * 0.85), int(size * 0.55), hex_to_rgb('#35CBE0'), 0.014),
        (int(size * 0.15), int(size * 0.42), hex_to_rgb('#00E6A0'), 0.012),
        (int(size * 0.20), int(size * 0.18), hex_to_rgb('#FFE27A'), 0.010),
        (int(size * 0.80), int(size * 0.82), hex_to_rgb('#E8B84A'), 0.010),
        (int(size * 0.10), int(size * 0.85), hex_to_rgb('#00E6A0'), 0.008),
        (int(size * 0.90), int(size * 0.15), hex_to_rgb('#35CBE0'), 0.008),
    ]
    for x, y, color, r_factor in accent_dots:
        r = max(1, int(size * r_factor))
        draw_filled_circle(pixels, size, size, x, y, r, color)

    return pixels


def main():
    sizes = [
        ('mdpi', 48),
        ('hdpi', 72),
        ('xhdpi', 96),
        ('xxhdpi', 144),
        ('xxxhdpi', 192),
    ]

    output_base = '/home/user/pro_v1/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/res'

    print("🎨 YOUNES Sovereign — Professional PNG Icon Generator v4 (Final)")
    print("=" * 60)

    for density, size in sizes:
        output_dir = f'{output_base}/mipmap-{density}'
        os.makedirs(output_dir, exist_ok=True)

        pixels = create_icon(size)

        png_path = f'{output_dir}/ic_launcher.png'
        write_png(png_path, size, size, pixels)
        print(f"✅ {density:8s} ({size}x{size})")

        round_path = f'{output_dir}/ic_launcher_round.png'
        write_png(round_path, size, size, pixels)

    print()
    print("🎉 All app icons generated successfully!")


if __name__ == '__main__':
    main()

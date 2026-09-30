import os
import zlib
import struct

DENSITIES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'app', 'src', 'main', 'res')

def draw_hrms_icon(size, is_round=False):
    from PIL import Image, ImageDraw
    
    # Render at 4x for clean antialiasing
    scale = 4
    hi_size = size * scale
    
    # Background: Solid dark graphite #14151E
    bg_color = (20, 21, 30, 255)
    
    img = Image.new('RGBA', (hi_size, hi_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    if is_round:
        draw.ellipse([0, 0, hi_size - 1, hi_size - 1], fill=bg_color)
    else:
        corner_r = int(hi_size * 0.22)
        draw.rounded_rectangle([0, 0, hi_size - 1, hi_size - 1], radius=corner_r, fill=bg_color)
    
    # Geometric bold HRMS letters (fills ~74% width, ~40% height)
    total_w = int(hi_size * 0.74)
    total_h = int(hi_size * 0.40)
    sx = (hi_size - total_w) // 2
    sy = (hi_size - total_h) // 2
    
    gw = max(4, int(hi_size * 0.032)) # gap
    st = max(6, int(hi_size * 0.068)) # stroke thickness
    lw = (total_w - 3 * gw) // 4     # letter width
    
    white = (255, 255, 255, 255)
    
    # --- H ---
    hx = sx
    draw.rectangle([hx, sy, hx + st - 1, sy + total_h - 1], fill=white)
    draw.rectangle([hx + lw - st, sy, hx + lw - 1, sy + total_h - 1], fill=white)
    mid_y = sy + (total_h - st) // 2
    draw.rectangle([hx, mid_y, hx + lw - 1, mid_y + st - 1], fill=white)
    
    # --- R ---
    rx = hx + lw + gw
    draw.rectangle([rx, sy, rx + st - 1, sy + total_h - 1], fill=white)
    loop_h = (total_h + st) // 2
    draw.rectangle([rx, sy, rx + lw - 1, sy + st - 1], fill=white)
    draw.rectangle([rx, sy + loop_h - st, rx + lw - 1, sy + loop_h - 1], fill=white)
    draw.rectangle([rx + lw - st, sy, rx + lw - 1, sy + loop_h - 1], fill=white)
    draw.polygon([
        (rx + st, sy + loop_h - st),
        (rx + lw - st, sy + loop_h - st),
        (rx + lw - 1, sy + total_h - 1),
        (rx + lw - st - 1, sy + total_h - 1)
    ], fill=white)
    
    # --- M ---
    mx = rx + lw + gw
    draw.rectangle([mx, sy, mx + st - 1, sy + total_h - 1], fill=white)
    draw.rectangle([mx + lw - st, sy, mx + lw - 1, sy + total_h - 1], fill=white)
    center_x = mx + lw // 2
    draw.polygon([
        (mx, sy),
        (mx + st, sy),
        (center_x, sy + loop_h),
        (center_x - st // 2, sy + loop_h)
    ], fill=white)
    draw.polygon([
        (mx + lw, sy),
        (mx + lw - st, sy),
        (center_x, sy + loop_h),
        (center_x + st // 2, sy + loop_h)
    ], fill=white)
    
    # --- S ---
    sx_pos = mx + lw + gw
    draw.rectangle([sx_pos, sy, sx_pos + lw - 1, sy + st - 1], fill=white)
    draw.rectangle([sx_pos, sy, sx_pos + st - 1, sy + loop_h - 1], fill=white)
    draw.rectangle([sx_pos, sy + loop_h - st, sx_pos + lw - 1, sy + loop_h - 1], fill=white)
    draw.rectangle([sx_pos + lw - st, sy + loop_h - st, sx_pos + lw - 1, sy + total_h - 1], fill=white)
    draw.rectangle([sx_pos, sy + total_h - st, sx_pos + lw - 1, sy + total_h - 1], fill=white)
    
    # Sub-caption accent bar for a premium signature look
    bar_y = sy + total_h + int(hi_size * 0.04)
    bar_h = max(2, int(hi_size * 0.018))
    draw.rectangle([sx, bar_y, sx + total_w - 1, bar_y + bar_h - 1], fill=(125, 232, 179, 255))
    
    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    for folder_name, size in DENSITIES.items():
        folder_path = os.path.join(BASE_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        square_path = os.path.join(folder_path, 'ic_launcher.png')
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')

        img_sq = draw_hrms_icon(size, is_round=False)
        img_sq.save(square_path, 'PNG')
        print(f"Generated bold HRMS icon: {square_path} ({size}x{size})")

        img_rd = draw_hrms_icon(size, is_round=True)
        img_rd.save(round_path, 'PNG')
        print(f"Generated bold round HRMS icon: {round_path} ({size}x{size})")

if __name__ == '__main__':
    main()

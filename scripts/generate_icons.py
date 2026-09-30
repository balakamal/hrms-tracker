import os
import zlib
import struct

# Density sizes for Android launcher icons
DENSITIES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'app', 'src', 'main', 'res')

def make_png(width, height, get_pixel_func):
    """Generate uncompressed/deflated raw PNG bytes without external dependencies."""
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # Filter type 0 (None)
        for x in range(width):
            r, g, b, a = get_pixel_func(x, y, width, height)
            raw_data.extend([r, g, b, a])
    
    def chunk(tag, data):
        c = tag + data
        crc = zlib.crc32(c) & 0xffffffff
        return struct.pack('>I', len(data)) + c + struct.pack('>I', crc)
    
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw_data), 9)
    
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', idat) + chunk(b'IEND', b'')

# 5x7 block bitmap font for H, R, M, S
FONT_5x7 = {
    'H': [
        "10001",
        "10001",
        "10001",
        "11111",
        "10001",
        "10001",
        "10001"
    ],
    'R': [
        "11110",
        "10001",
        "10001",
        "11110",
        "10100",
        "10010",
        "10001"
    ],
    'M': [
        "10001",
        "11011",
        "10101",
        "10101",
        "10001",
        "10001",
        "10001"
    ],
    'S': [
        "01111",
        "10000",
        "10000",
        "01110",
        "00001",
        "00001",
        "11110"
    ]
}

def generate_hrms_icon(is_round=False):
    def get_pixel(x, y, w, h):
        # Background: Solid dark graphite #161820
        bg_r, bg_g, bg_b = 0x16, 0x18, 0x20
        
        # If round icon, clip outside circle
        if is_round:
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            r = min(w, h) / 2.0
            dist_sq = (x - cx) ** 2 + (y - cy) ** 2
            if dist_sq > r ** 2:
                # Anti-alias edge slightly or make transparent
                if dist_sq > (r + 0.5) ** 2:
                    return 0, 0, 0, 0
                alpha = int(255 * max(0.0, (r + 0.5 - (dist_sq ** 0.5))))
                return bg_r, bg_g, bg_b, alpha

        # Calculate text scale
        # 4 letters, each 5 cols + 1 col gap = 5*4 + 3 = 23 cols total.
        # Height is 7 rows.
        # We want the text to occupy about 60% of width
        char_scale = max(1, int(w * 0.58 / 23))
        text_w = 23 * char_scale
        text_h = 7 * char_scale
        
        start_x = (w - text_w) // 2
        start_y = (h - text_h) // 2
        
        # Check if inside text bounding box
        if start_x <= x < start_x + text_w and start_y <= y < start_y + text_h:
            rel_x = (x - start_x) // char_scale
            rel_y = (y - start_y) // char_scale
            
            # Identify character
            # Positions: 0..4: 'H', 5: gap, 6..10: 'R', 11: gap, 12..16: 'M', 17: gap, 18..22: 'S'
            char_idx = rel_x // 6
            char_col = rel_x % 6
            
            chars = ['H', 'R', 'M', 'S']
            if char_idx < 4 and char_col < 5:
                char = chars[char_idx]
                if FONT_5x7[char][rel_y][char_col] == '1':
                    # Crisp Pure White #FFFFFF
                    return 0xFF, 0xFF, 0xFF, 255
                    
        return bg_r, bg_g, bg_b, 255

    return get_pixel

def main():
    try:
        # Check if Pillow is available for even higher quality rendering
        from PIL import Image, ImageDraw, ImageFont
        use_pil = True
    except ImportError:
        use_pil = False

    for folder_name, size in DENSITIES.items():
        folder_path = os.path.join(BASE_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        square_path = os.path.join(folder_path, 'ic_launcher.png')
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')

        if use_pil:
            for is_round, path in [(False, square_path), (True, round_path)]:
                # High-res rendering with antialiased supersampling
                scale = 4
                hi_size = size * scale
                img = Image.new('RGBA', (hi_size, hi_size), (0, 0, 0, 0))
                draw = ImageDraw.Draw(img)
                
                # Background
                if is_round:
                    draw.ellipse([0, 0, hi_size - 1, hi_size - 1], fill=(22, 24, 32, 255))
                else:
                    # Squircle / rounded rect or solid rect with slight corner radius
                    corner_r = int(hi_size * 0.18)
                    draw.rounded_rectangle([0, 0, hi_size - 1, hi_size - 1], radius=corner_r, fill=(22, 24, 32, 255))
                
                # Text HRMS
                try:
                    font_size = int(hi_size * 0.32)
                    # Try system font or default
                    font = None
                    for font_name in ["arialbd.ttf", "Arial Bold.ttf", "DejaVuSans-Bold.ttf", "Roboto-Bold.ttf"]:
                        try:
                            font = ImageFont.truetype(font_name, font_size)
                            break
                        except Exception:
                            continue
                    if font is None:
                        font = ImageFont.load_default()
                    
                    bbox = draw.textbbox((0, 0), "HRMS", font=font)
                    text_w = bbox[2] - bbox[0]
                    text_h = bbox[3] - bbox[1]
                    tx = (hi_size - text_w) / 2.0 - bbox[0]
                    ty = (hi_size - text_h) / 2.0 - bbox[1]
                    draw.text((tx, ty), "HRMS", fill=(255, 255, 255, 255), font=font)
                except Exception:
                    pass
                
                final_img = img.resize((size, size), Image.Resampling.LANCZOS)
                final_img.save(path, 'PNG')
                print(f"Generated via Pillow: {path} ({size}x{size})")
        else:
            # Fallback pure python generator
            png_square = make_png(size, size, generate_hrms_icon(is_round=False))
            with open(square_path, 'wb') as f:
                f.write(png_square)
            print(f"Generated (pure python): {square_path} ({size}x{size})")

            png_round = make_png(size, size, generate_hrms_icon(is_round=True))
            with open(round_path, 'wb') as f:
                f.write(png_round)
            print(f"Generated (pure python): {round_path} ({size}x{size})")

if __name__ == '__main__':
    main()

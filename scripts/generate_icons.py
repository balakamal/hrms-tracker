import os
from PIL import Image, ImageDraw, ImageFont

# Tuple of (legacy_size_px, adaptive_foreground_size_px)
DENSITIES = {
    'mipmap-mdpi': (48, 108),
    'mipmap-hdpi': (72, 162),
    'mipmap-xhdpi': (96, 216),
    'mipmap-xxhdpi': (144, 324),
    'mipmap-xxxhdpi': (192, 432),
}

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'app', 'src', 'main', 'res')
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'font.ttf')

def draw_hrms_legacy_icon(size, is_round=False):
    """Generates legacy launcher icon with solid background and clean Roboto HRMS text."""
    scale = 4
    hi_size = size * scale
    
    # Background: Solid clean dark graphite #161822
    bg_color = (22, 24, 34, 255)
    
    img = Image.new('RGBA', (hi_size, hi_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    if is_round:
        draw.ellipse([0, 0, hi_size - 1, hi_size - 1], fill=bg_color)
    else:
        corner_r = int(hi_size * 0.22)
        draw.rounded_rectangle([0, 0, hi_size - 1, hi_size - 1], radius=corner_r, fill=bg_color)
        
    font_size = int(hi_size * 0.30)
    font = ImageFont.truetype(FONT_PATH, font_size)
    
    bbox = draw.textbbox((0, 0), "HRMS", font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    
    tx = (hi_size - text_w) / 2.0 - bbox[0]
    ty = (hi_size - text_h) / 2.0 - bbox[1]
    
    draw.text((tx, ty), "HRMS", fill=(255, 255, 255, 255), font=font)
    
    return img.resize((size, size), Image.Resampling.LANCZOS)

def draw_hrms_adaptive_foreground(size):
    """
    Generates adaptive icon foreground layer (108dp canvas equivalent).
    Background is transparent; clean white Roboto HRMS text is centered
    within the 72dp safe zone so Android launcher masks never crop it.
    """
    scale = 4
    hi_size = size * scale
    
    img = Image.new('RGBA', (hi_size, hi_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Font size sized to comfortably fit within the 72dp safe circle
    font_size = int(hi_size * 0.22)
    font = ImageFont.truetype(FONT_PATH, font_size)
    
    bbox = draw.textbbox((0, 0), "HRMS", font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    
    tx = (hi_size - text_w) / 2.0 - bbox[0]
    ty = (hi_size - text_h) / 2.0 - bbox[1]
    
    draw.text((tx, ty), "HRMS", fill=(255, 255, 255, 255), font=font)
    
    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    print(f"Generating HRMS icons using font: {FONT_PATH}")
    for folder_name, (legacy_sz, adaptive_sz) in DENSITIES.items():
        folder_path = os.path.join(BASE_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        # 1. Legacy square launcher icon
        square_path = os.path.join(folder_path, 'ic_launcher.png')
        img_sq = draw_hrms_legacy_icon(legacy_sz, is_round=False)
        img_sq.save(square_path, 'PNG')
        print(f"Generated legacy square icon: {square_path} ({legacy_sz}x{legacy_sz})")

        # 2. Legacy round launcher icon
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')
        img_rd = draw_hrms_legacy_icon(legacy_sz, is_round=True)
        img_rd.save(round_path, 'PNG')
        print(f"Generated legacy round icon:  {round_path} ({legacy_sz}x{legacy_sz})")

        # 3. Adaptive foreground layer (for API 26+ Android adaptive launcher icons)
        fg_path = os.path.join(folder_path, 'ic_launcher_foreground.png')
        img_fg = draw_hrms_adaptive_foreground(adaptive_sz)
        img_fg.save(fg_path, 'PNG')
        print(f"Generated adaptive foreground: {fg_path} ({adaptive_sz}x{adaptive_sz})")

    print("All HRMS launcher and adaptive icon assets successfully generated!")

if __name__ == '__main__':
    main()

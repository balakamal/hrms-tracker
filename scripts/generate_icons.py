import os
from PIL import Image, ImageDraw, ImageFont

DENSITIES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'app', 'src', 'main', 'res')
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'font.ttf')

def draw_hrms_icon(size, is_round=False):
    # Render at 4x for clean supersampling
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

def main():
    for folder_name, size in DENSITIES.items():
        folder_path = os.path.join(BASE_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        square_path = os.path.join(folder_path, 'ic_launcher.png')
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')

        img_sq = draw_hrms_icon(size, is_round=False)
        img_sq.save(square_path, 'PNG')
        print(f"Generated clean Roboto HRMS icon: {square_path} ({size}x{size})")

        img_rd = draw_hrms_icon(size, is_round=True)
        img_rd.save(round_path, 'PNG')
        print(f"Generated clean Roboto round HRMS icon: {round_path} ({size}x{size})")

if __name__ == '__main__':
    main()

import os
import zipfile
from PIL import Image, ImageDraw, ImageFont

DENSITIES = {
    'mdpi': (48, 108),
    'hdpi': (72, 162),
    'xhdpi': (96, 216),
    'xxhdpi': (144, 324),
    'xxxhdpi': (192, 432),
}

RES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'app', 'src', 'main', 'res')
EXT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'hrms-extension')
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'font.ttf')

def draw_hrms_legacy_icon(size, is_round=False):
    """Generates standalone launcher icon with solid dark graphite background and clean Roboto HRMS text."""
    scale = 4
    hi_size = size * scale
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

    # Remove hand-drawn vector XMLs if they exist so AAPT2 links directly to raster PNG drawables
    for xml_name in ['ic_launcher_foreground.xml', 'ic_launcher_monochrome.xml']:
        xml_path = os.path.join(RES_DIR, 'drawable', xml_name)
        if os.path.exists(xml_path):
            os.remove(xml_path)
            print(f"Removed legacy vector XML: {xml_path}")

    for density, (legacy_sz, adaptive_sz) in DENSITIES.items():
        mipmap_folder = os.path.join(RES_DIR, f'mipmap-{density}')
        drawable_folder = os.path.join(RES_DIR, f'drawable-{density}')
        os.makedirs(mipmap_folder, exist_ok=True)
        os.makedirs(drawable_folder, exist_ok=True)
        
        # 1. Legacy square launcher icon (in mipmap)
        square_path = os.path.join(mipmap_folder, 'ic_launcher.png')
        img_sq = draw_hrms_legacy_icon(legacy_sz, is_round=False)
        img_sq.save(square_path, 'PNG')

        # 2. Legacy round launcher icon (in mipmap)
        round_path = os.path.join(mipmap_folder, 'ic_launcher_round.png')
        img_rd = draw_hrms_legacy_icon(legacy_sz, is_round=True)
        img_rd.save(round_path, 'PNG')

        # 3. Adaptive foreground PNG (in drawable-{density} for adaptive icon and splash screen)
        fg_drawable_path = os.path.join(drawable_folder, 'ic_launcher_foreground.png')
        img_fg = draw_hrms_adaptive_foreground(adaptive_sz)
        img_fg.save(fg_drawable_path, 'PNG')

        # Also save to mipmap folder for any tools that inspect mipmaps
        fg_mipmap_path = os.path.join(mipmap_folder, 'ic_launcher_foreground.png')
        img_fg.save(fg_mipmap_path, 'PNG')
        print(f"Generated icons for {density}: legacy={legacy_sz}px, adaptive={adaptive_sz}px")

    # 4. Generate extension icons
    if os.path.exists(EXT_DIR):
        for sz in [16, 48, 128]:
            ext_icon_path = os.path.join(EXT_DIR, f'icon{sz}.png')
            ext_img = draw_hrms_legacy_icon(sz, is_round=False)
            ext_img.save(ext_icon_path, 'PNG')
            print(f"Generated extension icon: {ext_icon_path} ({sz}x{sz})")

        # Repackage hrms-extension.zip
        zip_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'hrms-extension.zip')
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            for root, dirs, files in os.walk(EXT_DIR):
                for file in files:
                    file_path = os.path.join(root, file)
                    arcname = os.path.relpath(file_path, EXT_DIR)
                    zipf.write(file_path, arcname)
        print(f"Repackaged {zip_path}")

    print("All HRMS launcher, adaptive, and extension icon assets successfully generated!")

if __name__ == '__main__':
    main()

import os
import sys
import subprocess

try:
    from PIL import Image, ImageDraw
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image, ImageDraw

def create_icons(source_path, target_res_dir):
    sizes = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }
    
    if not os.path.exists(source_path):
        print(f"Error: Source image not found at {source_path}")
        sys.exit(1)
        
    img = Image.open(source_path).convert("RGBA")
    
    for density, size in sizes.items():
        # Create directory if it doesn't exist
        mipmap_dir = os.path.join(target_res_dir, f'mipmap-{density}')
        os.makedirs(mipmap_dir, exist_ok=True)
        
        # Resize and save
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save standard icon
        icon_path = os.path.join(mipmap_dir, 'ic_launcher.png')
        resized_img.save(icon_path, "PNG")
        
        # Create circle mask for round icon
        mask = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_img = Image.new('RGBA', (size, size))
        round_img.paste(resized_img, (0,0), mask)
        
        round_icon_path = os.path.join(mipmap_dir, 'ic_launcher_round.png')
        round_img.save(round_icon_path, "PNG")
        
        print(f"Created {density} icons at {mipmap_dir}")
        
if __name__ == "__main__":
    source_image = r"C:\Users\navee\Downloads\dashboard_icon.png"
    target_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\android-dashboard-app\app\src\main\res"
    create_icons(source_image, target_dir)

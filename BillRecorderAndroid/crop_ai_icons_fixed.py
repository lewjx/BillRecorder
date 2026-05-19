import cv2
import numpy as np
import os
from PIL import Image, ImageDraw

def crop_ai_icons_fixed():
    sheet_path = "C:/Users/hp/.gemini/antigravity/brain/bbd48354-a502-4b05-836a-4b463708fcca/budget_icons_sheet_1779170517877.png"
    drawable_dir = "c:/Users/hp/Downloads/BillRecorder/BillRecorder/BillRecorderAndroid/app/src/main/res/drawable"
    os.makedirs(drawable_dir, exist_ok=True)

    if not os.path.exists(sheet_path):
        print(f"Error: {sheet_path} does not exist.")
        return

    # Load with OpenCV for circle detection
    img = cv2.imread(sheet_path)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # Detect circles
    circles = cv2.HoughCircles(
        gray, 
        cv2.HOUGH_GRADIENT, 
        dp=1.2, 
        minDist=80, 
        param1=50, 
        param2=30, 
        minRadius=30, 
        maxRadius=120
    )

    if circles is None:
        print("No circles detected.")
        return

    circles = np.round(circles[0, :]).astype("int")
    
    # Filter for the 20 perfect circular badges (radius between 80 and 90 pixels)
    valid_circles = [c for c in circles if 80 <= c[2] <= 90]
    
    # Sort circles row-by-row (Y spaced), left-to-right (X spaced)
    # Spacing is ~255px vertical, ~197px horizontal
    circles_sorted = sorted(valid_circles, key=lambda c: (round(c[1] / 100) * 100, c[0]))
    
    print(f"Detected {len(circles_sorted)} valid circular icons.")

    # 1-to-1 Mapping coordinates from the sorted 20 circular icons:
    # Row 0: baby (0), beauty (1), bills (2), car (3), clothing (4)
    # Row 1: health (5), education (6), electronics (7), entertainment (8), food (9)
    # Row 2: home (10), insurance (11), shopping (12), social (13), sport (14)
    # Row 3: tax (15), telephone (16), unknown/repeat (17), buffer (18), others (19)

    # Let's map our 24 target drawable file names to these clean unique indices:
    mappings = {
        "baby": 0,          # Baby bottle
        "beauty": 1,        # Rose
        "bills": 2,         # Receipt
        "car": 3,           # Car
        "clothing": 4,      # T-shirt
        "health": 5,        # Heart
        "education": 6,     # Grad cap
        "electronics": 7,   # Plug
        "entertainment": 8, # Movie clapper
        "food": 9,          # Fork & spoon
        "home": 10,         # House
        "insurance": 11,    # Shield
        "shopping": 12,     # Shopping cart
        "social": 13,       # Group
        "sport": 14,        # Tennis racket
        "tax": 15,          # Scissors
        "telephone": 16,    # Pink phone
        "buffer": 18,       # Wallet
        "others": 19,       # Others symbol
        
        # Reuse related icons for similar categories to keep visual consistency:
        "transportation": 3,# Reuse Car icon
        "grocery": 12,      # Reuse Shopping cart
        "investment": 18,   # Reuse Wallet/money icon
        "investment_2": 18, # Reuse Wallet/money icon
        "transfer": 2       # Reuse Receipt icon
    }

    # Open with Pillow for transparent masking and saving
    pil_img = Image.open(sheet_path).convert("RGBA")

    for name, idx in mappings.items():
        if idx >= len(circles_sorted):
            print(f"Warning: Index {idx} out of range for {name}. Skipping.")
            continue
            
        cx, cy, r = circles_sorted[idx]
        
        # Crop square box around circle center
        pad = r
        left = max(0, cx - pad)
        top = max(0, cy - pad)
        right = min(img.shape[1], cx + pad)
        bottom = min(img.shape[0], cy + pad)
        
        cropped = pil_img.crop((left, top, right, bottom))
        
        # Create a premium high-quality transparent circular mask
        mask = Image.new("L", cropped.size, 0)
        draw = ImageDraw.Draw(mask)
        # Apply anti-aliasing inset boundary to make it exceptionally smooth
        draw.ellipse((2, 2, cropped.size[0] - 2, cropped.size[1] - 2), fill=255)
        
        output = Image.new("RGBA", cropped.size, (0, 0, 0, 0))
        output.paste(cropped, (0, 0), mask=mask)
        
        # Resize to extremely crisp 128x128 pixels for Android Drawables
        output = output.resize((128, 128), Image.Resampling.LANCZOS)
        
        target_path = os.path.join(drawable_dir, f"ic_{name}.png")
        output.save(target_path, "PNG")
        print(f"Extracted crisp {name} icon -> {target_path} (Center: {cx}, {cy}, idx: {idx})")

if __name__ == "__main__":
    crop_ai_icons_fixed()

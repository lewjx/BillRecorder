import cv2
import numpy as np
import os

def inspect():
    sheet_path = "C:/Users/hp/.gemini/antigravity/brain/bbd48354-a502-4b05-836a-4b463708fcca/budget_icons_sheet_1779170517877.png"
    if not os.path.exists(sheet_path):
        print("Not found")
        return
    img = cv2.imread(sheet_path)
    print("Dimensions:", img.shape)
    
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    circles = cv2.HoughCircles(
        gray, 
        cv2.HOUGH_GRADIENT, 
        dp=1.2, 
        minDist=50, 
        param1=50, 
        param2=20, # Low param2 to detect all potential circles
        minRadius=30, 
        maxRadius=120
    )
    if circles is not None:
        circles = np.round(circles[0, :]).astype("int")
        print("Detected circles count:", len(circles))
        for idx, c in enumerate(circles):
            print(f"Circle {idx}: x={c[0]}, y={c[1]}, r={c[2]}")
    else:
        print("No circles found")

if __name__ == "__main__":
    inspect()

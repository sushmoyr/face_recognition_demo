#!/usr/bin/env python3
"""
Generate sample face images for testing enrollment.

This creates placeholder face images that can be used to test the enrollment process.
In a real deployment, these would be actual photos of employees.
"""

import os
from PIL import Image, ImageDraw, ImageFont
import random


def create_sample_face(width=300, height=400, name="Sample Face"):
    """Create a sample face image for testing."""
    # Create a new image with a light background
    img = Image.new('RGB', (width, height), color=(240, 240, 240))
    draw = ImageDraw.Draw(img)
    
    # Draw a simple face representation
    # Face oval
    face_margin = 40
    face_left = face_margin
    face_top = face_margin + 20
    face_right = width - face_margin
    face_bottom = height - face_margin - 40
    
    draw.ellipse([face_left, face_top, face_right, face_bottom], 
                 fill=(255, 220, 177), outline=(200, 180, 140), width=2)
    
    # Eyes
    eye_y = face_top + (face_bottom - face_top) * 0.35
    left_eye_x = face_left + (face_right - face_left) * 0.3
    right_eye_x = face_left + (face_right - face_left) * 0.7
    eye_size = 15
    
    draw.ellipse([left_eye_x - eye_size, eye_y - eye_size//2, 
                  left_eye_x + eye_size, eye_y + eye_size//2], 
                 fill=(255, 255, 255), outline=(0, 0, 0))
    draw.ellipse([right_eye_x - eye_size, eye_y - eye_size//2, 
                  right_eye_x + eye_size, eye_y + eye_size//2], 
                 fill=(255, 255, 255), outline=(0, 0, 0))
    
    # Pupils
    pupil_size = 6
    draw.ellipse([left_eye_x - pupil_size, eye_y - pupil_size, 
                  left_eye_x + pupil_size, eye_y + pupil_size], 
                 fill=(0, 0, 0))
    draw.ellipse([right_eye_x - pupil_size, eye_y - pupil_size, 
                  right_eye_x + pupil_size, eye_y + pupil_size], 
                 fill=(0, 0, 0))
    
    # Nose
    nose_x = (left_eye_x + right_eye_x) // 2
    nose_y = eye_y + 40
    draw.ellipse([nose_x - 8, nose_y - 6, nose_x + 8, nose_y + 6], 
                 fill=(200, 160, 120))
    
    # Mouth
    mouth_x = nose_x
    mouth_y = nose_y + 40
    draw.arc([mouth_x - 25, mouth_y - 10, mouth_x + 25, mouth_y + 10], 
             start=0, end=180, fill=(150, 50, 50), width=3)
    
    # Add some variation for different "people"
    variation = random.randint(-20, 20)
    draw.rectangle([face_left + 10, face_top - 10, face_right - 10, face_top + 10], 
                   fill=(139 + variation, 69 + variation, 19 + variation))  # Hair
    
    # Add text label
    try:
        font = ImageFont.load_default()
        text_bbox = draw.textbbox((0, 0), name, font=font)
        text_width = text_bbox[2] - text_bbox[0]
        text_x = (width - text_width) // 2
        draw.text((text_x, height - 30), name, fill=(0, 0, 0), font=font)
    except:
        draw.text((width//2 - 30, height - 30), name, fill=(0, 0, 0))
    
    return img


def main():
    """Generate sample employee images."""
    # Create sample images for employee E1001
    employee_dir = "samples/employee_E1001"
    os.makedirs(employee_dir, exist_ok=True)
    
    print("Generating sample face images for employee E1001...")
    
    # Create 5 sample images with slight variations
    for i in range(1, 6):
        img = create_sample_face(name=f"E1001 Face {i}")
        filename = f"{employee_dir}/face_{i}.jpg"
        img.save(filename, "JPEG", quality=95)
        print(f"Created {filename}")
    
    # Create sample for another employee
    employee_dir_2 = "samples/employee_E1002" 
    os.makedirs(employee_dir_2, exist_ok=True)
    
    for i in range(1, 4):
        img = create_sample_face(name=f"E1002 Face {i}")
        filename = f"{employee_dir_2}/face_{i}.jpg"
        img.save(filename, "JPEG", quality=95)
        print(f"Created {filename}")
    
    print("\nSample images created successfully!")
    print("You can now test enrollment with:")
    print("python scripts/enroll_cli.py --employee-code E1001 --path samples/employee_E1001")


if __name__ == "__main__":
    main()

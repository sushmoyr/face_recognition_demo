#!/usr/bin/env python3
"""
CLI tool for enrolling employees with face images.

Usage:
    python enroll_cli.py --employee-code E1001 --path ./samples/employee_E1001
    python enroll_cli.py --employee-code E1001 --path ./samples/employee_E1001 --backend-url http://localhost:8080
"""

import argparse
import logging
import sys
from pathlib import Path
from typing import List, Optional
import requests
import json

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class EmployeeEnrollmentCLI:
    """CLI tool for employee face enrollment."""
    
    def __init__(self, backend_url: str = "http://localhost:8080"):
        self.backend_url = backend_url.rstrip('/')
        self.session = requests.Session()
        self.auth_token: Optional[str] = None
        
    def authenticate(self, username: str = "admin", password: str = "admin123") -> bool:
        """Authenticate with the backend API."""
        try:
            auth_url = f"{self.backend_url}/api/auth/login"
            response = self.session.post(auth_url, json={
                "username": username,
                "password": password
            })
            
            if response.status_code == 200:
                data = response.json()
                self.auth_token = data.get('token')
                self.session.headers.update({
                    'Authorization': f'Bearer {self.auth_token}'
                })
                logger.info("Authentication successful")
                return True
            else:
                logger.error("Authentication failed: %s", response.text)
                return False
                
        except Exception as e:
            logger.error("Authentication error: %s", str(e))
            return False
    
    def find_employee(self, employee_code: str) -> Optional[dict]:
        """Find employee by employee code."""
        try:
            # Search for employee
            search_url = f"{self.backend_url}/api/employees"
            response = self.session.get(search_url, params={
                'employeeCode': employee_code
            })
            
            if response.status_code == 200:
                data = response.json()
                if data.get('content') and len(data['content']) > 0:
                    employee = data['content'][0]
                    logger.info("Found employee: %s (%s)", 
                              employee.get('fullName'), employee.get('employeeCode'))
                    return employee
                else:
                    logger.error("Employee not found: %s", employee_code)
                    return None
            else:
                logger.error("Error searching employee: %s", response.text)
                return None
                
        except Exception as e:
            logger.error("Error finding employee: %s", str(e))
            return None
    
    def get_image_files(self, path: Path) -> List[Path]:
        """Get all image files from the specified path."""
        if not path.exists():
            logger.error("Path does not exist: %s", path)
            return []
        
        image_extensions = {'.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.tif'}
        image_files = []
        
        if path.is_file():
            if path.suffix.lower() in image_extensions:
                image_files.append(path)
            else:
                logger.warning("File is not a supported image format: %s", path)
        else:
            # Directory - find all image files
            for ext in image_extensions:
                image_files.extend(path.glob(f"*{ext}"))
                image_files.extend(path.glob(f"*{ext.upper()}"))
        
        logger.info("Found %d image files", len(image_files))
        return sorted(image_files)
    
    def enroll_employee(self, employee_id: str, image_files: List[Path], 
                       description: Optional[str] = None) -> bool:
        """Enroll employee with face images."""
        if not image_files:
            logger.error("No image files provided")
            return False
        
        try:
            enroll_url = f"{self.backend_url}/api/employees/{employee_id}/faces"
            
            # Prepare multipart form data
            files = []
            for i, image_file in enumerate(image_files):
                with open(image_file, 'rb') as f:
                    files.append(('images', (image_file.name, f.read(), 'image/jpeg')))
            
            data = {}
            if description:
                data['description'] = description
            
            logger.info("Enrolling %d images for employee %s...", len(image_files), employee_id)
            
            response = self.session.post(enroll_url, files=files, data=data)
            
            if response.status_code == 200:
                logger.info("Enrollment successful: %s", response.text)
                return True
            else:
                logger.error("Enrollment failed: %s", response.text)
                return False
                
        except Exception as e:
            logger.error("Enrollment error: %s", str(e))
            return False
    
    def verify_enrollment(self, employee_id: str) -> bool:
        """Verify that employee has face templates."""
        try:
            templates_url = f"{self.backend_url}/api/employees/{employee_id}/faces"
            response = self.session.get(templates_url)
            
            if response.status_code == 200:
                templates = response.json()
                template_count = len(templates)
                logger.info("Employee has %d face templates", template_count)
                return template_count > 0
            else:
                logger.warning("Could not verify enrollment: %s", response.text)
                return False
                
        except Exception as e:
            logger.error("Verification error: %s", str(e))
            return False


def main():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Enroll employee with face images",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python enroll_cli.py --employee-code E1001 --path ./samples/employee_E1001
  python enroll_cli.py --employee-code E1001 --path ./face1.jpg --description "Profile photo"
  python enroll_cli.py --employee-code E1001 --path ./images/ --backend-url http://localhost:8080
        """
    )
    
    parser.add_argument('--employee-code', required=True, 
                       help='Employee code (e.g., E1001)')
    parser.add_argument('--path', required=True, type=Path,
                       help='Path to image file or directory containing images')
    parser.add_argument('--backend-url', default='http://localhost:8080',
                       help='Backend API URL (default: http://localhost:8080)')
    parser.add_argument('--username', default='admin',
                       help='Username for authentication (default: admin)')
    parser.add_argument('--password', default='admin123',
                       help='Password for authentication (default: admin123)')
    parser.add_argument('--description', 
                       help='Optional description for the enrollment')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Initialize CLI tool
    cli = EmployeeEnrollmentCLI(args.backend_url)
    
    print(f"🚀 Employee Face Enrollment CLI")
    print(f"📍 Backend: {args.backend_url}")
    print(f"👤 Employee: {args.employee_code}")
    print(f"📁 Path: {args.path}")
    print()
    
    # Step 1: Authenticate
    print("🔐 Authenticating...")
    if not cli.authenticate(args.username, args.password):
        print("❌ Authentication failed")
        sys.exit(1)
    print("✅ Authentication successful")
    
    # Step 2: Find employee
    print(f"🔍 Finding employee {args.employee_code}...")
    employee = cli.find_employee(args.employee_code)
    if not employee:
        print("❌ Employee not found")
        sys.exit(1)
    print(f"✅ Found employee: {employee['fullName']}")
    
    # Step 3: Get image files
    print("📸 Collecting image files...")
    image_files = cli.get_image_files(args.path)
    if not image_files:
        print("❌ No image files found")
        sys.exit(1)
    
    print(f"✅ Found {len(image_files)} image files:")
    for img_file in image_files:
        print(f"   - {img_file.name}")
    print()
    
    # Step 4: Enroll faces
    print("🎯 Enrolling face templates...")
    success = cli.enroll_employee(
        employee['id'], 
        image_files, 
        args.description
    )
    
    if not success:
        print("❌ Enrollment failed")
        sys.exit(1)
    
    print("✅ Enrollment completed successfully")
    
    # Step 5: Verify enrollment
    print("🔍 Verifying enrollment...")
    if cli.verify_enrollment(employee['id']):
        print("✅ Enrollment verified - employee has face templates")
    else:
        print("⚠️  Could not verify enrollment")
    
    print()
    print("🎉 Face enrollment completed successfully!")


if __name__ == "__main__":
    main()

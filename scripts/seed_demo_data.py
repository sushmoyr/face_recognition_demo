#!/usr/bin/env python3
"""
Seed demo data into the attendance system.

Creates sample employees, shifts, and devices for testing.
"""

import json
import sys
from datetime import datetime, timedelta

import requests


def main():
    backend_url = "http://localhost:8080"
    
    print("Seeding demo data...")
    
    try:
        # Create demo employees
        employees = [
            {
                "employeeCode": "EMP001",
                "firstName": "John",
                "lastName": "Doe",
                "email": "john.doe@company.com",
                "department": "Engineering",
                "position": "Software Engineer"
            },
            {
                "employeeCode": "EMP002", 
                "firstName": "Jane",
                "lastName": "Smith",
                "email": "jane.smith@company.com",
                "department": "HR",
                "position": "HR Manager"
            },
            {
                "employeeCode": "EMP003",
                "firstName": "Mike",
                "lastName": "Johnson", 
                "email": "mike.johnson@company.com",
                "department": "Marketing",
                "position": "Marketing Specialist"
            }
        ]
        
        print("Creating employees...")
        for emp in employees:
            response = requests.post(f"{backend_url}/api/employees", json=emp)
            if response.status_code in [201, 409]:  # Created or already exists
                print(f"  ✓ {emp['employeeCode']}: {emp['firstName']} {emp['lastName']}")
            else:
                print(f"  ✗ Failed to create {emp['employeeCode']}: {response.status_code}")
        
        # Create demo devices
        devices = [
            {
                "deviceCode": "CAM001",
                "name": "Main Entrance Camera",
                "location": "Main Entrance",
                "deviceType": "CAMERA"
            },
            {
                "deviceCode": "CAM002",
                "name": "Back Door Camera", 
                "location": "Back Entrance",
                "deviceType": "CAMERA"
            },
            {
                "deviceCode": "TERM001",
                "name": "Reception Terminal",
                "location": "Reception Desk", 
                "deviceType": "TERMINAL"
            }
        ]
        
        print("\nCreating devices...")
        for device in devices:
            response = requests.post(f"{backend_url}/api/devices", json=device)
            if response.status_code in [201, 409]:  # Created or already exists
                print(f"  ✓ {device['deviceCode']}: {device['name']}")
            else:
                print(f"  ✗ Failed to create {device['deviceCode']}: {response.status_code}")
        
        # Create demo shifts (if needed)
        shifts = [
            {
                "name": "Morning Shift",
                "startTime": "08:00:00",
                "endTime": "17:00:00", 
                "gracePeriodMinutes": 15,
                "timezone": "Asia/Dhaka"
            },
            {
                "name": "Night Shift",
                "startTime": "20:00:00",
                "endTime": "05:00:00",
                "gracePeriodMinutes": 15,
                "isOvernight": True,
                "timezone": "Asia/Dhaka"
            }
        ]
        
        print("\nCreating shifts...")
        for shift in shifts:
            response = requests.post(f"{backend_url}/api/shifts", json=shift)
            if response.status_code in [201, 409]:  # Created or already exists
                print(f"  ✓ {shift['name']}")
            else:
                print(f"  ✗ Failed to create {shift['name']}: {response.status_code}")
        
        print("\n✅ Demo data seeded successfully!")
        print("\nNext steps:")
        print("1. Enroll employee faces using: python scripts/enroll_cli.py")
        print("2. Start edge service to begin recognition")
        print("3. View attendance at: http://localhost:8080/api/attendance/daily")
        
    except requests.RequestException as e:
        print(f"❌ Error communicating with backend: {e}")
        print("Make sure the backend service is running at http://localhost:8080")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()

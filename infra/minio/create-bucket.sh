#!/bin/bash
# Script to create MinIO bucket and set up lifecycle policies
# This script is run automatically by the createbuckets service in docker-compose

set -e

echo "Configuring MinIO..."

# Set MinIO client alias
mc alias set myminio http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}

# Create bucket for face snapshots
echo "Creating attendance-faces bucket..."
mc mb myminio/attendance-faces || echo "Bucket already exists"

# Set public read policy for snapshots (adjust as needed for security)
echo "Setting bucket policy..."
mc policy set public myminio/attendance-faces

# Set lifecycle policy to delete objects after 90 days
echo "Setting lifecycle policy..."
mc ilm add --expiry-days 90 myminio/attendance-faces

echo "MinIO configuration completed successfully!"

# Create additional buckets if needed
# mc mb myminio/attendance-reports || echo "Reports bucket already exists"
# mc mb myminio/attendance-backups || echo "Backups bucket already exists"

# Set up different policies for different buckets
# mc policy set private myminio/attendance-reports
# mc policy set private myminio/attendance-backups

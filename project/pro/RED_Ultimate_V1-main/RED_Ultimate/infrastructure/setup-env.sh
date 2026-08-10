#!/bin/bash
echo "🔴 RED: Initializing Sovereignty Environment..."

# 1. Create MinIO Buckets for Media System
# Using mc (MinIO Client) to ensure buckets exist
mc alias set local http://localhost:9000 admin password
mc mb local/red-media
mc mb local/red-backups
mc policy set public local/red-media

# 2. Initialize PostgreSQL Schemas
# (Spring Boot handles this via Flyway/Hibernate, but we ensure DB exists)
psql -h db -U admin -d postgres -c "CREATE DATABASE red_sovereign;"

echo "✅ RED: Environment Setup Complete."

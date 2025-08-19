# Face Recognition Attendance System - Makefile
# Provides convenient commands for development and deployment

.PHONY: help up down logs migrate seed test clean backend edge dev-deps test-backend test-edge build-all

# Default target
help:
	@echo "Face Recognition Attendance System - Available Commands:"
	@echo ""
	@echo "  make up           - Start all services"
	@echo "  make down         - Stop all services"
	@echo "  make logs         - View logs from all services"
	@echo "  make migrate      - Run database migrations"
	@echo "  make seed         - Seed demo data"
	@echo "  make test         - Run all tests"
	@echo "  make clean        - Clean up containers and volumes"
	@echo "  make backend      - Build backend service"
	@echo "  make edge         - Build edge service"
	@echo "  make dev-deps     - Start only dependencies (DB, MinIO)"
	@echo "  make test-backend - Run backend tests only"
	@echo "  make test-edge    - Run edge tests only"
	@echo "  make build-all    - Build all Docker images"
	@echo ""

# Start all services
up:
	@echo "Starting all services..."
	docker-compose up -d
	@echo "Services started. Access:"
	@echo "  Backend API: http://localhost:8080"
	@echo "  MinIO Console: http://localhost:9001"
	@echo "  Database: localhost:5432"

# Stop all services
down:
	@echo "Stopping all services..."
	docker-compose down

# View logs
logs:
	docker-compose logs -f

# Run database migrations
migrate:
	@echo "Running database migrations..."
	docker-compose exec -T backend ./mvnw flyway:migrate

# Seed demo data
seed:
	@echo "Seeding demo data..."
	docker-compose exec -T backend python /app/scripts/seed_demo_data.py

# Run all tests
test: test-backend test-edge

# Clean up everything
clean:
	@echo "Cleaning up containers, volumes, and networks..."
	docker-compose down -v --remove-orphans
	docker system prune -f

# Build backend service
backend:
	@echo "Building backend service..."
	cd backend && ./mvnw clean package -DskipTests
	docker-compose build backend

# Build edge service  
edge:
	@echo "Building edge service..."
	docker-compose build edge

# Start only dependencies for development
dev-deps:
	@echo "Starting development dependencies..."
	docker-compose up -d postgres minio createbuckets

# Run backend tests
test-backend:
	@echo "Running backend tests..."
	cd backend && ./mvnw test

# Run attendance policy tests specifically
test-attendance:
	@echo "Running attendance policy tests..."
	cd backend && ./mvnw test -Dtest=AttendancePolicyServiceTest

# Run edge tests
test-edge:
	@echo "Running edge tests..."
	cd edge && python -m pytest tests/ -v

# Build all Docker images
build-all:
	@echo "Building all Docker images..."
	docker-compose build --parallel

# Development mode - start with hot reload
dev:
	@echo "Starting in development mode..."
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up

# Check service health
health:
	@echo "Checking service health..."
	@echo "Backend Health:"
	@curl -f http://localhost:8080/actuator/health || echo "Backend not healthy"
	@echo ""
	@echo "MinIO Health:"
	@curl -f http://localhost:9000/minio/health/ready || echo "MinIO not healthy"

# Show service status
status:
	@echo "Service Status:"
	docker-compose ps

# Restart specific service
restart-backend:
	docker-compose restart backend

restart-edge:
	docker-compose restart edge

# View specific service logs
logs-backend:
	docker-compose logs -f backend

logs-edge:
	docker-compose logs -f edge

logs-db:
	docker-compose logs -f postgres

# Database operations
db-shell:
	docker-compose exec postgres psql -U attendance_user -d attendance_db

# MinIO operations
minio-shell:
	docker-compose exec minio sh

# Backup database
backup-db:
	@echo "Creating database backup..."
	docker-compose exec -T postgres pg_dump -U attendance_user attendance_db > backup_$(shell date +%Y%m%d_%H%M%S).sql

# Format code
format:
	@echo "Formatting code..."
	cd backend && ./mvnw spring-javaformat:apply
	cd edge && python -m black . && python -m isort .

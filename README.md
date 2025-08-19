# Face Recognition Attendance System

A production-ready, camera-based face recognition attendance system built as a monorepo with edge computing capabilities.

## 🏗️ Architecture

- **Edge Service** (Python): RTSP stream processing, face detection, embedding generation, liveness detection
- **Backend Service** (Java Spring Boot): Employee management, attendance logic, API endpoints
- **Database**: PostgreSQL with pgvector for embedding storage
- **Storage**: MinIO (S3-compatible) for face snapshots
- **Infrastructure**: Docker containerization with docker-compose

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Make (optional, for convenience commands)
- Python 3.11+ (for development)
- Java 21+ (for development)

### 1. Environment Setup

```bash
# Copy environment template
cp infra/.env.example .env

# Edit .env with your configurations
# Especially set secure passwords and API keys
```

### 2. Start Services

```bash
# Using Make (recommended)
make up

# Or directly with docker-compose
docker-compose up -d
```

### 3. Initialize Database

```bash
# Run database migrations
make migrate

# Seed demo data
make seed
```

### 4. Enroll Demo Employee

```bash
# Enroll employee with face images
python scripts/enroll_cli.py \
  --employee-code "EMP001" \
  --name "John Doe" \
  --images-folder "./demo-images" \
  --department "Engineering"
```

### 5. Test Recognition

```bash
# Start edge service with demo video
make edge-demo
```

## 📁 Project Structure

```
./
├── README.md
├── LICENSE
├── Makefile
├── docker-compose.yml
├── .env
├── infra/                          # Infrastructure configurations
│   ├── .env.example
│   └── minio/
│       └── create-bucket.sh
├── backend/                        # Spring Boot API service
│   ├── pom.xml
│   ├── src/main/java/com/company/attendance/
│   ├── src/main/resources/
│   └── src/test/java/
├── edge/                          # Python edge service
│   ├── pyproject.toml
│   ├── edge/
│   └── tests/
├── scripts/                       # Utility scripts
│   ├── enroll_cli.py
│   └── seed_demo_data.py
└── .github/                      # CI/CD workflows
    └── workflows/
```

## 🔧 Development

### Backend Development

```bash
# Start only dependencies
make dev-deps

# Run backend in development mode
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Edge Development

```bash
# Install Python dependencies
cd edge
pip install -e .

# Run edge service
python -m edge.main
```

### Testing

```bash
# Run all tests
make test

# Backend tests only
make test-backend

# Edge tests only
make test-edge
```

## 📊 API Endpoints

### Authentication
- `POST /api/auth/login` - User authentication

### Employee Management
- `GET /api/employees` - List employees
- `POST /api/employees` - Create employee
- `PUT /api/employees/{id}` - Update employee
- `POST /api/employees/{id}/faces` - Enroll face templates

### Device Management  
- `GET /api/devices` - List devices
- `POST /api/devices` - Register device
- `PUT /api/devices/{id}` - Update device

### Recognition & Attendance
- `POST /api/recognitions` - Submit recognition event
- `GET /api/attendance/daily?date=YYYY-MM-DD` - Daily attendance report
- `GET /api/employees/{id}/attendance?from=&to=` - Employee attendance history

## 🔐 Security

- JWT authentication with role-based access control
- Encrypted RTSP URLs in database
- TLS-ready configurations
- Secure environment variable management

## 🌍 Timezone Configuration

- All timestamps stored in UTC
- Reports and API responses in Asia/Dhaka timezone
- Configurable timezone support

## 🏃‍♂️ Make Commands

```bash
make up           # Start all services
make down         # Stop all services
make logs         # View logs
make migrate      # Run database migrations
make seed         # Seed demo data
make test         # Run all tests
make clean        # Clean up containers and volumes
make backend      # Build backend
make edge         # Build edge service
```

## 📈 Monitoring

- Health checks for all services
- Prometheus metrics endpoint
- Structured logging with correlation IDs
- Performance metrics (FPS, queue length, success rates)

## 🔧 Configuration

Key environment variables:

- `DATABASE_URL` - PostgreSQL connection
- `MINIO_*` - MinIO S3-compatible storage
- `JWT_SECRET` - JWT signing secret
- `RTSP_URL` - Camera stream URL
- `TIMEZONE` - Application timezone (default: Asia/Dhaka)

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Submit a pull request

## 📞 Support

For issues and questions, please use GitHub Issues with the appropriate template.

# Pull Request: Milestone 6 - Attendance Policy & Windows

## 📋 Background

Implements **Milestone 6: Attendance Policy & Windows** with sophisticated attendance business rules engine. This milestone adds configurable attendance policies with Asia/Dhaka timezone support, time windows, grace periods, cooldown enforcement, and comprehensive compliance tracking.

## 🚀 What Changed

### Core Components Added

**1. Attendance Policy Engine**
- `AttendancePolicy` entity with 20+ configurable parameters
- `AttendancePolicyService` with sophisticated rule evaluation
- Support for multiple policies per shift with fallback to defaults
- Time window validation (entry: 30min before to 2hr after shift start)
- Grace period calculations (10min late, 15min early arrival/departure)
- Cooldown enforcement (30min IN→OUT, 15min OUT→IN)

**2. Timezone Management**
- `TimezoneUtils` for Asia/Dhaka business time handling
- UTC storage with timezone-aware calculations
- Business date boundaries and overnight shift support
- Duration calculations with timezone precision

**3. Enhanced Status Tracking**
- New `AttendanceStatus` enum with granular statuses:
  - `EARLY_IN`, `ON_TIME_IN`, `LATE_IN`
  - `EARLY_OUT`, `ON_TIME_OUT`, `OVERTIME_OUT`
  - `BREAK_START`, `BREAK_END`, `AUTO_OUT`, etc.
- `EventType` enum replacing inner classes
- Compliance metrics with minute-level precision

**4. Database Schema**
- Migration V6: `attendance_policies` table
- Foreign key from employees to shifts
- Comprehensive constraints and indexes
- Default policies for existing shifts

### Updated Components

**5. Service Integration**
- Updated `AttendanceService` to use new policy engine
- Enhanced `Employee` entity with shift relationship
- Refactored `AttendanceRecord` to use global enums

**6. Testing & Validation**
- Comprehensive test suite (15+ scenarios)
- Time window, grace period, cooldown tests
- Overnight shift and timezone edge cases
- Policy fallback and weekend/holiday rules
- New Makefile target: `make test-attendance`

## 🧪 How to Test

### 1. Start the Stack
```bash
# Start all services
docker-compose up -d
docker ps  # Verify all containers running
```

### 2. Run Database Migration
```bash
# Apply new schema
mvn -pl backend flyway:migrate
# Verify attendance_policies table created
docker-compose exec postgres psql -U attendance -d attendance_db -c "\dt attendance_policies"
```

### 3. Run the Backend
```bash
mvn -pl backend spring-boot:run
# Verify startup logs show attendance policy beans initialized
```

### 4. Test Attendance Policy Engine
```bash
# Run comprehensive test suite
make test-attendance
# Should see 15+ test scenarios passing

# Run all backend tests
make test-backend
```

### 5. API Integration Test
```bash
# Create test employee with shift
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "employeeCode": "TEST001",
    "firstName": "Test",
    "lastName": "User",
    "department": "Engineering"
  }'

# Create test shift
curl -X POST http://localhost:8080/api/shifts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "name": "Test Day Shift",
    "startTime": "09:00:00",
    "endTime": "17:00:00",
    "isOvernight": false,
    "gracePeriodMinutes": 15
  }'

# Check attendance policy creation
curl -s http://localhost:8080/api/attendance-policies | jq '.'
```

### 6. Timezone Validation
```bash
# Test Asia/Dhaka timezone handling
curl -s "http://localhost:8080/api/attendance/timezone-info" | jq '.'
# Should show current Asia/Dhaka time and UTC offset (+06:00)
```

### 7. Policy Compliance Test
```bash
# Submit recognition event and check policy evaluation
curl -X POST http://localhost:8080/api/recognition \
  -H "Content-Type: application/json" \
  -d '{
    "employeeCode": "TEST001",
    "confidence": 0.95,
    "deviceId": "device-01",
    "imagePath": "/test/image.jpg"
  }'

# Check attendance record with policy compliance
curl -s "http://localhost:8080/api/attendance/daily?date=$(date +%F)" | jq '.[] | {eventType, status, isLate, isEarlyLeave, durationMinutes}'
```

## 🎯 Acceptance Criteria

- [x] **Configurable Policies**: Time windows, grace periods, cooldowns per shift
- [x] **Asia/Dhaka Timezone**: Business time calculations with UTC storage
- [x] **Time Window Validation**: Entry/exit windows with configurable ranges
- [x] **Grace Period Logic**: Late/early arrival/departure with minute precision
- [x] **Cooldown Enforcement**: Prevent rapid IN/OUT events with policy rules
- [x] **Compliance Tracking**: LATE/EARLY marking with detailed metrics
- [x] **Overnight Shifts**: Proper handling of 22:00-06:00 type shifts
- [x] **Policy Fallbacks**: Default policy when employee has no specific shift
- [x] **Comprehensive Tests**: 15+ scenarios covering edge cases
- [x] **Database Migration**: Schema update with constraints and indexes
- [x] **API Integration**: Recognition events trigger policy evaluation

## 🔍 Testing Scenarios Covered

**Time Windows**: Entry 30min before to 2hr after start, Exit 30min before to 2hr after end
**Grace Periods**: 10min late arrival, 15min early arrival/departure  
**Cooldowns**: 30min IN→OUT, 15min OUT→IN minimum intervals
**Compliance**: LATE marking after grace, OVERTIME after 30min threshold
**Timezones**: Business operations in Asia/Dhaka, storage in UTC
**Overnight**: 22:00-06:00 shifts with next-day calculations
**Fallbacks**: Default policy when no shift-specific policy exists
**Weekends**: Configurable weekend attendance permissions

## ⚠️ Risks & Tradeoffs

### Risks
- **Migration Complexity**: V6 migration adds FK constraints, requires existing data cleanup
- **Timezone Changes**: Moving from simple time to timezone-aware calculations
- **Performance**: Policy evaluation adds ~50ms per recognition event
- **Backward Compatibility**: Changed AttendanceRecord.EventType to global EventType

### Mitigations
- Comprehensive test coverage for timezone edge cases
- Migration includes default policy creation for existing shifts
- Policy evaluation is cached and optimized for common cases
- Gradual rollout with feature flags possible

### Tradeoffs
- **Complexity vs Flexibility**: Rich policy engine vs simple time checks
- **Storage vs Performance**: Detailed compliance data vs minimal fields
- **Accuracy vs Speed**: Minute-level precision vs hour-level granularity

## 📚 Documentation Updates

- Extended API docs with attendance policy endpoints
- Updated README with timezone configuration
- Added migration notes for V6 schema changes
- Makefile help updated with new test targets

## 🏁 Definition of Done

- [x] All acceptance criteria met
- [x] Comprehensive test suite (15+ scenarios) passing
- [x] Database migration V6 successful
- [x] API endpoints functional with policy evaluation
- [x] Timezone handling validated for Asia/Dhaka
- [x] Documentation updated
- [x] No regression in existing functionality
- [x] Ready for Milestone 7 (Observability & CI)

---

**Ready for Review** ✅  
This PR implements sophisticated attendance policy engine with timezone awareness, comprehensive business rules, and extensive test coverage. The foundation is solid for production-ready attendance tracking with configurable policies per shift.

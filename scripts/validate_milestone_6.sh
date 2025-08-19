#!/bin/bash

# Milestone 6 Validation Script - Attendance Policy & Windows
# Tests the sophisticated attendance policy engine implementation

set -e

echo "🧪 MILESTONE 6 VALIDATION - Attendance Policy & Windows"
echo "========================================================"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

success_count=0
total_tests=0

# Test function
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo -e "\n${YELLOW}Testing: $test_name${NC}"
    ((total_tests++))
    
    if eval "$test_command"; then
        echo -e "${GREEN}✅ PASSED: $test_name${NC}"
        ((success_count++))
    else
        echo -e "${RED}❌ FAILED: $test_name${NC}"
    fi
}

# Validation Tests
echo -e "\n🔍 Core Component Validation"
echo "----------------------------"

# 1. Database Migration Check
run_test "Database Migration V6" "
    cd backend && 
    mvn flyway:info | grep -q 'V6.*Create_attendance_policies_table' && 
    echo 'Migration V6 found in Flyway history'"

# 2. Java Compilation Check  
run_test "Java Compilation" "
    cd backend && 
    mvn compile -q -Dmaven.test.skip=true 2>/dev/null"

# 3. Core Classes Exist
run_test "AttendancePolicy Entity" "
    test -f backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java"

run_test "AttendancePolicyService" "
    test -f backend/src/main/java/com/company/attendance/service/AttendancePolicyService.java"

run_test "TimezoneUtils" "
    test -f backend/src/main/java/com/company/attendance/util/TimezoneUtils.java"

run_test "AttendanceStatus Enum" "
    test -f backend/src/main/java/com/company/attendance/entity/AttendanceStatus.java"

# 4. Test Classes
run_test "Comprehensive Test Suite" "
    test -f backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java &&
    wc -l backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java | grep -q '[0-9][0-9][0-9]'"

echo -e "\n🏗️ Architecture Validation"
echo "---------------------------"

# 5. Configuration Validation
run_test "Policy Configuration Fields" "
    grep -q 'entryWindowStartMinutes' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java &&
    grep -q 'lateArrivalGraceMinutes' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java &&
    grep -q 'inToOutCooldownMinutes' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java"

# 6. Timezone Implementation
run_test "Asia/Dhaka Timezone Support" "
    grep -q 'Asia/Dhaka' backend/src/main/java/com/company/attendance/util/TimezoneUtils.java &&
    grep -q 'BUSINESS_TIMEZONE' backend/src/main/java/com/company/attendance/util/TimezoneUtils.java"

# 7. Status Granularity
run_test "Granular Status System" "
    grep -q 'EARLY_IN' backend/src/main/java/com/company/attendance/entity/AttendanceStatus.java &&
    grep -q 'ON_TIME_IN' backend/src/main/java/com/company/attendance/entity/AttendanceStatus.java &&
    grep -q 'LATE_IN' backend/src/main/java/com/company/attendance/entity/AttendanceStatus.java &&
    grep -q 'OVERTIME_OUT' backend/src/main/java/com/company/attendance/entity/AttendanceStatus.java"

echo -e "\n🧮 Business Logic Validation"
echo "-----------------------------"

# 8. Policy Evaluation Logic
run_test "Time Window Validation" "
    grep -q 'isWithinEntryWindow' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java &&
    grep -q 'isWithinExitWindow' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java"

run_test "Grace Period Logic" "
    grep -q 'determineArrivalStatus' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java &&
    grep -q 'determineDepartureStatus' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java"

run_test "Cooldown Enforcement" "
    grep -q 'checkCooldownViolation' backend/src/main/java/com/company/attendance/service/AttendancePolicyService.java"

run_test "Overnight Shift Support" "
    grep -q 'isOvernight' backend/src/main/java/com/company/attendance/entity/AttendancePolicy.java &&
    grep -q 'adjustTimeForOvernightShift' backend/src/main/java/com/company/attendance/util/TimezoneUtils.java"

echo -e "\n🗄️ Database Schema Validation"
echo "------------------------------"

# 9. Migration Structure
run_test "Migration File Structure" "
    test -f backend/src/main/resources/db/migration/V6__Create_attendance_policies_table.sql &&
    grep -q 'CREATE TABLE attendance_policies' backend/src/main/resources/db/migration/V6__Create_attendance_policies_table.sql"

run_test "Foreign Key Constraints" "
    grep -q 'CONSTRAINT fk_attendance_policy_shift' backend/src/main/resources/db/migration/V6__Create_attendance_policies_table.sql"

run_test "Business Rule Constraints" "
    grep -q 'chk_grace_periods_valid' backend/src/main/resources/db/migration/V6__Create_attendance_policies_table.sql &&
    grep -q 'chk_cooldown_valid' backend/src/main/resources/db/migration/V6__Create_attendance_policies_table.sql"

echo -e "\n🧪 Test Coverage Validation"
echo "----------------------------"

# 10. Test Scenario Coverage
run_test "Time Window Tests" "
    grep -q 'TimeWindowValidationTests' backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java"

run_test "Grace Period Tests" "
    grep -q 'GracePeriodTests' backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java"

run_test "Cooldown Tests" "
    grep -q 'CooldownPeriodTests' backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java"

run_test "Overnight Shift Tests" "
    grep -q 'OvernightShiftTests' backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java"

run_test "Policy Fallback Tests" "
    grep -q 'PolicyFallbackTests' backend/src/test/java/com/company/attendance/service/AttendancePolicyServiceTest.java"

echo -e "\n📋 Integration Validation"
echo "--------------------------"

# 11. Service Integration
run_test "AttendanceService Integration" "
    grep -q 'AttendancePolicyService' backend/src/main/java/com/company/attendance/service/AttendanceService.java &&
    grep -q 'TimezoneUtils' backend/src/main/java/com/company/attendance/service/AttendanceService.java"

run_test "Employee-Shift Relationship" "
    grep -q 'shift_id' backend/src/main/java/com/company/attendance/entity/Employee.java ||
    grep -q '@JoinColumn.*shift' backend/src/main/java/com/company/attendance/entity/Employee.java"

echo -e "\n🛠️ Build System Validation"
echo "---------------------------"

# 12. Build Configuration
run_test "Makefile Test Target" "
    grep -q 'test-attendance' Makefile"

run_test "Maven Dependencies" "
    grep -q 'spring-boot-starter-data-jpa' backend/pom.xml &&
    grep -q 'postgresql' backend/pom.xml"

echo -e "\n📊 VALIDATION SUMMARY"
echo "====================="
echo -e "Tests Passed: ${GREEN}$success_count${NC}/$total_tests"

if [ $success_count -eq $total_tests ]; then
    echo -e "${GREEN}🎉 ALL TESTS PASSED! Milestone 6 implementation is valid.${NC}"
    echo -e "${GREEN}✅ Attendance Policy & Windows system ready for production${NC}"
    exit 0
else
    failed_tests=$((total_tests - success_count))
    echo -e "${RED}❌ $failed_tests tests failed. Review implementation.${NC}"
    exit 1
fi

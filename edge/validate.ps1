# Face Recognition Edge Service - Validation Script for Windows
# 
# This script validates the edge service components on Windows using PowerShell

param(
    [Parameter(HelpMessage="Run demo mode instead of validation")]
    [switch]$Demo,
    
    [Parameter(HelpMessage="Skip backend connectivity check")]
    [switch]$SkipBackend,
    
    [Parameter(HelpMessage="Video source for testing")]
    [string]$VideoSource = "0"
)

Write-Host "🔍 Face Recognition Edge Service - Windows Validation" -ForegroundColor Blue
Write-Host "=" * 60 -ForegroundColor Blue

# Check Python installation
Write-Host "`n📋 Checking Prerequisites..." -ForegroundColor Yellow

try {
    $pythonVersion = python --version 2>&1
    Write-Host "✅ Python: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Python not found. Please install Python 3.11+" -ForegroundColor Red
    exit 1
}

# Check if we're in the correct directory
if (-not (Test-Path "edge/main.py")) {
    Write-Host "❌ Please run this script from the edge service root directory" -ForegroundColor Red
    exit 1
}

# Check virtual environment
if ($env:VIRTUAL_ENV) {
    Write-Host "✅ Virtual environment: $env:VIRTUAL_ENV" -ForegroundColor Green
} else {
    Write-Host "⚠️  No virtual environment detected. Consider using one." -ForegroundColor Yellow
}

# Check backend connectivity (unless skipped)
if (-not $SkipBackend) {
    Write-Host "`n🌐 Checking Backend Connectivity..." -ForegroundColor Yellow
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 5
        Write-Host "✅ Backend is available and healthy" -ForegroundColor Green
    } catch {
        Write-Host "❌ Backend not available at http://localhost:8080" -ForegroundColor Red
        Write-Host "   Make sure the Spring Boot backend is running" -ForegroundColor Yellow
        Write-Host "   Or use -SkipBackend to skip this check" -ForegroundColor Yellow
    }
}

# Check MinIO (optional)
Write-Host "`n💾 Checking MinIO Storage..." -ForegroundColor Yellow

try {
    $response = Invoke-RestMethod -Uri "http://localhost:9000/minio/health/live" -Method Get -TimeoutSec 5
    Write-Host "✅ MinIO is available" -ForegroundColor Green
} catch {
    Write-Host "⚠️  MinIO not available - snapshot uploads will be disabled" -ForegroundColor Yellow
}

# Install/check dependencies
Write-Host "`n📦 Checking Dependencies..." -ForegroundColor Yellow

$requirements = Get-Content "requirements.txt" | Where-Object { $_ -match "^[a-zA-Z]" }
$missingPackages = @()

foreach ($req in $requirements) {
    $packageName = ($req -split ">=|==|~=")[0]
    
    try {
        python -c "import $($packageName.Replace('-', '_'))" 2>$null
        Write-Host "✅ $packageName" -ForegroundColor Green
    } catch {
        Write-Host "❌ $packageName (missing)" -ForegroundColor Red
        $missingPackages += $packageName
    }
}

if ($missingPackages.Count -gt 0) {
    Write-Host "`n🔧 Installing missing dependencies..." -ForegroundColor Yellow
    pip install -r requirements.txt
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to install dependencies" -ForegroundColor Red
        exit 1
    }
}

# Run validation or demo
if ($Demo) {
    Write-Host "`n🎭 Running Demo Mode..." -ForegroundColor Cyan
    Write-Host "This will create a test video and process it through the pipeline" -ForegroundColor Cyan
    Write-Host ""
    
    python demo.py
} else {
    Write-Host "`n🧪 Running Validation Tests..." -ForegroundColor Cyan
    Write-Host "This will test all pipeline components" -ForegroundColor Cyan
    Write-Host ""
    
    python validate_edge.py
}

$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "`n🎉 Validation completed successfully!" -ForegroundColor Green
    Write-Host "The edge service is ready to use." -ForegroundColor Green
    
    Write-Host "`n📚 Next Steps:" -ForegroundColor Blue
    Write-Host "1. Start the edge service:" -ForegroundColor White
    Write-Host "   python -m edge.main --source $VideoSource" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "2. Or run with RTSP stream:" -ForegroundColor White
    Write-Host "   python -m edge.main --source `"rtsp://username:password@ip:port/stream`"" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "3. Enable debug mode:" -ForegroundColor White
    Write-Host "   python -m edge.main --source $VideoSource --debug" -ForegroundColor Cyan
    
} else {
    Write-Host "`n❌ Validation failed!" -ForegroundColor Red
    Write-Host "Please check the error messages above." -ForegroundColor Red
}

Write-Host "`nPress any key to exit..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

exit $exitCode

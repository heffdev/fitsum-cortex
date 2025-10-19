# Fitsum Cortex Development Startup Script (PowerShell)

Write-Host "🚀 Starting Fitsum Cortex development environment..." -ForegroundColor Green

# Check if Docker is running
try {
    docker info | Out-Null
} catch {
    Write-Host "❌ Docker is not running. Please start Docker and try again." -ForegroundColor Red
    exit 1
}

# Start infrastructure services
Write-Host "📦 Starting infrastructure (Postgres, Ollama, Jaeger, Prometheus)..." -ForegroundColor Cyan
Set-Location infra/docker
docker-compose up -d

# Wait for Postgres to be ready
Write-Host "⏳ Waiting for Postgres to be ready..." -ForegroundColor Yellow
do {
    $ready = docker exec cortex-postgres pg_isready -U cortex 2>$null
    Start-Sleep -Seconds 1
} while ($LASTEXITCODE -ne 0)
Write-Host "✅ Postgres is ready" -ForegroundColor Green

# Pull Ollama model if not exists
Write-Host "🤖 Checking Ollama model..." -ForegroundColor Cyan
$modelExists = docker exec cortex-ollama ollama list | Select-String "qwen3"
if (-not $modelExists) {
    Write-Host "Pulling qwen3 model (this may take a few minutes)..."
    docker exec cortex-ollama ollama pull qwen3:4b-instruct-q4_K_M
}

Write-Host ""
Write-Host "✅ Infrastructure is ready!" -ForegroundColor Green
Write-Host ""
Write-Host "📊 Services available at:" -ForegroundColor Cyan
Write-Host "  - Postgres:    localhost:5432"
Write-Host "  - Ollama:      localhost:11234"
Write-Host "  - Jaeger UI:   http://localhost:16686"
Write-Host "  - Prometheus:  http://localhost:9090"
Write-Host ""
Write-Host "🔧 To start the application:" -ForegroundColor Yellow
Write-Host "  cd api"
Write-Host "  mvn spring-boot:run"
Write-Host ""
Write-Host "🛑 To stop infrastructure:" -ForegroundColor Yellow
Write-Host "  cd infra/docker"
Write-Host "  docker-compose down"


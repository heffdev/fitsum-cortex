#!/bin/bash

# Fitsum Cortex Development Startup Script

set -e

echo "🚀 Starting Fitsum Cortex development environment..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Start infrastructure services
echo "📦 Starting infrastructure (Postgres, Ollama, Jaeger, Prometheus)..."
cd infra/docker
docker-compose up -d

# Wait for Postgres to be ready
echo "⏳ Waiting for Postgres to be ready..."
until docker exec cortex-postgres pg_isready -U cortex > /dev/null 2>&1; do
    sleep 1
done
echo "✅ Postgres is ready"

# Pull Ollama model if not exists
echo "🤖 Checking Ollama model..."
docker exec cortex-ollama ollama list | grep qwen3 || \
    docker exec cortex-ollama ollama pull qwen3:4b-instruct-q4_K_M

echo ""
echo "✅ Infrastructure is ready!"
echo ""
echo "📊 Services available at:"
echo "  - Postgres:    localhost:5432"
echo "  - Ollama:      localhost:11234"
echo "  - Jaeger UI:   http://localhost:16686"
echo "  - Prometheus:  http://localhost:9090"
echo ""
echo "🔧 To start the application:"
echo "  cd api && mvn spring-boot:run"
echo ""
echo "🛑 To stop infrastructure:"
echo "  cd infra/docker && docker-compose down"


# Make It So - Fitsum Cortex Setup Guide

This guide will get you from zero to a fully operational Fitsum Cortex knowledge system.

## ⚡ Quick Start (5 minutes)

### 1. Prerequisites Check

```powershell
# Verify Java 21+
java -version

# Verify Maven
mvn -version

# Verify Docker
docker --version
```

### 2. Set Environment Variables

**Windows PowerShell:**
```powershell
$env:OPENAI_API_KEY = "sk-your-key-here"
```

**Linux/Mac:**
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

### 3. Start Infrastructure

```powershell
cd fitsum-cortex
.\scripts\dev.ps1
```

Wait for:
- ✅ Postgres is ready
- ✅ Ollama model downloaded

### 4. Build & Run

```bash
# Build all modules
mvn clean install

# Start API server
cd api
mvn spring-boot:run
```

### 5. Verify

Open browser: http://localhost:8080/actuator/health

You should see:
```json
{"status":"UP"}
```

## 📚 Adding Your First Documents

### Option 1: Local Files

1. Create a test directory with some documents:
```bash
mkdir -p ~/cortex-test-docs
echo "# My First Document\n\nThis is a test document for Fitsum Cortex." > ~/cortex-test-docs/test.md
```

2. Ingest via API:
```bash
curl -X POST http://localhost:8080/v1/ingest/local-files/sync \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "path": "/Users/yourname/cortex-test-docs",
    "recursive": true
  }'
```

### Option 2: Direct Text

```bash
curl -X POST http://localhost:8080/v1/documents \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "title": "Product Roadmap Q4 2024",
    "content": "Q4 Priorities:\n1. Launch new RAG features\n2. Improve retrieval accuracy\n3. Add Gmail connector"
  }'
```

## 💬 Your First Question

```bash
curl -X POST http://localhost:8080/v1/ask \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "question": "What are the Q4 priorities?",
    "allowFallback": false
  }'
```

Expected response:
```json
{
  "answer": "The Q4 priorities are: 1) Launch new RAG features, 2) Improve retrieval accuracy, and 3) Add Gmail connector [Product Roadmap Q4 2024, Section 1]",
  "citations": [...],
  "confidence": "HIGH",
  "sensitivity": "NONE",
  "provider": "OPENAI",
  "traceId": "abc-123",
  "latencyMs": 2341
}
```

## 🖥️ Using the UI

Start the Vaadin UI:

```bash
cd ui
mvn spring-boot:run
```

Open: http://localhost:8082

Features:
- Real-time streaming answers
- Citation cards with document references
- Privacy indicators (🔒 for sensitive content)
- Trace ID footer for debugging

## 🧪 Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Evaluation framework
./scripts/eval.sh
```

## 📊 Monitoring

### Jaeger (Tracing)
http://localhost:16686

Search for traces:
- Service: `fitsum-cortex-api`
- Operation: `cortex.ask`

View:
- Full advisor chain execution
- Retrieval timings
- LLM call details

### Prometheus (Metrics)
http://localhost:9090

Query examples:
```promql
# Average latency
rate(cortex_ask_latency_ms_sum[5m]) / rate(cortex_ask_latency_ms_count[5m])

# Requests per second
rate(cortex_ask_total[1m])

# Token usage
rate(cortex_llm_tokens_total[5m])
```

## 🔐 Testing Privacy Features

### Sensitive Content Detection

```bash
curl -X POST http://localhost:8080/v1/ask \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "question": "What is John Doe\u0027s SSN? It is 123-45-6789.",
    "allowFallback": false
  }'
```

Check the response:
- `sensitivity: "PII"`
- `provider: "OLLAMA"` (routed to local model)

### Blocked Empty Context

```bash
curl -X POST http://localhost:8080/v1/ask \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "question": "Who won the 2024 World Series?",
    "allowFallback": false
  }'
```

Expected error:
```json
{
  "error": "No relevant context found in knowledge base. Enable fallback mode to use general knowledge."
}
```

With fallback enabled:
```bash
curl -X POST http://localhost:8080/v1/ask \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "question": "Who won the 2024 World Series?",
    "allowFallback": true
  }'
```

## 🛠️ Configuration

### Tuning Retrieval

Edit `api/src/main/resources/application.yaml`:

```yaml
cortex:
  retrieval:
    fts-top-k: 30       # Increase for more FTS results
    ann-top-k: 30       # Increase for more vector results
    rerank-top-k: 12    # Final chunk count
    similarity-threshold: 0.65  # Lower = more lenient
```

Restart API and re-run evaluation:
```bash
./scripts/eval.sh
```

### Adding Custom Sensitivity Patterns

```yaml
cortex:
  sensitivity:
    patterns:
      pii:
        - "\\b\\d{3}-\\d{2}-\\d{4}\\b"  # SSN
        - "\\bcredit card\\b.*\\d{4}"   # Credit card mentions
      confidential:
        - "\\bconfidential\\b"
        - "\\bNDA\\b"
```

## 🚨 Troubleshooting

### Postgres Not Starting

```powershell
cd infra/docker
docker-compose logs postgres
```

Common fix:
```powershell
docker-compose down -v
docker-compose up -d
```

### Ollama Model Not Found

```powershell
docker exec cortex-ollama ollama pull qwen3:4b-instruct-q4_K_M
```

### API Won't Start

Check logs:
```bash
cd api
mvn spring-boot:run
# Look for errors in console
```

Common issues:
- OPENAI_API_KEY not set
- Postgres not ready
- Port 8080 already in use

### Empty Retrieval Results

1. Verify documents are ingested:
```bash
curl -X GET http://localhost:8080/v1/documents \
  -u demo@fitsum.ai:demo
```

2. Check embeddings are generated:
```sql
psql -U cortex -d cortex -c "SELECT COUNT(*) FROM chunk WHERE embedding IS NOT NULL;"
```

## 📈 Next Steps

### 1. Add Gmail Connector
- Configure OAuth credentials
- Set up incremental sync
- Index email history

### 2. Optimize Chunk Size
- Run evaluation with different sizes
- Measure impact on P@5 and MRR
- Document findings

### 3. Production Deployment
- Set up proper authentication (OAuth2/OIDC)
- Configure encrypted credential storage
- Set up SSL/TLS
- Deploy to Kubernetes

### 4. Custom Connectors
- Implement Slack connector
- Add Notion integration
- Build custom API connector

## 🎯 Success Criteria

You know it's working when:

✅ Infrastructure starts without errors  
✅ Documents ingest successfully  
✅ Questions return answers with citations  
✅ Sensitive content routes to local LLM  
✅ Jaeger shows complete traces  
✅ Evaluation metrics pass thresholds  

## 📚 Further Reading

- `README.md` - Main documentation
- `docs/architecture.md` - System design
- `fitsum_cortex_spec.md` - Complete specification
- Spring AI Docs: https://docs.spring.io/spring-ai/reference/

---

**Questions?** Check the FAQ or raise an issue.

**Happy knowledge managing! 🧠**


# 🧠 Fitsum Cortex

**A private knowledge hub that organizes, protects, and recalls your personal/organizational knowledge with strict citations, privacy-first routing, hybrid retrieval, and structured outputs.**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M3-blue.svg)](https://spring.io/projects/spring-ai)
[![Vaadin](https://img.shields.io/badge/Vaadin-24.5.4-00B4F0.svg)](https://vaadin.com/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)

## 🌟 Features

- **🔐 Privacy-First**: Local classification of sensitive content, routes to local LLM (Ollama) automatically
- **📚 Strict Citations**: Every answer includes precise references with document titles and locations
- **🔍 Hybrid Retrieval**: Combines Full-Text Search (FTS) + Vector Search (ANN) + Reranking for optimal results
- **🎯 RAG by Default**: Blocks hallucinations - only answers from your knowledge base unless explicitly allowed
- **📊 Observability**: Full OpenTelemetry integration with Jaeger and Prometheus
- **🔄 Streaming UI**: Real-time token streaming with Vaadin for responsive UX
- **🛡️ Security**: Spring Security with authentication and authorization
- **📈 Evaluation Framework**: Built-in metrics (Precision@5, MRR) to measure retrieval quality

## 🏗️ Architecture

```
fitsum-cortex/
├── api/          # RAG orchestration, Advisors chain, REST API
├── ingest/       # Document connectors, chunking, embeddings
├── ui/           # Vaadin web interface with streaming
├── infra/        # Docker Compose for infrastructure
├── docs/         # Documentation
└── scripts/      # Development and evaluation scripts
```

### Advisor Chain

1. **InputSanitizer** - Validates and sanitizes user input
2. **SensitivityGuard** - Classifies content sensitivity (PII, Confidential)
3. **RetrievalAdvisor** - Performs hybrid retrieval and injects context
4. **ModelRouting** - Routes sensitive queries to local LLM
5. **TelemetryAdvisor** - Captures metrics and logs interactions

## 🚀 Quick Start

### Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Docker & Docker Compose**
- **OpenAI API Key** (set as environment variable: `OPENAI_API_KEY`)

### 1. Start Infrastructure

```powershell
# Windows PowerShell
.\scripts\dev.ps1
```

```bash
# Linux/Mac
./scripts/dev.sh
```

This starts:
- PostgreSQL with pgvector
- Ollama (local LLM)
- Jaeger (tracing)
- Prometheus (metrics)

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run API Server

```bash
cd api
mvn spring-boot:run
```

API will be available at: `http://localhost:8080`

### 4. Run UI (Optional)

```bash
cd ui
mvn spring-boot:run
```

UI will be available at: `http://localhost:8082`

### 5. Test the API

```bash
curl -X POST http://localhost:8080/v1/ask \
  -H "Content-Type: application/json" \
  -u demo@fitsum.ai:demo \
  -d '{
    "question": "What is in my knowledge base?",
    "allowFallback": false
  }'
```

## 📥 Ingesting Documents

### Local Files

1. Create a source configuration
2. Point to a directory with documents (PDF, DOCX, TXT, MD, etc.)
3. Run sync operation

```java
// Programmatic ingestion example
LocalFilesConnector connector = ...;
JsonNode config = objectMapper.createObjectNode()
    .put("path", "/path/to/documents")
    .put("recursive", true);
    
List<IngestedDocument> docs = connector.sync(config);
```

### Supported Formats

- Text files (`.txt`, `.md`)
- PDFs (`.pdf`)
- Microsoft Word (`.doc`, `.docx`)
- HTML (`.html`, `.htm`)

## 🔧 Configuration

### Application Properties

Key configurations in `api/src/main/resources/application.yaml`:

```yaml
cortex:
  retrieval:
    max-chunks: 12              # Never exceed this many chunks
    fts-top-k: 25               # Full-text search results
    ann-top-k: 25               # Vector search results
    rerank-top-k: 10            # Final reranked results
    similarity-threshold: 0.7   # Minimum similarity score
    allow-empty-context: false  # Block answers without context
  
  sensitivity:
    patterns:
      pii:
        - "\\b\\d{3}-\\d{2}-\\d{4}\\b"  # SSN
        - "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"  # Email
      confidential:
        - "\\bconfidential\\b"
  
  security:
    enabled: true
    default-user: demo@fitsum.ai
```

### Environment Variables

- `OPENAI_API_KEY` - Your OpenAI API key (required)
- `SPRING_PROFILES_ACTIVE` - Set to `localLLM` to prefer local models

## 🧪 Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Run Evaluation

```bash
./scripts/eval.sh
```

This runs the evaluation framework and generates a quality report.

## 📊 Monitoring

### Jaeger UI (Distributed Tracing)
http://localhost:16686

View request traces with full advisor chain execution.

### Prometheus (Metrics)
http://localhost:9090

Query application metrics:
- `cortex_ask_latency_ms`
- `cortex_retrieval_chunks_count`
- `cortex_llm_tokens_total`

## 🔐 Security

### Default Credentials (Development)
- Username: `demo@fitsum.ai`
- Password: `demo`

**⚠️ Change these in production!**

### Privacy Features

- **Automatic Sensitivity Detection**: Regex-based classification
- **Local Model Routing**: Sensitive data never sent to cloud
- **Audit Logging**: All Q&A interactions logged to `qa_log` table
- **No Telemetry to Cloud**: Observability data stays local

## 🗂️ Database Schema

PostgreSQL with pgvector extension:

- `source` - Knowledge source configurations
- `document` - Original documents with metadata
- `chunk` - Text chunks with embeddings (vector[1024])
- `qa_log` - Complete audit trail
- `eval_case`, `eval_run`, `eval_result` - Evaluation framework

## 📚 MCP Integration

Fitsum Cortex exposes MCP (Model Context Protocol) tools:

- `search_knowledge` - Query the knowledge base
- `add_document` - Add documents programmatically
- `list_sources` - List configured sources

See `api/src/main/resources/mcp.json` for full schema.

## 🛠️ Development

### Project Structure

```
ai.fitsum.cortex.api/
├── advisor/       # Custom Spring AI advisors
├── config/        # Spring configuration
├── controller/    # REST controllers
├── domain/        # Entity models
├── dto/           # Data transfer objects
├── eval/          # Evaluation framework
├── repository/    # Spring Data JDBC repositories
├── retrieval/     # Hybrid retrieval system
└── service/       # Business logic
```

### Adding a New Connector

1. Implement connector in `ingest` module
2. Follow pattern from `LocalFilesConnector`
3. Add normalization → deduplication → chunking → embedding
4. Write tests with sample fixtures
5. Expose filter in `/v1/ask` API

### Tuning Retrieval Quality

1. Adjust FTS/ANN parameters in `application.yaml`
2. Run evaluation: `./scripts/eval.sh`
3. Review metrics (P@5, MRR)
4. Iterate on chunk size, overlap, or reranking strategy
5. Keep context ≤ 12 chunks

## 📖 Documentation

- `docs/architecture.md` - System architecture
- `docs/evaluation.md` - Evaluation framework guide
- `fitsum_cortex_spec.md` - Complete specification

## 🤝 Contributing

This is a private project. See `.cursorrules` for coding standards.

## 📄 License

Proprietary - All rights reserved

## 🙋 Support

For questions or issues, contact the development team.

---

**Built with ❤️ using Spring AI, Vaadin, and OpenTelemetry**


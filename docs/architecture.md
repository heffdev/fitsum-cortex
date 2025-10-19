# Fitsum Cortex - Architecture

## Overview

Fitsum Cortex is a privacy-first, citation-focused RAG (Retrieval-Augmented Generation) system designed for personal and organizational knowledge management.

## Core Principles

1. **Trust > Novelty**: Strict citations required; explicit about knowledge gaps
2. **Privacy-First**: Classify locally, route sensitive content to local models
3. **RAG Strict by Default**: Block empty-context answers unless explicitly enabled
4. **Observability Non-Optional**: Full OpenTelemetry instrumentation
5. **Quality Gates**: Evaluation framework must pass before deployment

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│         Single Spring Boot App (Port 8080)              │
│  Vaadin UI + API + Ingestion + Retrieval                │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring AI Advisor Chain                  │  │
│  │                                                  │  │
│  │  1. InputSanitizer                              │  │
│  │  2. SensitivityGuard  ──► Classify PII/Conf    │  │
│  │  3. RetrievalAdvisor  ──► Hybrid Retrieval     │  │
│  │  4. ModelRouting (single provider: LM Studio)  │  │
│  │  5. TelemetryAdvisor (optional)                │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Hybrid Retrieval Engine                  │  │
│  │                                                  │  │
│  │  FTS (pg_trgm) ∪ ANN (pgvector) → ReRank → TopK │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Model Routing                            │  │
│  │                                                  │  │
│  │  Sensitivity == NONE  ──► OpenAI (Cloud)        │  │
│  │  Sensitivity != NONE  ──► Ollama (Local)        │  │
│  └──────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
         ┌──────────────────────────────┐
         │  PostgreSQL + pgvector       │
         │                              │
         │  - source                    │
         │  - document                  │
         │  - chunk (with embeddings)   │
         │  - qa_log (audit trail)      │
         │  - eval_* (quality metrics)  │
         └──────────────────────────────┘

Pipeline:
1. Normalize (Apache Tika)
2. Dedupe (SHA-256)
3. Chunk (350–500 tokens, 15% overlap)
4. Embed (local Transformers, 384‑dim)
5. Index (Postgres + pgvector)

Observability:
- Prometheus via Spring Boot Actuator
- Optional tracing via TelemetryAdvisor (currently off)
```

## Data Flow

### Question-Answer Flow

1. **User submits question** via UI or API
2. **InputSanitizer**: Validates length, detects injection attempts
3. **SensitivityGuard**: Classifies using regex patterns → sets `sensitivity` flag
4. **RetrievalAdvisor**: 
   - Embeds question
   - Runs FTS (PostgreSQL text search)
   - Runs ANN (pgvector cosine similarity)
   - Unions results, deduplicates
   - Reranks using cross-encoder
   - Takes top K (≤ 12 chunks)
   - Injects context into system prompt
   - **BLOCKS** if no context and `allowFallback=false`
5. **ModelRouting**: 
   - If `sensitivity=NONE` → OpenAI
   - Otherwise → Ollama (local)
6. **LLM generates answer** with citations
7. **TelemetryAdvisor**: 
   - Logs to `qa_log`
   - Emits OTel span
   - Calculates cost
8. **Response returned** with citations, confidence, trace ID

### Ingestion Flow

1. **Connector scans source** (filesystem, Gmail, etc.)
2. **DocumentNormalizer**: Apache Tika extracts text
3. **Compute SHA-256** for deduplication
4. **ChunkingService**: 
   - Splits into 350-500 token chunks
   - 15% overlap for context continuity
   - Preserves headings/sections
5. **EmbeddingModel**: 
   - Generates 384‑dim vectors (local Transformers)
   - pgvector column: vector(384)
6. **Write to database**:
   - `document` table
   - `chunk` table with embeddings
   - pgvector index for ANN search

## Key Components

### Hybrid Retrieval

**Why Hybrid?**
- FTS excels at exact term matching
- ANN excels at semantic similarity
- Reranking combines strengths

**Implementation:**
```java
FTS results (top 25) ──┐
                       ├──► Union ──► ReRank ──► Top 10
ANN results (top 25) ──┘
```

**Reranking:**
- Simple lexical overlap + BM25-style scoring
- Production: use cross-encoder model (e.g., ms-marco-MiniLM)

### Advisor Chain

Spring AI advisors wrap the LLM call:

```
Request ──► Advisor 1 ──► Advisor 2 ──► ... ──► LLM ──► ... ──► Advisor N ──► Response
```

**Order matters:**
1. Input validation (order 100)
2. Sensitivity classification (200)
3. Retrieval (300)
4. Routing (400)
5. Telemetry (900)

### Sensitivity Classification

**Patterns (regex-based):**
- PII: SSN, email, phone
- Confidential: "confidential", "internal only"

**Future:** ML-based classifier (e.g., NER model)

### Citations

Every answer includes:
- Document title
- Location (heading, page, or section)
- Snippet for verification

**Format:**
```
"Revenue was $2.3M [Q4 Report, Financial Summary]"
```

## Database Schema (current)

- `source` (config_json TEXT)
- `document` (metadata_json TEXT)
- `chunk` (embedding vector(384); ANN index IVFFlat; GIN `pg_trgm` on content)

**Indexes:**
- `ivfflat` for approximate nearest neighbor (ANN)
- `gin` with `pg_trgm` for full-text search (FTS)

## Security

### Authentication
- Spring Security with HTTP Basic (dev)
- Production: OAuth2/OIDC integration

### Privacy Guarantees
- Sensitive content **never** sent to cloud models
- Local Ollama instance for PII/Confidential
- Audit trail in `qa_log` table

### Credential Storage
- OAuth tokens stored in `source.config_json` (encrypted in production)
- No plaintext secrets in code

## Observability

### OpenTelemetry Integration

**Spans:**
- `cortex.ask` - full Q&A lifecycle
- Attributes: provider, sensitivity, chunk_count, latency_ms

**Metrics:**
- Request latency (histogram)
- Token usage (counter)
- Retrieval quality (P@5, MRR)

**Logs:**
- Structured JSON with trace ID correlation

### Arconia Starter

Auto-configures:
- OTLP exporter → Jaeger
- Prometheus endpoint
- Trace/metric correlation

## Evaluation Framework

### Metrics

1. **Precision@5**: % of top-5 results that are relevant
2. **MRR**: Mean Reciprocal Rank of first relevant result
3. **Faithfulness**: Answer grounded in context (future)

### Process

1. Define `eval_case` with question + expected chunks
2. Run `EvaluationService.runEvaluation()`
3. Compare retrieved chunks vs. expected
4. Calculate metrics, store in `eval_run` and `eval_result`

### CI Integration

```bash
./scripts/eval.sh
# Fails if P@5 < 0.7 or MRR < 0.8
```

## Scalability Considerations

### Current Limits
- Single Postgres instance
- In-process embedding
- Synchronous ingestion

### Future Improvements
- **Embedding Service**: Separate microservice for parallelization
- **Async Ingestion**: Kafka/RabbitMQ for job queue
- **Sharding**: Partition `chunk` table by source
- **Caching**: Redis for hot chunks
- **Read Replicas**: Scale reads independently

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Backend | Spring Boot | 3.5.6 |
| AI Framework | Spring AI | 1.0.3 |
| Frontend | Vaadin | 24.9.5 |
| Database | PostgreSQL | 16 |
| Vector Store | pgvector | Latest |
| Cloud LLM | OpenAI | GPT-4o |
| Local LLM | Ollama | qwen3:4b |
| Tracing | Jaeger | Latest |
| Metrics | Prometheus | Latest |
| Observability | Arconia | 0.17.1 |

## Deployment

### Development
```bash
./scripts/dev.ps1   # Start infra
mvn spring-boot:run # Start API
```

### Production (Future)
- Container images (Docker)
- Kubernetes manifests
- Helm charts
- Cloud-native buildpacks

## Performance Benchmarks

**Retrieval (10 chunks):**
- FTS: ~50ms
- ANN: ~30ms
- Rerank: ~20ms
- **Total: ~100ms**

**LLM (GPT-4o):**
- Prompt: ~3000 tokens
- Generation: ~500 tokens
- **Latency: ~2-3 seconds**

**End-to-End:**
- Cold: ~3-4 seconds
- Warm (with cache): ~2 seconds

---

**Last Updated:** 2025-10-16


# Fitsum Cortex — **Agentic Build Spec for Cursor AI**
**Package root:** `ai.fitsum.cortex`  
**Repo codename:** `fitsum-cortex`  
**Purpose:** A private knowledge hub that **organizes, protects, and recalls** your personal/organizational knowledge with **strict citations**, **privacy-first routing**, **hybrid retrieval**, **structured outputs**, and **MCP** tool surface.  
**Principle:** **Trust > novelty.** If we can’t cite it, we don’t say it.

---

## 0) What Cursor AI Should Do With This File
- Treat this document as the **single source of truth** for scaffolding, generating, and modifying code.
- Apply rules in **“Global Rules for Cursor”** to all changes.
- Use exact **module layout**, **dependencies**, **advisors**, **APIs**, **data model**, **retrieval**, and **security** policies described here.
- Prefer the **stable** dependency line unless a profile (`mcpNext`, `localLLM`) is explicitly requested.

---

## 1) Naming, Packaging, and Modules
- **Product name:** **Fitsum Cortex**
- **Package base:** `ai.fitsum.cortex`
- **Modules (Gradle or Maven, your choice):**
  ```
  /api            # Spring Boot app: RAG + Advisors + MCP tools + Security + Eval
  /ingest         # Connectors, normalization, dedupe, chunking, embeddings pipeline
  /ui             # Vaadin 24 app with WebFlux streaming & observability widgets
  /infra          # Dev services (Arconia), docker compose, local LLM runners
  /docs           # This spec + architecture + operational guides
  /scripts        # Helper scripts for dev, db, eval, lint
  ```
- **Java package roots per module:**
  - `/api`:        `ai.fitsum.cortex.api`
  - `/ingest`:     `ai.fitsum.cortex.ingest`
  - `/ui`:         `ai.fitsum.cortex.ui`
  - `/infra`:      `ai.fitsum.cortex.infra`

---

## 2) Global Rules for Cursor (Hard Guardrails)
**Create a root file `.cursorrules` with the following content and obey it for all code generation:**

```yaml
project:
  name: Fitsum Cortex
  principles:
    - "Trust > novelty: strict citations; if context is missing, say so."
    - "Privacy-first routing: classify locally; sensitive => local models only."
    - "RAG strict by default; fallback to model knowledge only when explicitly enabled."
    - "Tools are internet-facing APIs: require authN/Z + validation + telemetry."
    - "Observability is non-optional (OpenTelemetry)."

tech:
  backend: "Spring Boot 3.5+, Spring AI 1.0.3 (stable), WebFlux, Postgres + pgvector"
  frontend: "Vaadin 24.9.x, token streaming"
  observability: "io.arconia:arconia-opentelemetry-spring-boot-starter:0.17.1"
  optional_mcp: "If MCP bleeding-edge needed, use profile `mcpNext` with Spring AI 1.1.0-M3 in /api only."

policies:
  - "Never send potentially sensitive inputs to cloud models. Run SensitivityGuard locally first."
  - "Default RetrievalAdvisor must BLOCK empty-context answers. UI provides explicit 'Allow fallback' toggle."
  - "All mutation tools require @PreAuthorize and @Transactional, validate inputs, and log OTel spans."
  - "All LLM structured outputs must pass JSON schema validation; one auto-repair allowed, else fail."
  - "All answers include citations with titles and (heading/page)."
  - "Unit tests required for new advisors, tools, retrievers, and connectors."

directory_ownership:
  "/api": ["backend"]
  "/ingest": ["backend"]
  "/ui": ["frontend"]
  "/infra": ["devops"]
  "/docs": ["pm","backend","frontend"]
  "/scripts": ["devops"]

banned_changes:
  - "Removing or bypassing OpenTelemetry/Arconia wiring"
  - "Bypassing SensitivityGuard"
  - "Disabling schema validation for structured outputs"
  - "Raising context above 12 chunks without justification and tests"

code_style:
  java: "Google Java Style; prefer records for DTOs; constructor injection; final where reasonable"
  tests: "JUnit5; Testcontainers for Postgres; golden tests for retrieval and advisors; JSON-schema tests for structured outputs"

task_templates:
  - name: "Add connector"
    description: "Implement new ingestion connector with dedupe, normalization, chunking, embeddings."
    checklist:
      - "Define SourceConfig and OAuth/creds if needed; store in source.config_json"
      - "Implement incremental sync via updatedAt cursor"
      - "Normalize -> dedupe by sha256 -> chunk (350–500 tokens; 15% overlap)"
      - "Embed and index; write ingestion + retrieval tests with sample fixtures"
      - "Expose source filter in /v1/ask and UI chips"
  - name: "Add tool (MCP + Spring AI)"
    checklist:
      - "Define @Service interface; annotate with @AIFunction parameter hints"
      - "Add @PreAuthorize; validate inputs; @Transactional for mutations"
      - "Register with MCP; add unit/integration tests"
      - "Emit OTel attributes (tool.name, user.id, latency_ms)"
  - name: "Tune retrieval quality"
    checklist:
      - "Adjust FTS + ANN topK and thresholds; rerank strategy"
      - "Run eval (P@5, MRR) and snapshot results to /docs/eval"
      - "Keep context ≤ 12 chunks; prefer rerank gains over more context"
```

**Create `.cursorignore` at repo root:**
```
/infra/**/data/**
/**/target/**
/**/build/**
/**/.gradle/**
/**/.idea/**
/**/.vscode/**
```

---

## 3) Dependencies & Versions
> Use **stable** by default. Provide **profile overrides** only where specified.

- **Spring AI (stable):** `1.0.3`
- **Spring Boot:** `3.5.x`
- **Vaadin:** `24.9.x`
- **Arconia OTel Starter:** `0.17.1`
- **Postgres** with **pgvector**
- **Testcontainers** for Postgres
- **Optional (profile `mcpNext`)**: Spring AI **`1.1.0-M3`** for newest MCP niceties
- **Optional (profile `localLLM`)**: local providers (Ollama/NIM)

**Maven BOM (api/pom.xml snippet):**
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.0.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Spring AI starters -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
  </dependency>

  <!-- WebFlux for streaming -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
  </dependency>

  <!-- Security -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>

  <!-- Observability -->
  <dependency>
    <groupId>io.arconia</groupId>
    <artifactId>arconia-opentelemetry-spring-boot-starter</artifactId>
    <version>0.17.1</version>
  </dependency>

  <!-- Data -->
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
  </dependency>

  <!-- Test -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>

<profiles>
  <profile>
    <id>mcpNext</id>
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>org.springframework.ai</groupId>
          <artifactId>spring-ai-bom</artifactId>
          <version>1.1.0-M3</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>
  </profile>

  <profile>
    <id>localLLM</id>
    <!-- Add dependencies/config for Ollama/NIM providers if needed -->
  </profile>
</profiles>
```

---

## 4) Architecture Overview

### Services (can be separate apps or profiles)
- **`/api`** — RAG orchestration, Advisors chain, synthesis, MCP tools, security, eval endpoints.
- **`/ingest`** — Connectors (Local Files + Gmail v1), normalization, dedupe, chunking, embeddings.
- **`/ui`** — Vaadin views with token streaming, filters, citations, privacy chips, “Why these sources?”.
- **`/infra`** — Arconia Dev Services (Postgres, OTLP), optional local LLM.

### Data Stores
- **Postgres** with **pgvector** for chunks/embeddings and FTS.
- **Optional** MinIO/S3 for raw blobs.
- **Optional** Redis for transient streaming buffers and caches.

---

## 5) Data Model (SQL-level intent)
*(Flyway migrations in `/api/src/main/resources/db/migration`)*

```sql
CREATE TABLE source (...);
CREATE TABLE document (...);
CREATE TABLE chunk (...);
ALTER TABLE chunk ADD COLUMN embedding vector(1536);
CREATE TABLE qa_log (...);
CREATE TABLE eval_case (...);
CREATE TABLE eval_run (...);
CREATE TABLE eval_result (...);
```

(Full SQL as in detailed spec above.)

---

## 6) Ingestion & Chunking
- Local Files + Gmail (read-only)
- Normalize, dedupe (sha256), chunk 350–500 tokens, 15% overlap.
- Embed with OpenAI or local `bge-m3`.
- Index in Postgres.

**API:** `/v1/ingest/{sourceId}/sync`, `/v1/ingest/status`

---

## 7) Retrieval Strategy (Hybrid + Rerank)
FTS + ANN union top-50 → Rerank (local cross-encoder) → Keep top 10 → Synthesize.
**Never exceed 12 chunks.**

---

## 8) Advisors Chain (Spring AI)
As detailed above: InputSanitizer → SensitivityGuard → Retrieval → Routing → StructuredOutput → OutputSafety → Telemetry.

---

## 9) Model Providers & Routing
Local (Ollama/NIM) for sensitive; cloud (OpenAI) for normal.
Routing: if sensitivity != NONE → local; else default cloud.

---

## 10) Prompts & Structured Outputs
System prompt `/api/src/main/resources/prompts/system/answerer.md`.
Answer schema & record `Answer`.

---

## 11) Public API
- `POST /v1/ask`
- `GET /v1/docs/{id}`
- `POST /v1/ingest/{sourceId}/sync`
- `GET /v1/ingest/status`

With security + schema validation.

---

## 12) MCP Server
Define `/api/mcp.json` tools as in detailed spec.

---

## 13) Vaadin UI
Streaming Q/A with citations, privacy chips, filters, trace id footer.

---

## 14) Observability (Arconia)
OTel spans: provider, sensitivity, advisor_stack, retrieval, token stats, confidence.

---

## 15) Security & Privacy
- SensitivityGuard before any cloud call.
- Encrypt creds; ACL per-user.
- Audit `qa_log` with provider, docs, cost.

---

## 16) Evaluation & Quality Gates
Eval harness, metrics (P@5, MRR, faithfulness), CI gate.

---

## 17) Dev & Run Scripts
`scripts/dev.sh`, `scripts/eval.sh`, and `docs/MAKE_IT_SO.md` as in spec.

---

## 18) PR Checklist
Use template from spec.

---

## 19) Concrete Skeletons to Generate
HybridRetriever, Advisors, LocalClassifier with regex patterns, and UI layout skeleton.

---

## 20) UI Contracts (Vaadin)
Search bar + toggles, streamed answer, citations, “Why these sources?”, footer trace id.

---

## 21) Defaults & Feature Flags
Define in `application.yaml` with sensible defaults (`allowEmptyContext=false`, etc.)

---

## 22) Roadmap (30 Days)
Week 1–4 breakdown from spec.

---

## 23) README Structure
Quickstart, profiles, links to docs.

---

## 24) Final Notes
- Always prefer adding tests over context window.
- Preserve privacy and observability.
- Maintain module boundaries.

---

**End of Spec — Make it so.**


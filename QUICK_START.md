# 🚀 Fitsum Cortex - Quick Start Guide

## ✅ Project Built Successfully!

All modules have been compiled and are ready to run.

## 📋 What Was Built

- ✅ **API Module** - RAG orchestration with Spring AI 1.0.0-M3
- ✅ **Ingest Module** - Document processing pipeline  
- ✅ **UI Module** - Vaadin 24.5.4 web interface
- ✅ **Infrastructure** - Docker Compose configuration

## 🔧 Configuration Notes

### Spring AI Version
- Using **Spring AI 1.0.0-M3** (milestone release from Spring Milestones repository)
- Note: Version 1.0.3 doesn't exist yet, so we're using the latest stable milestone

### Vaadin Version  
- Using **Vaadin 24.5.4** (LTS version)

### Your Local LLM Configuration
- **Ollama Base URL**: `http://localhost:11234`
- **Model**: `qwen3-4b-instruct-q1_k_m`
- Already configured in `application.yaml`

## 🚀 Next Steps

### 1. Start Infrastructure

Open PowerShell and run:
```powershell
cd D:\STS\sts_4.27\workspace\fitsum-cortex
.\scripts\dev.ps1
```

This will start:
- PostgreSQL with pgvector (port 5432)
- Ollama with your qwen3 model (port 11234)
- Jaeger for tracing (port 16686)
- Prometheus for metrics (port 9090)

### 2. Start the API Server

Open a new terminal:
```powershell
cd D:\STS\sts_4.27\workspace\fitsum-cortex\api
..\mvnw.cmd spring-boot:run
```

The API will start on: **http://localhost:8080**

### 3. Start the UI (Optional)

Open another terminal:
```powershell
cd D:\STS\sts_4.27\workspace\fitsum-cortex\ui  
..\mvnw.cmd spring-boot:run
```

The UI will start on: **http://localhost:8082**

### 4. Test It Out

**Using curl:**
```powershell
curl.exe -X POST http://localhost:8080/v1/ask `
  -H "Content-Type: application/json" `
  -u "demo@fitsum.ai:demo" `
  -d '{\"question\": \"What is Fitsum Cortex?\", \"allowFallback\": true}'
```

**Using the browser:**
- Open http://localhost:8082
- Type a question
- See streaming answers with citations!

## 📊 Monitoring

- **Jaeger UI**: http://localhost:16686
- **Prometheus**: http://localhost:9090
- **API Health**: http://localhost:8080/actuator/health

## 🔐 Default Credentials

- **Username**: `demo@fitsum.ai`
- **Password**: `demo`

## 📝 Important Notes

1. **OPENAI_API_KEY**: Make sure your Windows environment variable is set
   ```powershell
   $env:OPENAI_API_KEY
   # Should show your API key
   ```

2. **IDE Errors**: Refresh your IDE (Eclipse/STS):
   - Right-click project → Maven → Update Project
   - Or restart your IDE

3. **Docker**: Make sure Docker Desktop is running before starting infrastructure

## 🎯 What's Next?

1. **Ingest Documents**: Add your first documents to the knowledge base
2. **Ask Questions**: Test the RAG system with real queries
3. **View Traces**: Check Jaeger to see the full advisor chain in action
4. **Customize**: Tune retrieval parameters in `application.yaml`

## 📚 Documentation

- Full docs: `README.md`
- Architecture: `docs/architecture.md`  
- Detailed setup: `docs/MAKE_IT_SO.md`
- Build summary: `docs/BUILD_SUMMARY.md`

## 🐛 Troubleshooting

**If Postgres doesn't start:**
```powershell
cd infra/docker
docker-compose down -v
docker-compose up -d
```

**If Maven command not found:**
```powershell
# Use the wrapper instead
.\mvnw.cmd clean install
```

**To rebuild everything:**
```powershell
.\mvnw.cmd clean install -DskipTests
```

---

**🎉 You're all set! Enjoy building with Fitsum Cortex!** 🧠✨


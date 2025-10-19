# Spring Tool Suite Setup Guide

## Refresh Maven Project in STS

If STS doesn't recognize this as a Maven project, follow these steps:

### Option 1: Update Maven Project (Recommended)
1. In STS, right-click on the project `fitsum-cortex` in Project Explorer
2. Select **Maven** → **Update Project...**
3. Check the box for `fitsum-cortex`
4. Check **Force Update of Snapshots/Releases**
5. Click **OK**

### Option 2: Reimport Project
1. In STS, right-click on the project and select **Delete** (DO NOT check "Delete project contents on disk")
2. Go to **File** → **Import...**
3. Select **Maven** → **Existing Maven Projects**
4. Click **Next**
5. Browse to: `D:\STS\sts_4.27\workspace\fitsum-cortex`
6. Click **Finish**

### Option 3: Clean and Rebuild
1. In STS, select **Project** → **Clean...**
2. Select **fitsum-cortex**
3. Click **Clean**

---

## Running the Application from STS

### Method 1: Run as Spring Boot App (Recommended)
1. Right-click on `FitsumCortexApplication.java` in the Project Explorer
   - Located at: `src/main/java/ai/fitsum/cortex/FitsumCortexApplication.java`
2. Select **Run As** → **Spring Boot App**
3. The application will start in the console

### Method 2: Boot Dashboard
1. Open the **Boot Dashboard** view in STS
   - **Window** → **Show View** → **Boot Dashboard**
2. Find `fitsum-cortex` in the dashboard
3. Click the green **Start** button
4. Click the red **Stop** button to stop it

### Method 3: Maven Run Configuration
1. Right-click on project → **Run As** → **Maven build...**
2. In **Goals**, enter: `spring-boot:run`
3. Click **Run**

---

## Application Structure

This is now a **standard single-module Spring Boot application**:

```
fitsum-cortex/
├── src/main/java/ai/fitsum/cortex/
│   ├── FitsumCortexApplication.java    ← Main class (run this!)
│   ├── api/                            ← REST API, advisors, retrieval
│   ├── ingest/                         ← Document ingestion
│   └── ui/                             ← Vaadin UI
├── src/main/resources/
│   ├── application.yaml                ← Main config
│   └── db/migration/                   ← Flyway migrations
└── pom.xml                             ← Single POM file
```

---

## Verify Setup

### Check that STS recognizes the project:
1. Look for **[boot]** indicator next to the project name in Project Explorer
2. The project should have a Maven icon (M) next to it
3. You should see `src/main/java` and `src/test/java` as source folders

### Console output when running:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.5.6)
```

---

## Troubleshooting

### "Project configuration is not up-to-date"
- Right-click project → **Maven** → **Update Project**

### "Cannot resolve dependencies"
- Clean Maven local repository: `.\mvnw.cmd clean install -U`
- Or right-click project → **Maven** → **Update Project** with **Force Update**

### "Multiple main classes found"
- This has been fixed! There's now only one main class: `FitsumCortexApplication`

### Application won't start
1. **Database not running?**
   ```powershell
   cd infra/docker
   docker-compose up -d postgres
   ```

2. **Check logs** in the STS Console view for specific errors

3. **Environment variable missing?**
   - Add `OPENAI_API_KEY` to Run Configuration:
     - Right-click `FitsumCortexApplication.java`
     - **Run As** → **Run Configurations...**
     - Select your configuration
     - Go to **Environment** tab
     - Add: `OPENAI_API_KEY=your-key-here`

---

## Quick Start Commands

```powershell
# Start PostgreSQL (in another terminal)
cd infra/docker
docker-compose up -d postgres

# Verify build
.\mvnw.cmd clean install -DskipTests

# Run from command line (alternative to STS)
.\mvnw.cmd spring-boot:run
```

---

**✅ Project is now a standard Spring Boot application compatible with STS!**


# 🔧 Fixing Eclipse/STS IDE Errors

## The Issue
You're seeing errors like:
- "The declared package does not match the expected package"
- "Cannot be resolved to a type"
- Import statements showing as errors

**These are IDE configuration issues, NOT build problems!** ✅ The Maven build completed successfully.

## Quick Fix (Try This First)

### Option 1: Maven Update Project
1. In Eclipse/STS, **right-click** on the `fitsum-cortex` project
2. Select **Maven** → **Update Project...**
3. Check **all modules** (api, ingest, ui, infra)
4. Check these boxes:
   - ✅ **Update project configuration from pom.xml**
   - ✅ **Clean projects**
   - ✅ **Force Update of Snapshots/Releases**
5. Click **OK**
6. Wait for the update to complete (~30 seconds)

### Option 2: Clean and Rebuild
1. **Project** menu → **Clean...**
2. Select **Clean all projects**
3. Check **Start a build immediately**
4. Click **Clean**

### Option 3: Restart Eclipse/STS
Sometimes Eclipse just needs a fresh start:
1. **File** → **Exit**
2. Restart Eclipse/STS
3. Wait for workspace to load
4. Right-click project → **Maven** → **Update Project**

## If Errors Persist

### Step 1: Check Java Version
1. Right-click `fitsum-cortex` → **Properties**
2. **Java Build Path** → **Libraries**
3. Verify **JRE System Library** shows **JavaSE-21**
4. If not, click **Edit** and select Java 21

### Step 2: Verify Maven Settings
1. Right-click project → **Properties**
2. **Maven** section
3. Verify:
   - ✅ **Resolve dependencies from Workspace projects** is checked
   - ✅ **Enable Workspace Resolution** is checked

### Step 3: Refresh Source Folders
1. Right-click `fitsum-cortex` → **Properties**
2. **Java Build Path** → **Source** tab
3. Verify you see:
   ```
   api/src/main/java
   api/src/test/java
   ingest/src/main/java
   ingest/src/test/java
   ui/src/main/java
   ui/src/test/java
   ```
4. If folders show as `src/main/java` (without module prefix), remove them and click **Add Folder** to re-add correctly

### Step 4: Nuclear Option - Reimport
If nothing else works:

1. **Close** Eclipse/STS
2. Delete workspace metadata:
   ```powershell
   cd D:\STS\sts_4.27\workspace\fitsum-cortex
   Remove-Item -Recurse -Force .settings, .project, .classpath -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force */.settings, */.project, */.classpath -ErrorAction SilentlyContinue
   ```
3. **Restart** Eclipse/STS
4. **File** → **Import** → **Maven** → **Existing Maven Projects**
5. Browse to `D:\STS\sts_4.27\workspace\fitsum-cortex`
6. Select **all modules** (parent + api + ingest + ui + infra)
7. Click **Finish**

## Verify It's Fixed

After the fix, you should see:
- ✅ No red X marks on project folders
- ✅ All imports resolve correctly
- ✅ Package names match (no errors)
- ✅ Maven Dependencies library shows in Project Explorer

## Still Having Issues?

### Check Maven Console
1. **Window** → **Show View** → **Console**
2. In console dropdown, select **Maven Console**
3. Look for any error messages during update

### Verify Build from Command Line
The project **does** build successfully:
```powershell
.\mvnw.cmd clean install -DskipTests
```
This confirms it's purely an IDE issue, not a code problem.

## Common Causes

1. **Eclipse cache** - Old metadata confusing the IDE
2. **Workspace corruption** - Rare but happens
3. **Maven settings** - Wrong Java version or repository settings
4. **Plugin issues** - m2e (Maven plugin for Eclipse) needs refresh

## Expected Result

After fixing, your Project Explorer should look like:
```
📁 fitsum-cortex
  📁 api
    📁 src/main/java
      📦 ai.fitsum.cortex.api   ← No errors!
    📁 src/test/java
    📁 Maven Dependencies      ← All JARs listed
  📁 ingest
    📁 src/main/java
      📦 ai.fitsum.cortex.ingest
  📁 ui
    📁 src/main/java
      📦 ai.fitsum.cortex.ui    ← No errors!
```

---

**TL;DR**: Right-click project → Maven → Update Project → Check all → Force Update → OK

This will fix 99% of IDE issues! 🎯


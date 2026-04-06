# Build Troubleshooting Guide

## ZIP END Header Error (Corrupted JAR Files)

If you encounter errors like:
```
error reading C:\Users\...\ChatColorHandler-v2.5.3.jar; zip END header not found
ZipException opening "ChatColorHandler-v2.5.3.jar": zip END header not found
```

This indicates a **corrupted JAR file** in your local Maven repository.

> **Good news:** The build now automatically removes any cached `me/dave/ChatColorHandler` folder during the `validate` phase (before compilation). If the error still appears, your local cache may be heavily corrupted—follow the steps below to force a clean re-download.

### Root Cause
Maven downloaded a dependency JAR file, but the download was interrupted or corrupted. Maven stores these files in `~/.m2/repository` (Linux/Mac) or `C:\Users\<username>\.m2\repository` (Windows).

### Solution: Clean Corrupted Dependencies

**Option 1: Delete Specific Corrupted Dependency (Recommended)**
```bash
# Windows (PowerShell)
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\me\dave\ChatColorHandler"

# Linux/Mac
rm -rf ~/.m2/repository/me/dave/ChatColorHandler
```

**Option 2: Clean All Failed Downloads**
```bash
# Windows (PowerShell) - Remove failed download markers
Get-ChildItem -Path "$env:USERPROFILE\.m2\repository" -Recurse -Filter "*.lastUpdated" | Remove-Item -Force
Get-ChildItem -Path "$env:USERPROFILE\.m2\repository" -Recurse -Filter "resolver-status.properties" | Remove-Item -Force

# Linux/Mac
find ~/.m2/repository -name "*.lastUpdated" -delete
find ~/.m2/repository -name "resolver-status.properties" -delete
```

**Option 3: Complete Maven Cache Clean (Nuclear Option)**
```bash
# Windows
mvn dependency:purge-local-repository -DreResolve=true

# Linux/Mac
mvn dependency:purge-local-repository -DreResolve=true
```

**After Cleaning, Rebuild:**
```bash
mvn clean compile
```

### Prevention
- Ensure stable internet connection during Maven builds
- If build fails mid-download, always clean before retrying
- Use `mvn clean compile` instead of just `mvn compile` after network issues

---

## Dependency Resolution Errors

If you encounter errors like:
```
Could not resolve dependencies for project fr.wpets:Wpets:jar:1.0.0
Failed to read artifact descriptor for io.papermc.paper:paper-api:jar:1.21.4-R0.1-SNAPSHOT
```

### Common Causes

1. **Network Restrictions**: The required Maven repositories are not accessible from your network
2. **DNS Issues**: Domain name resolution is blocked or failing
3. **Firewall Rules**: Corporate/sandbox firewall blocking Maven repository access
4. **Repository Downtime**: One or more repositories may be temporarily unavailable

### Solutions

#### Option 1: Check Network Connectivity
Verify you can reach the required repositories:
```bash
curl -I https://repo.papermc.io/repository/maven-public/
curl -I https://mvn.lumine.io/repository/maven-public/
curl -I https://repo.codemc.io/repository/maven-public/
curl -I https://repo.fancyplugins.de/releases
```

#### Option 2: Clean Maven Cache
If you have corrupted dependency metadata:
```bash
# Remove failed download markers
find ~/.m2/repository -name "*.lastUpdated" -delete
find ~/.m2/repository -name "resolver-status.properties" -delete

# Then retry the build
mvn clean package
```

#### Option 3: Use Different Network
Build the project from an environment with unrestricted internet access:
- Local development machine (not corporate network)
- Different CI/CD environment
- VPN connection that allows Maven repository access

#### Option 4: Pre-cache Dependencies
If you have dependencies cached in another Maven repository:
```bash
# Copy from another machine's Maven cache
cp -r /path/to/other/.m2/repository/* ~/.m2/repository/
```

#### Option 5: Use Repository Mirror (Advanced)
Create `~/.m2/settings.xml` with mirror configuration if you have access to a repository proxy.

### Required Repositories

This project requires the following specialized Minecraft plugin repositories:
- **Paper MC**: https://repo.papermc.io/repository/maven-public/
- **Lumine**: https://mvn.lumine.io/repository/maven-public/
- **CodeMC**: https://repo.codemc.io/repository/maven-public/
- **FancyPlugins**: https://repo.fancyplugins.de/releases

All of these must be accessible for the build to succeed.

### Still Having Issues?

The build fundamentally requires network access to these Minecraft plugin repositories. If your environment blocks access to these domains, you will need to:
1. Build in a different environment with network access
2. Set up a repository proxy/mirror that your network can access
3. Obtain the dependencies manually and install them to your local Maven repository

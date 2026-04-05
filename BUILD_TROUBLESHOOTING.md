# Build Troubleshooting Guide

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

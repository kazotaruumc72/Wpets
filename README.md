# Wpets

A custom pet plugin for Minecraft with skill trees, level milestones, and MythicMobs/ModelEngine integration.

## Building

This project requires access to the following Maven repositories to download dependencies:
- https://repo.papermc.io/repository/maven-public/ (Paper API)
- https://mvn.lumine.io/repository/maven-public/ (MythicMobs, ModelEngine)
- https://repo.codemc.io/repository/maven-public/
- https://repo.fancyplugins.de/releases (FancyHolograms)

### Build Command
```bash
mvn clean package
```

### Network Requirements
If you encounter dependency resolution errors, ensure your network environment allows access to the above repositories. DNS resolution must work for these domains.

### Troubleshooting Build Issues

If you encounter build errors such as:
- `zip END header not found` - Corrupted JAR files in Maven cache
- `Could not resolve dependencies` - Network or repository access issues

See the [BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md) guide for detailed solutions.

**Quick Fix for Corrupted Dependencies:**
```bash
# Linux/Mac
./clean-maven-cache.sh

# Windows
clean-maven-cache.bat
```
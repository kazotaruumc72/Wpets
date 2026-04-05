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
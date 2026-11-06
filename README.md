# Procedure Renamer (MCreator 2026.1)

A Java plugin for MCreator 2026.1 that adds a singleton **Procedure Rename**
element. The element stores per-procedure target class names and packages. A
Gradle post-processor rewrites a shadow copy of `src/main/java` before
`compileJava`, so the built jar contains the renamed classes and updated
references while the original generated sources remain untouched.

## Scope

The plugin provides definitions and Gradle hooks for the 14 Java generators
shipped with MCreator 2026.1:

- Forge 1.14.4, 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1
- NeoForge 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8
- Fabric 1.21.8

Datapack, resourcepack and Bedrock addon generators have no Procedure element
and are intentionally excluded.

## Build

The project is offline and self-contained. It expects a JDK 21 and the local
Gradle 9.8.0 distribution supplied by the workspace.

```bat
set JAVA_HOME=<path-to-jdk-21>
gradlew.bat build -g ..\caches\gradle
```

The plugin archive is written to `build/libs/procedure_renamer-2026.1.zip`.
The archive name is fixed to `{plugin_id}-{mcreator_version}.zip`; the build
number in `version.properties` is still incremented for every `build`/`jar`
invocation and is written into the generated `plugin.json`.

## Install

1. In MCreator, enable **Java plugins** in Preferences.
2. Copy the plugin zip into the MCreator plugins folder.
3. Restart MCreator.
4. Create a **Procedure Rename** element in a Java generator workspace.

The virtual API `procedure_renamer_postprocess` is enabled automatically while
the element exists. It may appear as an optional mod metadata dependency; this
is documented in `MIGRATION-REPORT.md`.

## Notes

- The original `src/main/java` is never modified.
- Renaming happens in `build/procedure_renamer/java`.
- `build/procedure_rename_config.json` is the build-time configuration snapshot.
- All configuration errors are skipped and fall back to native MCreator output.

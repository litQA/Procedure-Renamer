<img width="1200" alt="banner_procedure_renamer" src="https://github.com/user-attachments/assets/5214669b-c377-497e-ad9b-8e5b053ce382" />

# Procedure Renamer (MCreator 2026.3)

A Java plugin for MCreator 2026.3 that adds a singleton **Procedure Rename**
element. The element stores per-procedure target class names and packages. A
Gradle post-processor rewrites a shadow copy of `src/main/java` before
`compileJava`, so the built jar contains the renamed classes and updated
references while the original generated sources remain untouched.

## Scope

The plugin covers 23 Java generators. The 14 generators supported by the
2026.1 branch remain declared so third-party generator plugins can provide
them in 2026.3:

- Forge 1.14.4, 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1
- NeoForge 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8
- Fabric 1.21.8

The 2026.3 expansion adds:

- Fabric 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3
- NeoForge 26.1.2, 26.2, 26.3

Fabric 1.21.8 is already part of the retained 2026.1 set and is not counted a
second time.

Datapack, resourcepack and Bedrock addon generators have no Procedure element
and are intentionally excluded.

## Build

The project is offline and self-contained. It expects the JDK 25 bundled with
MCreator EAP 2026.3 and the local Gradle 9.8.0 distribution supplied by the
workspace.

```bat
set JAVA_HOME=<path-to-mcreator-eap-jdk-25>
gradlew.bat build -g ..\caches\gradle
```

The plugin archive is written to `build/libs/procedure_renamer-2026.3.zip`.
The archive name is fixed to `{plugin_id}-{mcreator_version}.zip`; the build
number in `version.properties` is still incremented for every `build`/`jar`
invocation and is written into the generated `plugin.json`.

## Install

1. In MCreator, enable **Java plugins** in Preferences.
2. Copy the plugin zip into the MCreator plugins folder.
3. Restart MCreator.
4. Create a **Procedure Rename** element in a supported Java generator
   workspace.

The virtual API `procedure_renamer_postprocess` is enabled automatically while
the element exists. It may appear as an optional mod metadata dependency; this
is documented in `MIGRATION-REPORT.md`.

## Notes

- The original `src/main/java` is never modified.
- Renaming happens in `build/procedure_renamer/java`.
- `build/procedure_rename_config.json` is the build-time configuration snapshot.
- All configuration errors are skipped and fall back to native MCreator output.
- Generators not shipped with MCreator 2026.3 are supported through the same
  definition and API entries when a matching third-party generator plugin is
  installed.

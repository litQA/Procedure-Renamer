# Migration and Limitation Report

This report records the design decisions and known limitations of the
Procedure Renamer 2026.1 implementation.

## Scope

The plugin targets the 14 Java generators shipped with MCreator 2026.1. The
datapack, resourcepack and Bedrock addon generators do not contain Procedure
elements and are intentionally excluded.

## Known Limitations

1. **Lexical rewriting, not a full Java AST.**
   The post-processor masks comments, strings, character literals and text
   blocks, then applies a single-pass class/package replacement and injects
   imports for cross-package references. It does not resolve a complete Java
   symbol table. Reflection, dynamically loaded class names and class names
   embedded in non-Java resources are not rewritten.

2. **Cross-package import injection is conservative.**
   If a file already imports a different class with the same simple name, or
   declares a conflicting type, the entry is skipped with a warning instead of
   risking a wrong rewrite. Complex user code may therefore need manual import
   cleanup.

3. **Build script injection replaces the virtual API.**
   Older plugin versions provided a user-selectable virtual API named
   `procedure_renamer_postprocess`, which could add an optional dependency to
   mod metadata. The current implementation removes that API ID from
   `mcreatorDependencies` and injects a managed block into the workspace
   `build.gradle` instead. No `procedure_renamer_postprocess` dependency is
   generated.

   Workspaces that still have the removed API ID enabled cannot open after the
   API definition is removed. A separate, temporary resource-only plugin
   (`procedure_renamer_migration`) restores only the API definitions so those
   workspaces can open; the main plugin removes the legacy ID on load. Save the
   workspace, remove the helper, and restart MCreator. The helper uses empty
   Gradle blocks and does not affect builds or mod metadata.

4. **Future and third-party generators.**
   MCreator resolves element definitions by exact generator name. A new
   generator or a third-party Java generator requires an additional
   `<generator>/procedure_rename.definition.yaml`; the workspace-level
   `build.gradle` managed block does not need per-generator duplication.

5. **Run Client freshness.**
   The configuration is exported on load, save, element save and build start.
   If the user edits the element and starts Run Client without saving, the run
   uses the last saved configuration.

6. **Case-only class renames.**
   Renames that differ only by case are skipped because Windows file systems
   are case-insensitive and the final archive would be ambiguous.

7. **Generator matrix.**
   ForgeGradle 6 (Gradle 8.14.5), NeoForge ModDev (Gradle 9.4.1) and Fabric Loom
   (Gradle 9.4.1) are validated in separate representative workspaces. The full
   14-generator matrix remains a release-gate activity.

8. **Class-only rename is intentionally ignored.**
   A valid class name requires a valid target package. If the target package is
   empty or invalid, the entry makes no change; a valid target package with an
   empty or invalid class name performs a package-only change and keeps the
   generated `<procedureName>Procedure` class name.

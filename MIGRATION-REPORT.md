# Migration and Limitation Report

This report records the design decisions and known limitations of the
Procedure Renamer 2026.3 implementation.

## Scope

The plugin targets MCreator EAP 2026.3.44621 and declares 23 Java generators:
the 14 generators supported by the 2026.1 branch plus Fabric 1.20.1, 1.21.1,
1.21.11, 26.1.2, 26.2, 26.3 and NeoForge 26.1.2, 26.2, 26.3. Fabric 1.21.8 is
shared with the retained set.

The datapack, resourcepack and Bedrock addon generators do not contain
Procedure elements and are intentionally excluded.

The 14 retained generators are not all bundled with MCreator 2026.3. Their
definitions and API entries remain in the plugin so a matching third-party
generator plugin can use them. The same applies to `fabric-26.2`,
`neoforge-26.1.2` and `neoforge-26.2`, which are declared but are not bundled
with the EAP build used for this port.

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

3. **Optional metadata dependency.**
   The virtual API `procedure_renamer_postprocess` is enabled while the element
   exists so that Run Client also compiles the rewritten sources. MCreator
   writes enabled APIs into mod metadata as optional/suggests entries. The
   dependency does not have to be installed at runtime; it is a metadata side
   effect of the Gradle injection mechanism.

4. **Future and third-party generators.**
   MCreator resolves element definitions by exact generator name. A new
   generator or a third-party Java generator requires an additional
   `<generator>/procedure_rename.definition.yaml` and an entry in
   `apis/procedure_renamer_postprocess.yaml`.

5. **Run Client freshness.**
   The configuration is exported on load, save, element save and build start.
   If the user edits the element and starts Run Client without saving, the run
   uses the last saved configuration.

6. **Case-only class renames.**
   Renames that differ only by case are skipped because Windows file systems
   are case-insensitive and the final archive would be ambiguous.

7. **Generator matrix.**
   ForgeGradle 6 (Gradle 8.14.5), MCreator 2026.1 ModDev/Loom (Gradle 9.4.1)
   and MCreator 2026.3 ModDev/Loom (Gradle 9.8.x) are validated in separate
   representative workspaces. The full 23-generator matrix remains a
   release-gate activity.

8. **Class-only rename is intentionally ignored.**
   A valid class name requires a valid target package. If the target package is
   empty or invalid, the entry makes no change; a valid target package with an
   empty or invalid class name performs a package-only change and keeps the
   generated `<procedureName>Procedure` class name.

9. **Legacy generators in 2026.3.**
   Most 2026.1 generators are not shipped with MCreator 2026.3. The plugin keeps
   their definitions and API hooks, but a real build regression requires the
   corresponding third-party generator plugin to be installed.

10. **Declared but unavailable 2026.3 generators.**
    `fabric-26.2`, `neoforge-26.1.2` and `neoforge-26.2` are not bundled with
    the EAP build used for this port. Their declarations are validated
    statically; real build verification is pending until the matching generator
    plugins are available.

11. **Processor is not an AST.**
    Package-private types referenced across packages and complex same-name
    conflicts may not be repaired automatically.

12. **Configuration cache.**
    The plugin clears the Gradle configuration cache during workspace
    synchronization. Builds may take longer, but a stale cached task graph
    cannot silently skip `procedureRenamePrepare`.

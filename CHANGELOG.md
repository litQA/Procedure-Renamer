# Changelog

## 1.0.6.12-beta

## New Content
- MCreator 2026.3 EAP support, with plugin version identifiers `202600344621` and `2026003`.
- Generator coverage expanded to 23 Java generators: the 14 generators retained from the 2026.1 branch plus Fabric 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 and NeoForge 26.1.2, 26.2, 26.3.
- A 2026.3 compile-only API artifact extracted from the EAP distribution.

## Changes
- The fixed plugin archive name is now `procedure_renamer-2026.3.zip`.
- The plugin build uses the EAP JDK 25 toolchain while emitting Java 21 bytecode.
- README, MIGRATION-REPORT and the workspace target document describe the 2026.3 generator matrix and the retained legacy coverage.

## Known Limitations
- `fabric-26.2`, `neoforge-26.1.2` and `neoforge-26.2` are declared but are not bundled with the EAP build used for this port; real build verification is pending until matching generator plugins are available.
- Most 2026.1 generators are not bundled with MCreator 2026.3 and remain supported through third-party generator plugins.

## 1.0.5.9-beta

## Fixes
Fixed 1 bug(s).
- Cleared stale Gradle configuration cache during workspace synchronization so API, task and script changes cannot be masked by a previously cached task graph.

## 1.0.4.8-beta

## Fixes
Fixed 1 bug(s).
- Set `compileJava.source` to the rewritten shadow root plus other plugin source roots after project evaluation, so ModDev or other plugins cannot re-introduce `src/main/java` and bypass procedure renaming.

## 1.0.3.7-beta

## Fixes
Fixed 1 bug(s).
- Preserved Java source directories added by other plugins (notably optional_packs' `.optional_packs/generated/java`) when compiling the rewritten shadow source set.

## 1.0.2.5-beta

## Changes
- Clarified rename semantics: a valid target package is mandatory; an empty or invalid class name now applies a package-only change, while a class name without a valid target package makes no change.
- The plugin archive filename is now fixed to `procedure_renamer-2026.1.zip`; the version remains in `plugin.json`, source files and commit messages.
- Updated the GUI warnings to explain the package-only and class-only rules.

## 1.0.1.4-beta

## Fixes
Fixed 1 bug(s).
- Corrected the Procedure Rename icon identifier to match the theme asset key, preventing a workspace loading NullPointerException.
- Updated the bundled theme icon from the supplied source image and added the localized element name.

## 1.0.0.3-beta

## New Content
- Added the singleton `Procedure Rename` mod element for MCreator 2026.1 Java generators.
- Added definitions for 14 Java generators and a virtual Gradle API for build integration.
- Added a configuration exporter and automatic workspace API state synchronization.
- Added a build-time post-processor that renames and moves procedures in a shadow source tree and updates references and imports.

## Known Limitations
- The first implementation uses lexical source rewriting and import injection instead of a full Java AST.
- Future or third-party Java generators require additional definitions and API entries.
- The build integration API may appear as an optional mod metadata dependency.
- Run Client uses the configuration from the most recent save.

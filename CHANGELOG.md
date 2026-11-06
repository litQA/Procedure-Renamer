# Changelog

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

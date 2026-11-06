# Changelog

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

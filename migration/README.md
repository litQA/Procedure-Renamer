# Procedure Renamer Legacy Migration Helper

This is a temporary, resource-only MCreator plugin. It restores the removed
`procedure_renamer_postprocess` API definition so workspaces created with older
Procedure Renamer versions can open and let the main plugin clean the legacy
API ID.

## Usage

1. Install `procedure_renamer_migration-2026.1.zip` next to the main
   `procedure_renamer-2026.1.zip` plugin.
2. Restart MCreator and open each affected workspace.
   The main plugin removes `procedure_renamer_postprocess` from
   `mcreatorDependencies`.
3. Save the workspace.
4. Remove the migration helper and restart MCreator.

The helper's API implementations have an empty Gradle block; the helper does
not add build logic or mod metadata dependencies. Do not keep it installed for
normal use.

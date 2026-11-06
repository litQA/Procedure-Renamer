package com.litqa.procedurerenamer.element.types;

import net.mcreator.element.GeneratableElement;
import net.mcreator.workspace.elements.ModElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent configuration element holding the procedure rename entries.
 */
public class ProcedureRename extends GeneratableElement {

    public List<RenameEntry> entries = new ArrayList<>();

    public ProcedureRename(ModElement element) {
        super(element);
    }

    public List<RenameEntry> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public static class RenameEntry {

        public String procedureName;
        public String targetClassName;
        public String targetPackage;

        public RenameEntry() {
        }

        public RenameEntry(String procedureName) {
            this.procedureName = procedureName;
        }
    }
}

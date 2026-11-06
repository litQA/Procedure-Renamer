package com.litqa.procedurerenamer.element;

import com.litqa.procedurerenamer.element.types.ProcedureRename;
import com.litqa.procedurerenamer.ui.ProcedureRenameGUI;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.GeneratorFlavor;

/**
 * Registers the singleton Procedure Rename mod element type.
 */
public final class PluginElementTypes {

    public static ModElementType<?> PROCEDURE_RENAME;

    private PluginElementTypes() {
    }

    public static void load() {
        if (PROCEDURE_RENAME != null) {
            return;
        }

        PROCEDURE_RENAME = ModElementTypeLoader.register(
                new ModElementType<>(
                        "procedure_rename",
                        null,
                        ProcedureRenameGUI::new,
                        ProcedureRename.class
                ).coveredOn(GeneratorFlavor.FORGE, GeneratorFlavor.NEOFORGE, GeneratorFlavor.FABRIC)
        );

        System.out.println("[Procedure Renamer] Registered mod element type procedure_rename");
    }
}

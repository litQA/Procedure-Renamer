package com.litqa.procedurerenamer.util;

import com.litqa.procedurerenamer.element.types.ProcedureRename;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports the plugin configuration and injects the build integration block
 * directly into the workspace build script.
 */
public final class ConfigSync {

    private static final String LEGACY_API_ID = "procedure_renamer_postprocess";

    private static final String CONFIG_DIR = ".procedure_renamer";
    private static final String CONFIG_FILE = "config.json";
    private static final String SCRIPT_FILE = "postprocess.gradle";
    private static final String PLUGIN_JAR_FILE = "plugin-jar.txt";

    private static final String BUILD_BLOCK_START = "// >>> procedure_renamer managed block >>>";
    private static final String BUILD_BLOCK_END = "// <<< procedure_renamer managed block <<<";
    private static final String BUILD_BLOCK = BUILD_BLOCK_START + "\n"
            + "// This block is rewritten by the Procedure Renamer MCreator plugin on every build.\n"
            + "if (file('.procedure_renamer/postprocess.gradle').exists()) {\n"
            + "    apply from: '.procedure_renamer/postprocess.gradle'\n"
            + "}\n"
            + BUILD_BLOCK_END;

    private static File pluginJar;

    private ConfigSync() {
    }

    public static void setPluginJar(File pluginJar) {
        ConfigSync.pluginJar = pluginJar;
    }

    public static void sync(Workspace workspace) {
        if (workspace == null) {
            return;
        }

        try {
            File workspaceFolder = workspace.getWorkspaceFolder();
            if (workspaceFolder == null) {
                return;
            }

            File configDir = new File(workspaceFolder, CONFIG_DIR);
            if (!configDir.exists() && !configDir.mkdirs()) {
                log("Could not create " + configDir.getAbsolutePath());
                return;
            }

            ProcedureRename element = findFirstElement(workspace);
            boolean present = element != null;

            writeScript(configDir);
            writePluginJar(configDir);

            if (present) {
                writeConfig(configDir, element);
            } else {
                deleteConfig(configDir);
            }

            syncBuildScript(workspaceFolder, present);
            removeLegacyApi(workspace);
            clearGradleConfigurationCache(workspace);
        } catch (Exception exception) {
            log("Configuration export failed: " + exception.getMessage());
        }
    }

    private static void clearGradleConfigurationCache(Workspace workspace) {
        try {
            GradleUtils.clearGradleConfigurationCache(workspace);
        } catch (Exception exception) {
            log("Could not clear Gradle configuration cache: " + exception.getMessage());
        }
    }

    private static ProcedureRename findFirstElement(Workspace workspace) {
        ModElement first = null;
        int count = 0;

        for (ModElement element : workspace.getModElements()) {
            ModElementType<?> type = element.getType();
            if (type != null && "procedure_rename".equals(type.getRegistryName())) {
                if (first == null) {
                    first = element;
                }
                count++;
            }
        }

        if (count > 1) {
            log("Found " + count + " procedure_rename elements; only the first one is used");
        }
        if (first == null) {
            return null;
        }

        try {
            GeneratableElement generatableElement = first.getGeneratableElement();
            if (generatableElement instanceof ProcedureRename) {
                return (ProcedureRename) generatableElement;
            }
        } catch (Exception exception) {
            log("Could not read procedure_rename data: " + exception.getMessage());
        }
        return null;
    }

    private static void writeScript(File configDir) {
        String script = readResource("/gradle/postprocess.gradle");
        if (script == null) {
            log("Missing built-in postprocess.gradle resource");
            return;
        }
        writeIfChanged(new File(configDir, SCRIPT_FILE), script);
    }

    private static void writePluginJar(File configDir) {
        if (pluginJar == null || (!pluginJar.isFile() && !pluginJar.isDirectory())) {
            new File(configDir, PLUGIN_JAR_FILE).delete();
            return;
        }
        writeIfChanged(new File(configDir, PLUGIN_JAR_FILE), pluginJar.getAbsolutePath());
    }

    private static String readResource(String path) {
        try (InputStream stream = ConfigSync.class.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log("Could not read resource " + path + ": " + exception.getMessage());
            return null;
        }
    }

    private static void writeConfig(File configDir, ProcedureRename element) {
        List<String> jsonEntries = new ArrayList<>();

        for (ProcedureRename.RenameEntry entry : element.getEntries()) {
            if (entry == null) {
                continue;
            }
            jsonEntries.add("    {\n"
                    + "      \"procedureName\": \"" + escape(entry.procedureName) + "\",\n"
                    + "      \"targetClassName\": \"" + escape(entry.targetClassName) + "\",\n"
                    + "      \"targetPackage\": \"" + escape(entry.targetPackage) + "\"\n"
                    + "    }");
        }

        String json = "{\n  \"entries\": [\n"
                + String.join(",\n", jsonEntries)
                + (jsonEntries.isEmpty() ? "" : "\n")
                + "  ]\n}\n";

        writeIfChanged(new File(configDir, CONFIG_FILE), json);
    }

    private static void deleteConfig(File configDir) {
        new File(configDir, CONFIG_FILE).delete();
        new File(configDir, SCRIPT_FILE).delete();
    }

    private static void syncBuildScript(File workspaceFolder, boolean present) {
        File buildGradle = new File(workspaceFolder, "build.gradle");
        if (!buildGradle.isFile()) {
            log("Workspace build.gradle not found; build integration not injected");
            return;
        }

        try {
            String text = Files.readString(buildGradle.toPath(), StandardCharsets.UTF_8);
            String withoutManagedBlock = removeManagedBlock(text);
            boolean hadBlock = !withoutManagedBlock.equals(text);

            if (!present && !hadBlock) {
                return;
            }

            String updated;
            if (present) {
                String base = withoutManagedBlock.replaceAll("\\s+$", "");
                updated = (base.isEmpty() ? "" : base + "\n\n") + BUILD_BLOCK + "\n";
            } else {
                updated = withoutManagedBlock.replaceAll("\\s+$", "") + "\n";
            }

            writeIfChanged(buildGradle, updated);
        } catch (IOException exception) {
            log("Could not update workspace build.gradle: " + exception.getMessage());
        }
    }

    private static String removeManagedBlock(String text) {
        int start;
        while ((start = text.indexOf(BUILD_BLOCK_START)) >= 0) {
            int end = text.indexOf(BUILD_BLOCK_END, start);
            if (end < 0) {
                break;
            }
            end += BUILD_BLOCK_END.length();
            text = text.substring(0, start) + text.substring(end);
        }
        return text;
    }

    private static void removeLegacyApi(Workspace workspace) {
        WorkspaceSettings settings = workspace.getWorkspaceSettings();
        if (settings == null) {
            return;
        }

        Set<String> current = settings.getMCreatorDependenciesRaw();
        if (current == null || !current.contains(LEGACY_API_ID)) {
            return;
        }

        Set<String> updated = new LinkedHashSet<>(current);
        updated.remove(LEGACY_API_ID);
        settings.setMCreatorDependencies(updated);
        workspace.markDirty();
        log("Removed legacy virtual API ID " + LEGACY_API_ID);
    }

    private static void writeIfChanged(File file, String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (file.exists() && java.util.Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
                return;
            }
            Files.write(file.toPath(), bytes);
        } catch (IOException exception) {
            log("Could not write " + file.getAbsolutePath() + ": " + exception.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }
        return result.toString();
    }

    private static void log(String message) {
        System.out.println("[Procedure Renamer] " + message);
    }
}

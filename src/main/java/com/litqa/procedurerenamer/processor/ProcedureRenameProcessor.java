package com.litqa.procedurerenamer.processor;

import com.litqa.procedurerenamer.util.NameRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Standalone build-time processor. It is launched by the Gradle helper script
 * with the plugin archive on the classpath and never references MCreator
 * classes, so it can be tested with a plain JDK.
 */
public final class ProcedureRenameProcessor {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][\\w$.]*)\\s*;");
    private static final Pattern PACKAGE_DECLARATION_PATTERN = Pattern.compile(
            "(?m)^(\\s*package\\s+)([A-Za-z_$][\\w$.]*)(\\s*;)");

    private final Path projectDir;
    private final Path buildDir;
    private final Path configFile;
    private final Path sourceRoot;
    private final Path shadowDir;
    private final Path buildConfigFile;
    private final Path hashFile;
    private final Path classesDir;

    private boolean success;
    private boolean fallbackDone;

    private ProcedureRenameProcessor(Path projectDir, Path buildDir, Path configFile) {
        this.projectDir = projectDir;
        this.buildDir = buildDir;
        this.configFile = configFile;
        this.sourceRoot = projectDir.resolve("src/main/java");
        this.shadowDir = buildDir.resolve("procedure_renamer/java");
        this.buildConfigFile = buildDir.resolve("procedure_rename_config.json");
        this.hashFile = shadowDir.resolve(".meta/config.sha256");
        this.classesDir = buildDir.resolve("classes/java/main");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("[Procedure Renamer] Usage: <projectDir> <buildDir> [configFile]");
            System.exit(2);
            return;
        }

        Path projectDir = Paths.get(args[0]);
        Path buildDir = Paths.get(args[1]);
        Path configFile = args.length >= 3
                ? Paths.get(args[2])
                : projectDir.resolve(".procedure_renamer/config.json");

        ProcedureRenameProcessor processor = new ProcedureRenameProcessor(projectDir, buildDir, configFile);
        try {
            processor.process();
            processor.success = true;
        } catch (Throwable throwable) {
            processor.log("Post-processing failed: " + throwable);
            processor.fallback();
        }
        System.exit(processor.success || processor.fallbackDone ? 0 : 1);
    }

    private void process() throws IOException {
        String previousHash = readPreviousHash();

        deleteTree(shadowDir);
        Files.createDirectories(shadowDir);
        copyTree(sourceRoot, shadowDir);

        if (!Files.isRegularFile(configFile)) {
            Files.deleteIfExists(buildConfigFile);
            if (previousHash != null) {
                resetCompiledClasses();
            }
            log("No configuration found; using native generated sources");
            return;
        }

        Files.createDirectories(buildConfigFile.getParent());
        Files.copy(configFile, buildConfigFile, StandardCopyOption.REPLACE_EXISTING);

        byte[] configBytes = Files.readAllBytes(configFile);
        String hash = sha256(configBytes);
        boolean hashChanged = !hash.equals(previousHash);

        List<Map<String, String>> entries;
        try {
            Object parsed = new JsonParser(new String(configBytes, StandardCharsets.UTF_8)).parseDocument();
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("entries") instanceof List<?> rawEntries)) {
                log("Configuration has no entries array; using native generated sources");
                if (hashChanged) {
                    resetCompiledClasses();
                }
                return;
            }
            entries = new ArrayList<>();
            for (Object rawEntry : rawEntries) {
                if (rawEntry instanceof Map<?, ?> map) {
                    entries.add(stringMap(map));
                }
            }
        } catch (RuntimeException exception) {
            log("Invalid configuration JSON: " + exception.getMessage());
            if (hashChanged) {
                resetCompiledClasses();
            }
            return;
        }

        Map<Path, String> texts = readJavaFiles();
        List<Plan> plans = buildPlans(entries, texts);
        if (plans.isEmpty()) {
            log("No valid entries; using native generated sources");
            if (hashChanged) {
                resetCompiledClasses();
            }
            writeHash(hash);
            return;
        }

        int skipped = entries.size() - plans.size();
        applyPlans(plans, texts);

        if (hashChanged) {
            resetCompiledClasses();
        }
        writeHash(hash);

        log("Processed " + plans.size() + " entr" + (plans.size() == 1 ? "y" : "ies")
                + ", skipped " + skipped);
        for (Plan plan : plans) {
            log("Renamed " + plan.oldFqn() + " -> " + plan.newFqn());
        }
    }

    private String readPreviousHash() {
        try {
            return Files.isRegularFile(hashFile) ? Files.readString(hashFile, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private void writeHash(String hash) {
        try {
            Files.createDirectories(hashFile.getParent());
            Files.writeString(hashFile, hash, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log("Could not write configuration hash: " + exception.getMessage());
        }
    }

    private Map<Path, String> readJavaFiles() throws IOException {
        Map<Path, String> texts = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(shadowDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            texts.put(path, Files.readString(path, StandardCharsets.UTF_8));
                        } catch (IOException exception) {
                            throw new IllegalStateException("Could not read " + path, exception);
                        }
                    });
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        return texts;
    }

    private List<Plan> buildPlans(List<Map<String, String>> entries, Map<Path, String> texts) {
        List<Plan> plans = new ArrayList<>();
        Set<String> usedTargets = new HashSet<>();
        Set<String> usedProcedures = new HashSet<>();

        List<Path> procedureFiles = texts.keySet().stream()
                .filter(path -> path.getParent() != null && "procedures".equals(path.getParent().getFileName().toString()))
                .filter(path -> path.getFileName().toString().endsWith("Procedure.java"))
                .sorted()
                .toList();

        for (Map<String, String> entry : entries) {
            String procedureName = value(entry, "procedureName");
            String targetClassName = value(entry, "targetClassName");
            String targetPackage = value(entry, "targetPackage");

            if (procedureName.isEmpty()) {
                log("Skipped an entry without a procedure name");
                continue;
            }
            if (targetPackage.isEmpty()) {
                log("Skipped " + procedureName + ": a valid target package is required; class name alone is ignored");
                continue;
            }
            if (!NameRules.isValidPackage(targetPackage)) {
                log("Skipped " + procedureName + ": invalid target package '" + targetPackage + "'; no changes");
                continue;
            }

            boolean validClass = !targetClassName.isEmpty() && NameRules.isValidClassName(targetClassName);
            if (!targetClassName.isEmpty() && !validClass) {
                log("Ignoring invalid target class name for " + procedureName + "; applying package-only change");
            } else if (targetClassName.isEmpty()) {
                log("Applying package-only change for " + procedureName);
            }

            String oldClass = procedureName + "Procedure";
            List<Path> matches = procedureFiles.stream()
                    .filter(path -> path.getFileName().toString().equals(oldClass + ".java"))
                    .toList();
            if (matches.size() != 1) {
                log("Skipped " + procedureName + ": generated procedure file not found or ambiguous");
                continue;
            }

            Path source = matches.get(0);
            String text = texts.get(source);
            String oldPackage = parsePackage(text);
            if (oldPackage == null) {
                log("Skipped " + procedureName + ": generated file has no package declaration");
                continue;
            }
            if (!Pattern.compile("\\bclass\\s+" + Pattern.quote(oldClass) + "\\b").matcher(text).find()) {
                log("Skipped " + procedureName + ": class " + oldClass + " not found in generated file");
                continue;
            }

            String newClass = validClass ? targetClassName : oldClass;
            String newPackage = targetPackage;

            if (oldClass.equals(newClass) && oldPackage.equals(newPackage)) {
                log("Skipped " + procedureName + ": target is identical to the generated name");
                continue;
            }
            if (oldClass.equalsIgnoreCase(newClass) && !oldClass.equals(newClass)) {
                log("Skipped " + procedureName + ": case-only class renames are not supported on Windows");
                continue;
            }
            if (oldPackage.equalsIgnoreCase(newPackage) && !oldPackage.equals(newPackage)) {
                log("Skipped " + procedureName + ": case-only package renames are not supported on Windows");
                continue;
            }

            String newFqn = newPackage + "." + newClass;
            String oldFqn = oldPackage + "." + oldClass;
            Path target = shadowDir.resolve(newPackage.replace('.', '/')).resolve(newClass + ".java");

            if (Files.exists(target) && !target.equals(source)) {
                log("Skipped " + procedureName + ": target file already exists: " + newFqn);
                continue;
            }
            if (!usedTargets.add(newFqn)) {
                log("Skipped " + procedureName + ": duplicate target " + newFqn);
                continue;
            }

            Plan plan = new Plan(source, target, oldPackage, newPackage, oldClass, newClass);
            if (hasConflict(plan, texts)) {
                usedTargets.remove(newFqn);
                log("Skipped " + procedureName + ": conflicting class/import references detected");
                continue;
            }
            if (!usedProcedures.add(procedureName)) {
                log("Skipped duplicate procedure entry: " + procedureName);
                continue;
            }
            plans.add(plan);
        }
        return plans;
    }

    private boolean hasConflict(Plan plan, Map<Path, String> texts) {
        Pattern importNew = Pattern.compile(
                "(?m)^\\s*import\\s+(?!static\\s+)([\\w.]+)\\." + Pattern.quote(plan.newClass) + "\\s*;");
        Pattern importOld = Pattern.compile(
                "(?m)^\\s*import\\s+(?!static\\s+)([\\w.]+)\\." + Pattern.quote(plan.oldClass) + "\\s*;");
        Pattern typeNew = Pattern.compile(
                "\\b(class|interface|enum|record)\\s+" + Pattern.quote(plan.newClass) + "\\b");
        Pattern typeOld = Pattern.compile(
                "\\b(class|interface|enum|record)\\s+" + Pattern.quote(plan.oldClass) + "\\b");

        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            if (entry.getKey().equals(plan.source)) {
                continue;
            }
            String text = entry.getValue();
            Matcher matcher = importNew.matcher(text);
            if (matcher.find()) {
                String importedPackage = matcher.group(1);
                boolean ownOldClassImport = plan.newClass.equals(plan.oldClass)
                        && importedPackage.equals(plan.oldPackage);
                if (!importedPackage.equals(plan.newPackage) && !ownOldClassImport) {
                    return true;
                }
            }
            matcher = importOld.matcher(text);
            if (matcher.find() && !matcher.group(1).equals(plan.oldPackage)) {
                return true;
            }
            if (typeNew.matcher(text).find() || typeOld.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private void applyPlans(List<Plan> plans, Map<Path, String> texts) throws IOException {
        Map<String, String> replacements = new HashMap<>();
        for (Plan plan : plans) {
            replacements.put(plan.oldFqn(), plan.newFqn());
            replacements.put(plan.oldClass, plan.newClass);
            String sourceText = texts.get(plan.source);
            if (!plan.oldPackage.equals(plan.newPackage)) {
                texts.put(plan.source, replacePackage(sourceText, plan.oldPackage, plan.newPackage));
            }
        }

        Pattern replacementPattern = buildReplacementPattern(replacements);
        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            entry.setValue(replaceInCode(entry.getValue(), replacementPattern, replacements));
        }

        Set<Path> planSources = new HashSet<>();
        for (Plan plan : plans) {
            planSources.add(plan.source);
        }

        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            if (planSources.contains(entry.getKey())) {
                continue;
            }
            String withImports = insertImports(entry.getValue(), entry.getKey(), plans, texts);
            entry.setValue(withImports);
            Files.writeString(entry.getKey(), withImports, StandardCharsets.UTF_8);
        }

        for (Plan plan : plans) {
            String content = insertImports(texts.get(plan.source), plan.source, plans, texts);
            Files.createDirectories(plan.target.getParent());
            Files.writeString(plan.target, content, StandardCharsets.UTF_8);
        }
        for (Plan plan : plans) {
            Files.deleteIfExists(plan.source);
        }
    }

    private Pattern buildReplacementPattern(Map<String, String> replacements) {
        List<String> keys = new ArrayList<>(replacements.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alternatives = new StringBuilder();
        for (String key : keys) {
            if (alternatives.length() > 0) {
                alternatives.append('|');
            }
            alternatives.append(Pattern.quote(key));
        }
        return Pattern.compile("(?<![\\w$.])(?:" + alternatives + ")(?![\\w$])");
    }

    private String replacePackage(String text, String oldPackage, String newPackage) {
        Matcher matcher = PACKAGE_DECLARATION_PATTERN.matcher(text);
        if (matcher.find() && matcher.group(2).equals(oldPackage)) {
            return matcher.replaceFirst(
                    "$1" + Matcher.quoteReplacement(newPackage) + "$3");
        }
        return text;
    }

    private String replaceInCode(String text, Pattern pattern, Map<String, String> replacements) {
        boolean[] code = codeMask(text);
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        while (matcher.find()) {
            String replacement = matcher.group();
            if (matcher.start() < code.length && code[matcher.start()]) {
                replacement = replacements.getOrDefault(replacement, replacement);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String insertImports(String text, Path file, List<Plan> plans, Map<Path, String> texts) {
        String filePackage = parsePackage(text);
        if (filePackage == null) {
            return text;
        }

        Plan ownPlan = null;
        for (Plan plan : plans) {
            if (plan.source.equals(file)) {
                ownPlan = plan;
                break;
            }
        }

        Set<String> imports = new TreeSet<>();
        for (Plan plan : plans) {
            if (plan == ownPlan) {
                continue;
            }
            if (filePackage.equals(plan.newPackage)) {
                continue;
            }
            if (hasImport(text, plan.newFqn()) || hasWildcardImport(text, plan.newPackage)) {
                continue;
            }
            if (containsCodeIdentifier(text, plan.newClass)) {
                imports.add("import " + plan.newFqn() + ";");
            }
        }

        if (ownPlan != null && !ownPlan.oldPackage.equals(ownPlan.newPackage)) {
            Set<String> newPackageTypes = collectPublicTypes(texts, ownPlan.newPackage);
            for (String oldType : collectPublicTypes(texts, ownPlan.oldPackage)) {
                if (oldType.equals(ownPlan.oldClass) || oldType.equals(ownPlan.newClass)) {
                    continue;
                }
                if (isOldTypeMoved(plans, ownPlan.oldPackage, oldType)) {
                    continue;
                }
                if (newPackageTypes.contains(oldType)) {
                    log("Skipped old-package import " + oldType + ": the target package already declares that name");
                    continue;
                }
                if (hasImport(text, ownPlan.oldPackage + "." + oldType)
                        || hasWildcardImport(text, ownPlan.oldPackage)) {
                    continue;
                }
                if (containsCodeIdentifier(text, oldType)) {
                    imports.add("import " + ownPlan.oldPackage + "." + oldType + ";");
                }
            }
        }

        if (imports.isEmpty()) {
            return text;
        }

        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        int insertAt = matcher.end();
        return text.substring(0, insertAt)
                + "\n\n"
                + String.join("\n", imports)
                + text.substring(insertAt);
    }

    private Set<String> collectPublicTypes(Map<Path, String> texts, String packageName) {
        Set<String> types = new TreeSet<>();
        Pattern publicType = Pattern.compile(
                "(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                        + "(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            String text = entry.getValue();
            if (!packageName.equals(parsePackage(text))) {
                continue;
            }
            Matcher matcher = publicType.matcher(text);
            if (matcher.find()) {
                types.add(matcher.group(1));
            }
        }
        return types;
    }

    private boolean isOldTypeMoved(List<Plan> plans, String oldPackage, String oldClass) {
        for (Plan plan : plans) {
            if (plan.oldPackage.equals(oldPackage) && plan.oldClass.equals(oldClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasImport(String text, String fqn) {
        return Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(fqn) + "\\s*;").matcher(text).find();
    }

    private boolean hasWildcardImport(String text, String packageName) {
        return Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(packageName) + "\\.\\*\\s*;")
                .matcher(text).find();
    }

    private boolean containsCodeIdentifier(String text, String identifier) {
        Pattern pattern = Pattern.compile("(?<![\\w$.])" + Pattern.quote(identifier) + "(?![\\w$])");
        boolean[] code = codeMask(text);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.start() < code.length && code[matcher.start()]) {
                return true;
            }
        }
        return false;
    }

    private boolean[] codeMask(String text) {
        boolean[] code = new boolean[text.length()];
        int index = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean string = false;
        boolean character = false;
        boolean textBlock = false;

        while (index < text.length()) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    code[index] = true;
                }
                index++;
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    index += 2;
                    blockComment = false;
                } else {
                    index++;
                }
                continue;
            }
            if (textBlock) {
                if (current == '"' && next == '"' && index + 2 < text.length() && text.charAt(index + 2) == '"') {
                    index += 3;
                    textBlock = false;
                } else {
                    index++;
                }
                continue;
            }
            if (string) {
                if (current == '\\') {
                    index += 2;
                } else if (current == '"') {
                    string = false;
                    index++;
                } else {
                    index++;
                }
                continue;
            }
            if (character) {
                if (current == '\\') {
                    index += 2;
                } else if (current == '\'') {
                    character = false;
                    index++;
                } else {
                    index++;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                lineComment = true;
                index += 2;
                continue;
            }
            if (current == '/' && next == '*') {
                blockComment = true;
                index += 2;
                continue;
            }
            if (current == '"' && next == '"' && index + 2 < text.length() && text.charAt(index + 2) == '"') {
                textBlock = true;
                index += 3;
                continue;
            }
            if (current == '"') {
                string = true;
                index++;
                continue;
            }
            if (current == '\'') {
                character = true;
                index++;
                continue;
            }
            code[index] = true;
            index++;
        }
        return code;
    }

    private String parsePackage(String text) {
        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void resetCompiledClasses() {
        try {
            deleteTree(classesDir);
            log("Cleared stale compiled classes to avoid old procedure class files");
        } catch (IOException exception) {
            log("Could not clear compiled classes: " + exception.getMessage());
        }
    }

    private void fallback() {
        try {
            deleteTree(shadowDir);
            Files.createDirectories(shadowDir);
            copyTree(sourceRoot, shadowDir);
            resetCompiledClasses();
            fallbackDone = true;
            log("Fell back to native generated sources");
        } catch (IOException exception) {
            log("Fallback failed: " + exception.getMessage());
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            Files.createDirectories(target);
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(result.length * 2);
            for (byte value : result) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private Map<String, String> stringMap(Map<?, ?> map) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof String value) {
                result.put(String.valueOf(entry.getKey()), value);
            }
        }
        return result;
    }

    private String value(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value.trim();
    }

    private void log(String message) {
        System.out.println("[Procedure Renamer] " + message);
    }

    private static final class Plan {
        private final Path source;
        private final Path target;
        private final String oldPackage;
        private final String newPackage;
        private final String oldClass;
        private final String newClass;

        private Plan(Path source, Path target, String oldPackage, String newPackage,
                String oldClass, String newClass) {
            this.source = source;
            this.target = target;
            this.oldPackage = oldPackage;
            this.newPackage = newPackage;
            this.oldClass = oldClass;
            this.newClass = newClass;
        }

        private String oldFqn() {
            return oldPackage + "." + oldClass;
        }

        private String newFqn() {
            return newPackage + "." + newClass;
        }
    }

    /**
     * Minimal JSON parser for the plugin's flat configuration format.
     */
    private static final class JsonParser {
        private final String input;
        private int position;

        private JsonParser(String input) {
            this.input = input;
        }

        private Object parseDocument() {
            Object value = parseValue();
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException("Trailing characters at " + position);
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char current = input.charAt(position);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++;
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            position++;
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') {
                    return result.toString();
                }
                if (current == '\\') {
                    if (position >= input.length()) {
                        throw new IllegalArgumentException("Invalid escape");
                    }
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"' -> result.append('"');
                        case '\\' -> result.append('\\');
                        case '/' -> result.append('/');
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (position + 4 > input.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape");
                            }
                            String hex = input.substring(position, position + 4);
                            result.append((char) Integer.parseInt(hex, 16));
                            position += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape: " + escaped);
                    }
                } else {
                    result.append(current);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, position)) {
                throw new IllegalArgumentException("Invalid literal at " + position);
            }
            position += literal.length();
            return value;
        }

        private Object parseNumber() {
            int start = position;
            while (position < input.length()) {
                char current = input.charAt(position);
                if ((current >= '0' && current <= '9') || current == '-' || current == '+'
                        || current == '.' || current == 'e' || current == 'E') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new IllegalArgumentException("Unexpected character at " + position);
            }
            return input.substring(start, position);
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private void expect(char character) {
            if (position >= input.length() || input.charAt(position) != character) {
                throw new IllegalArgumentException("Expected '" + character + "' at " + position);
            }
            position++;
        }

        private boolean consume(char character) {
            if (position < input.length() && input.charAt(position) == character) {
                position++;
                return true;
            }
            return false;
        }
    }
}

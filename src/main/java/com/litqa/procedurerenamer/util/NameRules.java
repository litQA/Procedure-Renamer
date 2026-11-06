package com.litqa.procedurerenamer.util;

import javax.lang.model.SourceVersion;
import java.util.regex.Pattern;

/**
 * Shared validation rules for class names and packages.
 */
public final class NameRules {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern PACKAGE = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*");

    private NameRules() {
    }

    public static boolean isValidClassName(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return IDENTIFIER.matcher(value).matches() && !SourceVersion.isKeyword(value);
    }

    public static boolean isValidPackage(String value) {
        if (value == null || value.isEmpty() || !PACKAGE.matcher(value).matches()) {
            return false;
        }
        if (value.equals("java") || value.startsWith("java.")
                || value.equals("javax") || value.startsWith("javax.")) {
            return false;
        }
        for (String segment : value.split("\\.")) {
            if (!IDENTIFIER.matcher(segment).matches() || SourceVersion.isKeyword(segment)) {
                return false;
            }
        }
        return true;
    }
}

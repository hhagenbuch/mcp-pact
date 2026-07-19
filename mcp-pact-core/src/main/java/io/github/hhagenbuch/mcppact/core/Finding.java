package io.github.hhagenbuch.mcppact.core;

/**
 * A single classified difference between a pact and a provider.
 *
 * @param severity how it is classified
 * @param tool     the tool the finding concerns (or {@code "*"} for whole-server findings)
 * @param rule     a short stable code for the rule that fired, e.g. {@code "tool.missing"}
 * @param detail   a human-readable explanation
 */
public record Finding(Severity severity, String tool, String rule, String detail) {

    public static Finding breaking(String tool, String rule, String detail) {
        return new Finding(Severity.BREAKING, tool, rule, detail);
    }

    public static Finding warn(String tool, String rule, String detail) {
        return new Finding(Severity.WARN, tool, rule, detail);
    }

    public static Finding compat(String tool, String rule, String detail) {
        return new Finding(Severity.COMPAT, tool, rule, detail);
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + tool + " (" + rule + "): " + detail;
    }
}

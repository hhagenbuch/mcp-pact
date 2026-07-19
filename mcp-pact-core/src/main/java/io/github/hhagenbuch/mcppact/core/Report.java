package io.github.hhagenbuch.mcppact.core;

import java.util.List;

/**
 * The outcome of verifying a pact against a provider: all findings plus the
 * gate decision. {@code exitCode(strict)} is what the CLI returns — nonzero on
 * any {@code BREAKING}, and also on {@code WARN} when {@code strict}.
 */
public record Report(List<Finding> findings) {

    public Report {
        findings = List.copyOf(findings);
    }

    public long count(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    public boolean hasBreaking() {
        return count(Severity.BREAKING) > 0;
    }

    public boolean hasWarnings() {
        return count(Severity.WARN) > 0;
    }

    /** 0 = pass. Nonzero on BREAKING always, and on WARN when {@code strict}. */
    public int exitCode(boolean strict) {
        if (hasBreaking()) {
            return 1;
        }
        return strict && hasWarnings() ? 1 : 0;
    }

    public String summary() {
        return "%d breaking, %d warn, %d compat".formatted(
                count(Severity.BREAKING), count(Severity.WARN), count(Severity.COMPAT));
    }
}

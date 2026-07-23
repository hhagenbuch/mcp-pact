package io.github.hhagenbuch.mcppact.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.Severity;

import java.io.UncheckedIOException;

/** Renders a {@link Report} for humans or as machine-readable JSON. */
public final class ReportFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReportFormatter() {
    }

    public static String human(Report report, String consumer, String provider) {
        StringBuilder out = new StringBuilder();
        out.append("mcp-pact: ").append(consumer).append(" → ").append(provider).append('\n');
        if (report.findings().isEmpty()) {
            out.append("  ✓ no differences — contract holds\n");
        } else {
            for (Severity severity : new Severity[]{Severity.BREAKING, Severity.WARN, Severity.COMPAT}) {
                for (Finding finding : report.findings()) {
                    if (finding.severity() == severity) {
                        out.append("  ").append(marker(severity)).append(' ')
                                .append(severity).append(' ')
                                .append(finding.tool()).append(" (").append(finding.rule()).append("): ")
                                .append(finding.detail()).append('\n');
                    }
                }
            }
        }
        out.append("  ── ").append(report.summary());
        return out.toString();
    }

    public static String json(Report report, String consumer, String provider) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("consumer", consumer);
        root.put("provider", provider);
        root.put("breaking", report.count(Severity.BREAKING));
        root.put("warn", report.count(Severity.WARN));
        root.put("compat", report.count(Severity.COMPAT));
        ArrayNode findings = root.putArray("findings");
        for (Finding finding : report.findings()) {
            ObjectNode node = findings.addObject();
            node.put("severity", finding.severity().name());
            node.put("tool", finding.tool());
            node.put("rule", finding.rule());
            node.put("detail", finding.detail());
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }

    /**
     * Renders findings as GitHub Actions annotation lines.
     * BREAKING → {@code ::error}, WARN → {@code ::warning}, COMPAT → ignored.
     * The human summary is still printed to stdout; annotations are additive.
     */
    public static String github(Report report, String consumer, String provider) {
        StringBuilder out = new StringBuilder();
        out.append("mcp-pact: ").append(consumer).append(" → ").append(provider).append('\n');
        boolean hasAnnotations = report.findings().stream()
                .anyMatch(f -> f.severity() == Severity.BREAKING || f.severity() == Severity.WARN);
        if (!hasAnnotations) {
            out.append("  ✓ no differences — contract holds\n");
        } else {
            for (Finding finding : report.findings()) {
                switch (finding.severity()) {
                    case BREAKING -> out.append("::error title=mcp-pact: ")
                            .append(finding.tool()).append(" (").append(finding.rule()).append(")::")
                            .append(finding.detail()).append('\n');
                    case WARN -> out.append("::warning title=mcp-pact: ")
                            .append(finding.tool()).append(" (").append(finding.rule()).append(")::")
                            .append(finding.detail()).append('\n');
                    default -> { /* COMPAT — informational, no annotation */ }
                }
            }
        }
        out.append("  ── ").append(report.summary());
        return out.toString();
    }

    private static String marker(Severity severity) {
        return switch (severity) {
            case BREAKING -> "✗";
            case WARN -> "⚠";
            case COMPAT -> "+";
        };
    }
}

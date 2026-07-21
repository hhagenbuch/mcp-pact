package io.github.hhagenbuch.mcppact.verifier;

import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFormatterTest {

    private static final String CONSUMER = "test-consumer";
    private static final String PROVIDER = "test-provider";

    @Test
    void githubFormatEmitsErrorForBreaking() {
        Report report = new Report(List.of(
                Finding.breaking("search", "tool.missing", "tool not found")
        ));
        String output = ReportFormatter.github(report, CONSUMER, PROVIDER);
        assertThat(output).contains("::error title=mcp-pact: search (tool.missing)::tool not found");
    }

    @Test
    void githubFormatEmitsWarningForWarn() {
        Report report = new Report(List.of(
                Finding.warn("search", "description.changed", "description drifted")
        ));
        String output = ReportFormatter.github(report, CONSUMER, PROVIDER);
        assertThat(output).contains("::warning title=mcp-pact: search (description.changed)::description drifted");
    }

    @Test
    void githubFormatSkipsCompat() {
        Report report = new Report(List.of(
                Finding.compat("search", "tool.added", "new tool added")
        ));
        String output = ReportFormatter.github(report, CONSUMER, PROVIDER);
        assertThat(output).doesNotContain("::error").doesNotContain("::warning");
        assertThat(output).contains("no differences");
    }

    @Test
    void githubFormatShowsSummary() {
        Report report = new Report(List.of(
                Finding.breaking("a", "rule1", "detail1"),
                Finding.warn("b", "rule2", "detail2")
        ));
        String output = ReportFormatter.github(report, CONSUMER, PROVIDER);
        assertThat(output).contains("── 1 breaking, 1 warn, 0 compat");
    }

    @Test
    void githubFormatEmptyReport() {
        Report report = new Report(List.of());
        String output = ReportFormatter.github(report, CONSUMER, PROVIDER);
        assertThat(output).contains("✓ no differences — contract holds");
    }
}

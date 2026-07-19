package io.github.hhagenbuch.mcppact.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTest {

    @Test
    void cleanReportPasses() {
        Report report = new Report(List.of());
        assertThat(report.exitCode(false)).isZero();
        assertThat(report.exitCode(true)).isZero();
    }

    @Test
    void breakingFailsAlways() {
        Report report = new Report(List.of(Finding.breaking("t", "tool.missing", "gone")));
        assertThat(report.hasBreaking()).isTrue();
        assertThat(report.exitCode(false)).isEqualTo(1);
        assertThat(report.exitCode(true)).isEqualTo(1);
    }

    @Test
    void warnFailsOnlyInStrictMode() {
        Report report = new Report(List.of(Finding.warn("t", "tool.description", "drift")));
        assertThat(report.exitCode(false)).isZero();
        assertThat(report.exitCode(true)).isEqualTo(1);
    }

    @Test
    void compatIsAlwaysInformational() {
        Report report = new Report(List.of(Finding.compat("t", "tool.new", "added")));
        assertThat(report.exitCode(false)).isZero();
        assertThat(report.exitCode(true)).isZero();
        assertThat(report.summary()).isEqualTo("0 breaking, 0 warn, 1 compat");
    }
}

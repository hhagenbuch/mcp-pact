package io.github.hhagenbuch.mcppact.verifier;

/** Output format for the verify command. */
enum OutputFormat {
    /** Human-readable text with Unicode markers. */
    HUMAN,
    /** Machine-readable JSON. */
    JSON,
    /** GitHub Actions annotation lines (::error / ::warning). */
    GITHUB
}

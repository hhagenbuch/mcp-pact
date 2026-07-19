package io.github.hhagenbuch.mcppact.core;

/**
 * How a piece of provider drift is classified, semver-style.
 *
 * <ul>
 *   <li>{@link #BREAKING} — the consumer's recorded dependency is violated; fail the build.</li>
 *   <li>{@link #WARN} — schema-true but behavior-changing (e.g. tool description drift).</li>
 *   <li>{@link #COMPAT} — backwards-compatible addition; informational.</li>
 * </ul>
 */
public enum Severity {
    COMPAT,
    WARN,
    BREAKING
}

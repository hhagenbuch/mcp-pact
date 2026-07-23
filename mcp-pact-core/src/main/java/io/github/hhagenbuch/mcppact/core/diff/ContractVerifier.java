package io.github.hhagenbuch.mcppact.core.diff;

import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;
import io.github.hhagenbuch.mcppact.core.model.ServerTool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a {@link Pact} against a {@link ServerSnapshot} and classifies every
 * difference as BREAKING / COMPAT / WARN. Does no I/O; interaction replay is
 * layered on top in {@code mcp-pact-verifier} using the {@code matcher} engine.
 *
 * <p>Guiding invariant: <em>we either understood a change or we flagged it.</em>
 * The diff models a shallow slice of JSON Schema precisely (top-level property
 * presence, required-ness, and plain string types); anything deeper or
 * unrecognized degrades to a WARN rather than passing silently.
 */
public final class ContractVerifier {

    /**
     * Description rewrites with token-overlap at or above this Jaccard similarity
     * are treated as immaterial (reordering, punctuation, minor edits) and not
     * flagged. Below it, the reword is reported as WARN. See DESIGN.md §3.
     */
    private static final double DESCRIPTION_MATERIALITY = 0.8;

    private ContractVerifier() {
    }

    public static List<Finding> diff(Pact pact, ServerSnapshot snapshot) {
        List<Finding> findings = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();

        for (Expectation expectation : pact.expectations()) {
            String tool = expectation.tool();
            usedTools.add(tool);
            ServerTool server = snapshot.tools().get(tool);

            if (server == null) {
                findings.add(Finding.breaking(tool, "tool.missing",
                        "tool used by the pact is not advertised by the server (missing or renamed)"));
                continue;
            }

            checkDescription(findings, tool, expectation, server);
            checkCapabilities(findings, tool, expectation, snapshot);
            checkInputSchema(findings, tool, expectation, server);
        }

        // Tools the server offers that no expectation covers are backwards-compatible
        // additions — collapsed into one finding so a large server can't drown the report.
        List<String> uncovered = snapshot.tools().keySet().stream()
                .filter(name -> !usedTools.contains(name))
                .toList();
        if (!uncovered.isEmpty()) {
            String detail = "server advertises " + uncovered.size() + " tool(s) not covered by the pact"
                    + (uncovered.size() <= 10 ? ": " + String.join(", ", uncovered) : "");
            findings.add(Finding.compat("*", "tool.new", detail));
        }
        return findings;
    }

    private static void checkDescription(List<Finding> findings, String tool,
                                         Expectation expectation, ServerTool server) {
        String recorded = expectation.description();
        String current = server.description();
        if (recorded == null) {
            return; // nothing was recorded to compare against
        }
        if (current == null) {
            findings.add(Finding.warn(tool, "tool.descriptionRemoved",
                    "tool description was removed since the pact was recorded — "
                            + "a missing description changes model behavior"));
            return;
        }
        if (isMaterialRewrite(recorded, current)) {
            findings.add(Finding.warn(tool, "tool.description",
                    "tool description changed materially since the pact was recorded — "
                            + "schema-true but behavior-changing"));
        }
    }

    private static void checkCapabilities(List<Finding> findings, String tool,
                                          Expectation expectation, ServerSnapshot snapshot) {
        for (String capability : expectation.requiredCapabilities()) {
            if (!snapshot.capabilities().contains(capability)) {
                findings.add(Finding.breaking(tool, "capability.withdrawn",
                        "server no longer declares required capability '" + capability + "'"));
            }
        }
    }

    private static void checkInputSchema(List<Finding> findings, String tool,
                                         Expectation expectation, ServerTool server) {
        SchemaShape consumer = SchemaShape.of(expectation.inputSchema());
        SchemaShape provider = SchemaShape.of(server.inputSchema());
        findings.addAll(SchemaShape.diff(tool, consumer, provider));
    }

    /**
     * True when two descriptions differ enough to matter. Whitespace-, case-, and
     * punctuation-only edits never count; rewrites are compared by token-overlap
     * (Jaccard) so a reorder or minor tweak is immaterial while a genuine reword is
     * flagged. Limitation: a single-token typo fix lowers overlap and may still
     * flag — acceptable, and documented in DESIGN.md.
     */
    private static boolean isMaterialRewrite(String recorded, String current) {
        if (normalize(recorded).equals(normalize(current))) {
            return false;
        }
        Set<String> a = tokens(recorded);
        Set<String> b = tokens(current);
        if (a.isEmpty() && b.isEmpty()) {
            return false;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        double jaccard = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        return jaccard < DESCRIPTION_MATERIALITY;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static Set<String> tokens(String s) {
        Set<String> tokens = new HashSet<>();
        for (String token : s.toLowerCase().split("[^\\p{Alnum}]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}

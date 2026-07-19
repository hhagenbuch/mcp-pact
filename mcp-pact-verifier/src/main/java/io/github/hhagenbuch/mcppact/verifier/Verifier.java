package io.github.hhagenbuch.mcppact.verifier;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.diff.ContractVerifier;
import io.github.hhagenbuch.mcppact.core.matcher.MatcherEngine;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Interaction;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies a {@link Pact} against a live provider: first the static
 * {@code tools/list} diff, then interaction replay for every tool that still
 * exists (a missing tool is already reported by the diff, so replaying it would
 * only add noise).
 */
public final class Verifier {

    private Verifier() {
    }

    public static Report verify(Pact pact, McpConnection connection) {
        List<Finding> findings = new ArrayList<>();

        ServerSnapshot snapshot = connection.listTools();
        findings.addAll(ContractVerifier.diff(pact, snapshot));

        for (Expectation expectation : pact.expectations()) {
            if (!snapshot.tools().containsKey(expectation.tool())) {
                continue;
            }
            for (Interaction interaction : expectation.interactions()) {
                JsonNode response = connection.call(expectation.tool(), interaction.input());
                findings.addAll(MatcherEngine.evaluate(expectation.tool(),
                        interaction.response().matchers(), response));
            }
        }
        return new Report(findings);
    }
}

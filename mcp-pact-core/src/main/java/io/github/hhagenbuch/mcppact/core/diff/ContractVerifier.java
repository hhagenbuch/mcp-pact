package io.github.hhagenbuch.mcppact.core.diff;

import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;
import io.github.hhagenbuch.mcppact.core.model.ServerTool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares a {@link Pact} against a {@link ServerSnapshot} and classifies every
 * difference as BREAKING / COMPAT / WARN. This is the "brain" of the verifier;
 * it does no I/O — interaction replay (which needs live responses) is layered on
 * top in {@code mcp-pact-verifier} using the {@code matcher} engine.
 */
public final class ContractVerifier {

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

        // Tools the server offers that no expectation covers are backwards-compatible additions.
        for (String name : snapshot.tools().keySet()) {
            if (!usedTools.contains(name)) {
                findings.add(Finding.compat(name, "tool.new",
                        "server advertises a new tool not covered by the pact"));
            }
        }
        return findings;
    }

    private static void checkDescription(List<Finding> findings, String tool,
                                         Expectation expectation, ServerTool server) {
        String recorded = expectation.description();
        String current = server.description();
        if (recorded != null && current != null && !normalize(recorded).equals(normalize(current))) {
            findings.add(Finding.warn(tool, "tool.description",
                    "tool description changed since the pact was recorded — "
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

        // Params the consumer actually sends: removal or retyping is breaking.
        for (Map.Entry<String, String> used : consumer.propertyTypes().entrySet()) {
            String param = used.getKey();
            String consumerType = used.getValue();
            if (!provider.has(param)) {
                findings.add(Finding.breaking(tool, "param.removed",
                        "input param '" + param + "' the consumer sends is no longer accepted"));
                continue;
            }
            String providerType = provider.type(param);
            if (consumerType != null && providerType != null && !consumerType.equals(providerType)) {
                findings.add(Finding.breaking(tool, "param.type",
                        "input param '" + param + "' type changed from " + consumerType + " to " + providerType));
            }
        }

        // A newly-required param the consumer does not send breaks its calls.
        for (String required : provider.required()) {
            if (!consumer.has(required)) {
                findings.add(Finding.breaking(tool, "param.newRequired",
                        "server now requires input param '" + required + "' the consumer does not send"));
            }
        }

        // A new optional param is backwards compatible.
        for (String param : provider.propertyTypes().keySet()) {
            if (!consumer.has(param) && !provider.required().contains(param)) {
                findings.add(Finding.compat(tool, "param.newOptional",
                        "server added optional input param '" + param + "'"));
            }
        }
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }
}

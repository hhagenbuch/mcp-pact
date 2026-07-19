package io.github.hhagenbuch.mcppact.verifier;

import io.github.hhagenbuch.mcppact.core.JsonPaths;
import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Interaction;
import io.github.hhagenbuch.mcppact.core.model.Matcher;
import io.github.hhagenbuch.mcppact.core.model.Pact;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <pre>
 * mcp-pact verify &lt;pact.json&gt; [--strict] [--json] -- &lt;server command...&gt;
 * </pre>
 *
 * Exit code: {@code 0} = contract holds, {@code 1} = a BREAKING difference (or a
 * WARN under {@code --strict}), {@code 2} = could not run the check (usage error,
 * an invalid pact, or the provider failed to launch/respond). The 1-vs-2 split
 * is deliberate: "the contract is broken" must be distinguishable from "we could
 * not check it".
 */
public final class Cli {

    private static final Set<String> MATCHER_TYPES =
            Set.of("string", "number", "boolean", "object", "array", "null");

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length < 1 || !args[0].equals("verify")) {
            return usage("expected the 'verify' subcommand");
        }
        int dashDash = indexOf(args, "--");
        if (dashDash < 0 || dashDash == args.length - 1) {
            return usage("missing '-- <server command>' to launch the provider");
        }

        boolean strict = false;
        boolean asJson = false;
        String pactPath = null;
        for (int i = 1; i < dashDash; i++) {
            switch (args[i]) {
                case "--strict" -> strict = true;
                case "--json" -> asJson = true;
                default -> {
                    if (args[i].startsWith("--")) {
                        return usage("unknown flag: " + args[i]);
                    }
                    if (pactPath != null) {
                        return usage("unexpected extra argument: " + args[i]);
                    }
                    pactPath = args[i];
                }
            }
        }
        if (pactPath == null) {
            return usage("missing <pact.json>");
        }
        List<String> serverCommand = Arrays.asList(args).subList(dashDash + 1, args.length);

        Pact pact;
        try {
            pact = PactIO.load(Path.of(pactPath));
            validate(pact);
        } catch (RuntimeException e) {
            System.err.println("error: invalid pact: " + e.getMessage());
            return 2;
        }

        Report report;
        try (StdioMcpConnection connection = new StdioMcpConnection(serverCommand)) {
            report = Verifier.verify(pact, connection);
        } catch (RuntimeException e) {
            System.err.println("error: could not verify against the provider: " + e.getMessage());
            return 2;
        }

        System.out.println(asJson
                ? ReportFormatter.json(report, pact.consumer(), pact.provider())
                : ReportFormatter.human(report, pact.consumer(), pact.provider()));
        return report.exitCode(strict);
    }

    /** Pre-flights a pact so a bad regex, path, or matcher type is exit 2, not a mid-run crash. */
    static void validate(Pact pact) {
        for (Expectation expectation : pact.expectations()) {
            for (Interaction interaction : expectation.interactions()) {
                for (Matcher matcher : interaction.response().matchers()) {
                    JsonPaths.validate(matcher.path());
                    switch (matcher.kind()) {
                        case REGEX -> Pattern.compile(matcher.regex());
                        case TYPE -> {
                            if (!MATCHER_TYPES.contains(matcher.type())) {
                                throw new IllegalArgumentException("unknown matcher type: " + matcher.type());
                            }
                        }
                        default -> { /* equals/present need no pre-flight */ }
                    }
                }
            }
        }
    }

    private static int indexOf(String[] args, String token) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(token)) {
                return i;
            }
        }
        return -1;
    }

    private static int usage(String problem) {
        System.err.println("error: " + problem);
        System.err.println("usage: mcp-pact verify <pact.json> [--strict] [--json] -- <server command...>");
        return 2;
    }
}

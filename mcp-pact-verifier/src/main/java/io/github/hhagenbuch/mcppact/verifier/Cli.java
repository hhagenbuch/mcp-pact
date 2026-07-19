package io.github.hhagenbuch.mcppact.verifier;

import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.model.Pact;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <pre>
 * mcp-pact verify &lt;pact.json&gt; [--strict] [--json] -- &lt;server command...&gt;
 * </pre>
 *
 * Exit code: 0 = contract holds, 1 = a BREAKING difference (or WARN under
 * {@code --strict}), 2 = usage error.
 */
public final class Cli {

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

        Pact pact = PactIO.load(Path.of(pactPath));
        Report report;
        try (StdioMcpConnection connection = new StdioMcpConnection(serverCommand)) {
            report = Verifier.verify(pact, connection);
        }

        System.out.println(asJson
                ? ReportFormatter.json(report, pact.consumer(), pact.provider())
                : ReportFormatter.human(report, pact.consumer(), pact.provider()));
        return report.exitCode(strict);
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

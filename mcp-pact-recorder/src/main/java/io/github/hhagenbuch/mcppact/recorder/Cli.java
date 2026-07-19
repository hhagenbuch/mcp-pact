package io.github.hhagenbuch.mcppact.recorder;

import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.model.Pact;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * <pre>
 * mcp-pact record --out &lt;pact.json&gt; [--consumer NAME] [--provider NAME] -- &lt;server command...&gt;
 * </pre>
 *
 * Sits transparently between an MCP client and a real server, forwarding stdio
 * both ways, and writes the observed traffic to a pact when the client
 * disconnects. Point your MCP client's server command at this and run your agent
 * as usual; the pact falls out.
 *
 * Exit code: {@code 0} = a pact was written, {@code 2} = usage error.
 */
public final class Cli {

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out));
    }

    static int run(String[] args, java.io.InputStream clientIn, java.io.OutputStream clientOut) {
        if (args.length < 1 || !args[0].equals("record")) {
            return usage("expected the 'record' subcommand");
        }
        int dashDash = indexOf(args, "--");
        if (dashDash < 0 || dashDash == args.length - 1) {
            return usage("missing '-- <server command>' to launch the provider");
        }

        String out = null;
        String consumer = "recorded-consumer";
        String provider = "recorded-provider";
        for (int i = 1; i < dashDash; i++) {
            switch (args[i]) {
                case "--out" -> out = value(args, ++i, "--out");
                case "--consumer" -> consumer = value(args, ++i, "--consumer");
                case "--provider" -> provider = value(args, ++i, "--provider");
                default -> {
                    return usage("unknown flag: " + args[i]);
                }
            }
        }
        if (out == null) {
            return usage("missing --out <pact.json>");
        }
        List<String> serverCommand = Arrays.asList(args).subList(dashDash + 1, args.length);

        RecorderSession session = new RecorderSession();
        new RecorderProxy(serverCommand, clientIn, clientOut, session).run();

        Pact pact = session.toPact(consumer, provider);
        PactIO.write(Path.of(out), pact);
        System.err.println("mcp-pact: wrote " + pact.expectations().size() + " expectation(s) to " + out);
        return 0;
    }

    private static String value(String[] args, int i, String flag) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        return args[i];
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
        System.err.println("usage: mcp-pact record --out <pact.json> "
                + "[--consumer NAME] [--provider NAME] -- <server command...>");
        return 2;
    }
}

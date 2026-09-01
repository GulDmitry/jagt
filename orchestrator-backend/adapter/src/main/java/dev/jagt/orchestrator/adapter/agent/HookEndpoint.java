package dev.jagt.orchestrator.adapter.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * A hook is run by the harness rather than by the model, so what arrives here costs no tokens and still arrives
 * from a session that has run out of them. Which session it is comes from what jagt wrote into the line, not
 * from what the vendor hands the hook.
 */
@Component
public class HookEndpoint {

    private final String url;
    private final String gateUrl;

    public HookEndpoint(
            @Value("${orchestrator.hook-url:http://127.0.0.1:${server.port:8290}/api/agent/session}")
            String url,
            @Value("${orchestrator.gate-url:http://127.0.0.1:${server.port:8290}/api/agent}")
            String gateUrl) {
        this.url = url;
        this.gateUrl = gateUrl;
    }

    /** Same shape, another address; the answer is a verdict, and refusing it when jagt is unreachable would make
     *  a stopped backend look like a rule. */
    public String gateCommand(Path worktree) {
        return command(worktree, "tool", gateUrl);
    }

    /**
     * Capped and always successful: a hook reporting a failure would put jagt's plumbing in front of the human,
     * and one that hung would hold up the session. A harness adds a hook's stdout to the session's context, so
     * the answer is printed; {@code -f} keeps an error page out of that context by printing nothing on failure.
     * Whatever the harness hands the hook rides along on stdin, unparsed by the shell.
     */
    public String command(Path worktree, String state) {
        return command(worktree, state, url);
    }

    private String command(Path worktree, String state, String base) {
        return "curl -sf -m 2 -X POST --data-binary @- -H 'Content-Type: application/json' -H "
                + AbstractAgentRuntime.shellQuote(McpEndpoint.CALLER_HEADER + ": "
                        + McpEndpoint.callerHeaderValue(worktree))
                + " " + AbstractAgentRuntime.shellQuote(base + "/" + state) + " || true";
    }
}

package dev.jagt.orchestrator.adapter.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Where an agent CLI's own hooks report what a session is doing, and the line that reports it. A hook is run
 * by the harness rather than by the model, so what arrives here costs no tokens and still arrives from a
 * session that has run out of them.
 *
 * <p>Which session it is and what happened to it come from what jagt itself wrote into the line, so a vendor
 * changing the shape of what it hands a hook cannot quietly stop the reports.
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

    /**
     * The gate's own line: same shape, another address, and the answer is a verdict the CLI reads rather than a
     * brief. Refusing it when jagt is unreachable would make a stopped backend look like a rule.
     */
    public String gateCommand(Path worktree) {
        return command(worktree, "tool", gateUrl);
    }

    /**
     * Capped and always successful: a hook that reported a failure would put jagt's own plumbing in front of
     * the human working in that session, and one that hung would hold up the session itself.
     *
     * <p>What jagt ANSWERS is printed, because a harness adds a hook's stdout to the session's context — which
     * is the only way to hand a compacted session its brief back. {@code -f} is what keeps an error page out of
     * that context: on any failure curl prints nothing at all.
     *
     * <p>Whatever the harness hands the hook rides along on stdin, unparsed by the shell — so the line stays
     * the same for every event and every vendor, and the reading happens where it can be tested.
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

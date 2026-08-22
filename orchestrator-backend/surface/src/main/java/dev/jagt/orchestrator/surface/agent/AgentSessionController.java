package dev.jagt.orchestrator.surface.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WatchdogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Locale;

/**
 * What a session says about itself without a model in the path: its harness reports it, so a session that has
 * run out of tokens, stopped at a prompt or died still gets a word out.
 *
 * <p>The state comes from the address, which is what jagt itself wrote into the hook. The payload is read only
 * for what jagt can use and never required, so a vendor changing its shape costs a detail rather than the
 * report.
 */
@RestController
@RequestMapping("/api/agent/session")
@RequiredArgsConstructor
public class AgentSessionController {

    /** The one field worth taking: it names the file the session appends to, which jagt otherwise derives. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(@JsonProperty("transcript_path") String transcriptPath) {
    }

    private final StateService stateService;
    private final SessionProbe probe;
    private final WatchdogService watchdog;

    @PostMapping("/{state}")
    public void report(@PathVariable String state,
                       @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                       @RequestBody(required = false) Session session) {
        String taskId = stateService.findByWorktree(cwd)
                .orElseThrow(() -> new IllegalArgumentException("No task runs in '" + cwd + "'"))
                .getKey();
        if (session != null && session.transcriptPath() != null && !session.transcriptPath().isBlank()) {
            probe.logAt(taskId, Path.of(session.transcriptPath()));
        }
        probe.report(taskId, reported(state), System.currentTimeMillis());
        watchdog.check(taskId);
    }

    private static SessionProbe.State reported(String state) {
        try {
            return SessionProbe.State.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a session state: '" + state + "'");
        }
    }
}

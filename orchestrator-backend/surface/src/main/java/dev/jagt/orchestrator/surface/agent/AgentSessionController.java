package dev.jagt.orchestrator.surface.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.SessionReports;
import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    /**
     * Two fields worth taking: the file the session appends to, which jagt otherwise derives, and what STARTED
     * this session — the only way to tell a compaction from an ordinary start.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(@JsonProperty("transcript_path") String transcriptPath,
                          @JsonProperty("source") String source) {
    }

    private final StateService stateService;
    private final SessionReports reports;

    /**
     * The answer is for the MODEL, not the human: a harness adds a hook's stdout to the session's context, and
     * a session that has just been compacted is the one case where jagt has something to say into it. Every
     * other report answers nothing.
     */
    @PostMapping(value = "/{state}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(@PathVariable String state,
                         @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                         @RequestBody(required = false) Session session) {
        String taskId = stateService.findByWorktree(cwd)
                .orElseThrow(() -> new IllegalArgumentException("No task runs in '" + cwd + "'"))
                .getKey();
        Path sessionLog = session == null || session.transcriptPath() == null
                || session.transcriptPath().isBlank() ? null : Path.of(session.transcriptPath());
        return reports.record(taskId, reported(state), sessionLog, session == null ? null : session.source());
    }

    private static SessionProbe.State reported(String state) {
        try {
            return SessionProbe.State.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a session state: '" + state + "'");
        }
    }
}

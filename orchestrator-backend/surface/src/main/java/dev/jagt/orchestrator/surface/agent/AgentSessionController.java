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

/** The state comes from the address; the payload is read only for what jagt can use, and never required. */
@RestController
@RequestMapping("/api/agent/session")
@RequiredArgsConstructor
public class AgentSessionController {

    /** {@code source} is what STARTED this session — the only way to tell a compaction from an ordinary start. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(@JsonProperty("transcript_path") String transcriptPath,
                          @JsonProperty("source") String source,
                          @JsonProperty("message") String message) {
    }

    private final StateService stateService;
    private final SessionReports reports;

    /** The harness feeds this answer back into the session's context, so it is written for the model. */
    @PostMapping(value = "/{state}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(@PathVariable String state,
                         @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                         @RequestBody(required = false) Session session) {
        String taskId = stateService.findByWorktree(cwd)
                .orElseThrow(() -> new IllegalArgumentException("No task runs in '" + cwd + "'"))
                .getKey();
        return reports.record(taskId, reported(state), reported(session));
    }

    /** Deriving where a session writes its log is a guess, so a payload that named none must not become one. */
    private static SessionReports.Report reported(Session session) {
        if (session == null) {
            return SessionReports.Report.defaults();
        }
        String log = session.transcriptPath();
        return SessionReports.Report.defaults()
                .withSessionLog(log == null || log.isBlank() ? null : Path.of(log))
                .withStartedBy(session.source())
                .withSaid(session.message());
    }

    private static SessionProbe.State reported(String state) {
        try {
            return SessionProbe.State.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a session state: '" + state + "'");
        }
    }
}

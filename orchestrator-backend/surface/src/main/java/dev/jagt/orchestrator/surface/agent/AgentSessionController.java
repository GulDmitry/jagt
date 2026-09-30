package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.SessionHookReport;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.SessionReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The state comes from the address; the payload is read only for what jagt can use, and never required. */
@RestController
@RequestMapping("/api/agent/session")
@RequiredArgsConstructor
@Slf4j
public class AgentSessionController {

    private final StateService stateService;
    private final SessionReports reports;

    /** The harness feeds this answer back into the session's context, so it is written for the model. */
    @PostMapping(value = "/{state}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(@PathVariable String state,
                         @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                         @RequestBody(required = false) SessionHookReport session) {
        var task = task(cwd);
        return reports.record(task.getKey(), reported(state), read(task, session));
    }

    /** Answered in the CLI's own words where the turn may not end yet, and with nothing where it may. */
    @PostMapping(value = "/turn-end", produces = MediaType.TEXT_PLAIN_VALUE)
    public String turnEnd(@RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                          @RequestBody(required = false) SessionHookReport session) {
        var task = task(cwd);
        SessionHookReport said = session == null ? SessionHookReport.none() : session;
        return reports.turnEnded(task.getKey(), read(task, said), said.sentOn(), said.pausedOnBackgroundWork());
    }

    private Map.Entry<String, TaskState> task(String cwd) {
        return stateService.findByWorktree(cwd)
                .orElseThrow(() -> new IllegalArgumentException("No task runs in '" + cwd + "'"));
    }

    private SessionReports.Report read(Map.Entry<String, TaskState> task, SessionHookReport session) {
        SessionHookReport said = session == null ? SessionHookReport.none() : session;
        // Nobody reads a refusal here — the hook posts what its CLI handed it and discards the answer — so what
        // cannot be believed is dropped and said once in the log.
        List<Violation> violations = said.violations(MessageContext.NONE);
        if (!violations.isEmpty()) {
            log.atWarn().setMessage("session hook field dropped")
                    .addKeyValue("task", task.getKey())
                    .addKeyValue("cause", violations.toString())
                    .log();
        }
        return SessionReports.Report.defaults()
                .withSessionLog(said.sessionLog().orElse(null))
                .withStartedBy(said.source())
                .withSaid(said.message())
                .withTask(task.getValue());
    }

    private static SessionProbe.State reported(String state) {
        try {
            return SessionProbe.State.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a session state: '" + state + "'");
        }
    }
}

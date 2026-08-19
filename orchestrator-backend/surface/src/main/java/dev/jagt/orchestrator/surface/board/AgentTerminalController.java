package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.port.WebTerminal;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.OptionalInt;

/**
 * Where a task's agent session can be watched and talked to inside the board. It moves nothing: the window is
 * selected by the {@code focus} action, exactly as a typed command does it, and this only says which port on
 * this machine serves that session. The host is the browser's own — it is the only one that knows the name it
 * reached jagt under, and jagt would answer with a loopback address a second machine cannot use.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentTerminalController {

    /** A null port is a jagt with no web terminal configured — the answer to "where", not a failure. */
    public record Terminal(Integer port) {
    }

    private final AgentSessions sessions;
    private final WebTerminal webTerminal;

    @PostMapping("/tasks/{taskId}/terminal")
    public Terminal terminal(@PathVariable String taskId) {
        OptionalInt port = webTerminal.serve(sessions.sessionOf(taskId));
        return new Terminal(port.isPresent() ? port.getAsInt() : null);
    }
}

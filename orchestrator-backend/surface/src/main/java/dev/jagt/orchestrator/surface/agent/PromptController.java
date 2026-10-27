package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.protocol.SessionHookReport;
import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A prompt just typed: held back for a fresh session when this one has gone cold, else the session works. */
@RestController
@RequestMapping("/api/agent/session")
@RequiredArgsConstructor
public class PromptController {

    private final StateService stateService;
    private final PromptGate gate;
    private final AgentSessionController sessions;

    @PostMapping(value = "/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prompt(@RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                         @RequestBody(required = false) SessionHookReport session) {
        var owner = stateService.findByWorktree(cwd);
        if (owner.isEmpty()) {
            return "";
        }
        String blocked = gate.answer(owner.get().getKey(), owner.get().getValue(),
                session == null ? "" : session.typed());
        return blocked.isEmpty() ? sessions.report("working", cwd, session) : blocked;
    }
}

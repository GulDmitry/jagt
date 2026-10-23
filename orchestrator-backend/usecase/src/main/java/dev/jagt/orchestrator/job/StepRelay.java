package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A step's facts are jagt's and come first; what the session is asked to do with them is the brief's. */
@Service
@RequiredArgsConstructor
public class StepRelay {

    private final SessionBrief brief;
    private final AgentSessions sessions;

    /** False where nothing reached the session: the brief leaves the step empty, or the same text is already there. */
    public boolean relay(String taskId, String step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank())
                .map(ask -> sessions.relayIfChanged(taskId, facts + "\n\n" + ask))
                .orElse(false);
    }
}

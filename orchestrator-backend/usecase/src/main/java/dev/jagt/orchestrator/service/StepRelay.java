package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A step's facts are jagt's and come first; what the session is asked to do with them is the brief's. Each relay
 * answers false where the same text is already the session's. A step the brief leaves empty sends its facts alone.
 */
@Service
@RequiredArgsConstructor
public class StepRelay {

    private final SessionBrief brief;
    private final AgentSessions sessions;

    public boolean relay(String taskId, String step, String facts) {
        return sessions.relayIfChanged(taskId, told(step, facts));
    }

    /** In a session started afresh, which reads the text with the task's notes rather than its conversation. */
    public boolean relayFresh(String taskId, String step, String facts) {
        return sessions.relayFreshIfChanged(taskId, told(step, facts));
    }

    /** Below an instruction still pending, which stands. */
    public boolean relayUnder(String taskId, String step, String facts, boolean fresh) {
        return sessions.relayUnder(taskId, told(step, facts), fresh);
    }

    private String told(String step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank()).map(ask -> facts + "\n\n" + ask).orElse(facts);
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A step's facts are jagt's and come first; what the session is asked to do with them is the brief's. Each relay
 * answers false where the same text is already the session's, or where no case sends the step at the task's status.
 * A step the brief leaves empty sends its facts alone.
 */
@Service
@RequiredArgsConstructor
public class StepRelay {

    private final SessionBrief brief;
    private final AgentSessions sessions;
    private final CaseDoor door;

    public boolean relay(String taskId, String step, String facts) {
        return door.opens(taskId, step) && sessions.relayIfChanged(taskId, told(step, facts));
    }

    /** Written whatever the session already holds, as a hand-off that must land again. */
    public boolean write(String taskId, String step, String facts) {
        if (!door.opens(taskId, step)) {
            return false;
        }
        sessions.writeTaskContext(taskId, told(step, facts));
        return true;
    }

    /** In a session started afresh, which reads the text with the task's notes rather than its conversation. */
    public boolean relayFresh(String taskId, String step, String facts) {
        return door.opens(taskId, step) && sessions.relayFreshIfChanged(taskId, told(step, facts));
    }

    /** Below an instruction still pending, which stands. */
    public boolean relayUnder(String taskId, String step, String facts, boolean fresh) {
        return door.opens(taskId, step) && sessions.relayUnder(taskId, told(step, facts), fresh);
    }

    /** Typed into the session's window, which takes one line. */
    public boolean say(String taskId, String step, String facts) {
        if (!door.opens(taskId, step)) {
            return false;
        }
        sessions.say(taskId, told(step, facts).replaceAll("\\s*\\n\\s*", " "));
        return true;
    }

    private String told(String step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank()).map(ask -> facts + "\n\n" + ask).orElse(facts);
    }
}

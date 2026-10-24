package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * A step's facts are jagt's and come first; what the session is asked to do with them is the brief's. Every relay
 * opens the session's turn. Each answers false where no case sends the step at the task's status, or where the
 * session still holds the turn this step opened with the same facts.
 * A step the brief leaves empty sends its facts alone.
 */
@Service
@RequiredArgsConstructor
public class StepRelay {

    private final SessionBrief brief;
    private final AgentSessions sessions;
    private final CaseDoor door;
    private final TaskTurns turns;

    /** Whether a case sends the step at the task's status; a miss is recorded and stops the task. */
    public boolean opens(String taskId, String step) {
        return door.opens(taskId, step);
    }

    /** Whether a case lets the Master make {@code move} at the task's status; a miss is recorded and stops the task. */
    public boolean presses(String taskId, String move) {
        return door.presses(taskId, move);
    }

    public boolean relay(String taskId, String step, String facts) {
        return opened(taskId, step, facts, false, told -> sessions.writeTaskContext(taskId, told));
    }

    /** A round sent back to work, false too where the rounds of this step running reached their ceiling. */
    public boolean relayRound(String taskId, String step, String facts) {
        return opened(taskId, step, facts, true, told -> sessions.writeTaskContext(taskId, told));
    }

    /** Written whatever the session already holds, as a hand-off that must land again. */
    public boolean write(String taskId, String step, String facts) {
        if (!door.opens(taskId, step)) {
            return false;
        }
        sessions.writeTaskContext(taskId, told(step, facts));
        turns.opened(taskId, step, Turn.digestOf(facts));
        return true;
    }

    /** In a session started afresh, which reads the text with the task's notes rather than its conversation. */
    public boolean relayFresh(String taskId, String step, String facts) {
        return opened(taskId, step, facts, false, told -> sessions.relayFresh(taskId, told));
    }

    /** Below an instruction still pending, which stands. */
    public boolean relayUnder(String taskId, String step, String facts, boolean fresh) {
        return opened(taskId, step, facts, false, told -> sessions.relayUnder(taskId, told, fresh));
    }

    /** Typed into the session's window, which takes one line. */
    public boolean say(String taskId, String step, String facts) {
        if (!door.opens(taskId, step)) {
            return false;
        }
        sessions.say(taskId, told(step, facts).replaceAll("\\s*\\n\\s*", " "));
        turns.opened(taskId, step, Turn.digestOf(facts));
        return true;
    }

    /** A text already holding its steps' sections, sent afresh; every step has passed the door. */
    public boolean relayComposed(String taskId, List<String> steps, String text) {
        String step = String.join("+", steps);
        String digest = Turn.digestOf(text);
        if (turns.of(taskId).holds(step, digest)) {
            return false;
        }
        sessions.relayFresh(taskId, text);
        turns.opened(taskId, step, digest);
        return true;
    }

    private boolean opened(String taskId, String step, String facts, boolean round, Consumer<String> send) {
        if (!door.opens(taskId, step)) {
            return false;
        }
        String digest = Turn.digestOf(facts);
        if (turns.of(taskId).holds(step, digest) || round && !door.roundDue(taskId, step)) {
            return false;
        }
        send.accept(told(step, facts));
        turns.opened(taskId, step, digest);
        return true;
    }

    private String told(String step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank()).map(ask -> facts + "\n\n" + ask).orElse(facts);
    }
}

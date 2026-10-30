package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.SessionWakes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionWakeJob implements Job {

    private static final Map<String, String> LINES = Map.of(
            "wake", "The usage limit has reset.",
            "wait-over", "The wait you asked for is over.");

    private final SessionWakes wakes;
    private final AgentSessions sessions;
    private final StepRelay relay;

    @Override
    public String id() {
        return "session-wake";
    }

    @Override
    public String describe() {
        return "say one line to a session that waits, once its limit resets or its wait is over";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public void run() {
        for (SessionWakes.Due due : wakes.takeDue(sessions::composing)) {
            try {
                relay.say(due.taskId(), due.step(), LINES.getOrDefault(due.step(), ""), due.read());
            } catch (RuntimeException e) {
                log.atWarn().setMessage("session wake not sent").addKeyValue("task", due.taskId())
                        .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "the session stays stopped").log();
            }
        }
    }
}

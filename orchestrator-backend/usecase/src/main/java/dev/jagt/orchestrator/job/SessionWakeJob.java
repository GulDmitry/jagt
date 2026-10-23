package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.SessionWakes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "orchestrator.usage-limits.wake-on-reset", havingValue = "true")
public class SessionWakeJob implements Job {

    private final SessionWakes wakes;
    private final AgentSessions sessions;
    private final StepRelay relay;

    @Override
    public String id() {
        return "session-wake";
    }

    @Override
    public String describe() {
        return "say one line to a session that stopped on a usage limit, once the limit resets";
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
        for (String taskId : wakes.takeDue(sessions::composing)) {
            try {
                relay.say(taskId, "wake", "The usage limit has reset.");
            } catch (RuntimeException e) {
                log.atWarn().setMessage("session wake not sent").addKeyValue("task", taskId)
                        .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "the session stays stopped").log();
            }
        }
    }
}

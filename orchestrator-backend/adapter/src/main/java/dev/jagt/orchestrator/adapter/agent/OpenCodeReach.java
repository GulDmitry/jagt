package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.EnvironmentProbe;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** The OpenCode model endpoint, read while it is set: one that does not answer is jagt's lost reach until one does. */
@Component
@ConditionalOnExpression("'${orchestrator.opencode.endpoint:}' != ''")
@RequiredArgsConstructor
public class OpenCodeReach implements Job {

    private static final String DRIVER = "opencode";

    private final OpenCodeProperties opencode;
    private final EnvironmentProbe probe;
    private final Reach reach;

    @Override
    public String id() {
        return "opencode-reach";
    }

    @Override
    public String describe() {
        return "reads whether the sessions' model endpoint answers";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public void run() {
        String endpoint = opencode.endpoint();
        if (probe.answers(endpoint + "/models")) {
            reach.reached(DRIVER, endpoint);
        } else {
            reach.lost(new ReachLost(DRIVER, endpoint, List.of(), "opencode cannot reach " + endpoint));
        }
    }
}

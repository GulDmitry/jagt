package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** A model name the runtime does not list would start no session on it, so it is refused rather than ignored. */
@Component
@RequiredArgsConstructor
public class AgentModelCheck implements StartupCheck {

    private static final String KEY = "orchestrator.agent.model";

    private final ConfigService configService;
    private final AgentRuntime agentRuntime;

    @Override
    public List<Problem> problems() {
        String model = configService.load().agent().modelOrInherited();
        if (model.isEmpty() || !agentRuntime.choosesModel()) {
            return List.of();
        }
        return agentRuntime.models().contains(model) ? List.of() : List.of(new Problem(KEY, KEY + ": '" + model
                + "' is not one of " + agentRuntime.models()));
    }
}

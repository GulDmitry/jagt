package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** On Linux Claude's sandbox is bubblewrap with socat relaying its network, neither of which ships with the OS. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
public class ClaudeSandboxCheck implements StartupCheck {

    private static final List<String> LINUX_BINARIES = List.of("bwrap", "socat");

    private final OrchestratorProperties properties;
    private final ConfigService configService;
    private final Host host;

    public ClaudeSandboxCheck(OrchestratorProperties properties, ConfigService configService, Host host) {
        this.properties = properties;
        this.configService = configService;
        this.host = host;
    }

    @Override
    public List<Problem> problems() {
        if (!"linux".equalsIgnoreCase(properties.platform())
                || !configService.load().agent().sandboxOrDefault().enabledOrDefault()) {
            return List.of();
        }
        List<String> missing = LINUX_BINARIES.stream().filter(name -> !host.onPath(name)).toList();
        return missing.isEmpty() ? List.of()
                : List.of(new Problem("orchestrator.agent.sandbox.enabled", "orchestrator.agent.sandbox: "
                        + String.join(" and ", missing) + " not on PATH, and a session's shell runs inside"
                        + " bubblewrap. Install the packages bubblewrap and socat, or"
                        + " set orchestrator.agent.sandbox.enabled: false."));
    }
}

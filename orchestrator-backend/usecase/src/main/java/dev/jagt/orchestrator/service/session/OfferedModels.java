package dev.jagt.orchestrator.service.session;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The models a session may be told, and the agent CLI that would run them. */
@Service
@RequiredArgsConstructor
public class OfferedModels {

  private final ConfigService configService;
  private final AgentRuntime agentRuntime;

  public List<String> names() {
    return configService.load().agent().modelsFor(agentRuntime);
  }

  public String agentName() {
    return agentRuntime.displayName();
  }
}

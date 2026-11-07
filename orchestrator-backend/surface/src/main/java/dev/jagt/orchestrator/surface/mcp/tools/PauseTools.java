package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.PauseOrder;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PauseTools implements McpTools {

  private final TaskStops stops;

  @Override
  public void declare(McpToolRegistry tools) {
    tools.tool(
        "pause_task",
        Audience.MASTER,
        Effect.CHANGES,
        PauseOrder.schema(
            "Pause a started task because the human told you to, in this conversation: nothing of"
                + " jagt runs on it until they lift the stop. Never on your own judgement, a"
                + " verdict or a case. Refused where the board offers no pause (not started yet, or"
                + " already stopped)."),
        PauseOrder.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> stops.pauseOnWord(said.taskId(), said.reason()));
  }
}

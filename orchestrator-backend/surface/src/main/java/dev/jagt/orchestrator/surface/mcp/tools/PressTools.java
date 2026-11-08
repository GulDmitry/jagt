package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.command.MasterPresses;
import dev.jagt.orchestrator.protocol.CommandPress;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.TaskPress;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PressTools implements McpTools {

  private final MasterPresses presses;

  @Override
  public void declare(McpToolRegistry tools) {
    tools.tool(
        "press_task",
        Audience.MASTER,
        Effect.CHANGES,
        TaskPress.schema(
            "Press any verb the board offers on one task, as the human would: ship, trust, lift,"
                + " escalate, pause, done, remove-repo and the rest. Held to the Master's cases,"
                + " refused where the verb is not legal now or the human's `mine` keeps it."),
        TaskPress.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> presses.press(said.taskId(), said.verb(), said.project()));

    tools.tool(
        "run_command",
        Audience.MASTER,
        Effect.CHANGES,
        CommandPress.schema(
            "Run a command no task owns, as the human would type it. Refused where the human's"
                + " `mine` keeps it."),
        CommandPress.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> presses.run(said.verb(), said.tail()));
  }
}

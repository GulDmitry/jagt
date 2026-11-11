package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.NoArguments;

record AnsweringMcpTools(String name, Effect effect, String about, String answer)
    implements McpTools {

  @Override
  public void declare(McpToolRegistry tools) {
    tools.tool(
        name,
        Audience.ANYONE,
        effect,
        NoArguments.schema(about),
        NoArguments.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> answer);
  }
}

package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.message.NoArguments;

record ThrowingMcpTools(RuntimeException failure) implements McpTools {

  @Override
  public void declare(McpToolRegistry tools) {
    tools.tool(
        "boom",
        Audience.ANYONE,
        Effect.CHANGES,
        NoArguments.schema("throws"),
        NoArguments.class,
        (said, caller) -> MessageContext.NONE,
        (said, caller) -> {
          throw failure;
        });
  }
}

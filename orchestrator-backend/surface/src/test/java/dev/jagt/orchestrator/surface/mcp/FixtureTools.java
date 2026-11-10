package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.NoArguments;

final class FixtureTools {

  private FixtureTools() {}

  static McpTools throwing(RuntimeException failure) {
    return tools ->
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

  static McpTools answering(String name, Effect effect, String about, String answer) {
    return tools ->
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

package dev.jagt.orchestrator.surface.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.message.Message;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.MessageHandler;
import dev.jagt.orchestrator.surface.ui.log.LogReads;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DebugToolsTest {

  private static final class Registered implements McpToolRegistry {

    private final Map<String, String> tools = new HashMap<>();

    @Override
    public <T extends Message> void tool(
        String name,
        Audience audience,
        Effect effect,
        Schema schema,
        Class<T> message,
        BiFunction<T, @Nullable String, MessageContext> context,
        MessageHandler<T> handler) {
      tools.put(name, audience + " " + effect);
    }
  }

  @Test
  void declaresEveryDebugReadAsTheMastersAndAsChangingNothing() {
    Registered registered = new Registered();

    new DebugTools(mock(LogReads.class), mock(DebugReads.class)).declare(registered);

    assertThat(registered.tools)
        .containsOnlyKeys("read_record", "read_transcript")
        .containsValues("MASTER REPORTS");
  }
}

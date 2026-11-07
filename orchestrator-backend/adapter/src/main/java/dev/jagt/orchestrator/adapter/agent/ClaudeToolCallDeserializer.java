package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.protocol.ToolCall;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Reads the call Claude's tool hook posts: the tool's name, and its input holding the command or
 * file path.
 */
class ClaudeToolCallDeserializer extends ValueDeserializer<ToolCall> {

  @Override
  public ToolCall deserialize(JsonParser parser, DeserializationContext context) {
    JsonNode call = context.readTree(parser);
    JsonNode input = call.path("tool_input");
    JsonNode file =
        input.hasNonNull("file_path") ? input.get("file_path") : input.path("notebook_path");
    return new ToolCall(text(call.path("tool_name")), text(input.path("command")), text(file));
  }

  static JacksonModule module() {
    return new SimpleModule("claude-tool-call")
        .addDeserializer(ToolCall.class, new ClaudeToolCallDeserializer());
  }

  private static @Nullable String text(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    return node.isString() ? node.stringValue() : node.toString();
  }
}

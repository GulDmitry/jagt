package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.task.TokenUsage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** A failed call carries no envelope, only its cause: asking again would not mend it. */
record HeadlessReply(@Nullable JsonNode envelope, TokenUsage usage, String cause, boolean refused) {

  HeadlessReply(@Nullable JsonNode envelope, TokenUsage usage, String cause) {
    this(envelope, usage, cause, false);
  }

  HeadlessReply refusedByGate(boolean gate) {
    return new HeadlessReply(envelope, usage, cause, refused || gate);
  }

  static HeadlessReply failed(TokenUsage usage, String cause) {
    return new HeadlessReply(null, usage, cause);
  }
}

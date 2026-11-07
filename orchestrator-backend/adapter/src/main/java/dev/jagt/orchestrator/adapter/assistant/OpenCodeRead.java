package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.task.AssistantCallKind;
import java.nio.file.Path;
import java.time.Duration;

/**
 * One read on OpenCode: where it runs, what it is asked, the schema its answer follows and the
 * scope it is held to.
 */
record OpenCodeRead(
    Path cwd,
    String prompt,
    String schema,
    String model,
    OpenCodeScope scope,
    AssistantCallKind kind,
    Duration timeout) {

  private static final String ANSWER_RULE =
      "\n\nAnswer with one JSON object in a ```json fence, following this schema:\n";

  String framed() {
    return prompt + ANSWER_RULE + schema;
  }
}

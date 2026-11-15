package dev.jagt.orchestrator.protocol.debug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.protocol.message.Message;
import dev.jagt.orchestrator.protocol.message.TaskRef;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptRead(String taskId, @Nullable Integer maxChars) implements Message {

  public static final int DEFAULT_CHARS = 16_000;
  public static final int MAX_CHARS = 100_000;

  public static final Schema SCHEMA =
      TaskRef.schema(
              "Read-only: the last words of a task's session transcript, the human's prompts and"
                  + " the model's text, never a tool's input or output.")
          .optional(
              "maxChars",
              "integer",
              "Newest characters kept, at most " + MAX_CHARS + ". Default: " + DEFAULT_CHARS + ".");

  public TranscriptRead {
    taskId = taskId == null || taskId.isBlank() ? null : taskId;
  }

  public int count() {
    return maxChars == null ? DEFAULT_CHARS : maxChars;
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    List<Violation> found = new ArrayList<>(new TaskRef(taskId).violations(context));
    if (maxChars != null && (maxChars < 1 || maxChars > MAX_CHARS)) {
      found.add(new Violation("maxChars", "between 1 and " + MAX_CHARS + ", or left out"));
    }
    return List.copyOf(found);
  }
}

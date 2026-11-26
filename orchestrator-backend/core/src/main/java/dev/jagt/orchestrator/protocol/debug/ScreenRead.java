package dev.jagt.orchestrator.protocol.debug;

import static dev.jagt.orchestrator.protocol.Wire.absent;

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
public record ScreenRead(String taskId, @Nullable Integer lines) implements Message {

  public static final int DEFAULT_LINES = 50;
  public static final int MAX_LINES = 1000;

  public static final Schema SCHEMA =
      TaskRef.schema(
              "Read-only: the last lines a task's terminal shows now, colour codes removed. No key"
                  + " is sent.")
          .optional(
              "lines",
              "integer",
              "How many of the last lines, at most "
                  + MAX_LINES
                  + ". Default: "
                  + DEFAULT_LINES
                  + ".");

  public ScreenRead {
    taskId = absent(taskId);
  }

  public int count() {
    return lines == null ? DEFAULT_LINES : lines;
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    List<Violation> found = new ArrayList<>(new TaskRef(taskId).violations(context));
    if (lines != null && (lines < 1 || lines > MAX_LINES)) {
      found.add(new Violation("lines", "between 1 and " + MAX_LINES + ", or left out"));
    }
    return List.copyOf(found);
  }
}

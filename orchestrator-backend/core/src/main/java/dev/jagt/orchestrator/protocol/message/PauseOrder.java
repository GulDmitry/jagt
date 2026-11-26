package dev.jagt.orchestrator.protocol.message;

import static dev.jagt.orchestrator.protocol.Wire.absent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.protocol.Why;
import java.util.ArrayList;
import java.util.List;

/** A human's spoken order to pause one task, with the reason they gave. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PauseOrder(String taskId, String reason) implements Message {

  public PauseOrder {
    taskId = absent(taskId);
    reason = absent(reason);
  }

  public static Schema schema(String description) {
    return TaskRef.schema(description)
        .required(
            "reason",
            "string",
            "The human's reason, in their words, one sentence of at most "
                + Why.MAX
                + " characters.");
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    List<Violation> found = new ArrayList<>(new TaskRef(taskId).violations(context));
    if (reason == null) {
      found.add(new Violation("reason", "required: the reason the human gave"));
    } else if (reason.length() > Why.MAX) {
      found.add(
          new Violation("reason", "at most " + Why.MAX + " characters, not " + reason.length()));
    }
    return found;
  }
}

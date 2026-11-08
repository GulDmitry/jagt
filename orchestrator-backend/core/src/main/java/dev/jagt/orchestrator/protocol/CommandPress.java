package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One command no task owns, with what a human types after it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandPress(String verb, String tail) implements Message {

  public static Schema schema(String description) {
    return Schema.of(description)
        .required("verb", "string", "The command's id, as `help` lists it.")
        .text("tail", "What a human types after the verb; left out where it takes nothing.");
  }

  public CommandPress {
    verb = verb == null || verb.isBlank() ? null : verb.strip();
    tail = tail == null ? "" : tail.strip();
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    return verb == null
        ? List.of(new Violation("verb", "required: the command to run"))
        : List.of();
  }
}

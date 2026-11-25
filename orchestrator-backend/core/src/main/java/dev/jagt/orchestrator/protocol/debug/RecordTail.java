package dev.jagt.orchestrator.protocol.debug;

import static dev.jagt.orchestrator.protocol.Wire.absent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.protocol.message.Message;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordTail(String record, @Nullable Integer lines) implements Message {

  public static final List<String> RECORDS =
      List.of("backend-log", "master-log", "stops", "stops-reviewed");
  public static final int DEFAULT_LINES = 200;
  public static final int MAX_LINES = 1000;

  public static final Schema SCHEMA =
      Schema.of(
              "Read-only: the last lines of one of jagt's own records. backend-log and master-log"
                  + " are the log and the Master's feed, the same lines stdout carries under"
                  + " console logging; stops and stops-reviewed are the stop records.")
          .choiceRequired("record", RECORDS, "Which record.")
          .optional(
              "lines",
              "integer",
              "How many of the last lines, at most "
                  + MAX_LINES
                  + ". Default: "
                  + DEFAULT_LINES
                  + ".");

  public RecordTail {
    record = absent(record);
  }

  public int count() {
    return lines == null ? DEFAULT_LINES : lines;
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    List<Violation> found = new ArrayList<>();
    if (record == null || !RECORDS.contains(record)) {
      found.add(new Violation("record", "required, one of " + RECORDS));
    }
    if (lines != null && (lines < 1 || lines > MAX_LINES)) {
      found.add(new Violation("lines", "between 1 and " + MAX_LINES + ", or left out"));
    }
    return List.copyOf(found);
  }
}

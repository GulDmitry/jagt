package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a task's own agent sessions have burned, read from the log each session keeps of itself. The
 * mark is PER LOG: a session appends, so only what is past its own mark is new, and a task may have
 * more than one log alive at once. The cost is list price for the model each turn names, counted
 * from the turn it was first read.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSpend(TokenUsage usage, Map<String, Long> marks) {

  public static final AgentSpend NONE = new AgentSpend(TokenUsage.NONE, Map.of());

  public AgentSpend {
    // Insertion order is the order the logs were first read in, and an immutable copy loses it.
    marks =
        marks == null
            ? Map.of()
            : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(marks));
  }

  /** Where a read of that log starts: what was already counted, or nothing for a log never seen. */
  @JsonIgnore
  public long markFor(String log) {
    return marks.getOrDefault(log, 0L);
  }

  public AgentSpend plus(TokenUsage added, String log, long mark) {
    Map<String, Long> moved = new LinkedHashMap<>(marks);
    moved.put(log, mark);
    return new AgentSpend(usageOrNone().plus(added), moved);
  }

  @JsonIgnore
  public TokenUsage usageOrNone() {
    return usage == null ? TokenUsage.NONE : usage;
  }
}

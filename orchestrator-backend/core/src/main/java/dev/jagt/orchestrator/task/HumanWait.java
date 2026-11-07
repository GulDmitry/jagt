package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A stretch in which the human held the task's turn; {@code until} is 0 while it stands. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HumanWait(long since, long until, Why why) {

  public enum Why {
    QUESTION,
    PERMISSION,
    VERDICT,
    STATUS,
    STOP
  }

  @JsonIgnore
  public boolean open() {
    return until == 0;
  }

  public HumanWait closedAt(long now) {
    return new HumanWait(since, now, why);
  }

  /** The waits as a finished task keeps them: one still open ends at {@code now}. */
  public static List<HumanWait> endedAt(List<HumanWait> waits, long now) {
    return waits.stream().map(wait -> wait.open() ? wait.closedAt(now) : wait).toList();
  }
}

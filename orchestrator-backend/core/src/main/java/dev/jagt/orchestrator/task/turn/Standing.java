package dev.jagt.orchestrator.task.turn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** Where the task stands, and every move that brought it there. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Standing(
    TaskStatus status,
    long lastActiveTimestamp,
    @Nullable String message,
    AgentReport report,
    // Append-only, oldest first: every status this task actually moved TO, with when.
    List<StatusChange> history) {

  /**
   * The log cannot be unbounded, so the oldest entries drop; the first stays, for it says who
   * opened the task.
   */
  public static final int MAX_HISTORY = 50;

  public Standing {
    history = List.copyOf(history);
  }

  /**
   * {@code event} records a step even when the status is unchanged: true for something DONE to the
   * task, false for a task repeating itself, whose keep-alives would otherwise drown the real
   * transitions.
   */
  public Standing moved(TaskStatus to, @Nullable String line, boolean event, long now) {
    List<StatusChange> known = seeded(now);
    boolean record = event || to != status;
    return new Standing(
        to, now, line, report, record ? appended(known, new StatusChange(to, now, null)) : known);
  }

  /**
   * Something was done to the task that leaves its status where it is: one step in its history,
   * nothing else touched.
   */
  public Standing moveRecorded(long now) {
    return new Standing(
        status,
        lastActiveTimestamp,
        message,
        report,
        appended(seeded(now), new StatusChange(status, now, null)));
  }

  public Standing touched(long now) {
    return new Standing(status, now, message, report, seeded(now));
  }

  public Standing withReport(AgentReport changed) {
    return new Standing(status, lastActiveTimestamp, message, changed, history);
  }

  /**
   * Stamps who caused the step just taken — separate from taking it, because the transition is
   * built where the work happens and the asker is known only at the entry point.
   */
  public Standing withLastChangeOrigin(ActionOrigin origin) {
    return stamped(step -> step.by(origin));
  }

  /** The checker role behind the step just taken, stamped as its origin is. */
  public Standing withLastChangeRole(String role) {
    return stamped(step -> step.byChecker(role));
  }

  /** The reason the asker gave for the step just taken, stamped as its origin is. */
  public Standing withLastChangeWhy(String why) {
    return stamped(step -> step.because(why));
  }

  /** The tree a ship was pressed on, stamped on the step just taken where that step is the ship. */
  public Standing withLastShipTree(String tree) {
    return stamped(step -> step.status() == TaskStatus.SHIPPING ? step.on(tree) : step);
  }

  /**
   * Whether the oldest steps have been dropped, which makes any total over the log a FLOOR, not the
   * figure.
   */
  public boolean historyAtCap() {
    return history.size() >= MAX_HISTORY;
  }

  /** How many times the task went out for review: every entry into CI_POLLING is one round. */
  public int rounds() {
    return (int) history.stream().filter(step -> step.status() == TaskStatus.CI_POLLING).count();
  }

  /**
   * Since when the task has been in its CURRENT status. NOT {@code lastActiveTimestamp}, which a
   * keep-alive bumps.
   */
  public long statusSince() {
    return history.isEmpty() ? lastActiveTimestamp : history.get(history.size() - 1).at();
  }

  /** The digest of the question standing on the task, which the Master's unanswered mark names. */
  public String askedDigest() {
    return Turn.digestOf(message == null ? "" : message);
  }

  /**
   * A task written before history existed is seeded with its current status at the last activity
   * stamp, or {@link #statusSince()} falls back to a stamp every keep-alive bumps.
   */
  private List<StatusChange> seeded(long now) {
    if (!history.isEmpty()) {
      return history;
    }
    long since = lastActiveTimestamp > 0 ? lastActiveTimestamp : now;
    return List.of(new StatusChange(status, since, null));
  }

  private Standing stamped(UnaryOperator<StatusChange> stamp) {
    if (history.isEmpty()) {
      return this;
    }
    List<StatusChange> restamped = new ArrayList<>(history);
    restamped.set(restamped.size() - 1, stamp.apply(restamped.getLast()));
    return new Standing(status, lastActiveTimestamp, message, report, restamped);
  }

  private static List<StatusChange> appended(List<StatusChange> history, StatusChange change) {
    List<StatusChange> grown = new ArrayList<>(history);
    grown.add(change);
    if (grown.size() > MAX_HISTORY) {
      grown.subList(1, grown.size() - MAX_HISTORY + 1).clear();
    }
    return List.copyOf(grown);
  }
}

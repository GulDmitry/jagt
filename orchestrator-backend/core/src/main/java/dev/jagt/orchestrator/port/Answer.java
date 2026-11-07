package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;
import java.util.Optional;
import java.util.function.Function;

/**
 * One question to a model plus what it cost. Empty {@code facts} = unavailable or failed; the usage
 * is reported all the same, a call that failed having been paid for. {@code failure} is the read's
 * own sentence for what stopped it, blank where none was given. {@code refused} marks a read
 * stopped by a tool the read gate refused: the same question is refused again. {@code reach} is set
 * where the failure is jagt's own lost reach.
 */
public record Answer<T>(
    Optional<T> facts,
    TokenUsage usage,
    String failure,
    boolean refused,
    Optional<ReachLost> reach) {

  public Answer(Optional<T> facts, TokenUsage usage, String failure, boolean refused) {
    this(facts, usage, failure, refused, Optional.empty());
  }

  public Answer(Optional<T> facts, TokenUsage usage, String failure) {
    this(facts, usage, failure, false);
  }

  public Answer(Optional<T> facts, TokenUsage usage) {
    this(facts, usage, "");
  }

  public static <T> Answer<T> failed(TokenUsage usage, String failure) {
    return new Answer<>(Optional.empty(), usage, failure);
  }

  /**
   * The read failed because jagt cannot reach the party at all, which is jagt's failure and no
   * task's.
   */
  public static <T> Answer<T> unreached(ReachLost lost) {
    return new Answer<>(Optional.empty(), TokenUsage.NONE, lost.said(), false, Optional.of(lost));
  }

  /** Never happened, so it cost nothing. */
  public static <T> Answer<T> unavailable() {
    return new Answer<>(Optional.empty(), TokenUsage.NONE);
  }

  /** Shapes the facts while carrying the cost through untouched. */
  public <R> Answer<R> map(Function<? super T, ? extends R> mapper) {
    return new Answer<>(facts.map(mapper), usage, failure, refused, reach);
  }
}

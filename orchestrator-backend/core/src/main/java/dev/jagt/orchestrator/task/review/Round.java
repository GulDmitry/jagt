package dev.jagt.orchestrator.task.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import org.jspecify.annotations.Nullable;

/** The review round on the request: when it opened, how it is polled, what it was told. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Round(
    // mrCreatedAt is when jagt stamped the round, not the host's own creation time. Zero = unset.
    long mrCreatedAt,
    // When the HOST says the request was opened; 0 until a read said so (a model read cannot).
    @JsonSetter(nulls = Nulls.AS_EMPTY) long requestOpenedAt,
    long lastPolledAt,
    @Nullable Boolean autoReview,
    // Whether the host says this round is approved. Null = no read has said yet.
    @Nullable Boolean approved,
    // Null until an auto-review ping was sent.
    @Nullable ToldOnce told,
    @Nullable RoundTrail trail,
    @Nullable Untaken untaken) {

  /** An unset (null) per-task flag follows the config default. */
  public boolean autoReviewEnabled(boolean configDefault) {
    return autoReview == null ? configDefault : autoReview;
  }

  public RoundTrail trailOrNone() {
    return trail == null ? RoundTrail.none() : trail;
  }

  /** The trail without a round the session has not answered yet. */
  public RoundTrail answeredTrail() {
    return untaken == null ? trailOrNone() : trailOrNone().withoutLast();
  }

  public ToldOnce toldOrNone() {
    return told == null ? ToldOnce.none() : told;
  }

  /** The host's own answer; a read that does not know it (0) must not erase one that did. */
  public Round withRequestOpenedAt(long openedAt) {
    return openedAt <= 0
        ? this
        : new Round(
            mrCreatedAt, openedAt, lastPolledAt, autoReview, approved, told, trail, untaken);
  }

  public Round withLastPolledAt(long polledAt) {
    return new Round(
        mrCreatedAt, requestOpenedAt, polledAt, autoReview, approved, told, trail, untaken);
  }

  public Round withApproved(@Nullable Boolean verdict) {
    return new Round(
        mrCreatedAt, requestOpenedAt, lastPolledAt, autoReview, verdict, told, trail, untaken);
  }

  public Round withTrail(RoundTrail changed) {
    return new Round(
        mrCreatedAt, requestOpenedAt, lastPolledAt, autoReview, approved, told, changed, untaken);
  }

  public Round withUntaken(@Nullable Untaken changed) {
    return new Round(
        mrCreatedAt, requestOpenedAt, lastPolledAt, autoReview, approved, told, trail, changed);
  }

  /** A report naming the untaken round ends it. */
  public Round answeredBy(@Nullable String report) {
    return untaken != null && untaken.namedBy(report) ? withUntaken(null) : this;
  }

  public Round withTold(ToldOnce changed) {
    return new Round(
        mrCreatedAt,
        requestOpenedAt,
        lastPolledAt,
        autoReview,
        approved,
        changed.isEmpty() ? null : changed,
        trail,
        untaken);
  }
}

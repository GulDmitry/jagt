package dev.jagt.orchestrator.task;

/**
 * What one acceptance run made of the session's decline of a round on {@code sha}: a non-blank
 * {@code dispute} names the evidence against it.
 */
public record AcceptanceDecline(String sha, String failure, String dispute) {

  public static AcceptanceDecline failed(String sha, String failure) {
    return new AcceptanceDecline(sha, failure, "");
  }
}

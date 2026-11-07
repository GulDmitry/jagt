package dev.jagt.orchestrator.task;

/**
 * What one QA run made of the session's decline of a round on {@code sha}: a non-blank {@code
 * dispute} names the evidence against it.
 */
public record QaDecline(String sha, String failure, String dispute) {

  public static QaDecline failed(String sha, String failure) {
    return new QaDecline(sha, failure, "");
  }
}

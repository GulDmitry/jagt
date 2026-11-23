package dev.jagt.orchestrator.port.codehost.review;

import lombok.With;

/**
 * What a request changes: its title and description, the branch it targets, its head commit, its
 * unified diff and whether it still takes review.
 */
@With
public record RequestChange(
    String title,
    String description,
    String baseBranch,
    String headSha,
    String diff,
    RequestState state) {

  public static RequestChange defaults() {
    return new RequestChange("", "", "", "", "", RequestState.OPEN);
  }
}

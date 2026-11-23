package dev.jagt.orchestrator.port.codehost.review;

import lombok.With;

/**
 * What a request changes: its title and description, the branch it targets, its head commit and its
 * unified diff.
 */
@With
public record RequestChange(
    String title, String description, String baseBranch, String headSha, String diff) {

  public static RequestChange defaults() {
    return new RequestChange("", "", "", "", "");
  }
}

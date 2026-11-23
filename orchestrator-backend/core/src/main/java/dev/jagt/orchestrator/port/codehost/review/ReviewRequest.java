package dev.jagt.orchestrator.port.codehost.review;

import lombok.With;

/** An open request awaiting review: its address, title, author's name and head commit. */
@With
public record ReviewRequest(String url, String title, String author, String headSha) {

  public static ReviewRequest defaults() {
    return new ReviewRequest("", "", "", "");
  }
}

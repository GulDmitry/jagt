package dev.jagt.orchestrator.port.codehost.review;

import lombok.With;

/**
 * One note: the host's id, the id of its thread, who wrote it, what it says, the file it is on
 * (blank for a note on the request) and whether its thread is resolved.
 */
@With
public record RequestNote(
    String id,
    String thread,
    String author,
    AuthorKind kind,
    String body,
    String path,
    Resolution resolution) {

  public static RequestNote defaults() {
    return new RequestNote("", "", "", AuthorKind.PERSON, "", "", Resolution.UNKNOWN);
  }
}

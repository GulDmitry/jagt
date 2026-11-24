package dev.jagt.orchestrator.port.codehost.review;

/**
 * The line of the request's new side a note sits on, at the commit it was read at; a blank file is
 * a note on the request itself.
 */
public record NoteAnchor(String file, int line, String headSha) {

  public static final NoteAnchor NONE = new NoteAnchor("", 0, "");

  public boolean inline() {
    return !file.isBlank() && line > 0;
  }
}

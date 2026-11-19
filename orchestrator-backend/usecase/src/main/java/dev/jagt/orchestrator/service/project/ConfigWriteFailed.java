package dev.jagt.orchestrator.service.project;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** A change to the configuration file that did not land; the file holds its last good content. */
public final class ConfigWriteFailed extends RuntimeException {

  public enum Reason {
    NOT_WRITABLE,
    WRITE_FAILED,
    UNREADABLE_RESTORED,
    UNREADABLE_NOT_RESTORED
  }

  private final Reason reason;
  private final Path file;

  public ConfigWriteFailed(Reason reason, Path file, String detail, @Nullable Throwable cause) {
    super(sentence(reason, file, detail), cause);
    this.reason = reason;
    this.file = file;
  }

  public Reason reason() {
    return reason;
  }

  public Path file() {
    return file;
  }

  private static String sentence(Reason reason, Path file, String detail) {
    String name = String.valueOf(file.getFileName());
    return switch (reason) {
      case NOT_WRITABLE -> "Cannot change " + file + ": it is not writable here";
      case WRITE_FAILED -> "Cannot change " + file + ": " + detail;
      case UNREADABLE_RESTORED ->
          name + " did not read after the change, so it was put back: " + detail;
      case UNREADABLE_NOT_RESTORED ->
          name
              + " did not read after the change and the last good file was not put back: "
              + detail;
    };
  }
}

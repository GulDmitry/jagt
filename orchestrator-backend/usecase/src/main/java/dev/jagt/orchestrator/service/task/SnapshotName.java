package dev.jagt.orchestrator.service.task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** The name of a task's snapshot in the archive: its stamp to the second, then the task. */
public final class SnapshotName {

  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

  private SnapshotName() {}

  public static String of(LocalDateTime at, String taskSlug) {
    return at.format(STAMP) + "-" + taskSlug;
  }
}

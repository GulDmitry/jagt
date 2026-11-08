package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A kind of status change, as a word a human types. {@code CREATED} is a task's first step, the
 * others the statuses a change lands on.
 */
enum ActivityKind {
  CREATED,
  SHIPPED,
  DEPLOYED,
  STOPPED,
  DONE;

  String word() {
    return name().toLowerCase(Locale.ROOT);
  }

  static Optional<ActivityKind> named(String word) {
    return Arrays.stream(values()).filter(kind -> kind.word().equalsIgnoreCase(word)).findFirst();
  }

  boolean covers(boolean first, TaskStatus to) {
    return switch (this) {
      case CREATED -> first;
      case SHIPPED -> to == TaskStatus.SHIPPING;
      case DEPLOYED -> to == TaskStatus.DEPLOYED;
      case STOPPED -> to == TaskStatus.CI_FAILED || to == TaskStatus.DEPLOY_CONFLICT;
      case DONE -> to == TaskStatus.DONE;
    };
  }
}

package dev.jagt.orchestrator.task;

import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Reads a state file written when QA's record was the task's top-level {@code qa}, into {@code
 * roles["qa"]}, once.
 */
final class LegacyRoles {

  private static final String QA = "qa";

  private LegacyRoles() {}

  static Map<String, RoleRecord> of(
      @Nullable Map<String, RoleRecord> roles, @Nullable RoleRecord qa) {
    Map<String, RoleRecord> read = new TreeMap<>();
    if (roles != null) {
      roles.forEach(
          (id, record) -> {
            if (record != null) {
              read.put(id, record);
            }
          });
    }
    if (qa != null) {
      read.putIfAbsent(QA, qa);
    }
    return read;
  }
}

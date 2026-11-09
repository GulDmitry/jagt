package dev.jagt.orchestrator.task;

import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Reads a state file written while the acceptance role was called {@code qa}: its record, whether
 * the task's top-level {@code qa} or {@code roles["qa"]}, into {@code roles["acceptance"]}, once.
 */
final class LegacyRoles {

  private static final String QA = "qa";
  private static final String ACCEPTANCE = "acceptance";

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
    RoleRecord renamed = read.remove(QA);
    if (renamed != null) {
      read.putIfAbsent(ACCEPTANCE, renamed);
    }
    if (qa != null) {
      read.putIfAbsent(ACCEPTANCE, qa);
    }
    return read;
  }
}

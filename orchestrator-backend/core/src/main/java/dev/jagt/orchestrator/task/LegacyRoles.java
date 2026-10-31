package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/** Reads a state file written when QA's record was the task's top-level {@code qa}, into {@code roles["qa"]}, once. */
final class LegacyRoles {

    private static final String QA = "qa";

    private LegacyRoles() {
    }

    static Map<String, RoleRecord> of(@Nullable Map<String, RoleRecord> roles, @Nullable RoleRecord qa) {
        Map<String, RoleRecord> read = roles == null ? Map.of() : roles;
        if (qa == null || read.containsKey(QA)) {
            return read;
        }
        Map<String, RoleRecord> merged = new TreeMap<>(read);
        merged.put(QA, qa);
        return merged;
    }
}

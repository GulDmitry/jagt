package dev.jagt.orchestrator.task.turn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.task.TaskStatus;
import org.jspecify.annotations.Nullable;

/**
 * One step a task actually took, in the order it happened. {@code at} is epoch millis; {@code
 * origin} is null for a step recorded before origins existed, {@code why} wherever the asker gave
 * no reason, {@code tree} the digest of the tree a ship was pressed on, {@code role} the checker
 * role id behind an acceptance origin, null for an entry written before it was kept.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusChange(
    TaskStatus status,
    long at,
    @Nullable ActionOrigin origin,
    @Nullable String why,
    @Nullable String tree,
    @Nullable String role) {

  public StatusChange(TaskStatus status, long at, @Nullable ActionOrigin origin) {
    this(status, at, origin, null, null, null);
  }

  public StatusChange by(@Nullable ActionOrigin origin) {
    return new StatusChange(status, at, origin, why, tree, role);
  }

  public StatusChange because(@Nullable String why) {
    return new StatusChange(status, at, origin, why, tree, role);
  }

  public StatusChange on(String tree) {
    return new StatusChange(status, at, origin, why, tree, role);
  }

  public StatusChange byChecker(String role) {
    return new StatusChange(status, at, origin, why, tree, role);
  }
}

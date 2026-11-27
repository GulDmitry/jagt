package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The counter of {@code kind} a read was taken at, as the task held it then. */
public record Generation(Kind kind, String at) {

  public enum Kind {
    SESSION_START,
    HEAD,
    /** The requests linked: a new round links new ones. */
    ROUND,
    /** The commit each landed target of the deploy stands at. */
    DEPLOY_COMMIT
  }

  /** A read dropped because the task's counter moved past the one it was taken at. */
  public record Stale(@Nullable String alias, Kind kind, String readAt, String current) {}

  public static Generation of(Kind kind, TaskState task) {
    return new Generation(
        kind,
        switch (kind) {
          case SESSION_START -> String.valueOf(task.work().session().generation());
          case HEAD -> Objects.toString(task.checks().headTip(), "");
          case ROUND -> String.join(" ", task.requests());
          case DEPLOY_COMMIT ->
              task.work().deployment() == null ? "" : task.work().deployment().tips();
        });
  }

  Optional<Stale> landingOn(@Nullable String alias, Generation current) {
    return !at.equals(current.at())
        ? Optional.of(new Stale(alias, kind, at, current.at()))
        : Optional.empty();
  }
}

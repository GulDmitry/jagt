package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.job.deploy.DeployConflicts;
import dev.jagt.orchestrator.task.deploy.Conflict;
import java.nio.file.Path;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestWaitingConflictBuilder(
    Path worktree,
    boolean stagedInFull,
    Conflict.Progress progress,
    @Nullable String failure,
    boolean revert,
    SessionStanding standing) {

  public static TestWaitingConflictBuilder waitingConflict() {
    return new TestWaitingConflictBuilder(
        Path.of("/src/ABC-42-deploy"),
        false,
        new Conflict.Progress(7, 0, "d"),
        null,
        false,
        SessionStanding.ABSENT);
  }

  public DeployConflicts.WaitingConflict build() {
    return new DeployConflicts.WaitingConflict(
        worktree, stagedInFull, progress, failure, revert, standing);
  }
}

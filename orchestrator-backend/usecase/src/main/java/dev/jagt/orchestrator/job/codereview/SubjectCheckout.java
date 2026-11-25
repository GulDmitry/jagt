package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A detached checkout of a request's head, read only and never built, gone when the read ends any
 * way.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubjectCheckout {

  private static final Duration TIMEOUT = Duration.ofMinutes(5);

  private final Processes processes;
  private final OrchestratorPaths paths;
  private final ConfigService configService;

  /** A checkout to read; closing it removes it. */
  public record Open(Path path, Runnable removal) implements AutoCloseable {

    @Override
    public void close() {
      removal.run();
    }
  }

  /** Empty where the head could not be fetched or checked out; the cause is logged. */
  public Optional<Open> open(Subject subject, String head) {
    ProjectConfig declared = configService.load().projects().get(subject.project());
    if (declared == null) {
      return Optional.empty();
    }
    Path repository = Path.of(declared.path());
    Path dir =
        paths
            .layout()
            .codeReviewCheckouts()
            .resolve(
                UUID.nameUUIDFromBytes(
                        (subject.url() + "\0" + head).getBytes(StandardCharsets.UTF_8))
                    .toString());
    Processes.Result fetched =
        processes.run(
            repository, TIMEOUT, List.of("git", "fetch", "--quiet", subject.remoteUrl(), head));
    if (fetched.exitCode() != 0) {
      return refused(subject, "fetch", fetched);
    }
    processes.run(
        repository, TIMEOUT, List.of("git", "worktree", "remove", "--force", dir.toString()));
    processes.run(repository, TIMEOUT, List.of("git", "worktree", "prune"));
    Processes.Result added =
        processes.run(
            repository,
            TIMEOUT,
            List.of("git", "worktree", "add", "--detach", dir.toString(), head));
    if (added.exitCode() != 0) {
      return refused(subject, "checkout", added);
    }
    return Optional.of(
        new Open(
            dir,
            () ->
                processes.run(
                    repository,
                    TIMEOUT,
                    List.of("git", "worktree", "remove", "--force", dir.toString()))));
  }

  private static Optional<Open> refused(Subject subject, String step, Processes.Result result) {
    log.atWarn()
        .setMessage("code review checkout failed")
        .addKeyValue("request", subject.url())
        .addKeyValue("step", step)
        .addKeyValue("cause", result.stderr().strip())
        .addKeyValue("effect", "the head stays unreviewed and is read again")
        .log();
    return Optional.empty();
  }
}

package dev.jagt.orchestrator.job.codereview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubjectCheckoutTest {

  private final Processes processes = mock(Processes.class);
  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final SubjectCheckout checkout = new SubjectCheckout(processes, paths, configService);
  private final Subject subject = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");

  @Test
  void checkoutLeftByKilledReadIsClearedBeforeTheHeadIsCheckedOutAgain() {
    when(paths.codeReviewCheckouts()).thenReturn(Path.of("/data/checkouts"));
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop", new ProjectConfig("/work/shop", "origin/main", null, List.of()))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    Optional<SubjectCheckout.Open> opened = checkout.open(subject, "abc123");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, org.mockito.Mockito.times(4))
        .run(any(), any(Duration.class), commands.capture());
    assertThat(opened).isPresent();
    assertThat(commands.getAllValues())
        .extracting(command -> String.join(" ", command.subList(1, 3)))
        .containsExactly("fetch --quiet", "worktree remove", "worktree prune", "worktree add");
  }
}

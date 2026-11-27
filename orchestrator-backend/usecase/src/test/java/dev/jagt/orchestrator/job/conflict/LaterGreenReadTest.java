package dev.jagt.orchestrator.job.conflict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.job.deploy.DeployPipelines;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LaterGreenReadTest {

  @Test
  void conflictNoLaterGreenDisprovesStandsUntouched() {
    DeployPipelines pipelines = mock(DeployPipelines.class);
    FlowReports reports = mock(FlowReports.class);
    TaskState conflicted = TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    when(pipelines.redConflictDisproved(conflicted)).thenReturn(false);

    boolean changed =
        new LaterGreenRead(mock(ConfigService.class), pipelines, reports)
            .read("ABC-42", conflicted);

    assertThat(changed).isFalse();
    verifyNoInteractions(reports);
  }

  @Test
  void endsTheConflictOnTheDeployCommitTheLaterGreenWasReadAgainst() {
    DeployPipelines pipelines = mock(DeployPipelines.class);
    FlowReports reports = mock(FlowReports.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
    TaskState conflicted = TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    when(pipelines.redConflictDisproved(conflicted)).thenReturn(true);

    boolean changed = new LaterGreenRead(config, pipelines, reports).read("ABC-42", conflicted);

    assertThat(changed).isTrue();
    verify(reports)
        .endRedConflict(
            "ABC-42", new Generation(Generation.Kind.DEPLOY_COMMIT, ""), "deployed to dev");
  }
}

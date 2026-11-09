package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class LaterGreenReadTest {

  @Test
  void aConflictNoLaterGreenDisprovesStandsUntouched() {
    DeployPipelines pipelines = mock(DeployPipelines.class);
    FlowReports reports = mock(FlowReports.class);
    TaskState conflicted = TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    when(pipelines.redConflictDisproved(conflicted)).thenReturn(false);

    boolean changed =
        new LaterGreenRead(mock(ConfigService.class), pipelines, reports).read("ABC-42", conflicted);

    assertThat(changed).isFalse();
    verifyNoInteractions(reports);
  }
}

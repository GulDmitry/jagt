package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineUrlTest {

  @Test
  void listedPipelineUrlIsKept() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build();

    assertThat(task.withPipelineUrl("https://host/pipelines/9").pipelineUrl())
        .isEqualTo("https://host/pipelines/9");
  }

  @Test
  void readThatListedNoneKeepsTheOneAnEarlierReadListed() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .pipelineUrl("https://host/pipelines/9")
            .build();

    assertThat(task.withPipelineUrl(" ").pipelineUrl()).isEqualTo("https://host/pipelines/9");
  }

  @Test
  void resettingTheChecksForgetsThePipelineUrl() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .pipelineUrl("https://host/pipelines/9")
            .build();

    assertThat(task.withChecksReset().pipelineUrl()).isNull();
  }
}

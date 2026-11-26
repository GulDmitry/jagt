package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskOffersTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @CsvSource({
    "REVIEWED,true,false",
    "APPROVED,true,true",
    "REVIEWED,false,true",
    "APPROVED,false,true"
  })
  void offersDeployOnReviewedRequestOnlyWhereNoApprovalIsRequired(
      TaskStatus status, boolean requireApproval, boolean offered) {
    Facts facts =
        Facts.defaults()
            .withDelivery(
                Facts.Delivery.defaults().withHasReviewRequest(true).withChecksGreen(true))
            .withReach(Facts.Reach.defaults().withRequireApproval(requireApproval));

    assertThat(rules.allows(status, TaskAction.DEPLOY, facts)).isEqualTo(offered);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED", "APPROVED"})
  void offersNoDeployWhileTheChecksAreNotGreenOnTheHead(TaskStatus status) {
    Facts facts =
        Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true));

    assertThat(rules.allows(status, TaskAction.DEPLOY, facts)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED", "APPROVED"})
  void offersDeployOnRequestWhoseHostListsNoPipeline(TaskStatus status) {
    TaskState task =
        TaskState.builder("demo", "/wt", status)
            .mrUrl("http://mr/1")
            .pipelineStatus("none")
            .build();

    assertThat(rules.allows(status, TaskAction.DEPLOY, Facts.projected(task))).isTrue();
  }
}

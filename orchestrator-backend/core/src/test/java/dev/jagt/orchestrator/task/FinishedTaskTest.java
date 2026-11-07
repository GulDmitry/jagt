package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class FinishedTaskTest {

  @Test
  void learnsFromWhereAHumanChoseToDoTheWork() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                .build(),
            2L);

    assertThat(task.routingWorthLearningFrom()).isTrue();
  }

  @Test
  void learnsFromARoutingOfItsOwnOnlyOnceTheWorkReachedTheSharedBranch() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER),
                        new StatusChange(TaskStatus.DEPLOYED, 2L, ActionOrigin.BOARD)))
                .build(),
            3L);

    assertThat(task.routingWorthLearningFrom()).isTrue();
  }

  @Test
  void learnsNothingFromARoutingOfItsOwnThatNothingEverConfirmed() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                .build(),
            2L);

    assertThat(task.routingWorthLearningFrom()).isFalse();
  }

  @Test
  void learnsNothingFromATaskThatWasDoneInSeveralRepositoriesAtOnce() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder(
                    List.of(TaskRepo.of("sc", "/wt-sc"), TaskRepo.of("api", "/wt-api")),
                    TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                .build(),
            2L);

    assertThat(task.routingWorthLearningFrom()).isFalse();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "ready,true,true,false",
        "' Ready ',true,true,false",
        "question,true,false,true",
        "needs-work,true,false,false",
        "'  ',false,false,false",
        "NULL,false,false,false"
      })
  void readsTheMastersVerdictOnlyByItsOneWordForPassingAndForAsking(
      String verdict, boolean judged, boolean passed, boolean asked) {
    var task =
        FinishedTask.of(
            "ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE).build(), 2L, verdict);

    assertThat(task.judged()).isEqualTo(judged);
    assertThat(task.passed()).isEqualTo(passed);
    assertThat(task.asked()).isEqualTo(asked);
  }

  @Test
  void saysWhoOpenedItFromTheFirstStepOnly() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD),
                        new StatusChange(TaskStatus.DONE, 2L, ActionOrigin.TRACKER)))
                .build(),
            3L);

    assertThat(task.openedBy()).isEqualTo(ActionOrigin.BOARD);
  }

  @Test
  void countsAJudgementBeforeDeployWhenTheLastReviewCameBackBeforeTheFirstDeploy() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null)))
                .build(),
            4L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isTrue();
  }

  @Test
  void countsNoJudgementBeforeDeployWhenAReviewCameBackAfterIt() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 4L, null)))
                .build(),
            5L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
  }

  @Test
  void measuresAgainstTheFirstDeployWhenTheTaskWasDeployedTwice() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 2L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 3L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 4L, null)))
                .build(),
            5L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
  }

  @Test
  void measuresAgainstTheFirstDeployEvenWhenThatWasTheVeryFirstStep() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.DEPLOYED, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null)))
                .build(),
            4L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
  }

  @Test
  void countsNoJudgementBeforeTheReviewRoundItself() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null)))
                .build(),
            3L);

    assertThat(task.judgedBefore(TaskStatus.REVIEW_PENDING)).isFalse();
  }

  @Test
  void countsADeployAsNeverJudgedWhenNoReviewEverCameBack() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, null)))
                .build(),
            2L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isTrue();
  }

  @Test
  void countsNoJudgementBeforeAStatusTheTaskNeverReached() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null)))
                .build(),
            2L);

    assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
  }

  @Test
  void takesFromItsFirstStepToTheMomentItWasRetired() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1_000L, null)))
                .build(),
            6_000L);

    assertThat(task.tookMillis()).isEqualTo(5_000L);
  }

  @Test
  void tookNothingWhenNoStepWasEverRecorded() {
    var task =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            6_000L,
            new FinishedTask.Spend(0, null, null, null),
            new FinishedTask.Course(List.of(), 0, null, null, null, null),
            null);

    assertThat(task.tookMillis()).isZero();
  }

  @Test
  void costsTheSessionAndTheReadsTogetherOnceTheSessionSpendWasRead() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .usage(TokenUsage.ofCall(10, 0, 10, 0.5))
                .agentSpend(new AgentSpend(TokenUsage.ofCall(10, 0, 10, 2.0), java.util.Map.of()))
                .build(),
            2L);

    assertThat(task.spend().costUsd()).isEqualTo(2.5);
  }

  @Test
  void costsNothingKnownWhenTheSessionSpendWasNeverRead() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .usage(TokenUsage.ofCall(10, 0, 10, 0.5))
                .build(),
            2L);

    assertThat(task.spend().costUsd()).isNull();
  }

  @Test
  void carriesBothUsageBreakdownsAndTheTranscriptPaths() {
    var task =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .usage(TokenUsage.ofCall(10, 0, 10, 0.5))
                .agentSpend(
                    new AgentSpend(
                        TokenUsage.ofCall(30, 5, 20, 2.0), java.util.Map.of("/logs/a.jsonl", 9L)))
                .build(),
            2L);

    assertThat(task.spend().usage()).isEqualTo(TokenUsage.ofCall(10, 0, 10, 0.5));
    assertThat(task.spend().agentUsage()).isEqualTo(TokenUsage.ofCall(30, 5, 20, 2.0));
    assertThat(task.transcripts()).containsExactly("/logs/a.jsonl");
  }

  @Test
  void carriesNoUsageForACountThatNeverReadAnything() {
    var task =
        FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE).build(), 2L);

    assertThat(task.spend().usage()).isNull();
    assertThat(task.spend().agentUsage()).isNull();
    assertThat(task.transcripts()).isEmpty();
  }

  @Test
  void readsTheCostAsALowerBoundWhenTheOwnReadsHadAnUnpricedCall() {
    var task =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            9L,
            new FinishedTask.Spend(
                0, 1.0, TokenUsage.ofUnpricedCall(10, 0, 10), TokenUsage.ofCall(10, 0, 10, 1.0)),
            new FinishedTask.Course(List.of(), 0, null, null, null, null),
            null);

    assertThat(task.spend().costIsLowerBound()).isTrue();
  }

  @Test
  void readsTheCostAsALowerBoundWhenTheAgentSessionHadAnUnpricedCall() {
    var task =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            9L,
            new FinishedTask.Spend(
                0, 1.0, TokenUsage.ofCall(10, 0, 10, 1.0), TokenUsage.ofUnpricedCall(10, 0, 10)),
            new FinishedTask.Course(List.of(), 0, null, null, null, null),
            null);

    assertThat(task.spend().costIsLowerBound()).isTrue();
  }

  @Test
  void readsTheCostAsCompleteWhenEveryCallWasPriced() {
    var task =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            9L,
            new FinishedTask.Spend(
                0, 1.0, TokenUsage.ofCall(10, 0, 10, 0.5), TokenUsage.ofCall(10, 0, 10, 0.5)),
            new FinishedTask.Course(List.of(), 0, null, null, null, null),
            null);

    assertThat(task.spend().costIsLowerBound()).isFalse();
  }

  @Test
  void readsNoCostAsNoBoundWhateverTheUnpricedCalls() {
    var task =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            9L,
            new FinishedTask.Spend(0, null, TokenUsage.ofUnpricedCall(10, 0, 10), null),
            new FinishedTask.Course(List.of(), 0, null, null, null, null),
            null);

    assertThat(task.spend().costIsLowerBound()).isFalse();
  }

  @Test
  void readsAndWritesAFinishedTaskAsOneFlatObject() {
    String usage =
        "{\"calls\":1,\"inputTokens\":10,\"cachedInputTokens\":0,\"outputTokens\":10,"
            + "\"costUsd\":0.5,\"coldStarts\":0,\"warmStarts\":0}";
    String json =
        "{\"id\":\"ABC-1\",\"alias\":\"abc\",\"title\":\"a thing\",\"projects\":[\"sc\"],"
            + "\"ticketUrl\":\"https://t/ABC-1\",\"finishedAt\":9,\"tokens\":20,\"costUsd\":0.5,"
            + "\"usage\":"
            + usage
            + ",\"agentUsage\":"
            + usage
            + ",\"history\":[{\"status\":\"NEW\",\"at\":1,\"origin\":\"BOARD\"}],\"rounds\":2,"
            + "\"checks\":\"green\",\"refusals\":{\"SESSION_LINE\":3},"
            + "\"waits\":[{\"since\":100,\"until\":700,\"why\":\"STATUS\"}],\"masterVerdict\":\"ready\","
            + "\"transcripts\":[\"/logs/a.jsonl\"]}";
    JsonMapper mapper = new JsonMapper();

    FinishedTask task = mapper.readValue(json, FinishedTask.class);

    assertThat(task.identity().id()).isEqualTo("ABC-1");
    assertThat(task.spend().costUsd()).isEqualTo(0.5);
    assertThat(task.course().refusals()).containsEntry(Guard.SESSION_LINE, 3);
    assertThat(task.course().waits())
        .containsExactly(new HumanWait(100, 700, HumanWait.Why.STATUS));
    assertThat(task.transcripts()).containsExactly("/logs/a.jsonl");
    assertThat(mapper.readTree(mapper.writeValueAsString(task))).isEqualTo(mapper.readTree(json));
  }
}

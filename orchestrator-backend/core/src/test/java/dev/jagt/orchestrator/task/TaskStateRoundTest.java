package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskStateRoundTest {

  @Test
  void opensNewPollingWindowForEveryRoundShippedOntoTheSameRequest() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/mr/9")
            .mrCreatedAt(1_000L)
            .lastPolledAt(9_000L)
            .build();

    TaskState round = polling.withReviewRound("https://host/mr/9", 2_000L);

    assertThat(round.round().mrCreatedAt()).isEqualTo(2_000L);
    assertThat(round.round().lastPolledAt()).isZero();
    assertThat(round.primary().mrUrl()).isEqualTo("https://host/mr/9");
  }

  @Test
  void keepsReviewRequestOnTheRepositoryItBelongsTo() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped =
        task.withReviewRound("php", "https://host/php/mr/1", 2_000)
            .withReviewRound("java", "https://host/java/mr/2", 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.hasReviewRequest()).isTrue();
  }

  @Test
  void keepsKnownRequestAgeWhenReadCannotSayWhenItWasOpened() {
    TaskState stamped =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    assertThat(stamped.withRequestOpenedAt(0).round().requestOpenedAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void datesRequestItJustOpenedByTheClockInsteadOfLeavingTheAgeBlank() {
    TaskState stamped =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    long opened = stamped.withMrUrl("http://mr/2", 1_700_000_005_000L).round().requestOpenedAt();

    assertThat(opened).isEqualTo(1_700_000_005_000L);
    assertThat(stamped.withMrUrl("http://mr/1", 1_700_000_005_000L).round().requestOpenedAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void keepsWhatWasReadWhenTheLinkHasNotChanged() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("https://host/mr/1")
            .approved(true)
            .pipelineStatus("success")
            .build();

    assertThat(task.withMrUrl("https://host/mr/1", 2_000).round().approved()).isTrue();
  }

  @Test
  void followsTheConfigDefaultForAutoReviewOnlyWhereTheTaskSetNone() {
    TaskState on = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(true).build();
    TaskState off = TaskState.builder("proj", "/wt", TaskStatus.NEW).autoReview(false).build();
    TaskState unset = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

    assertThat(on.round().autoReviewEnabled(false)).isTrue();
    assertThat(off.round().autoReviewEnabled(true)).isFalse();
    assertThat(unset.round().autoReviewEnabled(true)).isTrue();
    assertThat(unset.round().autoReviewEnabled(false)).isFalse();
  }

  @Test
  void findsTheReviewRequestOfOneProjectAndNoneForProjectWithoutOne() {
    TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("php", "/wt/php").withMrUrl("https://host/php/mr/1"),
                    TaskRepo.of("java", "/wt/java"),
                    TaskRepo.of("web", "/wt/web").withMrUrl(" ")),
                TaskStatus.REVIEW_PENDING)
            .build();

    assertThat(task.reviewRequestOf("php")).contains("https://host/php/mr/1");
    assertThat(task.reviewRequestOf("java")).isEmpty();
    assertThat(task.reviewRequestOf("web")).isEmpty();
  }

  @Test
  void recordsOneRoundForShipAcrossRepositoriesCarryingTheSessionRepositoryLink() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped =
        task.withReviewRound(
            Map.of("php", "https://host/php/mr/1", "java", "https://host/java/mr/2"), 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isEqualTo("https://host/php/mr/1");
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.mrUrl()).isEqualTo("https://host/php/mr/1");
  }

  @Test
  void leavesTheSessionRepositoryUnlinkedWhenTheShipLandedOnlyInAnother() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.REVIEW_PENDING)
            .build();

    TaskState shipped = task.withReviewRound(Map.of("java", "https://host/java/mr/2"), 2_000);

    assertThat(shipped.repo("php").orElseThrow().mrUrl()).isNull();
    assertThat(shipped.repo("java").orElseThrow().mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.mrUrl()).isEqualTo("https://host/java/mr/2");
    assertThat(shipped.round().mrCreatedAt()).isEqualTo(2_000);
  }

  @Test
  void leavesTheTaskAloneWhenShipLandedInNoRepository() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    assertThat(task.withReviewRound(Map.of(), 2_000)).isSameAs(task);
  }

  @Test
  void keepsTheLastPollAndTheRemoteUrlItWasGiven() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("php", "/wt/php"), TaskRepo.of("java", "/wt/java")),
                TaskStatus.DEPLOYED)
            .build();

    TaskState noted = task.withLastPolledAt(42).withRemoteUrl("java", "git@host:g/java.git");

    assertThat(noted.round().lastPolledAt()).isEqualTo(42);
    assertThat(noted.repo("java").orElseThrow().remoteUrl()).isEqualTo("git@host:g/java.git");
    assertThat(noted.repo("php").orElseThrow().remoteUrl()).isNull();
  }
}

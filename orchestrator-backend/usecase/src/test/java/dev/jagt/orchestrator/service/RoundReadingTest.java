package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoundReadingTest {

  private final StateService stateService = mock(StateService.class);
  private final ReviewRound reviewReader = mock(ReviewRound.class);
  private final MergedRequests mergedRequests = mock(MergedRequests.class);
  private final RoundReading reading = new RoundReading(stateService, reviewReader, mergedRequests);

  @Test
  void saysThereIsNoRequestToReadWithoutTouchingTheCodeHost() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR));
    verifyNoInteractions(reviewReader);
  }

  @Test
  void doesNotCallARoundCleanWhileOneRepositoryHasNoRequestAtAll() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", null)),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(mergedRequests.owingARequest(any())).thenReturn(List.of("web"));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused -> {
              assertThat(refused.result().kind())
                  .isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
              assertThat(refused.result().message()).contains("no request in web");
            });
    verifyNoInteractions(reviewReader);
  }

  @Test
  void waitsOnNoRequestFromARepositoryWithNoCodeHostWhateverRoundCameAfterItsPush() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", null, null)),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(mergedRequests.owingARequest(any())).thenReturn(List.of());
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
  }

  @Test
  void reportsAnUnreadableReviewInsteadOfTreatingItAsClean() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .alias("a1")
                    .mrUrl("http://mr/1")
                    .build()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
  }

  @Test
  void namesWhatStoppedAnUnreadableReviewOnOneLine() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .alias("a1")
                    .mrUrl("http://mr/1")
                    .build()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.empty(),
                TokenUsage.NONE,
                "tracker server failed to connect\n(CONNECT_TIMEOUT)"));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().message())
                    .isEqualTo(
                        "error: read failed: http://mr/1 — tracker server failed to connect"
                            + " (CONNECT_TIMEOUT)"));
  }

  @Test
  void failsTheWholeSweepWhenOneRepositoriesRequestCannotBeRead() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, true, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
  }

  @Test
  void keepsAThreadWaitingOnTheReviewerInOneOfSeveralRepositories() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of(), List.of("mia: Why?\nagent: Because."), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round ->
                assertThat(round.facts().discussion().awaitingReviewer())
                    .containsExactly("[web] mia: Why?\nagent: Because."));
  }

  @Test
  void holdsATaskBackWhileOneOfItsRepositoriesIsStillBuilding() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("running", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().pipeline().status()).isEqualTo("running"));
  }

  @Test
  void isApprovedOnlyWhenEveryRepositoryIs() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, true, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().request().approved()).isFalse());
  }

  @Test
  void namesTheRepositoryEachThreadCameFrom() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of("http://mr/api#note_1\nbot: tighten this"),
                            List.of(),
                            List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(
                            List.of("http://mr/web#note_2\nbot: rename that"),
                            List.of(),
                            List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round ->
                assertThat(round.facts().discussion().threads())
                    .containsExactly(
                        "[api] http://mr/api#note_1\nbot: tighten this",
                        "[web] http://mr/web#note_2\nbot: rename that"));
  }

  @Test
  void quotesTheLogOfTheRepositoryThatFailedRatherThanOfTheGreenOne() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("failed", "lint: unused import Widget", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round ->
                assertThat(round.facts().pipeline().failure())
                    .isEqualTo("[web] lint: unused import Widget"));
  }

  @Test
  void carriesTheProjectWhosePipelineTheRoundsChecksAre() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("failed", "lint", "http://pipe/web"),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class, round -> assertThat(round.project()).isEqualTo("web"));
  }

  @Test
  void reportsTheOldestRequestOfAMultiRepoTaskAsHowLongTheReviewHasBeenWaiting() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            TaskRepo.of("api", "/wt-api").withMrUrl("http://mr/1"),
                            TaskRepo.of("web", "/wt-web").withMrUrl("http://mr/2")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 1_700_000_100_000L),
                        new ReviewFacts.Pipeline("running", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/2"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 1_700_000_000_000L),
                        new ReviewFacts.Pipeline("running", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().request().openedAt()).isEqualTo(1_700_000_000_000L));
  }

  @Test
  void ignoresARequestWhoseAgeIsUnknownWhenReportingHowLongTheReviewHasWaited() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            TaskRepo.of("api", "/wt-api").withMrUrl("http://mr/1"),
                            TaskRepo.of("web", "/wt-web").withMrUrl("http://mr/2")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-1", "api", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("running", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-1", "web", "http://mr/2"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 1_700_000_000_000L),
                        new ReviewFacts.Pipeline("running", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().request().openedAt()).isEqualTo(1_700_000_000_000L));
  }

  @Test
  void readsTheOneRequestOfASingleRepositoryTaskAsItsRound() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .alias("a1")
                    .mrUrl("http://mr/1")
                    .build()));
    ReviewFacts facts =
        new ReviewFacts(
            new ReviewFacts.Request(true, true, false, false, 7),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(List.of("rename x"), List.of(), List.of()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(new Answer<>(Optional.of(facts), TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> {
              assertThat(round.mrUrl()).isEqualTo("http://mr/1");
              assertThat(round.facts()).isEqualTo(facts);
            });
  }

  @Test
  void refusesARoundWhoseRequestTheHostSaysDoesNotExist() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .alias("a1")
                    .mrUrl("http://mr/1")
                    .build()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(false, false, false, false, 0),
                        new ReviewFacts.Pipeline("", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused -> {
              assertThat(refused.result().kind())
                  .isEqualTo(ReviewSweepService.SweepResult.Kind.GONE);
              assertThat(refused.result().message())
                  .isEqualTo("error: no such request: http://mr/1 (the host says so)");
            });
  }

  @Test
  void callsARequestClosedOnTheHostGoneAndNamesIt() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .alias("a1")
                    .mrUrl("http://mr/1")
                    .build()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused -> {
              assertThat(refused.result().kind())
                  .isEqualTo(ReviewSweepService.SweepResult.Kind.GONE);
              assertThat(refused.request()).isEqualTo("http://mr/1");
            });
  }

  @Test
  void judgesTheRemainingRepositoriesWhereOneRequestWasClosedAndLeftTheTask() {
    TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");
    when(stateService.task("ABC-42"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"), web),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-42", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, true, false, false, 0),
                        new ReviewFacts.Pipeline("success", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(reviewReader.read("ABC-42", "web", "http://mr/web"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(mergedRequests.hostSaidClosed("ABC-42", web)).thenReturn(true);

    var read = reading.read("ABC-42");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> {
              assertThat(round.project()).isEqualTo("api");
              assertThat(round.mrUrl()).isEqualTo("http://mr/api");
              assertThat(round.facts().request().approved()).isTrue();
            });
  }

  @Test
  void callsARequestTheHostSaysDoesNotExistGoneWhateverElseTheTaskHolds() {
    when(stateService.task("ABC-42"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(
                            new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                            new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-42", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(false, false, false, false, 0),
                        new ReviewFacts.Pipeline("", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));

    var read = reading.read("ABC-42");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.GONE));
    verify(mergedRequests, never()).hostSaidClosed(any(), any());
  }

  @Test
  void
      refusesARoundWithNoRequestLeftWhereTheClosedOnesWereTheOnlyRequestsAndAnotherRepositoryHasNone() {
    TaskRepo api = new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api");
    when(stateService.task("ABC-42"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(api, new TaskRepo("docs", "/docs-wt", null, null)),
                        TaskStatus.CI_POLLING)
                    .alias("a1")
                    .build()));
    when(reviewReader.read("ABC-42", "api", "http://mr/api"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, true, 0),
                        new ReviewFacts.Pipeline("none", "", ""),
                        new ReviewFacts.Discussion(List.of(), List.of(), List.of()))),
                TokenUsage.NONE));
    when(mergedRequests.hostSaidClosed("ABC-42", api)).thenReturn(true);

    var read = reading.read("ABC-42");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR));
  }
}

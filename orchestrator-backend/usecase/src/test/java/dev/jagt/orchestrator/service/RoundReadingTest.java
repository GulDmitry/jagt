package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.ReviewReadFixtures.answer;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.api;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.polling;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.pollingOneRepository;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.quiet;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.request;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.unreadable;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.web;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoundReadingTest {

  private final StateService stateService = mock(StateService.class);
  private final ReviewRound reviewReader = mock(ReviewRound.class);
  private final MergedRequests mergedRequests = mock(MergedRequests.class);
  private final RoundReading reading = new RoundReading(stateService, reviewReader, mergedRequests);
  private final RoundReadingFixture fixture =
      new RoundReadingFixture(stateService, reviewReader);

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
  void doesNotCallRoundCleanWhileOneRepositoryHasNoRequestAtAll() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                polling(api(), new TaskRepo("web", "/web-wt", "git@host:g/web.git", null))));
    when(mergedRequests.owingRequest(any())).thenReturn(List.of("web"));

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
  void waitsOnNoRequestFromRepositoryWithNoCodeHostWhateverRoundCameAfterItsPush() {
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(polling(api(), new TaskRepo("web", "/web-wt", null, null))));
    when(mergedRequests.owingRequest(any())).thenReturn(List.of());
    when(reviewReader.read("ABC-1", "api", "http://mr/api")).thenReturn(unreadable());

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
    when(stateService.task("ABC-1")).thenReturn(Optional.of(pollingOneRepository()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1")).thenReturn(unreadable());

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
    when(stateService.task("ABC-1")).thenReturn(Optional.of(pollingOneRepository()));
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
    fixture.twoRepositories(
        "ABC-1",
        answer(true, "success"),
        unreadable());

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
  }

  @Test
  void keepsThreadWaitingOnTheReviewerInOneOfSeveralRepositories() {
    fixture.twoRepositories(
        "ABC-1",
        answer(false, "success"),
        answer(
            request(false),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of(), List.of("mia: Why?\nagent: Because."), List.of())));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round ->
                assertThat(round.facts().discussion().awaitingReviewer())
                    .containsExactly("[web] mia: Why?\nagent: Because."));
  }

  @Test
  void holdsTaskBackWhileOneOfItsRepositoriesIsStillBuilding() {
    fixture.twoRepositories(
        "ABC-1",
        answer(false, "success"),
        answer(false, "running"));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().pipeline().status()).isEqualTo("running"));
  }

  @Test
  void isApprovedOnlyWhenEveryRepositoryIs() {
    fixture.twoRepositories(
        "ABC-1",
        answer(true, "success"),
        answer(false, "success"));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().request().approved()).isFalse());
  }

  @Test
  void namesTheRepositoryEachThreadCameFrom() {
    fixture.twoRepositories(
        "ABC-1",
        answer(
            request(false),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("http://mr/api#note_1\nbot: tighten this"), List.of(), List.of())),
        answer(
            request(false),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("http://mr/web#note_2\nbot: rename that"), List.of(), List.of())));

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
    fixture.twoRepositories(
        "ABC-1",
        answer(false, "success"),
        answer(false, "failed", "lint: unused import Widget", ""));

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
    fixture.twoRepositories(
        "ABC-1",
        answer(false, "success"),
        answer(false, "failed", "lint", "http://pipe/web"));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class, round -> assertThat(round.project()).isEqualTo("web"));
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 1_700_000_100_000L})
  void reportsTheOldestKnownRequestOfMultiRepoTaskAsHowLongTheReviewHasBeenWaiting(
      long apiOpenedAt) {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                polling(
                    TaskRepo.of("api", "/wt-api").withMrUrl("http://mr/1"),
                    TaskRepo.of("web", "/wt-web").withMrUrl("http://mr/2"))));
    when(reviewReader.read("ABC-1", "api", "http://mr/1"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(true, false, false, false, apiOpenedAt),
                new ReviewFacts.Pipeline("running", "", ""),
                quiet()));
    when(reviewReader.read("ABC-1", "web", "http://mr/2"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(true, false, false, false, 1_700_000_000_000L),
                new ReviewFacts.Pipeline("running", "", ""),
                quiet()));

    var read = reading.read("ABC-1");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Round.class,
            round -> assertThat(round.facts().request().openedAt()).isEqualTo(1_700_000_000_000L));
  }

  @Test
  void readsTheOneRequestOfSingleRepositoryTaskAsItsRound() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(pollingOneRepository()));
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
  void refusesRoundWhoseRequestTheHostSaysDoesNotExist() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(pollingOneRepository()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(false, false, false, false, 0),
                new ReviewFacts.Pipeline("", "", ""),
                quiet()));

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
  void callsRequestClosedOnTheHostGoneAndNamesIt() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(pollingOneRepository()));
    when(reviewReader.read("ABC-1", "proj", "http://mr/1"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(true, false, false, true, 0),
                new ReviewFacts.Pipeline("none", "", ""),
                quiet()));

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
    fixture.twoRepositories(
        "ABC-42",
        answer(true, "success"),
        answer(
            new ReviewFacts.Request(true, false, false, true, 0),
            new ReviewFacts.Pipeline("none", "", ""),
            quiet()));
    when(mergedRequests.hostSaidClosed("ABC-42", web())).thenReturn(true);

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
  void callsRequestTheHostSaysDoesNotExistGoneWhateverElseTheTaskHolds() {
    when(stateService.task("ABC-42")).thenReturn(Optional.of(polling(api(), web())));
    when(reviewReader.read("ABC-42", "api", "http://mr/api"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(false, false, false, false, 0),
                new ReviewFacts.Pipeline("", "", ""),
                quiet()));

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
      refusesRoundWithNoRequestLeftWhereTheClosedOnesWereTheOnlyRequestsAndAnotherRepoHasNone() {
    when(stateService.task("ABC-42"))
        .thenReturn(Optional.of(polling(api(), new TaskRepo("docs", "/docs-wt", null, null))));
    when(reviewReader.read("ABC-42", "api", "http://mr/api"))
        .thenReturn(
            answer(
                new ReviewFacts.Request(true, false, false, true, 0),
                new ReviewFacts.Pipeline("none", "", ""),
                quiet()));
    when(mergedRequests.hostSaidClosed("ABC-42", api())).thenReturn(true);

    var read = reading.read("ABC-42");

    assertThat(read)
        .isInstanceOfSatisfying(
            RoundReading.Refused.class,
            refused ->
                assertThat(refused.result().kind())
                    .isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR));
  }
}

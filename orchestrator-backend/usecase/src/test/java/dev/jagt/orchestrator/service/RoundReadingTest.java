package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoundReadingTest {

    private final StateService stateService = mock(StateService.class);
    private final ReviewReader reviewReader = mock(ReviewReader.class);
    private final RoundReading reading = new RoundReading(stateService, reviewReader);

    @Test
    void saysThereIsNoRequestToReadWithoutTouchingTheCodeHost() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Refused.class, refused -> assertThat(
                refused.result().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.NO_MR));
        verifyNoInteractions(reviewReader);
    }

    @Test
    void doesNotCallARoundCleanWhileOneRepositoryHasNoRequestAtAll() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", null, null)),
                TaskStatus.CI_POLLING).alias("a1").build()));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Refused.class, refused -> {
            assertThat(refused.result().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.PENDING);
            assertThat(refused.result().message()).contains("no request in web");
        });
        verifyNoInteractions(reviewReader);
    }

    @Test
    void reportsAnUnreadableReviewInsteadOfTreatingItAsClean() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").mrUrl("http://mr/1").build()));
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(Optional.empty());

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Refused.class, refused -> assertThat(
                refused.result().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
    }

    @Test
    void failsTheWholeSweepWhenOneRepositoriesRequestCannotBeRead() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web", null)),
                TaskStatus.CI_POLLING).alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/api"))
                .thenReturn(Optional.of(new ReviewFacts(true, true, "success", List.of())));
        when(reviewReader.read("ABC-1", "http://mr/web")).thenReturn(Optional.empty());

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Refused.class, refused -> assertThat(
                refused.result().kind()).isEqualTo(ReviewSweepService.SweepResult.Kind.UNREADABLE));
    }

    @Test
    void holdsATaskBackWhileOneOfItsRepositoriesIsStillBuilding() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web", null)),
                TaskStatus.CI_POLLING).alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/api"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "success", List.of())));
        when(reviewReader.read("ABC-1", "http://mr/web"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "running", List.of())));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Round.class, round -> assertThat(
                round.facts().pipelineStatus()).isEqualTo("running"));
    }

    @Test
    void isApprovedOnlyWhenEveryRepositoryIs() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web", null)),
                TaskStatus.CI_POLLING).alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/api"))
                .thenReturn(Optional.of(new ReviewFacts(true, true, "success", List.of())));
        when(reviewReader.read("ABC-1", "http://mr/web"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "success", List.of())));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Round.class, round -> assertThat(
                round.facts().approved()).isFalse());
    }

    @Test
    void namesTheRepositoryEachThreadCameFrom() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web", null)),
                TaskStatus.CI_POLLING).alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/api")).thenReturn(Optional.of(new ReviewFacts(true, false,
                "success", List.of("http://mr/api#note_1\nbot: tighten this"))));
        when(reviewReader.read("ABC-1", "http://mr/web")).thenReturn(Optional.of(new ReviewFacts(true, false,
                "success", List.of("http://mr/web#note_2\nbot: rename that"))));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Round.class, round -> assertThat(
                round.facts().threads()).containsExactly("[api] http://mr/api#note_1\nbot: tighten this",
                "[web] http://mr/web#note_2\nbot: rename that"));
    }

    @Test
    void quotesTheLogOfTheRepositoryThatFailedRatherThanOfTheGreenOne() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder(List.of(
                new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web", null)),
                TaskStatus.CI_POLLING).alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/api"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "success", List.of())));
        when(reviewReader.read("ABC-1", "http://mr/web")).thenReturn(Optional.of(new ReviewFacts(true, false,
                "failed", "lint: unused import Widget", List.of(), 0)));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Round.class, round -> assertThat(
                round.facts().pipelineFailure()).isEqualTo("[web] lint: unused import Widget"));
    }

    @Test
    void reportsTheOldestRequestOfAMultiRepoTaskAsHowLongTheReviewHasBeenWaiting() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder(List.of(TaskRepo.of("api", "/wt-api").withMrUrl("http://mr/1"),
                        TaskRepo.of("web", "/wt-web").withMrUrl("http://mr/2")), TaskStatus.CI_POLLING)
                .alias("a1").build()));
        when(reviewReader.read("ABC-1", "http://mr/1"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "running", List.of(), 1_700_000_100_000L)));
        when(reviewReader.read("ABC-1", "http://mr/2"))
                .thenReturn(Optional.of(new ReviewFacts(true, false, "running", List.of(), 1_700_000_000_000L)));

        var read = reading.read("ABC-1");

        assertThat(read).isInstanceOfSatisfying(RoundReading.Round.class, round -> assertThat(
                round.facts().openedAt()).isEqualTo(1_700_000_000_000L));
    }
}

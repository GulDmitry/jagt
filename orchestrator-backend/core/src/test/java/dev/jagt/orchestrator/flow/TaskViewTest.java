package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TaskViewTest {

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> watches() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.watching(1L), Owner.CI),
                org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.none(), Owner.CI),
                org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.windowElapsed(24), Owner.YOU),
                org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.offForTask(), Owner.YOU),
                org.junit.jupiter.params.provider.Arguments.of(AutoReviewWatch.noRound(), Owner.YOU));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("watches")
    void asksForAHumanOnlyWhereAnExpectedPollHasStopped(AutoReviewWatch watch, Owner expected) {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://host/mr/1").build(), false, watch, Map.of("proj", "dev"));

        assertThat(view.owner()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:fetch('/api/tasks/a1/actions/deploy',{method:'POST'})",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "not a url at all",
    })
    void dropsALinkThatIsNotHttpRatherThanHandingItToAnAnchor(String hostile) {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .ticketUrl(hostile).mrUrl(hostile).build(), false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.ticketUrl()).isNull();
        assertThat(view.reviewRequestUrl()).isNull();
    }

    @Test
    void keepsRealLinksAndTrimsTheWhitespaceAroundThem() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .ticketUrl("  https://tracker.example.com/browse/ABC-1  ")
                .mrUrl("http://host.example.com/x/-/merge_requests/9").build(), false, AutoReviewWatch.none(),
                Map.of("proj", "dev"));

        assertThat(view.ticketUrl()).isEqualTo("https://tracker.example.com/browse/ABC-1");
        assertThat(view.reviewRequestUrl()).isEqualTo("http://host.example.com/x/-/merge_requests/9");
    }

    @Test
    void leavesBothLinksUnsetWhenTheTaskHasNone() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build(),
                false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.ticketUrl()).isNull();
        assertThat(view.reviewRequestUrl()).isNull();
    }

    @Test
    void stillSaysSomethingWhenTheStoredRequestLinkWasUnusable() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("javascript:alert(1)").build(), false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.detail())
                .isEqualTo("PROBLEM: review request link unusable: javascript:alert(1)");
    }

    @Test
    void carriesEveryRepositoryWithItsOwnRequestSoNoSurfaceHasToGuessWhichDiffIsWhich() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                new TaskRepo("api", "/api-wt", "git@host:g/api.git", "https://host/api/-/merge_requests/1", null),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "https://host/web/-/merge_requests/2", null)),
                TaskStatus.CI_POLLING).alias("a1").build(), false, AutoReviewWatch.none(),
                Map.of("api", "dev", "web", "dev"));

        assertThat(view.repos()).extracting(TaskView.RepoView::project, TaskView.RepoView::reviewRequestUrl)
                .containsExactly(tuple("api", "https://host/api/-/merge_requests/1"),
                        tuple("web", "https://host/web/-/merge_requests/2"));
        assertThat(view.project()).isEqualTo("api");
    }

    @Test
    void doneStandsApartFromTheOtherVerbs() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1")
                .build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).filteredOn(TaskView.ActionView::apart).extracting(TaskView.ActionView::id)
                .containsExactly("done");
    }

    @Test
    void doneCarriesNoKey() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1")
                .build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).filteredOn(action -> action.id().equals("done")).singleElement()
                .extracting(TaskView.ActionView::key).isEqualTo("");
    }

    @Test
    void shipCarriesItsLetter() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1")
                .build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).filteredOn(action -> action.id().equals("ship")).singleElement()
                .extracting(TaskView.ActionView::key).isEqualTo("s");
    }

    @Test
    void highlightsNoVerbWhileTheAgentHoldsTheMove() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1")
                .build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).noneMatch(TaskView.ActionView::primary);
    }

    @Test
    void highlightsTheMovesVerbWhereTheMoveIsTheHumans() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.CI_FAILED).alias("a1")
                .mrUrl("http://host/mr/1").build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).filteredOn(TaskView.ActionView::primary).extracting(TaskView.ActionView::id)
                .containsExactly("sweep");
    }

    @Test
    void theHistorySaysEachStatusInTheBoardsWords() {
        TaskState started = TaskState.builder("api", "/api-wt", TaskStatus.NEW).alias("a1").build();
        TaskView view = TaskView.of("ABC-1", started.withStatus(TaskStatus.IN_PROGRESS, "working"), false,
                AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.history()).extracting(TaskView.Step::label).endsWith("agent working");
    }

    @Test
    void onlyFocusRidesOnTheRowOfAWorkingTask() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/api-wt", TaskStatus.IN_PROGRESS).alias("a1")
                .build(), false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.actions()).filteredOn(TaskView.ActionView::onRow).extracting(TaskView.ActionView::id)
                .containsExactly("focus");
    }

    @Test
    void offersASweepAsSoonAsANYRepositoryHasARequestOpen() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                TaskRepo.of("api", "/api-wt").withRemoteUrl("git@host:g/api.git"),
                new TaskRepo("web", "/web-wt", "git@host:g/web.git", "https://host/web/-/merge_requests/2", null)),
                TaskStatus.CI_POLLING).alias("a1").build(), false, AutoReviewWatch.none(),
                Map.of("api", "dev", "web", "dev"));

        assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("sweep");
        assertThat(view.actions()).filteredOn(action -> action.id().equals("sweep"))
                .singleElement().extracting(TaskView.ActionView::group).isEqualTo("flow");
    }

    @Test
    void marksATaskWhoseWorkIsOnASharedBranchAfterTheDeployStatusHasMovedOn() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1")
                .deployCommit("abc1234").build(), false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.deployed()).isTrue();
    }

    @Test
    void takesTheMarkOffOnceTheRevertHasForgottenTheMergeCommit() {
        TaskState reverted = TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1")
                .deployCommit("abc1234").build().withDeployCommit("proj", null);

        TaskView view = TaskView.of("ABC-1", reverted, false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.deployed()).isFalse();
    }

    @Test
    void marksTheDeployVerbAsARepeatWhileItsLastRunIsStillLive() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .mrUrl("https://host/mr/1").deployCommit("abc1234").build(),
                false, AutoReviewWatch.none(), Map.of("proj", "dev"));

        assertThat(view.actions()).filteredOn(action -> action.id().equals("deploy"))
                .singleElement().extracting(TaskView.ActionView::again).isEqualTo(true);
    }

    @Test
    void offersTheRevertOnACardWhoseDeployBrokeOffAfterARepositoryLanded() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                TaskRepo.of("api", "/api-wt").withDeployCommit("abc1234"),
                TaskRepo.of("web", "/web-wt")),
                TaskStatus.APPROVED).alias("a1").build(), false, AutoReviewWatch.none(),
                Map.of("api", "dev", "web", "dev"));

        assertThat(view.actions()).extracting(TaskView.ActionView::id).contains("revert");
    }

    @Test
    void marksAMultiRepoTaskAsSoonAsONERepositoryHasLanded() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(
                TaskRepo.of("api", "/api-wt").withDeployCommit("abc1234"),
                TaskRepo.of("web", "/web-wt")),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").build(), false, AutoReviewWatch.none(),
                Map.of("api", "dev", "web", "dev"));

        assertThat(view.deployed()).isTrue();
    }

    @Test
    void asksBeforeEveryPressThatCannotBeTakenBackNamingTheBranchEachRepositoryWrites() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder(List.of(TaskRepo.of("php", "/wt/php"),
                TaskRepo.of("java", "/wt/java")), TaskStatus.APPROVED).build(), false, AutoReviewWatch.none(),
                Map.of("php", "main"));

        assertThat(view.confirmations()).containsOnlyKeys("deploy", "revert", "done");
        assertThat(view.confirmations().get("deploy"))
                .contains("php → main").contains("java → no deployBranch in jagt.yml");
    }

    @Test
    void carriesTheRequestAgeAndTheApprovalOnlyWhileARequestIsOpen() {
        TaskState open = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).mrUrl("http://host/mr/1")
                .requestOpenedAt(1_700_000_000_000L).approved(true).build();
        TaskState none = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .requestOpenedAt(1_700_000_000_000L).approved(true).build();

        TaskView withRequest = TaskView.of("ABC-1", open, false, AutoReviewWatch.none(), Map.of("api", "dev"));
        TaskView withoutRequest = TaskView.of("ABC-1", none, false, AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(withRequest.requestOpenedAt()).isEqualTo(1_700_000_000_000L);
        assertThat(withRequest.approved()).isTrue();
        assertThat(withoutRequest.requestOpenedAt()).isZero();
        assertThat(withoutRequest.approved()).isNull();
    }

    @Test
    void readsATaskWithNoWatchAsOneNothingPolls() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://host/mr/1").build(), false, null, Map.of("api", "dev"));

        assertThat(view.owner()).isEqualTo(Owner.CI);
    }

    @Test
    void namesTheMasterAsTheStatusLabelWhileItReadsTheRound() {
        TaskView view = TaskView.of("ABC-1", TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).build(),
                RoundState.NONE.withMasterReading(true), AutoReviewWatch.none(), Map.of("api", "dev"));

        assertThat(view.statusLabel()).isEqualTo("master review");
    }
}

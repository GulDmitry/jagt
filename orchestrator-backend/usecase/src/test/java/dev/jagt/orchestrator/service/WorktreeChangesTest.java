package dev.jagt.orchestrator.service;

import java.util.Map;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorktreeChangesTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final WorktreeInspection inspection = mock(WorktreeInspection.class);

    @Test
    void readsARepositoryGitCannotAnswerForAsHoldingWorkSoNoShipPassesItOver() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.hasUncommittedChanges(any(Path.class), any(Path.class)))
                .thenThrow(new IllegalStateException("git status in /wt failed (exit 128): not a git repository"));

        boolean holdsWork = new WorktreeChanges(configService, inspection).holdsWork("demo", "/wt", "main");

        assertThat(holdsWork).isTrue();
    }

    @Test
    void findsNothingNewToShipWhereNoRepositoryHoldsWorkAndNoneIsInReview() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));

        boolean anythingNew = new WorktreeChanges(configService, inspection)
                .anythingNewToShip(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(anythingNew).isFalse();
    }

    @Test
    void namesWhyARepositorysDiffCouldNotBeReadRatherThanQuotingNothing() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changesSince(any(Path.class), any(Path.class), any(String.class)))
                .thenThrow(new IllegalStateException("git merge-base in /wt failed (exit 1)"));

        String diff = new WorktreeChanges(configService, inspection)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).isEqualTo("# demo\njagt could not read it: git merge-base in /wt failed (exit 1)\n");
    }

    @Test
    void namesNoChangedPathsWhereGitCouldNotReadThemRatherThanFindingNone() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changedPaths(any(Path.class), any(Path.class), any(String.class)))
                .thenThrow(new IllegalStateException("git merge-base in /wt failed (exit 1)"));

        var paths = new WorktreeChanges(configService, inspection)
                .paths(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(paths).isEmpty();
    }

    @Test
    void quotesNoDiffTooLargeForAPromptSoEachRoleReadsItItself() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changesSince(any(Path.class), any(Path.class), any(String.class)))
                .thenReturn("+x".repeat(50_001));

        String diff = new WorktreeChanges(configService, inspection)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).isEmpty();
    }

    @Test
    void readsAgentFileLinesGitCannotCountAsUnknownRatherThanNone() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.agentFileLinesAdded(any(Path.class), any(Path.class), any(String.class)))
                .thenThrow(new IllegalStateException("git merge-base in /wt failed (exit 1)"));

        Optional<Integer> added = new WorktreeChanges(configService, inspection)
                .agentFileLinesAdded(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(added).isEmpty();
    }

    @Test
    void findsUncommittedChangesWhereAnyRepositoryHoldsThem() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(configService.project("web")).thenReturn(new ProjectConfig("/web", "origin/main", "dev", List.of()));
        when(inspection.hasUncommittedChanges(Path.of("/repo"), Path.of("/wt"))).thenReturn(false);
        when(inspection.hasUncommittedChanges(Path.of("/web"), Path.of("/wt-web"))).thenReturn(true);
        TaskState task = TaskState.builder(List.of(TaskRepo.of("demo", "/wt"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.REVIEW_PENDING).build();

        WorktreeChanges changes = new WorktreeChanges(configService, inspection);

        assertThat(changes.uncommitted(task)).contains(true);
        assertThat(changes.anyUncommitted(task)).isTrue();
    }

    @Test
    void findsNoUncommittedChangesWhereEveryRepositoryIsClean() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.hasUncommittedChanges(Path.of("/repo"), Path.of("/wt"))).thenReturn(false);
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

        assertThat(new WorktreeChanges(configService, inspection).uncommitted(task)).contains(false);
    }

    @Test
    void readsAWorktreeGitCannotAnswerForAsCleanForTheYesNoQuestionAndUnknownForTheOther() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.hasUncommittedChanges(any(Path.class), any(Path.class)))
                .thenThrow(new IllegalStateException("git status in /wt failed (exit 128)"));
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

        WorktreeChanges changes = new WorktreeChanges(configService, inspection);

        assertThat(changes.anyUncommitted(task)).isFalse();
        assertThat(changes.uncommitted(task)).isEmpty();
    }

    @Test
    void joinsEveryRepositorysTreeStateIntoOne() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(configService.project("web")).thenReturn(new ProjectConfig("/web", "origin/main", "dev", List.of()));
        when(inspection.treeState(Path.of("/repo"), Path.of("/wt"))).thenReturn("a1");
        when(inspection.treeState(Path.of("/web"), Path.of("/wt-web"))).thenReturn("b2");
        TaskState task = TaskState.builder(List.of(TaskRepo.of("demo", "/wt"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.REVIEW_PENDING).build();

        assertThat(new WorktreeChanges(configService, inspection).state(task)).contains("a1;b2;");
    }

    @Test
    void namesNoTreeStateWhereGitCouldNotReadOne() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.treeState(any(Path.class), any(Path.class)))
                .thenThrow(new IllegalStateException("git status in /wt failed (exit 128)"));

        Optional<String> state = new WorktreeChanges(configService, inspection)
                .state(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(state).isEmpty();
    }

    @Test
    void holdsNoWorkWhereTheWorktreeIsCleanAndNothingIsAheadOfItsTarget() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));

        assertThat(new WorktreeChanges(configService, inspection).holdsWork("demo", "/wt", "main")).isFalse();
    }

    @Test
    void holdsWorkWhereCommitsAreAheadOfTheTargetThoughTheWorktreeIsClean() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.aheadOfTarget(Path.of("/repo"), Path.of("/wt"), "main")).thenReturn(true);

        assertThat(new WorktreeChanges(configService, inspection).holdsWork("demo", "/wt", "main")).isTrue();
    }

    @Test
    void shipsNothingNewWhereARequestIsOpenAndTheBranchIsPushedUnchanged() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.aheadOfTarget(Path.of("/repo"), Path.of("/wt"), "main")).thenReturn(true);
        TaskState task = TaskState.builder(List.of(new TaskRepo("demo", "/wt", null, "https://host/demo/-/merge_requests/1", null)),
                TaskStatus.REVIEW_PENDING).build();

        assertThat(new WorktreeChanges(configService, inspection).anythingNewToShip(task)).isFalse();
    }

    @Test
    void shipsACommitTheRemoteBranchDoesNotHoldYet() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.unpublished(Path.of("/repo"), Path.of("/wt"))).thenReturn(true);

        assertThat(new WorktreeChanges(configService, inspection)
                .anythingNewToShip(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).isTrue();
    }

    @Test
    void shipsWhereGitCannotSayWhetherAnythingIsNew() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.unpublished(Path.of("/repo"), Path.of("/wt"))).thenThrow(new IllegalStateException("git failed"));

        assertThat(new WorktreeChanges(configService, inspection)
                .anythingNewToShip(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).isTrue();
    }

    @Test
    void shipsAnUncommittedChangeNoRequestCarriesYet() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.hasUncommittedChanges(Path.of("/repo"), Path.of("/wt"))).thenReturn(true);

        assertThat(new WorktreeChanges(configService, inspection)
                .anythingNewToShip(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).isTrue();
    }

    @Test
    void quotesEachRepositorysChangesUnderItsProjectName() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changesSince(Path.of("/repo"), Path.of("/wt"), "origin/main")).thenReturn("+added\n");

        String diff = new WorktreeChanges(configService, inspection)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).isEqualTo("# demo\n+added\n");
    }

    @Test
    void quotesADiffOfExactlyTheLargestSizeAPromptTakes() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changesSince(any(Path.class), any(Path.class), any(String.class)))
                .thenReturn("+".repeat(99_993));

        String diff = new WorktreeChanges(configService, inspection)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).hasSize(100_000);
    }

    @Test
    void namesEachRepositorysChangedPathsByProject() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.changedPaths(Path.of("/repo"), Path.of("/wt"), "origin/main")).thenReturn(List.of("a.txt", "b.txt"));

        var paths = new WorktreeChanges(configService, inspection)
                .paths(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(paths).contains(Map.of("demo", List.of("a.txt", "b.txt")));
    }

    @Test
    void joinsEveryRepositorysOwnDiffIntoOne() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.ownDiffId(Path.of("/repo"), Path.of("/wt"), "origin/main")).thenReturn(Optional.of("d1"));

        assertThat(new WorktreeChanges(configService, inspection)
                .ownDiff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).contains("d1;");
    }

    @Test
    void namesNoOwnDiffWhereOneRepositoryCannotSayWhatItsOwnIs() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.ownDiffId(any(Path.class), any(Path.class), any(String.class))).thenReturn(Optional.empty());

        assertThat(new WorktreeChanges(configService, inspection)
                .ownDiff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).isEmpty();
    }

    @Test
    void namesNoOwnDiffWhereGitCouldNotReadOne() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(inspection.ownDiffId(any(Path.class), any(Path.class), any(String.class)))
                .thenThrow(new IllegalStateException("git merge-base in /wt failed (exit 1)"));

        assertThat(new WorktreeChanges(configService, inspection)
                .ownDiff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build())).isEmpty();
    }

    @Test
    void addsUpTheAgentFileLinesEveryRepositoryTookOn() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(configService.project("web")).thenReturn(new ProjectConfig("/web", "origin/main", "dev", List.of()));
        when(inspection.agentFileLinesAdded(Path.of("/repo"), Path.of("/wt"), "origin/main")).thenReturn(2);
        when(inspection.agentFileLinesAdded(Path.of("/web"), Path.of("/wt-web"), "origin/main")).thenReturn(1);
        TaskState task = TaskState.builder(List.of(TaskRepo.of("demo", "/wt"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.REVIEW_PENDING).build();

        assertThat(new WorktreeChanges(configService, inspection).agentFileLinesAdded(task)).contains(3);
    }
}

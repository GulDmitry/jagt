package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.task.FailedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubCliTest {

    private final Processes processes = mock(Processes.class);
    private final ReachLedger reach = new ReachLedger();
    private final GitHubCli github = new GitHubCli(processes, reach);
    private final HostSettings host = new HostSettings("shop", "orchestrator.codeHost", null, Path.of("/repo"),
            List.of(), List.of(), List.of(), List.of());

    @Test
    void aRunReRunsOnlyItsFailedJobsOnTheHostItLivesOn() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", "Requested rerun of run 5"));

        github.retryFailed(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("run", "rerun", "5", "--failed", "-R",
                "ghe.corp.invalid/acme/shop");
    }

    @Test
    void aCommitIsListedOnTheEnterpriseHostItsRemoteNames() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        github.onCommit(host, "git@ghe.corp.invalid:acme/shop.git", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("run", "list", "--commit", "0123abc", "-R",
                "ghe.corp.invalid/acme/shop");
    }

    @Test
    void theNewestRunsOnTheBranchAheadOfTheDeployCommitAreTheAnswerAtTheirWorstOfOneCommit() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"status\":\"completed\",\"conclusion\":\"success\","
                        + "\"url\":\"https://ghe.corp.invalid/acme/shop/actions/runs/9\",\"headSha\":\"fedcba98\"},"
                        + "{\"status\":\"completed\",\"conclusion\":\"failure\","
                        + "\"url\":\"https://ghe.corp.invalid/acme/shop/actions/runs/8\",\"headSha\":\"11111111\"}]", ""))
                .thenReturn(new Processes.Result(0, "ahead\n", ""));

        Answer<CommitPipeline> answer = github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(0)).containsSubsequence("run", "list", "--branch", "dev", "-R",
                "ghe.corp.invalid/acme/shop");
        assertThat(command.getAllValues().get(1)).containsSubsequence("api", "--hostname", "ghe.corp.invalid",
                "repos/acme/shop/compare/0123abc...fedcba98");
        assertThat(answer.facts()).contains(new CommitPipeline("success",
                "https://ghe.corp.invalid/acme/shop/actions/runs/9"));
    }

    @Test
    void theNewestCommitOnTheBranchReadsRunningWhileOneOfItsRunsHasNotFinished() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"status\":\"completed\",\"conclusion\":\"success\","
                        + "\"url\":\"u9\",\"headSha\":\"fedcba98\"},"
                        + "{\"status\":\"in_progress\",\"conclusion\":\"\","
                        + "\"url\":\"u10\",\"headSha\":\"fedcba98\"}]", ""))
                .thenReturn(new Processes.Result(0, "ahead\n", ""));

        Answer<CommitPipeline> answer = github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(0)).doesNotContain("--status");
        assertThat(answer.facts()).contains(new CommitPipeline("running", "u10"));
    }

    @Test
    void aRunOnTheBranchThatIsNotAheadOfTheDeployCommitReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"status\":\"completed\",\"conclusion\":\"success\","
                        + "\"url\":\"u\",\"headSha\":\"fedcba98\"}]", ""))
                .thenReturn(new Processes.Result(0, "diverged\n", ""));

        Answer<CommitPipeline> answer = github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void aLocalPathRemoteHasNoHostToAskForABranch() {
        Answer<CommitPipeline> answer = github.onBranchSince(host, "/srv/git/web.git", "dev", "0123abc");

        assertThat(answer.facts().orElseThrow().hosted()).isFalse();
        verifyNoInteractions(processes);
    }

    @Test
    void aBranchWithNoRunReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        Answer<CommitPipeline> answer = github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void aRunThatNeverStartedIsToldAsInfrastructure() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "{\"conclusion\":\"startup_failure\",\"jobs\":[]}", ""));

        Answer<List<FailedJob>> answer = github.failedJobs(host, "https://github.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.facts()).contains(List.of(new FailedJob("5", "workflow", "startup_failure", true)));
    }

    @Test
    void aJobThatFailedItsStepsIsToldAsTheCodes() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "{\"conclusion\":\"failure\",\"jobs\":[{\"databaseId\":3,\"name\":\"build\",\"conclusion\":\"failure\"},"
                        + "{\"databaseId\":4,\"name\":\"lint\",\"conclusion\":\"success\"}]}", ""));

        Answer<List<FailedJob>> answer = github.failedJobs(host, "https://github.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.facts()).contains(List.of(new FailedJob("3", "build", "failure", false)));
    }

    @ParameterizedTest
    @CsvSource({"completed,success,success", "completed,failure,failed", "completed,cancelled,failed",
            "completed,startup_failure,failed", "completed,timed_out,failed", "in_progress,'',running",
            "queued,'',running", "completed,'',unknown"})
    void aRunIsToldInJagtsOwnWords(String status, String conclusion, String verdict) {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"" + status + "\",\"conclusion\":\"" + conclusion + "\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"}]", ""));

        Answer<CommitPipeline> answer = github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline(verdict,
                "https://github.example.invalid/acme/shop/actions/runs/1"));
    }

    @Test
    void aCommitReadsAsItsWorstRun() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"},"
                        + "{\"status\":\"in_progress\",\"conclusion\":\"\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/2\"}]", ""));

        Answer<CommitPipeline> answer = github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("running",
                "https://github.example.invalid/acme/shop/actions/runs/2"));
    }

    @Test
    void aCommitWithTwoRunsEquallyBadReadsAsTheFirst() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"completed\",\"conclusion\":\"failure\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"},"
                        + "{\"status\":\"completed\",\"conclusion\":\"failure\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/2\"}]", ""));

        Answer<CommitPipeline> answer = github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("failed",
                "https://github.example.invalid/acme/shop/actions/runs/1"));
    }

    @Test
    void aSignedOutGhNamesItsWebLoginOnTheHostAsTheFix() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "HTTP 401"));

        Answer<String> answer = github.retryFailed(host, "https://github.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.reach().orElseThrow().fix())
                .containsExactly("gh", "auth", "login", "--hostname", "github.example.invalid", "--web");
    }

    @Test
    void aCliExitingNonZeroIsReportedWithItsExitCodeAndWhatItSaid() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(2, "", "boom"));

        Answer<String> answer = github.retryFailed(host, "https://github.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.failure()).isEqualTo("gh exited 2: boom");
    }

    @Test
    void aRequestIsClosedByItsAddressOnTheHostItLivesOn() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", "Closed pull request #7"));

        Answer<String> answer = github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/pull/7");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("pr", "close", "https://ghe.corp.invalid/acme/shop/pull/7");
        assertThat(answer.facts()).isPresent();
    }

    @Test
    void anAddressThatIsNoRequestIsNotClosedAndNothingIsRun() {
        Answer<String> answer = github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

        assertThat(answer.failure()).startsWith("not the address of a request");
        verifyNoInteractions(processes);
    }

    @Test
    void aRefusalOfTheHostOnCloseCarriesItsOwnWords() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "GraphQL: Resource not accessible"));

        Answer<String> answer = github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/pull/7");

        assertThat(answer.failure()).contains("Resource not accessible");
        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void anUnresolvedThreadAnsweredLastByAnotherAccountGivesItsNewestNoteId() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, """
                {"data":{"viewer":{"login":"agent"},"repository":{"pullRequest":{"reviewThreads":{"nodes":[
                {"isResolved":false,"comments":{"nodes":[{"databaseId":101,"author":{"login":"mia"}}]}},
                {"isResolved":true,"comments":{"nodes":[{"databaseId":102,"author":{"login":"mia"}}]}},
                {"isResolved":false,"comments":{"nodes":[{"databaseId":103,"author":{"login":"agent"}}]}}]}}}}}
                """, ""));

        Answer<List<String>> answer = github.unresolvedNotes(host, "https://ghe.corp.invalid/acme/shop/pull/7");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("api", "graphql", "--hostname", "ghe.corp.invalid");
        assertThat(command.getValue()).contains("owner=acme", "name=shop", "number=7");
        assertThat(answer.facts()).contains(List.of("101"));
    }

    @Test
    void anAddressThatIsNoRequestHasNoNotesReadAndNothingIsRun() {
        Answer<List<String>> answer = github.unresolvedNotes(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

        assertThat(answer.failure()).startsWith("not the address of a request");
        verifyNoInteractions(processes);
    }
}

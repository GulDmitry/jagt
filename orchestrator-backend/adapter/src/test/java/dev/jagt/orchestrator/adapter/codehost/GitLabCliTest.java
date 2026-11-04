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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitLabCliTest {

    private final Processes processes = mock(Processes.class);
    private final ReachLedger reach = new ReachLedger();
    private final GitLabCli gitlab = new GitLabCli(processes, reach);
    private final HostSettings host = new HostSettings("shop", "orchestrator.codeHost", null, Path.of("/repo"),
            List.of(), List.of(), List.of());

    @Test
    void aPipelineIsRetriedThroughItsRetryEndpoint() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "{\"status\":\"pending\"}", ""));

        Answer<String> answer = gitlab.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/77");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("api", "--hostname", "git.example.invalid", "-X", "POST",
                "projects/shop%2Fweb/pipelines/77/retry");
        assertThat(answer.facts()).isPresent();
    }

    @Test
    void aHostWithPortKeepsItInTheHostnameWhereTheRemoteHasOne() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        gitlab.onCommit(host, "https://git.example.invalid:8443/shop/web.git", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("--hostname", "git.example.invalid:8443");
    }

    @Test
    void theConfiguredHostNameAnswersInPlaceOfTheOneAnSshRemoteNames() {
        HostSettings named = new HostSettings("shop", "orchestrator.codeHost", "gitlab.corp.invalid", Path.of("/repo"),
                List.of(), List.of(), List.of());
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        gitlab.onCommit(named, "git@work-alias:shop/web.git", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("--hostname", "gitlab.corp.invalid");
    }

    @Test
    void aJobCarriesTheReasonTheHostGivesAndIsToldAsInfrastructure() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"id\":9,\"name\":\"unit-tests\",\"failure_reason\":\"runner_system_failure\"},"
                        + "{\"id\":10,\"name\":\"lint\",\"failure_reason\":\"script_failure\"}]", ""));

        Answer<List<FailedJob>> answer = gitlab.failedJobs(host, "https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).contains(List.of(new FailedJob("9", "unit-tests", "runner_system_failure", true),
                new FailedJob("10", "lint", "script_failure", false)));
    }

    @ParameterizedTest
    @CsvSource({"success,success", "failed,failed", "canceled,failed", "running,running", "pending,running",
            "manual,unknown"})
    void aCommitsPipelineIsToldInJagtsOwnWords(String hostWord, String verdict) {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"" + hostWord + "\",\"web_url\":\"https://git.example.invalid/shop/web/-/pipelines/78\"}]", ""));

        Answer<CommitPipeline> answer = gitlab.onCommit(host, "git@git.example.invalid:shop/web.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline(verdict,
                "https://git.example.invalid/shop/web/-/pipelines/78"));
    }

    @Test
    void aLaterPipelineOnTheBranchWhoseCommitHasTheDeployCommitBehindItIsTheAnswer() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"sha\":\"fedcba98\",\"status\":\"success\","
                        + "\"web_url\":\"https://git.example.invalid/shop/web/-/pipelines/80\"}]", ""))
                .thenReturn(new Processes.Result(0, "{\"id\":\"0123abc\"}", ""));

        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(0)).contains("projects/shop%2Fweb/pipelines?ref=dev&scope=finished&per_page=1");
        assertThat(command.getAllValues().get(1)).contains(
                "projects/shop%2Fweb/repository/merge_base?refs[]=0123abc&refs[]=fedcba98");
        assertThat(answer.facts()).contains(new CommitPipeline("success",
                "https://git.example.invalid/shop/web/-/pipelines/80"));
    }

    @Test
    void aPipelineOnTheBranchWhoseCommitDoesNotHaveTheDeployCommitBehindItReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"sha\":\"fedcba98\",\"status\":\"success\",\"web_url\":\"u\"}]", ""))
                .thenReturn(new Processes.Result(0, "{\"id\":\"5555555\"}", ""));

        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void theDeployCommitsOwnPipelineIsTheNewestOneWithoutAskingForAMergeBase() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(0, "[{\"sha\":\"0123abcdef\",\"status\":\"failed\",\"web_url\":\"u\"}]", ""));

        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

        verify(processes).run(isNull(), any(Duration.class), anyList());
        assertThat(answer.facts()).contains(new CommitPipeline("failed", "u"));
    }

    @Test
    void aBranchWithNoFinishedPipelineReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void aLocalPathRemoteHasNoHostToAskForABranch() {
        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "/srv/git/web.git", "dev", "0123abc");

        assertThat(answer.facts().orElseThrow().hosted()).isFalse();
        verifyNoInteractions(processes);
    }

    @Test
    void aBranchListingTheHostRefusesIsAFailureNotAnAnswer() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "HTTP 500"));

        Answer<CommitPipeline> answer = gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void aCommitTheHostRanNoPipelineForReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        Answer<CommitPipeline> answer = gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void aProjectTheHostHidesFromACliSignedOutSaysNotLoggedIn() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "glab: 404 Project Not Found (HTTP 404)"))
                .thenReturn(new Processes.Result(1, "", "git.example.invalid has not been authenticated with glab"));

        Answer<CommitPipeline> answer = gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
    }

    @Test
    void aSignedOutGlabNamesItsLoginOnTheHostAsTheFix() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "HTTP 401"));

        Answer<CommitPipeline> answer = gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.reach().orElseThrow().fix())
                .containsExactly("glab", "auth", "login", "--hostname", "git.example.invalid");
    }

    @Test
    void aCliNotOnPathIsUnavailableNamingIt() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenThrow(new IllegalStateException("Failed to start command: glab"));

        Answer<String> answer = gitlab.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.failure()).startsWith("glab ");
    }

    @Test
    void aCommitWhoseHostAnswersWithSomethingThatIsNotJsonSaysSo() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "<html>", ""));

        Answer<CommitPipeline> answer = gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.failure()).isEqualTo("glab gave output that is not JSON");
    }

    @Test
    void aLocalPathRemoteHasNoHostToAsk() {
        Answer<CommitPipeline> answer = gitlab.onCommit(host, "/srv/git/shop.git", "0123abc");

        assertThat(answer.facts().orElseThrow().hosted()).isFalse();
        verifyNoInteractions(processes);
    }

    @Test
    void anAddressThatIsNoPipelineIsRefusedWithoutRunningAnything() {
        Answer<List<FailedJob>> answer = gitlab.failedJobs(host, "https://git.example.invalid/shop/web/-/merge_requests/4");

        assertThat(answer.failure()).startsWith("not the address of a pipeline");
        verifyNoInteractions(processes);
    }
}

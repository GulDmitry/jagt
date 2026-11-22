package dev.jagt.orchestrator.adapter.codehost.cli;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CommitPipeline;
import dev.jagt.orchestrator.port.codehost.RequestDraft;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.task.deploy.FailedJob;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class GitHubCliTest {

  private final Processes processes = mock(Processes.class);
  private final ReachLedger reach = new ReachLedger();
  private final GitHubCli github = new GitHubCli(processes, reach);
  private final HostSettings host = hostSettings().withRepository(Path.of("/repo")).build();

  @Test
  void runReRunsOnlyItsFailedJobsOnTheHostItLivesOn() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", "Requested rerun of run 5"));

    github.retryFailed(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("run", "rerun", "5", "--failed", "-R", "ghe.corp.invalid/acme/shop");
  }

  @Test
  void commitIsListedOnTheEnterpriseHostItsRemoteNames() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    github.onCommit(host, "git@ghe.corp.invalid:acme/shop.git", "0123abc");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence(
            "run", "list", "--commit", "0123abc", "-R", "ghe.corp.invalid/acme/shop");
  }

  @Test
  void theNewestRunsOnTheBranchAheadOfTheDeployCommitAreTheAnswerAtTheirWorstOfOneCommit() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\","
                    + "\"url\":\"https://ghe.corp.invalid/acme/shop/actions/runs/9\",\"headSha\":\"fedcba98\"},"
                    + "{\"status\":\"completed\",\"conclusion\":\"failure\","
                    + "\"url\":\"https://ghe.corp.invalid/acme/shop/actions/runs/8\",\"headSha\":\"11111111\"}]",
                ""))
        .thenReturn(new Processes.Result(0, "ahead\n", ""));

    Answer<CommitPipeline> answer =
        github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

    ArgumentCaptor<List<String>> captured = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), captured.capture());
    List<List<String>> command = captured.getAllValues();
    assertThat(command.get(0))
        .containsSubsequence("run", "list", "--branch", "dev", "-R", "ghe.corp.invalid/acme/shop");
    assertThat(command.get(1))
        .containsSubsequence(
            "api", "--hostname", "ghe.corp.invalid", "repos/acme/shop/compare/0123abc...fedcba98");
    assertThat(answer.facts())
        .contains(
            new CommitPipeline("success", "https://ghe.corp.invalid/acme/shop/actions/runs/9"));
  }

  @Test
  void theNewestCommitOnTheBranchReadsRunningWhileOneOfItsRunsHasNotFinished() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\","
                    + "\"url\":\"u9\",\"headSha\":\"fedcba98\"},"
                    + "{\"status\":\"in_progress\",\"conclusion\":\"\","
                    + "\"url\":\"u10\",\"headSha\":\"fedcba98\"}]",
                ""))
        .thenReturn(new Processes.Result(0, "ahead\n", ""));

    Answer<CommitPipeline> answer =
        github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

    ArgumentCaptor<List<String>> captured = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), captured.capture());
    assertThat(captured.getAllValues().get(0)).doesNotContain("--status");
    assertThat(answer.facts()).contains(new CommitPipeline("running", "u10"));
  }

  @Test
  void runOnTheBranchThatIsNotAheadOfTheDeployCommitReadsAsNone() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\","
                    + "\"url\":\"u\",\"headSha\":\"fedcba98\"}]",
                ""))
        .thenReturn(new Processes.Result(0, "diverged\n", ""));

    Answer<CommitPipeline> answer =
        github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void localPathRemoteHasNoHostToAskForBranch() {
    Answer<CommitPipeline> answer =
        github.onBranchSince(host, "/srv/git/web.git", "dev", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    verifyNoInteractions(processes);
  }

  @Test
  void branchWithNoRunReadsAsNone() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<CommitPipeline> answer =
        github.onBranchSince(host, "git@ghe.corp.invalid:acme/shop.git", "dev", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void runThatNeverStartedIsToldAsInfrastructure() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "{\"conclusion\":\"startup_failure\",\"jobs\":[]}", ""));

    Answer<List<FailedJob>> answer =
        github.failedJobs(host, "https://github.example.invalid/acme/shop/actions/runs/5");

    assertThat(answer.facts())
        .contains(List.of(new FailedJob("5", "workflow", "startup_failure", true)));
  }

  @Test
  void jobThatFailedItsStepsIsToldAsTheCodes() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"conclusion\":\"failure\",\"jobs\":[{\"databaseId\":3,\"name\":\"build\",\"con"
                    + "clusion\":\"failure\"},"
                    + "{\"databaseId\":4,\"name\":\"lint\",\"conclusion\":\"success\"}]}",
                ""));

    Answer<List<FailedJob>> answer =
        github.failedJobs(host, "https://github.example.invalid/acme/shop/actions/runs/5");

    assertThat(answer.facts()).contains(List.of(new FailedJob("3", "build", "failure", false)));
  }

  @ParameterizedTest
  @CsvSource({
    "completed,success,success",
    "completed,failure,failed",
    "completed,cancelled,failed",
    "completed,startup_failure,failed",
    "completed,timed_out,failed",
    "in_progress,'',running",
    "queued,'',running",
    "completed,'',unknown"
  })
  void runIsToldInJagtsOwnWords(String status, String conclusion, String verdict) {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\""
                    + status
                    + "\",\"conclusion\":\""
                    + conclusion
                    + "\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"}]",
                ""));

    Answer<CommitPipeline> answer =
        github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

    assertThat(answer.facts())
        .contains(
            new CommitPipeline(verdict, "https://github.example.invalid/acme/shop/actions/runs/1"));
  }

  @Test
  void commitReadsAsItsWorstRun() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"},"
                    + "{\"status\":\"in_progress\",\"conclusion\":\"\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/2\"}]",
                ""));

    Answer<CommitPipeline> answer =
        github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

    assertThat(answer.facts())
        .contains(
            new CommitPipeline(
                "running", "https://github.example.invalid/acme/shop/actions/runs/2"));
  }

  @Test
  void commitWithTwoRunsEquallyBadReadsAsTheFirst() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\"completed\",\"conclusion\":\"failure\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"},"
                    + "{\"status\":\"completed\",\"conclusion\":\"failure\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/2\"}]",
                ""));

    Answer<CommitPipeline> answer =
        github.onCommit(host, "https://github.example.invalid/acme/shop.git", "0123abc");

    assertThat(answer.facts())
        .contains(
            new CommitPipeline(
                "failed", "https://github.example.invalid/acme/shop/actions/runs/1"));
  }

  @Test
  void signedOutGhNamesItsLoginAsTheFixWithTheBrowserTurnedOff() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "HTTP 401"));

    Answer<String> answer =
        github.retryFailed(host, "https://github.example.invalid/acme/shop/actions/runs/5");

    assertThat(answer.reach().orElseThrow().fix())
        .containsExactly(
            "env",
            "GH_BROWSER=true",
            "gh",
            "auth",
            "login",
            "--hostname",
            "github.example.invalid",
            "--web");
  }

  @Test
  void cliExitingNonZeroIsReportedWithItsExitCodeAndWhatItSaid() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(2, "", "boom"));

    Answer<String> answer =
        github.retryFailed(host, "https://github.example.invalid/acme/shop/actions/runs/5");

    assertThat(answer.failure()).isEqualTo("gh exited 2: boom");
  }

  @Test
  void requestIsClosedByItsAddressOnTheHostItLivesOn() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", "Closed pull request #7"));

    Answer<String> answer = github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/pull/7");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("pr", "close", "https://ghe.corp.invalid/acme/shop/pull/7");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void addressThatIsNoRequestIsNotClosedAndNothingIsRun() {
    Answer<String> answer =
        github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void refusalOfTheHostOnCloseCarriesItsOwnWords() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "GraphQL: Resource not accessible"));

    Answer<String> answer = github.closeRequest(host, "https://ghe.corp.invalid/acme/shop/pull/7");

    assertThat(answer.failure()).contains("Resource not accessible");
    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void unresolvedThreadAnsweredLastByAnotherAccountGivesItsNewestNoteId() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"data":{"viewer":{"login":"agent"},"repository":{"pullRequest":{"reviewThreads":{"nodes":[
                {"isResolved":false,"comments":{"nodes":[{"databaseId":101,"author":{"login":"mia"}}]}},
                {"isResolved":true,"comments":{"nodes":[{"databaseId":102,"author":{"login":"mia"}}]}},
                {"isResolved":false,"comments":{"nodes":[{"databaseId":103,"author":{"login":"agent"}}]}}]}}}}}
                """,
                ""));

    Answer<List<String>> answer =
        github.unresolvedNotes(host, "https://ghe.corp.invalid/acme/shop/pull/7");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("api", "graphql", "--hostname", "ghe.corp.invalid");
    assertThat(command.getValue()).contains("owner=acme", "name=shop", "number=7");
    assertThat(answer.facts()).contains(List.of("101"));
  }

  @Test
  void requestsFingerprintIsWhatTheHostShowsOfItsStateHeadChecksAndReviews() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "[\"OPEN\",\"2026-11-09T08:00:00Z\",\"abc123\",\"\",2,[],[]]\n", ""));

    Answer<String> answer =
        github.requestFingerprint(host, "https://ghe.corp.invalid/acme/shop/pull/7");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("pr", "view", "https://ghe.corp.invalid/acme/shop/pull/7", "--json");
    assertThat(String.join(" ", command.getValue()))
        .contains("statusCheckRollup", "latestReviews", "headRefOid", "updatedAt");
    assertThat(answer.facts())
        .contains("[\"OPEN\",\"2026-11-09T08:00:00Z\",\"abc123\",\"\",2,[],[]]");
  }

  @Test
  void addressThatIsNoRequestHasNoFingerprintAndNothingIsRun() {
    Answer<String> answer =
        github.requestFingerprint(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void addressThatIsNoRequestHasNoNotesReadAndNothingIsRun() {
    Answer<List<String>> answer =
        github.unresolvedNotes(host, "https://ghe.corp.invalid/acme/shop/actions/runs/5");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void openPullRequestForTheBranchIsTheOneReturnedAndNoSecondIsCreated() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "[{\"url\":\"https://github.example.invalid/shop/web/pull/7\"}]", ""));

    Answer<String> answer =
        github.openRequest(
            host,
            "git@github.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "title", "body"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence(
            "pr",
            "list",
            "--head",
            "jagt/acceptance-acceptance",
            "--state",
            "open",
            "-R",
            "github.example.invalid/shop/web");
    assertThat(answer.facts()).contains("https://github.example.invalid/shop/web/pull/7");
  }

  @Test
  void pullRequestIsCreatedFromTheBranchIntoTheTargetWhereNoneIsOpen() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""))
        .thenReturn(
            new Processes.Result(
                0, "a first line\nhttps://github.example.invalid/shop/web/pull/8\n", "Creating"));

    Answer<String> answer =
        github.openRequest(
            host,
            "git@github.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "the title", "the body"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getAllValues().get(1))
        .containsSubsequence(
            "pr",
            "create",
            "--head",
            "jagt/acceptance-acceptance",
            "--base",
            "main",
            "--title",
            "the title",
            "--body",
            "the body",
            "-R",
            "github.example.invalid/shop/web");
    assertThat(answer.facts()).contains("https://github.example.invalid/shop/web/pull/8");
  }

  @Test
  void pullRequestIsNotCreatedWhenTheListOfOpenOnesCouldNotBeRead() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "boom"));

    Answer<String> answer =
        github.openRequest(
            host,
            "git@github.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "title", "body"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("boom");
    verify(processes).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void openRequestOfBranchIsEmptyWhereNoPullRequestIsOpenAndNoneIsCreated() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<String> answer =
        github.openRequestOf(
            host, "git@github.example.invalid:shop/web.git", "jagt/acceptance-acceptance");

    assertThat(answer.facts()).contains("");
    verify(processes).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void openRequestOfBranchFailsNamingTheHostsStatusAndWordsWhenTheHostAnswersWithServerError() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "HTTP 502: Bad Gateway"));

    Answer<String> answer =
        github.openRequestOf(host, "git@github.example.invalid:shop/web.git", "ABC-42");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.reach()).isEmpty();
    assertThat(answer.failure()).isEqualTo("gh exited 1: HTTP 502: Bad Gateway");
  }

  @Test
  void openRequestOfBranchIsTheOpenPullRequest() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "[{\"url\":\"https://github.example.invalid/shop/web/pull/7\"}]", ""));

    Answer<String> answer =
        github.openRequestOf(
            host, "git@github.example.invalid:shop/web.git", "jagt/acceptance-acceptance");

    assertThat(answer.facts()).contains("https://github.example.invalid/shop/web/pull/7");
  }

  @Test
  void remoteThatIsNoRepositoryAddressOpensNothingAndRunsNothing() {
    Answer<String> answer =
        github.openRequest(host, "", new RequestDraft("b", "main", "title", "body"));

    assertThat(answer.failure()).startsWith("not the address of a repository");
    verifyNoInteractions(processes);
  }

  @Test
  void remoteThatIsNoRepositoryAddressHasNoOpenRequestToLookUp() {
    Answer<String> answer = github.openRequestOf(host, "", "b");

    assertThat(answer.failure()).startsWith("not the address of a repository");
    verifyNoInteractions(processes);
  }
}

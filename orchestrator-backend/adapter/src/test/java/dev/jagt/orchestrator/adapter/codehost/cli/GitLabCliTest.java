package dev.jagt.orchestrator.adapter.codehost.cli;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
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

class GitLabCliTest {

  private final Processes processes = mock(Processes.class);
  private final ReachLedger reach = new ReachLedger();
  private final GitLabCli gitlab = new GitLabCli(processes, reach);
  private final HostSettings host = hostSettings().withRepository(Path.of("/repo")).build();

  @Test
  void pipelineIsRetriedThroughItsRetryEndpoint() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"status\":\"pending\"}", ""));

    Answer<String> answer =
        gitlab.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/77");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence(
            "api",
            "--hostname",
            "git.example.invalid",
            "-X",
            "POST",
            "projects/shop%2Fweb/pipelines/77/retry");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void hostWithPortKeepsItInTheHostnameWhereTheRemoteHasOne() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    gitlab.onCommit(host, "https://git.example.invalid:8443/shop/web.git", "0123abc");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue()).containsSubsequence("--hostname", "git.example.invalid:8443");
  }

  @Test
  void theConfiguredHostNameAnswersInPlaceOfTheOneSshRemoteNames() {
    HostSettings named =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo"),
            new dev.jagt.orchestrator.task.repo.ProjectConfig.CodeHost(
                null,
                "gitlab.corp.invalid",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                dev.jagt.orchestrator.task.repo.ProjectConfig.Review.NONE));
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    gitlab.onCommit(named, "git@work-alias:shop/web.git", "0123abc");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue()).containsSubsequence("--hostname", "gitlab.corp.invalid");
  }

  @Test
  void jobCarriesTheReasonTheHostGivesAndIsToldAsInfrastructure() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"id\":9,\"name\":\"unit-tests\",\"failure_reason\":\"runner_system_failure\"},"
                    + "{\"id\":10,\"name\":\"lint\",\"failure_reason\":\"script_failure\"}]",
                ""));

    Answer<List<FailedJob>> answer =
        gitlab.failedJobs(host, "https://git.example.invalid/shop/web/-/pipelines/77");

    assertThat(answer.facts())
        .contains(
            List.of(
                new FailedJob("9", "unit-tests", "runner_system_failure", true),
                new FailedJob("10", "lint", "script_failure", false)));
  }

  @ParameterizedTest
  @CsvSource({
    "success,success",
    "failed,failed",
    "canceled,failed",
    "running,running",
    "pending,running",
    "manual,unknown"
  })
  void commitsPipelineIsToldInJagtsOwnWords(String hostWord, String verdict) {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"status\":\""
                    + hostWord
                    + "\",\"web_url\":\"https://git.example.invalid/shop/web/-/pipelines/78\"}]",
                ""));

    Answer<CommitPipeline> answer =
        gitlab.onCommit(host, "git@git.example.invalid:shop/web.git", "0123abc");

    assertThat(answer.facts())
        .contains(
            new CommitPipeline(verdict, "https://git.example.invalid/shop/web/-/pipelines/78"));
  }

  @Test
  void laterPipelineOnTheBranchWhoseCommitHasTheDeployCommitBehindItIsTheAnswer() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"sha\":\"fedcba98\",\"status\":\"success\","
                    + "\"web_url\":\"https://git.example.invalid/shop/web/-/pipelines/80\"}]",
                ""))
        .thenReturn(new Processes.Result(0, "{\"id\":\"0123abc\"}", ""));

    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

    ArgumentCaptor<List<String>> captured = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), captured.capture());
    List<List<String>> command = captured.getAllValues();
    assertThat(command.get(0))
        .contains("projects/shop%2Fweb/pipelines?ref=dev&scope=finished&per_page=1");
    assertThat(command.get(1))
        .contains("projects/shop%2Fweb/repository/merge_base?refs[]=0123abc&refs[]=fedcba98");
    assertThat(answer.facts())
        .contains(
            new CommitPipeline("success", "https://git.example.invalid/shop/web/-/pipelines/80"));
  }

  @Test
  void pipelineOnTheBranchWhoseCommitDoesNotHaveTheDeployCommitBehindItReadsAsNone() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "[{\"sha\":\"fedcba98\",\"status\":\"success\",\"web_url\":\"u\"}]", ""))
        .thenReturn(new Processes.Result(0, "{\"id\":\"5555555\"}", ""));

    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void theDeployCommitsOwnPipelineIsTheNewestOneWithoutAskingForMergeBase() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0, "[{\"sha\":\"0123abcdef\",\"status\":\"failed\",\"web_url\":\"u\"}]", ""));

    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

    verify(processes).run(isNull(), any(Duration.class), anyList());
    assertThat(answer.facts()).contains(new CommitPipeline("failed", "u"));
  }

  @Test
  void branchWithNoFinishedPipelineReadsAsNone() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void localPathRemoteHasNoHostToAskForBranch() {
    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "/srv/git/web.git", "dev", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    verifyNoInteractions(processes);
  }

  @Test
  void branchListingTheHostRefusesIsFailureNotAnswer() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "HTTP 500"));

    Answer<CommitPipeline> answer =
        gitlab.onBranchSince(host, "https://git.example.invalid/shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void commitTheHostRanNoPipelineForReadsAsNone() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<CommitPipeline> answer =
        gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void projectTheHostHidesFromCliSignedOutSaysNotLoggedIn() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "glab: 404 Project Not Found (HTTP 404)"))
        .thenReturn(
            new Processes.Result(
                1, "", "git.example.invalid has not been authenticated with glab"));

    Answer<CommitPipeline> answer =
        gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

    assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
  }

  @Test
  void signedOutGlabTriesTheDeviceFlowThenItsOwnPromptAsTheFix() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "HTTP 401"));

    Answer<CommitPipeline> answer =
        gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

    assertThat(answer.reach().orElseThrow().fix())
        .satisfies(fix -> {
          assertThat(fix.get(0)).isEqualTo("sh");
          assertThat(fix.get(1)).isEqualTo("-c");
          assertThat(fix.get(2))
              .contains(" auth login --hostname 'git.example.invalid' --device || ")
              .endsWith(" auth login --hostname 'git.example.invalid'");
        });
  }

  @Test
  void cliNotOnPathIsUnavailableNamingIt() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenThrow(new IllegalStateException("Failed to start command: glab"));

    Answer<String> answer =
        gitlab.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/77");

    assertThat(answer.failure()).startsWith("glab ");
  }

  @Test
  void commitWhoseHostAnswersWithSomethingThatIsNotJsonSaysSo() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "<html>", ""));

    Answer<CommitPipeline> answer =
        gitlab.onCommit(host, "https://git.example.invalid/shop/web.git", "0123abc");

    assertThat(answer.failure()).isEqualTo("glab gave output that is not JSON");
  }

  @Test
  void localPathRemoteHasNoHostToAsk() {
    Answer<CommitPipeline> answer = gitlab.onCommit(host, "/srv/git/shop.git", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    verifyNoInteractions(processes);
  }

  @Test
  void addressThatIsNoPipelineIsRefusedWithoutRunningAnything() {
    Answer<List<FailedJob>> answer =
        gitlab.failedJobs(host, "https://git.example.invalid/shop/web/-/merge_requests/4");

    assertThat(answer.failure()).startsWith("not the address of a pipeline");
    verifyNoInteractions(processes);
  }

  @Test
  void requestIsClosedByItsNumberInTheRepositoryItLivesIn() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", "Closed"));

    Answer<String> answer =
        gitlab.closeRequest(host, "https://git.example.invalid:8443/shop/web/-/merge_requests/7");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence("mr", "close", "7", "-R", "git.example.invalid:8443/shop/web");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void addressThatIsNoRequestIsNotClosedAndNothingIsRun() {
    Answer<String> answer =
        gitlab.closeRequest(host, "https://git.example.invalid/shop/web/-/pipelines/77");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void unresolvedDiscussionAnsweredLastByAnotherAccountGivesItsNewestNoteId() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"username\":\"agent\"}", ""))
        .thenReturn(
            new Processes.Result(
                0,
                """
                [{"individual_note":false,"notes":[
                  {"id":201,"resolvable":true,"resolved":false,"author":{"username":"mia"}},
                  {"id":202,"resolvable":true,"resolved":false,"author":{"username":"lee"}}]},
                 {"individual_note":false,"notes":[
                  {"id":203,"resolvable":true,"resolved":true,"author":{"username":"mia"}}]},
                 {"individual_note":true,"notes":[
                  {"id":204,"resolvable":false,"resolved":false,"author":{"username":"mia"}}]},
                 {"individual_note":false,"notes":[
                  {"id":205,"resolvable":true,"resolved":false,"author":{"username":"mia"}},
                  {"id":206,"resolvable":true,"resolved":false,"author":{"username":"agent"}}]}]
                """,
                ""));

    Answer<List<String>> answer =
        gitlab.unresolvedNotes(host, "https://git.example.invalid/shop/web/-/merge_requests/9");

    ArgumentCaptor<List<String>> captured = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), captured.capture());
    List<List<String>> command = captured.getAllValues();
    assertThat(command.get(1))
        .containsSubsequence(
            "api",
            "--hostname",
            "git.example.invalid",
            "projects/shop%2Fweb/merge_requests/9/discussions?per_page=100");
    assertThat(answer.facts()).contains(List.of("202"));
  }

  @Test
  void discussionWhoseNewestNoteOpensWithTheAiLineAwaitsAnAnswerEvenFromTheActingAccount() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "{\"username\":\"agent\"}", ""))
        .thenReturn(
            new Processes.Result(
                0,
                """
                [{"individual_note":false,"notes":[
                  {"id":301,"resolvable":true,"resolved":false,"author":{"username":"mia"},"body":"Why?"},
                  {"id":302,"resolvable":true,"resolved":false,"author":{"username":"agent"},
                   "body":"AI review note: react with a thumbs up if it helps, a thumbs down if it does not.\\n\\nUnbounded loop."}]},
                 {"individual_note":false,"notes":[
                  {"id":303,"resolvable":true,"resolved":false,"author":{"username":"agent"},
                   "body":"Fixed, see Paging."}]},
                 {"individual_note":false,"notes":[
                  {"id":304,"resolvable":true,"resolved":true,"author":{"username":"agent"},
                   "body":"AI review note: react with a thumbs up if it helps, a thumbs down if it does not."}]}]
                """,
                ""));

    Answer<List<String>> answer =
        gitlab.unresolvedNotes(host, "https://git.example.invalid/shop/web/-/merge_requests/9");

    assertThat(answer.facts()).contains(List.of("302"));
  }

  @Test
  void requestsFingerprintChangesWithItsHeadPipelineAndApprovals() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"state":"opened","updated_at":"2026-11-09T08:00:00Z","sha":"abc123",
                 "user_notes_count":2,"head_pipeline":{"id":77,"status":"running"}}
                """,
                ""))
        .thenReturn(
            new Processes.Result(0, "{\"approved_by\":[{\"user\":{\"username\":\"mia\"}}]}", ""))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"state":"opened","updated_at":"2026-11-09T08:00:00Z","sha":"abc123",
                 "user_notes_count":2,"head_pipeline":{"id":77,"status":"success"}}
                """,
                ""))
        .thenReturn(
            new Processes.Result(0, "{\"approved_by\":[{\"user\":{\"username\":\"mia\"}}]}", ""))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"state":"opened","updated_at":"2026-11-09T08:00:00Z","sha":"abc123",
                 "user_notes_count":2,"head_pipeline":{"id":77,"status":"success"}}
                """,
                ""))
        .thenReturn(new Processes.Result(0, "{\"approved_by\":[]}", ""));
    String request = "https://git.example.invalid/shop/web/-/merge_requests/9";

    String running = gitlab.requestFingerprint(host, request).facts().orElseThrow();
    String green = gitlab.requestFingerprint(host, request).facts().orElseThrow();
    String unapproved = gitlab.requestFingerprint(host, request).facts().orElseThrow();

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes, times(6)).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getAllValues().get(1))
        .contains("projects/shop%2Fweb/merge_requests/9/approvals");
    assertThat(running).isNotEqualTo(green);
    assertThat(green).isNotEqualTo(unapproved);
  }

  @Test
  void addressThatIsNoRequestHasNoFingerprintAndNothingIsRun() {
    Answer<String> answer =
        gitlab.requestFingerprint(host, "https://git.example.invalid/shop/web/-/pipelines/7");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void addressThatIsNoRequestHasNoNotesReadAndNothingIsRun() {
    Answer<List<String>> answer =
        gitlab.unresolvedNotes(host, "https://git.example.invalid/shop/web/-/pipelines/7");

    assertThat(answer.failure()).startsWith("not the address of a request");
    verifyNoInteractions(processes);
  }

  @Test
  void openRequestForTheBranchIsTheOneReturnedAndNoSecondIsCreated() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"web_url\":\"https://git.example.invalid/shop/web/-/merge_requests/7\"}]",
                ""));

    Answer<String> answer =
        gitlab.openRequest(
            host,
            "git@git.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "title", "body"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .containsSubsequence(
            "api",
            "--hostname",
            "git.example.invalid",
            "projects/shop%2Fweb/merge_requests?state=opened&per_page=1&source_branch="
                + "jagt%2Facceptance-acceptance");
    assertThat(answer.facts()).contains("https://git.example.invalid/shop/web/-/merge_requests/7");
  }

  @Test
  void openRequestOfBranchIsEmptyWhereNoRequestIsOpenAndNoneIsCreated() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<String> answer =
        gitlab.openRequestOf(
            host, "git@git.example.invalid:shop/web.git", "jagt/acceptance-acceptance");

    assertThat(answer.facts()).contains("");
    verify(processes).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void openRequestOfBranchIsTheOpenRequest() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(
                0,
                "[{\"web_url\":\"https://git.example.invalid/shop/web/-/merge_requests/7\"}]",
                ""));

    Answer<String> answer =
        gitlab.openRequestOf(
            host, "git@git.example.invalid:shop/web.git", "jagt/acceptance-acceptance");

    assertThat(answer.facts()).contains("https://git.example.invalid/shop/web/-/merge_requests/7");
  }

  @Test
  void requestIsCreatedFromTheBranchIntoTheTargetWhereNoneIsOpen() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"web_url\":\"https://git.example.invalid/shop/web/-/merge_requests/8\"}",
                ""));

    Answer<String> answer =
        gitlab.openRequest(
            host,
            "git@git.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "the title", "the body"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getAllValues().get(1))
        .containsSubsequence(
            "api",
            "--hostname",
            "git.example.invalid",
            "-X",
            "POST",
            "projects/shop%2Fweb/merge_requests",
            "-f",
            "source_branch=jagt/acceptance-acceptance",
            "-f",
            "target_branch=main",
            "-f",
            "title=the title",
            "-f",
            "description=the body");
    assertThat(answer.facts()).contains("https://git.example.invalid/shop/web/-/merge_requests/8");
  }

  @Test
  void requestIsNotCreatedWhenTheListOfOpenOnesCouldNotBeRead() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "boom"));

    Answer<String> answer =
        gitlab.openRequest(
            host,
            "git@git.example.invalid:shop/web.git",
            new RequestDraft("jagt/acceptance-acceptance", "main", "title", "body"));

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).contains("boom");
    verify(processes).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void remoteThatIsNoRepositoryAddressOpensNothingAndRunsNothing() {
    Answer<String> answer =
        gitlab.openRequest(host, "", new RequestDraft("b", "main", "title", "body"));

    assertThat(answer.failure()).startsWith("not the address of a repository");
    verifyNoInteractions(processes);
  }

  @Test
  void remoteThatIsNoRepositoryAddressHasNoOpenRequestToLookUp() {
    Answer<String> answer = gitlab.openRequestOf(host, "", "b");

    assertThat(answer.failure()).startsWith("not the address of a repository");
    verifyNoInteractions(processes);
  }
}

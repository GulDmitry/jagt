package dev.jagt.orchestrator.job.codereview;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.job.codereview.request.LedgerRow;
import dev.jagt.orchestrator.job.codereview.request.ReviewLedger;
import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.service.assistant.RoundReader;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CodeReviewPanelTest {

  @TempDir Path root;

  private final ChargedReviews charged = mock(ChargedReviews.class);
  private final Assistants assistants = mock(Assistants.class);
  private final RoundReviewer reviewer = mock(RoundReviewer.class);
  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);
  private final ReviewLedger ledger = mock(ReviewLedger.class);

  @Test
  void thirdPartyReadIsChargedToTheRequestsLedgerRow() throws IOException {
    Files.createDirectories(root.resolve("roles/code-reviewer"));
    Files.writeString(root.resolve("roles/code-reviewer/brief.md.dist"), "the brief");
    when(paths.root()).thenReturn(root);
    when(assistants.reviewer()).thenReturn(reviewer);
    when(reviewer.review(any()))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE)),
                new TokenUsage(1, 10, 0, 5, 0.75)));
    CodeReviewPanel panel =
        new CodeReviewPanel(
            charged,
            paths,
            new ReviewReads(new RoundReader(assistants), ledger, new FakeJobFaults()),
            new PreShipCoverage(stateService().withRoot(root).build()));
    Subject other = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");

    panel.review(other, RequestChange.defaults(), root, "");

    ArgumentCaptor<UnaryOperator<LedgerRow>> change = ArgumentCaptor.captor();
    Mockito.verify(ledger).update(any(), any(), any(), change.capture());
    LedgerRow after =
        change.getValue().apply(LedgerRow.of(other.url(), "shop", "").withCostUsd(1.0));
    assertThat(after.signals().costUsd()).isEqualTo(1.75);
  }

  @Test
  void ownReadLeavesOutWhatTheReviewBeforeTheShipRaised() throws IOException {
    Files.createDirectories(root.resolve("roles/code-reviewer"));
    Files.writeString(root.resolve("roles/code-reviewer/brief.md.dist"), "the brief");
    when(paths.root()).thenReturn(root);
    Finding leak = new Finding("A.java", "leaks", "", "wrong", "close()", "resource");
    Finding gap = new Finding("B.java", "no test", "", "unproven", "run()", "test");
    when(charged.review(any(), any()))
        .thenReturn(
            new Judgement(
                "", Verdict.NOT_READY, List.of(leak, gap), "", List.of(), "", Press.NONE));
    StateService state = stateService().withRoot(root).build();
    state.putTask("ABC-42", TaskState.builder("shop", "/wt", TaskStatus.REVIEW_PENDING).build());
    PreShipCoverage coverage = new PreShipCoverage(state);
    coverage.raise("ABC-42", List.of(leak));
    Subject own = new Subject("shop", "https://h/a/b/7", "remote.example:a/b.git", "ABC-42");

    Judgement judged =
        new CodeReviewPanel(
                charged,
                paths,
                new ReviewReads(new RoundReader(assistants), ledger, new FakeJobFaults()),
                coverage)
            .review(own, RequestChange.defaults(), root, "");

    assertThat(judged.findings()).containsExactly(gap);
  }

  @Test
  void changeReadThatFailedIsReportedAsItsJobsFailure() {
    FakeJobFaults faults = new FakeJobFaults();

    new CodeReviewPanel(
            charged,
            paths,
            new ReviewReads(new RoundReader(assistants), ledger, faults),
            new PreShipCoverage(stateService().withRoot(root).build()))
        .unread(Answer.failed(TokenUsage.NONE, "the host refused"));

    assertThat(faults.causes()).containsExactly("the host refused");
  }

  @Test
  void anUnreadableBriefIsReportedAsItsJobsFailure() {
    when(paths.root()).thenReturn(root);
    FakeJobFaults faults = new FakeJobFaults();
    Subject other = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");

    new CodeReviewPanel(
            charged,
            paths,
            new ReviewReads(new RoundReader(assistants), ledger, faults),
            new PreShipCoverage(stateService().withRoot(root).build()))
        .review(other, RequestChange.defaults(), root, "");

    assertThat(faults.causes()).containsExactly("the code reviewer's brief is unreadable");
  }
}

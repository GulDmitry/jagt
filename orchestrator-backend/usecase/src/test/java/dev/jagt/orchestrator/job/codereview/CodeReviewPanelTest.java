package dev.jagt.orchestrator.job.codereview;

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
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import dev.jagt.orchestrator.task.usage.TokenUsage;
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
                Optional.of(new Judgement("", "ready", List.of(), "", List.of(), "")),
                new TokenUsage(1, 10, 0, 5, 0.75)));
    CodeReviewPanel panel = new CodeReviewPanel(charged, assistants, paths, ledger);
    Subject other = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");

    panel.review(other, RequestChange.defaults(), root, "");

    ArgumentCaptor<UnaryOperator<LedgerRow>> change = ArgumentCaptor.captor();
    Mockito.verify(ledger).update(any(), any(), any(), change.capture());
    LedgerRow after =
        change.getValue().apply(LedgerRow.of(other.url(), "shop", "").withCostUsd(1.0));
    assertThat(after.signals().costUsd()).isEqualTo(1.75);
  }
}

package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewReaderTest {

  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final McpHealth mcp = mock(McpHealth.class);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final ReviewReader paid = new ReviewReader(assistants, mcp, usage, RetryPolicy.PAID_READ);
  private final ReviewReader retrying =
      new ReviewReader(
          assistants, mcp, usage, new RetryPolicy(3, Duration.ZERO, Duration.ofMinutes(2)));
  private final ReviewFacts.Discussion quiet =
      new ReviewFacts.Discussion(List.of(), List.of(), List.of());

  @Test
  void answersTheRoundWithWhatTheReadCameBackWith() {
    when(assistants.codeHost().readReview("https://other.example.com/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("running", "", ""),
                        quiet)),
                TokenUsage.NONE));

    var facts =
        paid.read("ABC-1", "https://other.example.com/g/p/-/merge_requests/7");

    assertThat(facts.facts())
        .contains(
            new ReviewFacts(
                new ReviewFacts.Request(true, false, false, false, 0),
                new ReviewFacts.Pipeline("running", "", ""),
                quiet));
  }

  @Test
  void sendsReadThatCameBackWithNothingToReadAgainUpToTheLimit() {
    when(assistants.codeHost().readReview("https://host/g/p/-/merge_requests/7"))
        .thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    retrying.read("ABC-1", "https://host/g/p/-/merge_requests/7");

    verify(assistants.codeHost(), times(3)).readReview("https://host/g/p/-/merge_requests/7");
  }

  @Test
  void believesHostSayingThereIsNoSuchRequestWithoutPayingToHearItAgain() {
    when(assistants.codeHost().readReview("https://host/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(false, false, false, false, 0),
                        new ReviewFacts.Pipeline("unknown", "", ""),
                        quiet)),
                TokenUsage.NONE));

    retrying.read("ABC-1", "https://host/g/p/-/merge_requests/7");

    verify(assistants.codeHost(), times(1)).readReview("https://host/g/p/-/merge_requests/7");
  }

  @Test
  void chargesEveryAttemptItPaidForEvenWhereTheReadCameBackEmpty() {
    when(assistants.codeHost().readReview("https://host/g/p/-/merge_requests/7"))
        .thenReturn(new Answer<>(Optional.empty(), TokenUsage.ofCall(10, 0, 1, 0.5)));

    retrying.read("ABC-1", "https://host/g/p/-/merge_requests/7");

    verify(usage).chargeTask("ABC-1", new TokenUsage(3, 30, 0, 3, 1.5));
  }

  @Test
  void answersAnOpenRequestWithWhatTheReadCameBackWith() {
    when(assistants.codeHost().readMergeRequest("https://other.example.com/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(new MergeRequestFacts(true, "ABC-1", "main", "t")), TokenUsage.NONE));

    var read =
        paid.readRequest("https://other.example.com/g/p/-/merge_requests/7");

    assertThat(read.facts()).contains(new MergeRequestFacts(true, "ABC-1", "main", "t"));
  }

  @Test
  void asksWhichMcpServersAreDownWhenPaidReadDeniesTheRequest() {
    when(assistants.codeHost().readMergeRequest("https://other.example.com/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(Optional.of(new MergeRequestFacts(false, "", "", "")), TokenUsage.NONE));

    paid.readRequest("https://other.example.com/g/p/-/merge_requests/7");

    verify(mcp).brokenServers();
  }

  @Test
  void doesNotProbeTheMcpServersForRequestItReadFine() {
    when(assistants.codeHost().readMergeRequest("https://other.example.com/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(new MergeRequestFacts(true, "ABC-42", "main", "Excel export")),
                TokenUsage.NONE));

    paid.readRequest("https://other.example.com/g/p/-/merge_requests/7");

    verify(mcp, never()).brokenServers();
  }

  @Test
  void asksAgainForRoundWhoseFailureLinesBelongToRunThatDidNotFail() {
    when(assistants.codeHost().readReview("https://host/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new ReviewFacts(
                        new ReviewFacts.Request(true, false, false, false, 0),
                        new ReviewFacts.Pipeline("success", "lint failed", ""),
                        quiet)),
                TokenUsage.NONE));

    retrying.read("ABC-1", "https://host/g/p/-/merge_requests/7");

    verify(assistants.codeHost(), times(3)).readReview("https://host/g/p/-/merge_requests/7");
  }

  @Test
  void asksAgainForAnOpenRequestThatNamesNoSourceBranch() {
    when(assistants.codeHost().readMergeRequest("https://host/g/p/-/merge_requests/7"))
        .thenReturn(
            new Answer<>(
                Optional.of(new MergeRequestFacts(true, "", "main", "t")), TokenUsage.NONE));

    retrying.readRequest("https://host/g/p/-/merge_requests/7");

    verify(assistants.codeHost(), times(3)).readMergeRequest("https://host/g/p/-/merge_requests/7");
  }
}

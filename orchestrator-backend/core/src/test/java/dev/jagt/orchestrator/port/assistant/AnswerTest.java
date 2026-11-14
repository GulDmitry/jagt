package dev.jagt.orchestrator.port.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnswerTest {

  private final ReachLost lost =
      new ReachLost(
          "glab",
          "git.example.invalid",
          List.of("glab", "auth", "login"),
          "glab not logged in to git.example.invalid");

  @Test
  void anUnreachedAnswerCarriesItsLostReachAndNoFacts() {
    Answer<String> answer = Answer.unreached(lost);

    assertThat(answer.reach()).contains(lost);
    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
  }

  @Test
  void shapingTheFactsKeepsTheLostReach() {
    Answer<Integer> shaped =
        new Answer<String>(Optional.empty(), TokenUsage.NONE, "said", false, Optional.of(lost))
            .map(String::length);

    assertThat(shaped.reach()).contains(lost);
  }

  @Test
  void reportsFailedReadWithItsSentenceAndWhatItCost() {
    TokenUsage spent = TokenUsage.ofCall(10, 0, 5, 0.01);

    Answer<String> answer = Answer.failed(spent, "timed out");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.usage()).isEqualTo(spent);
    assertThat(answer.failure()).isEqualTo("timed out");
  }

  @Test
  void costsNothingForReadThatNeverHappened() {
    Answer<String> answer = Answer.unavailable();

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.usage()).isEqualTo(TokenUsage.NONE);
    assertThat(answer.failure()).isEmpty();
  }

  @Test
  void shapesTheFactsWhileCarryingTheCostAndTheFailureThrough() {
    TokenUsage spent = TokenUsage.ofCall(10, 0, 5, 0.01);

    Answer<Integer> shaped = new Answer<>(Optional.of("abc"), spent, "note").map(String::length);

    assertThat(shaped.facts()).contains(3);
    assertThat(shaped.usage()).isEqualTo(spent);
    assertThat(shaped.failure()).isEqualTo("note");
  }
}

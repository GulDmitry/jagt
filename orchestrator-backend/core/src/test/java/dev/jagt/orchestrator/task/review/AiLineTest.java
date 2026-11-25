package dev.jagt.orchestrator.task.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiLineTest {

  @Test
  void noteOpeningWithTheLineAwaitsAnAnswer() {
    assertThat(AiLine.opens("\n  " + AiLine.TEXT + "\n\nThis loop is unbounded.")).isTrue();
  }

  @Test
  void noteMentioningTheLineLaterDoesNotOpenWithIt() {
    assertThat(AiLine.opens("Why is this unbounded?\n" + AiLine.TEXT)).isFalse();
  }

  @Test
  void noteOpeningWithTheEarlierLineStillAwaitsAnAnswer() {
    assertThat(
            AiLine.opens(
                "AI review note: react with a thumbs up if it helps, a thumbs down if it does not."
                    + "\n\nThis loop is unbounded."))
        .isTrue();
  }

  @Test
  void noteWithNoBodyDoesNotOpenWithIt() {
    assertThat(AiLine.opens(null)).isFalse();
  }
}

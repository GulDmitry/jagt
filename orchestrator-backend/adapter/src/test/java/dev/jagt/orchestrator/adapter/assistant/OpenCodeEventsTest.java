package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenCodeEventsTest {

  @Test
  void readsTheLastTextTheTokensOfEveryStepAndEveryToolCallOfARun() throws IOException {
    OpenCodeEvents events =
        OpenCodeEvents.parse(Files.readString(Path.of("src/test/resources/opencode/run.jsonl")));

    assertThat(events.answer())
        .hasValueSatisfying(
            said -> assertThat(said).contains("```json").contains("\"commits\": 1"));
    assertThat(events.usage().calls()).isEqualTo(4);
    assertThat(events.usage().inputTokens()).isEqualTo(1650);
    assertThat(events.usage().cachedInputTokens()).isEqualTo(4530);
    assertThat(events.usage().outputTokens()).isEqualTo(169);
    assertThat(events.calls())
        .extracting(OpenCodeEvents.Call::status)
        .containsExactly("completed", "error", "error");
    assertThat(events.calls().getFirst().tool()).isEqualTo("shell");
    assertThat(events.calls().getFirst().input().path("command").asString())
        .isEqualTo("git log --oneline --no-ext-diff --no-textconv");
  }

  @Test
  void countsReasoningTokensAndCacheWritesAsTheModelsOwnWork() {
    String line =
        "{\"type\":\"step_finish\",\"part\":{\"cost\":0,\"tokens\":{\"input\":10,\"output\":5,\"reasoning\":7,"
            + "\"cache\":{\"read\":3,\"write\":2}}}}";

    OpenCodeEvents events = OpenCodeEvents.parse(line);

    assertThat(events.usage().inputTokens()).isEqualTo(12);
    assertThat(events.usage().cachedInputTokens()).isEqualTo(3);
    assertThat(events.usage().outputTokens()).isEqualTo(12);
  }

  @Test
  void sayingNothingAndPrintingNoiseLeavesNoAnswerNoUsageAndNoCalls() {
    OpenCodeEvents events =
        OpenCodeEvents.parse("Error: no model\n\n{\"type\":\"step_start\",\"part\":{}}");

    assertThat(events.answer()).isEmpty();
    assertThat(events.usage().isNone()).isTrue();
    assertThat(events.calls()).isEmpty();
  }

  @Test
  void countsAStepThatReportsNoCostAsUnpricedButAZeroCostAsPriced() {
    String free =
        "{\"type\":\"step_finish\",\"part\":{\"cost\":0,\"tokens\":{\"input\":10,\"output\":5}}}\n";
    String unknown = "{\"type\":\"step_finish\",\"part\":{\"tokens\":{\"input\":10,\"output\":5}}}";

    assertThat(OpenCodeEvents.parse(free).usage().costIsLowerBound()).isFalse();
    assertThat(OpenCodeEvents.parse(free + unknown).usage().unpricedCalls()).isEqualTo(1);
  }
}

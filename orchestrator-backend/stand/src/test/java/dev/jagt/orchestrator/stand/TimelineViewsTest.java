package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TimelineViewsTest {

  @TempDir Path dir;

  @Test
  void taskSeenIsRecordedWithItsStatusOwnerAndReport() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));

    timeline.record(
        JsonMapper.builder()
            .build()
            .readTree(
                "{\"tasks\":{\"ABC-42\":{\"status\":\"IN_PROGRESS\",\"report\":\"QUESTION\"}}}"),
        JsonMapper.builder()
            .build()
            .readTree("{\"tasks\":[{\"id\":\"ABC-42\",\"owner\":\"YOU\"}]}"));

    assertThat(timeline.lastStatus()).isEqualTo("IN_PROGRESS");
    assertThat(timeline.lastOwner()).isEqualTo("YOU");
    assertThat(timeline.lastReport()).isEqualTo("QUESTION");
    assertThat(timeline.tail(5))
        .singleElement()
        .asString()
        .contains("ABC-42 IN_PROGRESS owner=YOU");
  }

  @Test
  void taskNotOnTheBoardHasAnUnknownOwner() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));

    timeline.record(
        JsonMapper.builder().build().readTree("{\"tasks\":{\"ABC-42\":{\"status\":\"TODO\"}}}"),
        JsonMapper.builder().build().readTree("{}"));

    assertThat(timeline.lastOwner()).isEqualTo("?");
  }

  @Test
  void anUnchangedTaskIsRecordedOnce() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));
    JsonNode state =
        JsonMapper.builder().build().readTree("{\"tasks\":{\"ABC-42\":{\"status\":\"TODO\"}}}");

    timeline.record(state, JsonMapper.builder().build().readTree("{}"));
    timeline.record(state, JsonMapper.builder().build().readTree("{}"));

    assertThat(timeline.tail(5)).hasSize(1);
  }

  @Test
  void tmuxWindowsAreNotedOnlyWhenTheyChange() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));

    timeline.windows("a b");
    timeline.windows("a b");
    timeline.windows("a");

    assertThat(timeline.tail(5)).hasSize(2);
  }

  @Test
  void theTailKeepsOnlyTheLastLines() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));
    timeline.note("one");
    timeline.note("two");
    timeline.note("three");

    assertThat(timeline.tail(2)).hasSize(2).last().asString().endsWith("three");
  }

  @Test
  void timelineIsStalledOnlyAfterItsQuietPassed() {
    Timeline timeline = new Timeline(dir.resolve("t.log"), mock(ContextRelays.class));

    assertThat(timeline.stalledFor(Duration.ofHours(1))).isFalse();
    assertThat(timeline.stalledFor(Duration.ofMillis(-1))).isTrue();
  }

  @Test
  void recognisedRelayCountsAndNamesItsSteps() {
    ContextRelays relays = mock(ContextRelays.class);
    when(relays.changed(eq("ABC-42"), any())).thenReturn(Optional.of(List.of("ship")));
    Timeline timeline = new Timeline(dir.resolve("t.log"), relays);

    timeline.record(
        JsonMapper.builder().build().readTree("{\"tasks\":{\"ABC-42\":{\"status\":\"TODO\"}}}"),
        JsonMapper.builder().build().readTree("{}"));

    assertThat(timeline.relaysSeen()).isEqualTo(1);
    assertThat(timeline.relayed("ship")).isTrue();
    assertThat(timeline.relayed("deploy")).isFalse();
  }

  @Test
  void changedContextWithNoRecognisedStepIsNotedButNotCounted() {
    ContextRelays relays = mock(ContextRelays.class);
    when(relays.changed(eq("ABC-42"), any())).thenReturn(Optional.of(List.of()));
    Timeline timeline = new Timeline(dir.resolve("t.log"), relays);

    timeline.record(
        JsonMapper.builder().build().readTree("{\"tasks\":{\"ABC-42\":{\"status\":\"TODO\"}}}"),
        JsonMapper.builder().build().readTree("{}"));

    assertThat(timeline.relaysSeen()).isZero();
    assertThat(timeline.tail(5)).anyMatch(line -> line.contains("no brief step recognised"));
  }
}

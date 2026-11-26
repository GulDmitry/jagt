package dev.jagt.orchestrator.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.task.TaskArtifacts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlanVerdictTest {

  private final TaskArtifacts artifacts = mock(TaskArtifacts.class);

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "verdict: sound|true",
        "verdict: sound — the ticket is clear|true",
        "verdict: unsound — contradicts itself|false",
        "verdict: soundish|false",
        "# Plan|false"
      })
  void readsTheVerdictFromTheFirstLineOfThePlan(String head, boolean sound) {
    when(artifacts.present("ABC-1")).thenReturn(List.of("plan.md"));
    when(artifacts.read("ABC-1", "plan.md")).thenReturn(head + "\n# Plan\n");

    assertThat(new PlanVerdict(artifacts).sound("ABC-1")).isEqualTo(sound);
  }

  @Test
  void readsPlanHeadedSoundOnItsSecondLineAsNotSound() {
    when(artifacts.present("ABC-1")).thenReturn(List.of("plan.md"));
    when(artifacts.read("ABC-1", "plan.md")).thenReturn("# Plan\nverdict: sound\n");

    assertThat(new PlanVerdict(artifacts).sound("ABC-1")).isFalse();
  }

  @Test
  void readsAbsentPlanAsNotSound() {
    when(artifacts.present("ABC-1")).thenReturn(List.of());

    assertThat(new PlanVerdict(artifacts).sound("ABC-1")).isFalse();
  }
}

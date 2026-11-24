package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScriptTest {

  @Test
  void scriptToStatusStartsWithNoLaunchModeNoEventsAndNoDeployConflict() {
    Script script = Script.to("ABC-42", "DONE");

    assertThat(script.ref()).isEqualTo("ABC-42");
    assertThat(script.until()).isEqualTo("DONE");
    assertThat(script.launchMode()).isNull();
    assertThat(script.events()).isEmpty();
    assertThat(script.expectsDeployConflict()).isFalse();
  }

  @Test
  void planFirstLaunchesInPlanMode() {
    Script script = Script.to("ABC-42", "DONE").planFirst();

    assertThat(script.launchMode()).isEqualTo("plan");
  }

  @Test
  void withinReplacesTheTimeoutAndKeepsTheRest() {
    Script script = Script.to("ABC-42", "DONE").within(WaitCeiling.LIMIT);

    assertThat(script.timeout()).isEqualTo(WaitCeiling.LIMIT);
    assertThat(script.until()).isEqualTo("DONE");
  }

  @Test
  void withAddsTheEventAfterTheOnesAlreadyThere() {
    WorldEvent first = world -> true;
    WorldEvent second = world -> false;

    Script script = Script.to("ABC-42", "DONE").with(first).with(second);

    assertThat(script.events()).containsExactly(first, second);
  }

  @Test
  void expectingDeployConflictRecordsWhetherItsStepComes() {
    Script script = Script.to("ABC-42", "DONE").expectingDeployConflict(true);

    assertThat(script.expectsDeployConflict()).isTrue();
    assertThat(script.conflictStepComes()).isTrue();
  }
}

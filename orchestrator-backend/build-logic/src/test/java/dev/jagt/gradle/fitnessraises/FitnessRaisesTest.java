package dev.jagt.gradle.fitnessraises;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FitnessRaisesTest {

  private static final String PORTS_58 =
      "technical:\n  modularity:\n    ports: { governs: count, max: 58, decision: \"0176\" }\n";
  private static final String PORTS_59 =
      "technical:\n  modularity:\n    ports: { governs: count, max: 59, decision: \"0176\" }\n";
  private static final String PORTS_59_NEW_DECISION =
      "technical:\n  modularity:\n    ports: { governs: count, max: 59, decision: \"0300\" }\n";
  private static final String PORTS_59_NO_DECISION =
      "technical:\n  modularity:\n    ports: { governs: count, max: 59 }\n";
  private static final String PORTS_57 =
      "technical:\n  modularity:\n    ports: { governs: count, max: 57, decision: \"0176\" }\n";
  private static final String FLOOR_11 =
      "technical:\n  size:\n    exceptions: { governs: size, min: 11, decision: \"0166\" }\n";
  private static final String FLOOR_10 =
      "technical:\n  size:\n    exceptions: { governs: size, min: 10, decision: \"0166\" }\n";
  private static final String FLOOR_12 =
      "technical:\n  size:\n    exceptions: { governs: size, min: 12, decision: \"0166\" }\n";

  @Test
  void raisedCeilingWithSameDecisionIsNamedWithBothNumbers() {
    List<String> found = FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_59, id -> true);

    assertThat(found)
        .containsExactly(
            "fitness.yml technical.modularity.ports: max went from 58 to 59"
                + " and decision: is 0176, not a new decision record");
  }

  @Test
  void raisedCeilingWithNewExistingDecisionPasses() {
    List<String> found =
        FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_59_NEW_DECISION, "0300"::equals);

    assertThat(found).isEmpty();
  }

  @Test
  void raisedCeilingNamingNoExistingRecordIsNamed() {
    assertThat(FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_59_NEW_DECISION, id -> false))
        .hasSize(1);
  }

  @Test
  void raisedCeilingWithDecisionDroppedIsNamedAsAbsent() {
    List<String> found =
        FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_59_NO_DECISION, id -> true);

    assertThat(found.getFirst()).endsWith("decision: is absent, not a new decision record");
  }

  @Test
  void loweredCeilingPasses() {
    assertThat(FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_57, id -> true)).isEmpty();
  }

  @Test
  void unchangedCeilingPasses() {
    assertThat(FitnessRaises.loosened("fitness.yml", PORTS_58, PORTS_58, id -> true)).isEmpty();
  }

  @Test
  void fallenFloorWithSameDecisionIsNamed() {
    List<String> found = FitnessRaises.loosened("fitness.yml", FLOOR_11, FLOOR_10, id -> true);

    assertThat(found).hasSize(1);
    assertThat(found.getFirst()).contains("technical.size.exceptions: min went from 11 to 10");
  }

  @Test
  void raisedFloorPasses() {
    assertThat(FitnessRaises.loosened("fitness.yml", FLOOR_11, FLOOR_12, id -> true)).isEmpty();
  }

  @Test
  void raisedCeilingPointingAtPreExistingUnchangedRecordIsNamed() {
    List<String> changed = List.of("docs/decisions/0176-ports-ceiling.md");

    assertThat(
            FitnessRaises.loosened(
                "fitness.yml",
                PORTS_58,
                PORTS_59_NEW_DECISION,
                FitnessRaises.recordChangedIn(changed)))
        .hasSize(1);
  }

  @Test
  void raisedCeilingPointingAtRecordChangedSinceMergeBasePasses() {
    List<String> changed = List.of("docs/decisions/0300-ports-ceiling.md");

    assertThat(
            FitnessRaises.loosened(
                "fitness.yml",
                PORTS_58,
                PORTS_59_NEW_DECISION,
                FitnessRaises.recordChangedIn(changed)))
        .isEmpty();
  }

  @Test
  void rowAbsentFromBasePasses() {
    assertThat(FitnessRaises.loosened("fitness.yml", "technical: {}\n", PORTS_59, id -> true))
        .isEmpty();
  }
}

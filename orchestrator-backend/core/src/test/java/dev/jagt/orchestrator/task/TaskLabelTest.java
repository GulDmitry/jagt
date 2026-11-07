package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskLabelTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {"ABC-1,a1,ABC-1 (a1)", "ABC-1,'  ',ABC-1", "ABC-1,NULL,ABC-1"})
  void writesTheAliasBesideTheIdOnlyWhereThereIsOne(String id, String alias, String label) {
    assertThat(TaskLabel.of(id, alias)).isEqualTo(label);
  }
}

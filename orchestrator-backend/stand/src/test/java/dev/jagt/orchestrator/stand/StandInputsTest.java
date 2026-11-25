package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandInputsTest {

  @Test
  void textInputIsTheSystemPropertyWhenSet() {
    assertThat(StandInputs.of("java.vm.specification.name", "none"))
        .isEqualTo("Java Virtual Machine Specification");
  }

  @Test
  void textInputIsTheFallbackWhenUnset() {
    assertThat(StandInputs.of("stand.absent.input", "fallback")).isEqualTo("fallback");
  }

  @Test
  void numberInputIsTheFallbackWhenUnset() {
    assertThat(StandInputs.of("stand.absent.input", 6)).isEqualTo(6);
  }

  @Test
  void numberInputIsTheSystemPropertyWhenSet() {
    assertThat(StandInputs.of("java.specification.version", -1)).isPositive();
  }

  @Test
  void numberInputIsTheFallbackWhenTheValueIsNoNumber() {
    assertThat(StandInputs.of("java.vm.specification.name", 6)).isEqualTo(6);
  }
}

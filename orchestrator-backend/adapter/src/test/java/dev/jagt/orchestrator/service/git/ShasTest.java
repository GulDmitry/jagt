package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShasTest {

  @ParameterizedTest
  @CsvSource(
      value = {"0123456789abcdef,01234567", "abcdef12,abcdef12", "abc1234,abc1234", "NIL,null"},
      nullValues = "NIL")
  void showsShaAsItsFirstEightCharactersAndMissingOneAsNull(String sha, String shown) {
    assertThat(Shas.shortSha(sha)).isEqualTo(shown);
  }
}

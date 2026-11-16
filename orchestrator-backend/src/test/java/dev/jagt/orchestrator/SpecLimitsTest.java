package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecLimitsTest {

  @Test
  void noRequirementTextPassesFiveHundredCharacters() {
    List<String> tooLong =
        DocFiles.specs().stream()
            .flatMap(spec ->
                DocFiles.requirementTexts(spec).entrySet().stream()
                    .filter(requirement -> requirement.getValue().length() > 500)
                    .map(requirement -> spec + " -> " + requirement.getKey()))
            .toList();

    assertThat(tooLong).isEmpty();
  }

  @Test
  void everyPurposeIsWrittenAndNotBrief() {
    List<String> unwritten =
        DocFiles.specs().stream()
            .filter(spec ->
                DocFiles.purpose(spec).length() < 50
                    || DocFiles.purpose(spec).matches("(?s)^(TBD|TODO)\\b.*")
                    || DocFiles.purpose(spec).contains("Update Purpose after archive."))
            .map(Object::toString)
            .toList();

    assertThat(unwritten).isEmpty();
  }
}

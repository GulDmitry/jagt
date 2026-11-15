package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RoleVocabularyTest {

  private static final String PAGE = DocFiles.read(Path.of("../docs/role-vocabulary.md"));

  private static final String DEFINITIONS =
      DocFiles.read(
          Path.of("usecase/src/main/java/dev/jagt/orchestrator/config/role/RoleDefinitions.java"));

  @Test
  void everyTriggerOutcomeAndOncePerWordOfRoleDefinitionsHasItsRowOnThePage() {
    List<String> words =
        Pattern.compile("(?:TRIGGERS|ONCE_PER|OUTCOMES) =\\s*Set\\.of\\(([^)]*)\\)")
            .matcher(DEFINITIONS)
            .results()
            .flatMap(set -> Pattern.compile("\"([^\"]+)\"").matcher(set.group(1)).results())
            .map(word -> word.group(1))
            .toList();

    assertThat(words).hasSize(10);
    assertThat(words)
        .allSatisfy(
            word -> assertThat(PAGE).describedAs("%s", word).contains("| `" + word + "` |"));
  }
}

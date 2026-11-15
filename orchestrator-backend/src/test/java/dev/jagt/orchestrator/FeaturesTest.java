package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FeaturesTest {

  private static final List<Path> FEATURES =
      Stream.concat(
              Stream.of(Path.of("../docs/features.md")),
              DocFiles.markdownIn(Path.of("../docs/features")).stream())
          .toList();

  @Test
  void everySpecIsLinkedAndEveryLinkedSpecExists() {
    List<String> linked =
        Pattern.compile("openspec/specs/([^/)#]+)/spec\\.md")
            .matcher(FEATURES.stream().map(DocFiles::read).collect(Collectors.joining("\n")))
            .results()
            .map(match -> match.group(1))
            .distinct()
            .sorted()
            .toList();

    assertThat(linked).isEqualTo(DocFiles.specDirectories());
  }

  @Test
  void everyKeyNamedAsOptInIsDescribedInJagtYmlDist() {
    String dist = DocFiles.read(Path.of("../jagt.desktop.yml.dist"));
    List<String> keys =
        Pattern.compile("Key: `([^`]+)`")
            .matcher(FEATURES.stream().map(DocFiles::read).collect(Collectors.joining("\n")))
            .results()
            .map(match -> match.group(1))
            .toList();

    assertThat(keys)
        .isNotEmpty()
        .allSatisfy(key ->
            assertThat(DocFiles.describesInOrder(dist, key)).describedAs("%s", key).isTrue());
  }
}

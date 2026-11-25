package dev.jagt.orchestrator;

import static dev.jagt.orchestrator.DocFiles.specs;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocLinksTest {

  private static final Pattern LINK = Pattern.compile("]\\(([^)\\s#]+)[^)]*\\)");

  private static final Pattern ANCHORED_LINK =
      Pattern.compile("]\\(([^)\\s#]*)#([^)\\s]+)[^)]*\\)");

  @Test
  void everyRelativeLinkReachesFile() {
    List<String> broken =
        DocFiles.documents().stream()
            .flatMap(document ->
                LINK.matcher(DocFiles.withoutCodeBlocks(document))
                    .results()
                    .map(link -> link.group(1))
                    .filter(target -> !target.matches("[a-z]+:.*"))
                    .filter(target -> !Files.exists(document.resolveSibling(target)))
                    .map(target -> document + " -> " + target))
            .toList();

    assertThat(broken).isEmpty();
  }

  @Test
  void everyAnchoredLinkReachesHeading() {
    List<String> broken =
        DocFiles.documents().stream()
            .flatMap(document ->
                ANCHORED_LINK
                    .matcher(DocFiles.withoutCodeBlocks(document))
                    .results()
                    .filter(link -> !link.group(1).matches("[a-z]+:.*"))
                    .filter(link ->
                        !DocFiles.headingSlugs(
                                document.resolveSibling(
                                    link.group(1).isEmpty()
                                        ? document.getFileName().toString()
                                        : link.group(1)))
                            .contains(link.group(2)))
                    .map(link -> document + " -> " + link.group(1) + "#" + link.group(2)))
            .toList();

    assertThat(broken).isEmpty();
  }

  @Test
  void decisionIndexListsEveryRecordAndNothingElse() {
    Path directory = Path.of("../docs/decisions");
    List<String> records =
        DocFiles.namesIn(directory).stream()
            .filter(name -> name.matches("\\d{4}-.*"))
            .sorted()
            .toList();
    List<String> indexed =
        Stream.of("index-0001-0099.md", "index-0100-0199.md", "index-0200-0299.md")
            .flatMap(page ->
                Pattern.compile("]\\((\\d{4}-[^)#]*\\.md)")
                    .matcher(DocFiles.withoutCodeBlocks(directory.resolve(page)))
                    .results()
                    .map(link -> link.group(1)))
            .sorted()
            .toList();

    assertThat(indexed).isEqualTo(records);
  }

  @Test
  void noOpenChangeSitsOnMain() throws IOException {
    try (Stream<Path> changes = Files.list(Path.of("../openspec/changes"))) {
      assertThat(
              changes
                  .map(path -> path.getFileName().toString())
                  .filter(name -> !name.equals("archive")))
          .isEmpty();
    }
  }

  @Test
  void everyRuleHasPathScopedPointer() {
    assertThat(DocFiles.namesIn(Path.of("../.claude/rules")))
        .containsAll(DocFiles.namesIn(Path.of("../docs/rules")));
  }

  @Test
  void everyPointerNamesRuleOrSpec() {
    List<String> sources =
        Stream.concat(
                DocFiles.namesIn(Path.of("../docs/rules")).stream(),
                specs().stream()
                    .map(spec -> Objects.requireNonNull(spec.getParent()).getFileName() + ".md"))
            .toList();

    assertThat(DocFiles.namesIn(Path.of("../.claude/rules"))).isSubsetOf(sources);
  }

  @Test
  void everySpecHasPathScopedPointerOrExemption() {
    assertThat(DocFiles.specsWithoutPointer(Path.of("src/test/resources/pointerless-specs.txt")))
        .isEmpty();
  }

  @Test
  void everyPointerGlobMatchesTrackedFile() {
    List<String> tracked = DocFiles.trackedFiles();
    List<String> dead =
        DocFiles.markdownIn(Path.of("../.claude/rules")).stream()
            .flatMap(pointer ->
                DocFiles.pointerGlobs(pointer).stream()
                    .filter(glob -> !DocFiles.matchesAnyFile(glob, tracked))
                    .map(glob -> pointer.getFileName() + " -> " + glob))
            .toList();

    assertThat(dead).isEmpty();
  }

  @Test
  void everyPointerLinksOnlyItsOwnSpec() {
    List<String> foreign =
        DocFiles.markdownIn(Path.of("../.claude/rules")).stream()
            .flatMap(pointer -> DocFiles.foreignSpecLinks(pointer).stream())
            .toList();

    assertThat(foreign).isEmpty();
  }
}

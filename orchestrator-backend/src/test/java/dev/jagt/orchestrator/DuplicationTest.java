package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CPDReport;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.cpd.Mark;
import net.sourceforge.pmd.lang.LanguageRegistry;
import org.junit.jupiter.api.Test;

class DuplicationTest {

  private static final int MINIMUM_TOKENS = 75;

  private static final List<Path> SKIPPED =
      Stream.of("capability", "command", "job", "service")
          .map(name -> Path.of("usecase/src/test/java/dev/jagt/orchestrator", name))
          .toList();

  @Test
  void noBlockOfCodeIsCopiedElsewhere() throws IOException {
    CPDConfiguration configuration = new CPDConfiguration();
    configuration.setOnlyRecognizeLanguage(LanguageRegistry.CPD.getLanguageById("java"));
    configuration.setMinimumTileSize(MINIMUM_TOKENS);
    SourceRoots.javaFiles()
        .filter(file -> SKIPPED.stream().noneMatch(file::startsWith))
        .forEach(configuration::addInputPath);
    List<String> duplicates = new ArrayList<>();

    try (CpdAnalysis cpd = CpdAnalysis.create(configuration)) {
      cpd.performAnalysis(
          (CPDReport report) -> {
            assertThat(report.getProcessingErrors()).isEmpty();
            report
                .getMatches()
                .forEach(
                    match ->
                        duplicates.add(
                            match.getTokenCount()
                                + " tokens: "
                                + StreamSupport.stream(match.spliterator(), false)
                                    .map(DuplicationTest::place)
                                    .toList()));
          });
    }

    assertThat(String.join(System.lineSeparator(), duplicates)).isEmpty();
  }

  private static String place(Mark mark) {
    return mark.getLocation().getFileId().getFileName() + ":" + mark.getLocation().getStartLine();
  }
}

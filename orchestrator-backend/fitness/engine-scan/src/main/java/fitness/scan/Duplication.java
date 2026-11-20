package fitness.scan;

import fitness.core.Project;
import fitness.core.FitnessFunction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;

final class Duplication {

  private Duplication() {}

  static List<Sized> tokens(FitnessFunction function, Project project, List<Path> files) {
    CPDConfiguration configuration = new CPDConfiguration();
    configuration.setOnlyRecognizeLanguage(LanguageRegistry.CPD.getLanguageById("java"));
    configuration.setMinimumTileSize((int) function.threshold().limit() + 1);
    files.forEach(configuration::addInputPath);
    List<Sized> blocks = new ArrayList<>();
    try (CpdAnalysis cpd = CpdAnalysis.create(configuration)) {
      cpd.performAnalysis(
          report -> {
            if (!report.getProcessingErrors().isEmpty()) {
              throw new IllegalStateException(
                  "CPD could not process: " + report.getProcessingErrors());
            }
            report
                .getMatches()
                .forEach(
                    match ->
                        blocks.add(
                            new Sized(
                                StreamSupport.stream(match.spliterator(), false)
                                    .map(
                                        mark ->
                                            mark.getLocation().getFileId().getFileName()
                                                + ":"
                                                + mark.getLocation().getStartLine())
                                    .toList()
                                    .toString(),
                                match.getTokenCount())));
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return blocks;
  }
}

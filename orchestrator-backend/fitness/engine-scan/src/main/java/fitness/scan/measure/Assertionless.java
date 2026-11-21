package fitness.scan.measure;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;

public final class Assertionless {

  private Assertionless() {}

  public static List<String> tests(FitnessFunction function, Project project, List<Path> files) {
    PMDConfiguration configuration = new PMDConfiguration();
    configuration.setDefaultLanguageVersion(
        LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
    configuration.setIgnoreIncrementalAnalysis(true);
    files.forEach(configuration::addInputPath);
    try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
      pmd.addRuleSet(RuleSet.forSingleRule(new AssertionlessTestRule()));
      Report report = pmd.performAnalysisAndCollectReport();
      if (!report.getProcessingErrors().isEmpty()) {
        throw new IllegalStateException("PMD could not process: " + report.getProcessingErrors());
      }
      return report.getViolations().stream().map(violation -> violation.getDescription()).toList();
    }
  }
}

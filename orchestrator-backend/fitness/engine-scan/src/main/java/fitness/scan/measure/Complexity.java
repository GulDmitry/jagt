package fitness.scan.measure;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.scan.Sized;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.reporting.Report;

public final class Complexity {

  private static final String RULESET =
      """
      <ruleset name="cognitive" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
        <description>cognitive complexity</description>
        <rule ref="category/java/design.xml/CognitiveComplexity">
          <properties><property name="reportLevel" value="1"/></properties>
        </rule>
      </ruleset>\
      """;
  private static final Pattern DESCRIPTION =
      Pattern.compile("^(.*?) has a cognitive complexity of (\\d+)");

  private Complexity() {}

  public static List<Sized> cognitive(FitnessFunction function, Project project, List<Path> files) {
    PMDConfiguration configuration = new PMDConfiguration();
    configuration.setDefaultLanguageVersion(
        LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
    configuration.setIgnoreIncrementalAnalysis(true);
    files.forEach(configuration::addInputPath);
    try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
      pmd.addRuleSet(pmd.newRuleSetLoader().loadFromString("cognitive.xml", RULESET));
      Report report = pmd.performAnalysisAndCollectReport();
      if (!report.getProcessingErrors().isEmpty()) {
        throw new IllegalStateException("PMD could not process: " + report.getProcessingErrors());
      }
      return report.getViolations().stream()
          .map(
              violation -> {
                Matcher found = DESCRIPTION.matcher(violation.getDescription());
                found.find();
                return new Sized(
                    violation.getFileId().getFileName()
                        + ":"
                        + violation.getBeginLine()
                        + " "
                        + found.group(1),
                    Long.parseLong(found.group(2)));
              })
          .toList();
    }
  }
}

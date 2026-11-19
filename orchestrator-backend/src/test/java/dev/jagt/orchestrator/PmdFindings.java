package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;

final class PmdFindings {

  private static final int COGNITIVE_CEILING = 12;
  private static final String COGNITIVE_RULESET =
      """
      <ruleset name="cognitive" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
        <description>cognitive complexity</description>
        <rule ref="category/java/design.xml/CognitiveComplexity">
          <properties><property name="reportLevel" value="%d"/></properties>
        </rule>
      </ruleset>\
      """
          .formatted(COGNITIVE_CEILING + 1);

  static final List<String> PROCESSING_ERRORS;
  static final List<String> COGNITIVE_VIOLATIONS;
  static final List<String> ASSERTIONLESS_TESTS;

  static {
    PMDConfiguration configuration = new PMDConfiguration();
    configuration.setDefaultLanguageVersion(
        LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
    configuration.setIgnoreIncrementalAnalysis(true);
    SourceRoots.ROOTS.forEach(configuration::addInputPath);
    List<Path> testRoots =
        SourceRoots.ROOTS.stream()
            .filter(SourceRoots::isTest)
            .map(root -> root.toAbsolutePath().normalize())
            .toList();

    try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
      pmd.addRuleSet(pmd.newRuleSetLoader().loadFromString("cognitive.xml", COGNITIVE_RULESET));
      pmd.addRuleSet(RuleSet.forSingleRule(new AssertionlessTestRule()));
      Report report = pmd.performAnalysisAndCollectReport();

      PROCESSING_ERRORS = report.getProcessingErrors().stream().map(Object::toString).toList();
      List<String> cognitive = new ArrayList<>();
      List<String> assertionless = new ArrayList<>();
      report
          .getViolations()
          .forEach(violation -> {
            if (violation.getRule() instanceof AssertionlessTestRule) {
              Path file = Path.of(violation.getFileId().getAbsolutePath()).normalize();
              if (testRoots.stream().anyMatch(file::startsWith)) {
                assertionless.add(violation.getDescription());
              }
            } else {
              cognitive.add(
                  violation.getFileId().getFileName()
                      + ":"
                      + violation.getBeginLine()
                      + " "
                      + violation.getDescription().replaceFirst(", current threshold.*", ""));
            }
          });
      COGNITIVE_VIOLATIONS = List.copyOf(cognitive);
      ASSERTIONLESS_TESTS = List.copyOf(assertionless);
    }
  }

  private PmdFindings() {}
}

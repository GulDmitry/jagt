package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;

class EveryTestAssertsTest {

  @Test
  void everyTestMethodReachesAnAssertion() {
    PMDConfiguration configuration = new PMDConfiguration();
    configuration.setDefaultLanguageVersion(
        LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
    configuration.setIgnoreIncrementalAnalysis(true);
    SourceRoots.ROOTS.stream().filter(SourceRoots::isTest).forEach(configuration::addInputPath);

    try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
      pmd.addRuleSet(RuleSet.forSingleRule(new AssertionlessTestRule()));
      Report report = pmd.performAnalysisAndCollectReport();

      assertThat(report.getProcessingErrors()).isEmpty();
      assertThat(report.getViolations())
          .extracting(violation -> violation.getDescription())
          .isEmpty();
    }
  }
}

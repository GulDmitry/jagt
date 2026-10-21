package dev.jagt.orchestrator;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveComplexityTest {

    private static final int CEILING = 12;
    private static final String RULESET = """
            <ruleset name="cognitive" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
              <description>cognitive complexity</description>
              <rule ref="category/java/design.xml/CognitiveComplexity">
                <properties><property name="reportLevel" value="%d"/></properties>
              </rule>
            </ruleset>""".formatted(CEILING + 1);

    @Test
    void noMethodRunsPastTheCognitiveComplexityCeiling() {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setDefaultLanguageVersion(LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
        configuration.setIgnoreIncrementalAnalysis(true);
        Stream.of("core", "usecase", "adapter", "surface", ".")
                .map(module -> Path.of(module, "src/main/java")).forEach(configuration::addInputPath);

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            pmd.addRuleSet(pmd.newRuleSetLoader().loadFromString("cognitive.xml", RULESET));
            Report report = pmd.performAnalysisAndCollectReport();

            assertThat(report.getProcessingErrors()).isEmpty();
            assertThat(report.getViolations()).extracting(violation -> violation.getFileId().getFileName()
                    + ":" + violation.getBeginLine() + " "
                    + violation.getDescription().replaceFirst(", current threshold.*", "")).isEmpty();
        }
    }
}

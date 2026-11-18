package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QualityGatesTest {

  private static final String CONVENTIONS = "|build-logic/src/main/groovy/jagt.conventions.gradle|";
  private static final String PITEST = "|build-logic/src/main/groovy/jagt.pitest.gradle|";

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "JaCoCo" + CONVENTIONS + "finalizedBy tasks.named.*jacocoTestCoverageVerification",
        "PIT" + PITEST + "finalizedBy ':pitestDiff'",
        "PIT on a branch's own lines" + PITEST + "'merge-base', base, 'HEAD'",
        "Spotless" + CONVENTIONS + "dependsOn tasks.named.*spotlessApply",
        "Checkstyle" + CONVENTIONS + "dependsOn tasks.withType.Checkstyle",
        "Error Prone" + CONVENTIONS + "apply plugin: 'net.ltgt.errorprone'",
        "Error Prone as errors" + CONVENTIONS + "'-Werror'",
        "ArchUnit|build.gradle|testImplementation 'com.tngtech.archunit:archunit",
        "dependency vulnerability scan|../.github/workflows/ci.yml"
            + "|google/osv-scanner-action/osv-scanner-action@[0-9a-f]{40}",
        "dependency vulnerability scan on GitLab|../.gitlab-ci.yml|scripts/vulnerability-scan.sh",
        "dependency vulnerability scan binary|scripts/vulnerability-scan.sh|sha256=[0-9a-f]{64}"
      })
  void failsNamingTheGateWhenItsWiringIsGone(String gate, String file, String wiring) {
    assertThat(SourceRoots.read(Path.of(file)))
        .describedAs("gate %s is not wired in %s", gate, file)
        .containsPattern(wiring);
  }
}

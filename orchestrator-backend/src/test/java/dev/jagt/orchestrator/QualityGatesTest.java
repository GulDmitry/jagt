package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QualityGatesTest {

  private static final String CONVENTIONS = "|build-logic/src/main/groovy/jagt.conventions.gradle|";
  private static final String SUITES = "|build-logic/src/main/groovy/jagt.suites.gradle|";
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
        "dependency vulnerability scan in the scan gate"
            + SUITES
            + "gateScan: \\[.*'vulnerabilityScan'",
        "dependency licence check in the test gate" + SUITES + "gateTest: \\[.*'checkLicences'",
        "test gate on GitHub|../.github/workflows/ci.yml|gradlew --no-daemon gateTest",
        "test gate on GitLab|../.gitlab-ci.yml|gradlew --no-daemon gateTest",
        "dependency licence check|build-logic/src/main/groovy/jagt.sbom.gradle"
            + "|tasks.register\\('checkLicences'\\)",
        "reproducible archives" + CONVENTIONS + "reproducibleFileOrder = true",
        "reproducible archive timestamps" + CONVENTIONS + "preserveFileTimestamps = false",
        "reproducible build info|build-logic/src/main/groovy/jagt.stage-jar.gradle"
            + "|excludes = \\['time'\\]",
        "reproducible SBOM|build-logic/src/main/groovy/jagt.sbom.gradle|\"timestamp\"",
        "dependency vulnerability scan binary|scripts/vulnerability-scan.sh|sha256=[0-9a-f]{64}"
      })
  void failsNamingTheGateWhenItsWiringIsGone(String gate, String file, String wiring) {
    assertThat(SourceRoots.read(Path.of(file)))
        .describedAs("gate %s is not wired in %s", gate, file)
        .containsPattern(wiring);
  }
}

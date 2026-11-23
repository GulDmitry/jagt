package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

class CiWorkflowTest {

  private static final Path CODEQL = Path.of("../.github/workflows/codeql.yml");

  private static final Path GATES = Path.of("build-logic/src/main/groovy/jagt.suites.gradle");

  @Test
  void publishesReleaseOnlyOnceEveryJobOnTheMainPushIsGreen() {
    Map<String, Map<String, Object>> jobs = CiWorkflow.jobs();
    Set<String> others =
        jobs.keySet().stream()
            .filter(job -> !job.equals("release") && !job.equals("heavy"))
            .collect(Collectors.toSet());
    assertThat(Objects.requireNonNull(jobs.get("release")).get("needs"))
        .asInstanceOf(list(String.class))
        .containsExactlyInAnyOrderElementsOf(others);
  }

  @Test
  void publishesFromNoJobButTheRelease() {
    Set<String> publishing =
        CiWorkflow.jobs().entrySet().stream()
            .filter(job -> job.getValue().toString().contains("gh release"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(publishing).containsExactly("release");
  }

  @Test
  void grantsWritingToTheRepositoryToNoJobButTheRelease() {
    Set<String> writing =
        CiWorkflow.jobs().entrySet().stream()
            .filter(
                job -> String.valueOf(job.getValue().get("permissions")).contains("contents=write"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(writing).containsExactly("release");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ci", "nightly", "base-image", "base-images", "codeql", "scorecard"})
  void setsOneTopLevelPermissionsBlockInEveryWorkflow(String workflow) {
    Map<?, ?> parsed =
        new Yaml().load(SourceRoots.read(Path.of("../.github/workflows/" + workflow + ".yml")));

    assertThat(parsed.get("permissions")).isInstanceOf(Map.class);
  }

  @Test
  void letsTheWorkflowReadTheRepositoryAndNothingElseUnlessItsJobWidensIt() {
    Map<?, ?> parsed = new Yaml().load(SourceRoots.read(Path.of("../.github/workflows/ci.yml")));
    Set<String> widened =
        CiWorkflow.jobs().entrySet().stream()
            .filter(job -> job.getValue().containsKey("permissions"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(parsed.get("permissions")).isEqualTo(Map.of("contents", "read"));
    assertThat(widened).containsExactly("release");
  }

  @Test
  void runsExactlyTheFourGroupsAndTheInstallCheckOnGithub() {
    assertThat(CiWorkflow.jobs().keySet())
        .containsExactlyInAnyOrder("test", "heavy", "stand", "install", "release");
  }

  @Test
  void runsExactlyTheFourGroupsOnGitlab() {
    assertThat(CiWorkflow.gitlabJobs())
        .containsExactlyInAnyOrder("test", "heavy", "stand", "release");
  }

  @Test
  void startsOnGithubOnlyOnPushPullRequestAndWorkflowCall() {
    assertThat(CiWorkflow.githubTriggers())
        .containsExactlyInAnyOrder("push", "pull_request", "workflow_call");
  }

  @Test
  void startsOnGitlabOnlyOnPushMergeRequestScheduleAndTag() {
    assertThat(CiWorkflow.gitlabText())
        .contains("$CI_PIPELINE_SOURCE == \"push\"")
        .contains("$CI_PIPELINE_SOURCE == \"merge_request_event\"")
        .contains("$CI_PIPELINE_SOURCE == \"schedule\"")
        .doesNotContain("\"web\"")
        .doesNotContain("\"api\"");
  }

  @Test
  void runsTheHeavyJobOnScheduleAndPullRequest() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("heavy")).get("if"))
        .isEqualTo("inputs.nightly || github.event_name == 'pull_request'");
  }

  @Test
  void startsNoCiRunOnTagsSoNobodyPublishesByPushingOne() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .doesNotContainPattern("(?m)^\\s+tags:")
        .doesNotContain("refs/tags/v");
  }

  @Test
  void publishesOnThePushToMainOnlyWhenTheVersionsTagDoesNotExistYet() {
    Map<String, Map<String, Object>> jobs = CiWorkflow.jobs();
    String test = Objects.requireNonNull(jobs.get("test")).toString();

    assertThat(test)
        .contains("github.event_name == 'push' && github.ref == 'refs/heads/main'")
        .contains("printVersion")
        .contains("git ls-remote --exit-code --tags origin");
    assertThat(Objects.requireNonNull(jobs.get("release")).get("if"))
        .isEqualTo("needs.test.outputs.new == 'true'");
  }

  @Test
  void createsTheReleaseTagOnThePushedShaWithoutCancellingTheRunOnMain() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release).contains("--target \"$GITHUB_SHA\"").doesNotContain("--verify-tag");
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .contains("cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}");
  }

  @Test
  void letsTheNightlyBeStartedByHand() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")))
        .contains("  workflow_dispatch:");
  }

  @Test
  void runsTheStandOnEveryEngineForPullRequests() {
    assertThat(
            String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("stand")).get("strategy")))
        .contains(
            "(inputs.nightly || github.event_name == 'pull_request')"
                + " && fromJSON('[\"compose\",\"kind\"]')");
  }

  @ParameterizedTest
  @CsvSource({
    "test, ./gradlew --no-daemon gateTest",
    "heavy, scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy",
    "stand, ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=",
    "install, ./gradlew --no-daemon gateInstall"
  })
  void runsEachPullRequestJobAsTheGateLineTheLocalCiRunsToo(String job, String line) {
    assertThat(String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps")))
        .contains("run=" + line);
    assertThat(SourceRoots.read(Path.of("scripts/linux-suite.sh")))
        .contains(job + ") echo '" + line);
  }

  @Test
  void runsTheMacosInstallJobsGateNativelyInTheLocalCi() {
    assertThat(SourceRoots.read(Path.of("scripts/ci-local.sh")))
        .contains("jobs=${*:-test heavy stand install install-macos}")
        .contains("./gradlew --no-daemon gateInstall");
  }

  @Test
  void runsEveryLinuxJobLocallyWithItsOwnDockerEngineAndUntimedAsOnCi() {
    assertThat(SourceRoots.read(Path.of("scripts/linux-suite.sh")))
        .contains("--privileged -e CI=true -e ORG_GRADLE_PROJECT_timed=false")
        .contains("dockerd");
  }

  @Test
  void runsTheHeavyJobOnGitlabForMergeRequests() {
    assertThat(CiWorkflow.gitlabText())
        .contains(
            "$CI_PIPELINE_SOURCE == \"schedule\" || $CI_PIPELINE_SOURCE == \"merge_request_event\""
                + " || $CI_COMMIT_TAG =~ /^v/");
  }

  @Test
  void runsTheTestJobNeverOnSchedule() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("test")).get("if"))
        .isEqualTo("!inputs.nightly");
  }

  @Test
  void runsTheNightlyAsItsOwnWorkflowCallingCiOnTheSchedule() {
    String nightly = SourceRoots.read(Path.of("../.github/workflows/nightly.yml"));

    assertThat(nightly)
        .contains("name: nightly")
        .contains("cron: '17 3 * * *'")
        .contains("uses: ./.github/workflows/ci.yml")
        .contains("nightly: true")
        .contains("secrets: inherit");
  }

  @Test
  void grantsTheNightlyEveryPermissionThatCiJobsItCallsAskFor() {
    Map<?, ?> nightly =
        new Yaml().load(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")));
    Set<Object> asked =
        CiWorkflow.jobs().values().stream()
            .map(job -> job.get("permissions"))
            .filter(Map.class::isInstance)
            .flatMap(permissions -> ((Map<?, ?>) permissions).entrySet().stream())
            .collect(Collectors.<Object>toSet());

    Map<?, ?> granted = (Map<?, ?>) Objects.requireNonNull(nightly.get("permissions"));

    assertThat(Set.<Object>copyOf(granted.entrySet())).containsAll(asked);
  }

  @Test
  void startsTheNightlyOffTheHour() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")))
        .doesNotContainPattern("cron: '0 ");
  }

  @Test
  void startsCiOnNoScheduleSoThePushDoesNotRunTheNightly() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .doesNotContain("schedule")
        .doesNotContain("run-name");
  }

  @Test
  void attachesTheCycloneDxSbomToTheReleaseBesideTheJar() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .contains("./gradlew --no-daemon gateRelease")
        .contains("build/reports/sbom/jagt.cdx.json")
        .contains("jagt-$version.cdx.json")
        .contains("gh release upload \"$TAG\" build/release/*");
  }

  @Test
  void attachesTheInstallScriptUnderAnUnversionedNameSoTheLatestReleaseServesIt() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release).contains("cp ../install.sh build/release/install.sh");
  }

  @Test
  void attachesThePowerShellInstallerBesideTheShellOne() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release).contains("cp ../install.ps1 build/release/install.ps1");
  }

  @Test
  void runsTheInstallCheckOnLinuxMacAndWindows() {
    String install = Objects.requireNonNull(CiWorkflow.jobs().get("install")).toString();

    assertThat(install)
        .contains("ubuntu-latest")
        .contains("macos-latest")
        .contains("windows-latest")
        .contains("timeout-minutes=");
  }

  @Test
  void publishesTheImageWithItsSbom() {
    assertThat(SourceRoots.read(Path.of("scripts/publish-image.sh"))).contains("--sbom=true");
  }

  @Test
  void attachesTheSbomToTheGitlabReleaseToo() {
    assertThat(CiWorkflow.gitlabText())
        .contains("./gradlew --no-daemon gateRelease")
        .contains("orchestrator-backend/build/reports/sbom/jagt.cdx.json");
    assertThat(SourceRoots.read(GATES)).contains("gateRelease: ['bootJar', 'cyclonedxBom'],");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "heavy", "stand", "install", "release"})
  void keepsGradleCachesBetweenRunsByThePinnedSetupGradleOnItsOpenSourceProvider(String job) {
    String steps = String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps"));

    assertThat(steps)
        .contains("gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c")
        .contains("cache-provider=external")
        .doesNotContain("cache-provider=basic")
        .doesNotContain("cache-read-only");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "heavy", "stand", "install", "release"})
  void restoresTheNewestGradleCacheOfTheJobWhenTheBuildFilesChangedSoTheRunStartsWarm(String job) {
    String steps = String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps"));

    assertThat(steps)
        .containsPattern(
            "actions/cache@[0-9a-f]{40}, with=\\{path=~/.gradle/caches\\n~/.gradle/wrapper")
        .contains("key=gradle-${{ runner.os }}-${{ github.job }}-")
        .contains("restore-keys=gradle-${{ runner.os }}-${{ github.job }}-");
  }

  @Test
  void keepsTheStandGradleCacheOfEachEngineApart() {
    String steps =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("stand")).get("steps"));

    assertThat(steps).contains("${{ github.job }}-${{ matrix.engine }}-${{ hashFiles(");
  }

  @Test
  void restoresTheCodeqlGradleCacheAndSavesItOnlyFromMain() {
    String workflow = SourceRoots.read(CODEQL);

    assertThat(workflow)
        .contains("cache-provider: external")
        .containsPattern("actions/cache/restore@[0-9a-f]{40} # v6")
        .containsPattern("actions/cache/save@[0-9a-f]{40} # v6")
        .contains("&& github.ref == 'refs/heads/main'");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "systemProp.org.gradle.internal.repository.max.tentatives=5",
        "systemProp.org.gradle.internal.repository.initial.backoff=5000",
        "systemProp.org.gradle.internal.network.retry.max.attempts=5",
        "systemProp.org.gradle.internal.network.retry.initial.backOff=5000"
      })
  void retriesRepositoryRequestsFiveTimesWithDoublingWaitsFromFiveSeconds(String property) {
    assertThat(SourceRoots.read(Path.of("gradle.properties"))).contains(property);
  }

  @Test
  void signsProvenanceOfTheJarAndTheImageByOnePinnedAction() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .contains("actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8")
        .contains("orchestrator-backend/build/release/*.jar")
        .contains("orchestrator-backend/build/release/*.cdx.json")
        .contains("orchestrator-backend/build/release/*.sha256")
        .contains("subject-digest=${{ steps.image.outputs.digest }}")
        .contains("push-to-registry=true");
  }

  @Test
  void grantsTheReleaseJobTheRightsToSignAndStoreAttestations() {
    String permissions =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("release")).get("permissions"));

    assertThat(permissions).contains("attestations=write").contains("id-token=write");
  }

  @Test
  void attachesSha256FilesForTheJarAndTheImageToTheRelease() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .contains("sha256sum \"jagt-$version.jar\" \"jagt-$version.cdx.json\"")
        .contains("jagt-$version.image.sha256");
  }

  @Test
  void buildsTheBaseImageForBothPlatformsAndSignsItsProvenance() {
    String workflow = SourceRoots.read(Path.of("../.github/workflows/base-image.yml"));
    String script = SourceRoots.read(Path.of("scripts/publish-base-image.sh"));

    assertThat(script).contains("--platform linux/amd64,linux/arm64").contains("--sbom=true");
    assertThat(workflow)
        .contains("actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8")
        .contains("subject-digest: ${{ steps.image.outputs.digest }}")
        .contains("attestations: write");
  }

  @Test
  void buildsTheStandBaseAfterTheOsBaseFromItsOwnDockerfileOnTheOsBaseTag() {
    String workflow = SourceRoots.read(Path.of("../.github/workflows/base-images.yml"));
    String script = SourceRoots.read(Path.of("scripts/publish-base-image.sh"));
    String dockerfile = SourceRoots.read(Path.of("../base/Dockerfile.stand"));

    assertThat(workflow)
        .containsSubsequence(
            "name: base\n      dockerfile: Dockerfile\n",
            "needs: base",
            "name: stand-base\n      dockerfile: Dockerfile.stand\n      from: base");
    assertThat(script).contains("BASE=ghcr.io/${GITHUB_REPOSITORY,,}-${from}:base");
    assertThat(dockerfile).contains("FROM ${BASE}").contains("@anthropic-ai/claude-code");
  }

  @Test
  void deletesTheImageOfEveryReleaseTheRetentionDeletesAndEveryPagesDeploymentButTheNewest() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .containsPattern("dataaxiom/ghcr-cleanup-action@[0-9a-f]{40}")
        .contains("delete-tags=${{ steps.retention.outputs.image-tags }}")
        .contains("jq -r '.[1:][] | .id'")
        .doesNotContain("REFS")
        .contains("state=inactive")
        .contains("deployments/$id")
        .contains("packages=write")
        .contains("deployments=write");
  }

  @Test
  void scansTheBaseImageByDigestWithTheChecksumVerifiedScanBinary() {
    String workflow = SourceRoots.read(Path.of("../.github/workflows/base-image.yml"));
    String scan = SourceRoots.read(Path.of("scripts/vulnerability-scan.sh"));

    assertThat(workflow)
        .contains(
            "scripts/vulnerability-scan.sh scan image ${{ steps.image.outputs.name }}@${{"
                + " steps.image.outputs.digest }}")
        .doesNotContain("osv-scanner-action");
    assertThat(scan).contains("sha256sum -c").contains("\"$tool\" \"$@\"");
  }

  @Test
  void failsAnImageScanOnlyWhenFindingHasPublishedFix() {
    String scan = SourceRoots.read(Path.of("scripts/vulnerability-scan.sh"));
    String dockerfile = SourceRoots.read(Path.of("../base/Dockerfile"));

    assertThat(scan).contains("--format vertical").contains("Minimal Fix Version: '[^N]");
    assertThat(dockerfile).contains("apt-get upgrade -y");
  }

  @Test
  void passesAnImageScanWhenTheOnlyFixNeedsUbuntuPro() {
    String scan = SourceRoots.read(Path.of("scripts/vulnerability-scan.sh"));

    assertThat(scan).contains("grep -Fvq '~esm'");
  }

  @Test
  void keepsTheNewestTenUntaggedBaseImagesWithTheirChildrenByOnePinnedAction() {
    String workflow = SourceRoots.read(Path.of("../.github/workflows/base-image.yml"));

    assertThat(workflow)
        .containsPattern("dataaxiom/ghcr-cleanup-action@[0-9a-f]{40}")
        .contains("packages: ${{ github.event.repository.name }}-${{ inputs.name }}")
        .contains("keep-n-untagged: 10")
        .contains("delete-partial-images: true")
        .contains("delete-orphaned-images: true")
        .doesNotContain("delete-package-versions");
  }

  @Test
  void measuresNoTimeOnCi() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .doesNotContain("standTimingGate")
        .doesNotContain("github-action-benchmark")
        .doesNotContain("timings-history");
  }

  @Test
  void limitsNoSuiteByTimeAndRecordsNoSuiteTimeOnEitherCiEngine() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .contains("  ORG_GRADLE_PROJECT_timed: 'false'");
    assertThat(CiWorkflow.gitlabText()).contains("export ORG_GRADLE_PROJECT_timed=false");
    assertThat(SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.fitness.gradle")))
        .contains("timed = providers.gradleProperty('timed').map { it != 'false' }.orElse(true)");
    assertThat(SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.suite-stats.gradle")))
        .contains("if (providers.gradleProperty('timed').getOrElse('true') != 'false') {");
    assertThat(SourceRoots.read(Path.of("fitness/build.gradle")))
        .contains("if (providers.gradleProperty('timed').getOrElse('true') != 'false') {");
  }

  @Test
  void scansTheTestJobForSecretsInTheTreeAndFullHistoryOnBothCiEngines() {
    String steps =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("test")).get("steps"));

    assertThat(steps).contains("fetch-depth=0").contains("gateTest");
    assertThat(SourceRoots.read(GATES))
        .contains("gateTest: ['secretScan', 'boardCheck',")
        .contains("'orchestrator-backend/scripts/secret-scan.sh'");
    assertThat(CiWorkflow.gitlabText()).contains("GIT_DEPTH: \"0\"").contains("gateTest");
  }

  @Test
  void scansTheReleaseSbomForKnownVulnerabilitiesInGitlabsTestJobThroughTheSharedScript() {
    Map<?, ?> pipeline = new Yaml().load(CiWorkflow.gitlabText());
    Map<?, ?> test = (Map<?, ?>) Objects.requireNonNull(pipeline.get("test"));

    assertThat(String.valueOf(test.get("script"))).contains("gateTest");
  }

  @Test
  void scansTheReleaseSbomInGithubsTestJobThroughTheSharedScriptNotTheAction() {
    assertThat(SourceRoots.read(GATES))
        .contains("'test', 'guardsTest', 'vulnerabilityScan',")
        .contains("file('scripts/vulnerability-scan.sh')");
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .doesNotContain("osv-scanner-action");
  }

  @Test
  void analysesJavaAndJavaScriptWithCodeqlOnPushAndPullRequestToMain() {
    Map<?, ?> workflow = new Yaml().load(SourceRoots.read(CODEQL));
    Map<?, ?> on = (Map<?, ?>) Objects.requireNonNull(workflow.get(true));
    Set<String> triggers = on.keySet().stream().map(String::valueOf).collect(Collectors.toSet());

    assertThat(triggers).containsExactlyInAnyOrder("push", "pull_request");
    assertThat(SourceRoots.read(CODEQL))
        .contains("language: java-kotlin")
        .contains("language: javascript-typescript");
  }

  @Test
  void letsCodeqlReadTheRepositoryAndWriteSecurityEventsOnItsAnalyseJobOnly() {
    Map<?, ?> workflow = new Yaml().load(SourceRoots.read(CODEQL));
    Map<?, ?> analyse =
        (Map<?, ?>)
            Objects.requireNonNull(
                ((Map<?, ?>) Objects.requireNonNull(workflow.get("jobs"))).get("analyse"));

    assertThat(workflow.get("permissions")).isEqualTo(Map.of("contents", "read"));
    assertThat(analyse.get("permissions"))
        .isEqualTo(Map.of("contents", "read", "security-events", "write"));
  }

  @Test
  void failsTheCodeqlRunOnFindingsOfSeverityErrorAndPinsEveryActionBySha() {
    String workflow = SourceRoots.read(CODEQL);

    assertThat(workflow)
        .contains("select(. == \"error\")")
        .contains("test \"$errors\" -eq 0")
        .doesNotContainPattern("uses: [^.\\n]+@(?![0-9a-f]{40} )");
  }

  @Test
  void verifiesTheSecretScannerBinaryByChecksumAndScansTreeAndHistory() {
    assertThat(SourceRoots.read(Path.of("scripts/secret-scan.sh")))
        .containsPattern("sha256=[0-9a-f]{64}")
        .contains("sha256sum -c")
        .contains("gitleaks\" dir")
        .contains("gitleaks\" git");
  }

  @Test
  void handsTheImageDigestToTheWorkflowFromThePublishScript() {
    assertThat(SourceRoots.read(Path.of("scripts/publish-image.sh")))
        .contains("--metadata-file")
        .contains("digest=");
  }
}

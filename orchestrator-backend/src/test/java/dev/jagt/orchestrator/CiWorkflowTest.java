package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

class CiWorkflowTest {

  private static final Path CODEQL = Path.of("../.github/workflows/codeql.yml");

  private static final Path SETTINGS = Path.of("settings.gradle");

  private static final Path PITEST = Path.of("build-logic/src/main/groovy/jagt.pitest.gradle");

  private static final Path GATES = Path.of("build-logic/src/main/groovy/jagt.suites.gradle");

  @Test
  void publishesReleaseOnceTheMainPushsUnitSuiteIsGreen() {
    Map<String, Map<String, Object>> jobs = CiWorkflow.jobs();
    assertThat(Objects.requireNonNull(jobs.get("release")).get("needs"))
        .asInstanceOf(list(String.class))
        .containsExactly("test");
  }

  @ParameterizedTest
  @ValueSource(strings = {"stand", "install"})
  void reRunsNoSlowJobOnTheMainPush(String job) {
    assertThat(String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("if")))
        .contains("github.event_name != 'push'");
  }

  @Test
  void publishesOnlyAfterTheTreeCheckOfTheMergeCommit() {
    String steps =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("release")).get("steps"));

    assertThat(steps.indexOf("provenTree")).isPositive().isLessThan(steps.indexOf("gateRelease"));
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
        .containsExactlyInAnyOrder("test", "heavy", "audit", "stand", "install", "release");
  }

  @Test
  void runsExactlyTheFourGroupsOnGitlab() {
    assertThat(CiWorkflow.gitlabJobs())
        .containsExactlyInAnyOrder("test", "audit", "heavy", "stand", "release");
  }

  @Test
  void startsOnGithubOnlyOnPushAndPullRequest() {
    assertThat(CiWorkflow.githubTriggers()).containsExactlyInAnyOrder("push", "pull_request");
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
  void runsTheHeavyJobOnPullRequestsOnly() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("heavy")).get("if"))
        .isEqualTo("github.event_name == 'pull_request'");
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
        .contains("github.event_name == 'pull_request' && fromJSON('[\"compose\",\"kind\"]')");
  }

  @ParameterizedTest
  @CsvSource({
    "test, ./gradlew --no-daemon gateTest",
    "heavy, scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy",
    "stand, ./gradlew --no-daemon gateStand -Pmodelfree -Pengine=",
    "install, ./gradlew --no-daemon gateInstall",
    "audit, ./gradlew --no-daemon gateScan -Paudit.base="
  })
  void runsEachPullRequestJobAsTheGateLineTheLocalCiRunsToo(String job, String line) {
    assertThat(String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps")))
        .contains("run=" + line);
    assertThat(SourceRoots.read(Path.of("scripts/linux-suite.sh")))
        .contains(job + ") echo '" + line);
  }

  @Test
  void scansLiveDataPerPushOnlyOnTheDependencyDiff() {
    Map<String, Object> audit = Objects.requireNonNull(CiWorkflow.jobs().get("audit"));

    assertThat(String.valueOf(audit.get("env")))
        .contains("format('origin/{0}', github.base_ref)")
        .contains("github.event.before");
    assertThat(String.valueOf(audit.get("steps")))
        .contains("run=./gradlew --no-daemon gateScan -Paudit.base=\"$AUDIT_BASE\"");
  }

  @Test
  void scansGitlabOnItsScheduleAndOnTheDependencyDiffThroughTheSameGate() {
    assertThat(CiWorkflow.gitlabText())
        .contains("audit:\n  stage: test")
        .contains("- if: $CI_PIPELINE_SOURCE == \"schedule\"")
        .contains("compare_to: refs/heads/main")
        .contains("- board/package-lock.json");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "heavy", "stand", "install"})
  void readsNoLiveVulnerabilityDataInTheOtherPerPushJobs(String job) {
    assertThat(String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps")))
        .doesNotContain("npm audit")
        .doesNotContain("vulnerability-scan.sh")
        .doesNotContain("gateScan");
  }

  @Test
  void runsTheNightlyScanWithNoBaseSoItAlwaysScans() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")))
        .contains("./gradlew --no-daemon gateScan")
        .doesNotContain("audit.base");
  }

  @Test
  void runsTheMacosInstallJobsGateNativelyInTheLocalCi() {
    assertThat(SourceRoots.read(Path.of("scripts/ci-local.sh")))
        .contains("jobs=${*:-test heavy stand install install-macos audit}")
        .contains("./gradlew --no-daemon gateInstall");
  }

  @Test
  void runsEveryLinuxJobLocallyWithItsOwnDockerEngineAndUntimedAsOnCi() {
    assertThat(SourceRoots.read(Path.of("scripts/linux-suite.sh")))
        .contains("--privileged")
        .contains("-e CI=true -e ORG_GRADLE_PROJECT_timed=false")
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
  void runsTheNightlyAsItsOwnWorkflowThatCallsNoPullRequestJob() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")))
        .contains("name: nightly")
        .contains("cron: '17 3 * * *'")
        .doesNotContain("uses: ./.github/workflows/ci.yml");
  }

  @Test
  void runsOnlyWhatTheLocalGateCannotInTheNightly() {
    assertThat(CiWorkflow.nightlyJobs().keySet())
        .containsExactlyInAnyOrder(
            "stand", "e2e", "properties", "pitest", "stryker", "scan", "install", "report");
  }

  @Test
  void mutatesEachProjectWithMutationTaskInItsOwnNightlyShard() {
    Map<?, ?> strategy =
        (Map<?, ?>)
            Objects.requireNonNull(
                Objects.requireNonNull(CiWorkflow.nightlyJobs().get("pitest")).get("strategy"));
    Map<?, ?> matrix = (Map<?, ?>) Objects.requireNonNull(strategy.get("matrix"));
    List<?> shards = (List<?>) Objects.requireNonNull(matrix.get("include"));
    Set<String> projects = new HashSet<>(List.of(":pitest"));
    Matcher included =
        Pattern.compile("(?m)^include '([^']+)'").matcher(SourceRoots.read(SETTINGS));
    while (included.find()) {
      projects.add(":" + included.group(1) + ":pitest");
    }

    Set<String> tasks =
        shards.stream()
            .map(shard -> String.valueOf(((Map<?, ?>) shard).get("task")))
            .collect(Collectors.toSet());

    assertThat(SourceRoots.read(PITEST))
        .contains("allprojects {")
        .contains("apply plugin: 'info.solidsoft.pitest'");
    assertThat(strategy.get("fail-fast")).isEqualTo(false);
    assertThat(shards).hasSameSizeAs(tasks);
    assertThat(tasks).isEqualTo(projects);
  }

  @Test
  void letsTheNightlyWriteIssuesOnItsReportJobAndReadElsewhere() {
    Map<?, ?> nightly =
        new Yaml().load(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")));
    Set<String> widened =
        CiWorkflow.nightlyJobs().entrySet().stream()
            .filter(job -> job.getValue().containsKey("permissions"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    assertThat(nightly.get("permissions")).isEqualTo(Map.of("contents", "read"));
    assertThat(widened).containsExactly("report");
    assertThat(Objects.requireNonNull(CiWorkflow.nightlyJobs().get("report")).get("permissions"))
        .isEqualTo(Map.of("issues", "write"));
  }

  @Test
  void opensAnIssueWhenAnyNightlyJobIsRed() {
    Map<String, Object> report = Objects.requireNonNull(CiWorkflow.nightlyJobs().get("report"));

    assertThat(report.get("if")).isEqualTo("failure()");
    assertThat(report.get("needs"))
        .asInstanceOf(list(String.class))
        .containsExactlyInAnyOrder(
            "stand", "e2e", "properties", "pitest", "stryker", "scan", "install");
  }

  @Test
  void verifiesTheRealFeedInstallsProvenanceWithTheRunsToken() {
    Map<String, Object> install = Objects.requireNonNull(CiWorkflow.nightlyJobs().get("install"));

    assertThat(install.get("env")).isEqualTo(Map.of("GH_TOKEN", "${{ github.token }}"));
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
  @ValueSource(strings = {"test", "heavy", "audit", "stand", "install", "release"})
  void keepsGradleCachesBetweenRunsByThePinnedSetupGradleOnItsOpenSourceProvider(String job) {
    String steps = String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps"));

    assertThat(steps)
        .contains("gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c")
        .contains("cache-provider=external")
        .doesNotContain("cache-provider=basic")
        .doesNotContain("cache-read-only");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "heavy", "audit", "stand", "install", "release"})
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
  void buildsTheCiImageFromItsDockerfileBesideTheOtherBaseImages() {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/base-images.yml")))
        .contains("name: ci\n      dockerfile: Dockerfile.ci");
    assertThat(SourceRoots.read(Path.of("../base/Dockerfile.ci")))
        .containsPattern("(?m)^FROM eclipse-temurin:25-jdk-noble@sha256:[0-9a-f]{64}$")
        .containsPattern("(?m)^FROM node:22\\S+@sha256:[0-9a-f]{64} AS node$")
        .contains("linux-test-deps.sh");
  }

  @ParameterizedTest
  @CsvSource({"base, Dockerfile", "stand-base, Dockerfile.stand", "ci, Dockerfile.ci"})
  void scansEachBaseImageTheRegistryWorkflowPublishesInTheLocalAudit(
      String name, String dockerfile) {
    assertThat(SourceRoots.read(Path.of("../.github/workflows/base-images.yml")))
        .contains("name: " + name + "\n      dockerfile: " + dockerfile);
    assertThat(
            SourceRoots.read(
                Path.of("build-logic/src/main/java/dev/jagt/gradle/audit/BaseImages.java")))
        .contains("new Image(\"" + name + "\", \"" + dockerfile + "\"");
    assertThat(SourceRoots.read(Path.of("scripts/ci-local.sh")))
        .contains("audit) images=1")
        .contains("./gradlew --no-daemon baseImageScan -Paudit.base=origin/main");
    assertThat(SourceRoots.read(Path.of("../.github/workflows/base-image.yml")))
        .contains("scripts/vulnerability-scan.sh scan image");
  }

  @Test
  void runsTheLocalGateInTheImageCiPinsAndBuildsItWhenItsDigestIsNotPullable() {
    assertThat(SourceRoots.read(Path.of("scripts/linux-suite.sh")))
        .contains("workflows/ci.yml")
        .contains("docker pull")
        .contains("base/Dockerfile.ci");
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

    assertThat(scan).contains("grep -Evq '[~+]esm'");
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
  void scansTheReleaseSbomForKnownVulnerabilitiesInGitlabsAuditJobThroughTheSharedGate() {
    Map<?, ?> pipeline = new Yaml().load(CiWorkflow.gitlabText());
    Map<?, ?> audit = (Map<?, ?>) Objects.requireNonNull(pipeline.get("audit"));

    assertThat(String.valueOf(audit.get("script"))).contains("gateScan");
  }

  @Test
  void scansTheReleaseSbomOnEveryEngineThroughTheSharedScriptNotTheAction() {
    assertThat(SourceRoots.read(GATES))
        .contains("gateScan: ['boardAudit', 'vulnerabilityScan'],")
        .contains("file('scripts/vulnerability-scan.sh')");
    assertThat(SourceRoots.read(Path.of("../.github/workflows/ci.yml")))
        .doesNotContain("osv-scanner-action");
    assertThat(SourceRoots.read(Path.of("../.github/workflows/nightly.yml")))
        .contains("./gradlew --no-daemon gateScan")
        .doesNotContain("osv-scanner-action");
    assertThat(CiWorkflow.gitlabText()).doesNotContain("osv-scanner-action");
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

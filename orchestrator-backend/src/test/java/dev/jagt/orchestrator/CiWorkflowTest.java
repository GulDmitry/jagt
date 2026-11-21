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
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

class CiWorkflowTest {

  private static final Path CODEQL = Path.of("../.github/workflows/codeql.yml");

  @Test
  void publishesReleaseOnlyOnceEveryOtherJobIsGreen() {
    Map<String, Map<String, Object>> jobs = CiWorkflow.jobs();
    Set<String> others =
        jobs.keySet().stream()
            .filter(job -> !job.equals("release") && !job.equals("pages"))
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
  void startsOnGithubOnlyOnPushPullRequestWorkflowCallAndTag() {
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
  void runsTheHeavyJobOnScheduleTagAndPullRequest() {
    assertThat(Objects.requireNonNull(CiWorkflow.jobs().get("heavy")).get("if"))
        .isEqualTo(
            "inputs.nightly || github.event_name == 'pull_request'"
                + " || startsWith(github.ref, 'refs/tags/v')");
  }

  @Test
  void runsTheStandOnEveryEngineForPullRequests() {
    assertThat(
            String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("stand")).get("strategy")))
        .contains(
            "(inputs.nightly || github.event_name == 'pull_request')"
                + " && fromJSON('[\"compose\",\"kind\"]')");
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
  void startsTheNightlyOffTheHourAndLogsItsStartDelayAsNotice() {
    String nightly = SourceRoots.read(Path.of("../.github/workflows/nightly.yml"));

    assertThat(nightly)
        .doesNotContainPattern("cron: '0 ")
        .contains("actions/runs/$GITHUB_RUN_ID")
        .contains("--jq .created_at")
        .contains("::notice title=nightly start delay::event=nightly.delay")
        .contains("delay_seconds=$delay");
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
        .contains("cyclonedxBom")
        .contains("build/reports/sbom/jagt.cdx.json")
        .contains("jagt-$version.cdx.json")
        .contains("gh release upload \"$GITHUB_REF_NAME\" build/release/*");
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
  void runsTheInstallCheckScriptOnTheBuiltBootJar() {
    String install = Objects.requireNonNull(CiWorkflow.jobs().get("install")).toString();

    assertThat(install)
        .contains("bootJar")
        .contains("scripts/install-check.sh build/libs/jagt.jar");
  }

  @Test
  void publishesTheImageWithItsSbom() {
    assertThat(SourceRoots.read(Path.of("scripts/publish-image.sh"))).contains("--sbom=true");
  }

  @Test
  void attachesTheSbomToTheGitlabReleaseToo() {
    assertThat(CiWorkflow.gitlabText())
        .contains("bootJar cyclonedxBom")
        .contains("orchestrator-backend/build/reports/sbom/jagt.cdx.json");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "heavy", "stand", "release"})
  void keepsGradleCachesBetweenRunsByThePinnedSetupGradleOnItsOpenSourceProvider(String job) {
    String steps = String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get(job)).get("steps"));

    assertThat(steps)
        .contains("gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c")
        .contains("cache-provider=basic")
        .doesNotContain("cache=gradle");
  }

  @Test
  void signsProvenanceOfTheJarAndTheImageByOnePinnedAction() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .contains("actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8")
        .contains("subject-path=orchestrator-backend/build/release/*.jar")
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
  void deletesTheImageAndThePagesDeploymentsOfEveryReleaseTheRetentionDeletes() {
    String release = Objects.requireNonNull(CiWorkflow.jobs().get("release")).toString();

    assertThat(release)
        .containsPattern("dataaxiom/ghcr-cleanup-action@[0-9a-f]{40}")
        .contains("delete-tags=${{ steps.retention.outputs.image-tags }}")
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
  void failsTheStandJobWhenOneTimingPassesTwiceItsHistoryKeptInTheActionsCache() {
    String stand =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("stand")).get("steps"));

    assertThat(stand)
        .containsPattern("benchmark-action/github-action-benchmark@[0-9a-f]{40}")
        .contains("tool=customSmallerIsBetter")
        .contains(
            "external-data-json-path=orchestrator-backend/stand/build/stand/timings-history.json")
        .contains("fail-on-alert=true")
        .contains("alert-threshold=200%")
        .contains("-Prunner=ubuntu-24.04")
        .doesNotContain("auto-push")
        .doesNotContain("gh-pages");
  }

  @Test
  void scansTheTestJobForSecretsInTheTreeAndFullHistoryOnBothCiEngines() {
    String steps =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("test")).get("steps"));

    assertThat(steps)
        .contains("fetch-depth=0")
        .contains("orchestrator-backend/scripts/secret-scan.sh");
    assertThat(CiWorkflow.gitlabText())
        .contains("GIT_DEPTH: \"0\"")
        .contains("orchestrator-backend/scripts/secret-scan.sh");
  }

  @Test
  void scansTheReleaseSbomForKnownVulnerabilitiesInGitlabsTestJobThroughTheSharedScript() {
    Map<?, ?> pipeline = new Yaml().load(CiWorkflow.gitlabText());
    Map<?, ?> test = (Map<?, ?>) Objects.requireNonNull(pipeline.get("test"));

    assertThat(String.valueOf(test.get("script"))).contains("scripts/vulnerability-scan.sh");
  }

  @Test
  void scansTheReleaseSbomInGithubsTestJobThroughTheSharedScriptNotTheAction() {
    String steps =
        String.valueOf(Objects.requireNonNull(CiWorkflow.jobs().get("test")).get("steps"));

    assertThat(steps)
        .contains("run=scripts/vulnerability-scan.sh")
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

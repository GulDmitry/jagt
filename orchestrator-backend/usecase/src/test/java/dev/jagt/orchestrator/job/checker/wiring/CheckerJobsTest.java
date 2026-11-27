package dev.jagt.orchestrator.job.checker.wiring;

import static dev.jagt.orchestrator.job.TestAcceptanceVerdictBuilder.acceptanceVerdict;
import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.job.checker.CheckerOutcomes;
import dev.jagt.orchestrator.job.checker.CheckerReads;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.role.RoleSettings;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckerJobsTest {

  private final ConfigService config = mock(ConfigService.class);
  private final CheckerReaders readers = mock(CheckerReaders.class, RETURNS_DEEP_STUBS);
  private final CheckerActs acts = mock(CheckerActs.class, RETURNS_DEEP_STUBS);
  private final TicketTexts tickets = mock(TicketTexts.class);

  @ParameterizedTest
  @ValueSource(
      strings = {"[audit, audit-again, audit-decline]", "[audit, audit-decline]", "[audit]"})
  void everyInstalledCheckerRunsItsOwnJobsBesideAcceptance(String readSteps, @TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.createDirectories(root.resolve("roles/audit"));
    Files.writeString(
        root.resolve("roles/audit/role.yml"),
        Files.readString(Path.of("src/test/resources/checker-fixture/roles/audit/role.yml"))
            .replace("[audit, audit-again, audit-decline]", readSteps));
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    List<RoleKit> kits =
        new InstalledRoles(paths)
            .definitions().stream()
                .map(role ->
                    new RoleKit(
                        new RoleSettings(role, config, paths), new RoleFiles(paths, role.id())))
                .toList();

    List<TaskJob> jobs =
        new CheckerJobs(
                new RoleKits(kits),
                readers,
                acts,
                new CheckerLedgers(paths, new FakeClock(), tickets),
                url -> true)
            .jobs();

    assertThat(jobs)
        .extracting(TaskJob::id)
        .containsExactlyInAnyOrder(
            "acceptance-check",
            "acceptance-wait",
            "acceptance-decline",
            "audit-check",
            "audit-wait",
            "audit-decline");
  }

  @Test
  void theSecondCheckersCheckReadsItsOwnStepAndEndsInItsOwnOutcomesAtDeploy(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("roles/audit"));
    Files.copy(
        Path.of("src/test/resources/checker-fixture/roles/audit/role.yml"),
        root.resolve("roles/audit/role.yml"));
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withRoles(Map.of("audit", new ConfigFile.RoleConfig(true, null))));
    List<RoleKit> kits =
        new InstalledRoles(paths)
            .definitions().stream()
                .map(role ->
                    new RoleKit(
                        new RoleSettings(role, config, paths), new RoleFiles(paths, role.id())))
                .toList();
    RoleKit auditKit = kits.getFirst();
    CheckerReads reads = readers.reads(auditKit);
    CheckerOutcomes outcomes = acts.outcomes(auditKit);
    when(reads.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(acceptanceVerdict().build()), TokenUsage.NONE));
    when(outcomes.apply(any(), any(), any())).thenReturn(AcceptanceEnding.PASSED);
    TaskJob audit =
        new CheckerJobs(
                new RoleKits(kits),
                readers,
                acts,
                new CheckerLedgers(paths, new FakeClock(), tickets),
                url -> true)
            .jobs().stream()
                .filter(job -> job.id().equals("audit-check"))
                .findFirst()
                .orElseThrow();

    audit.run("ABC-42", askedDeploy().build());

    verify(reads).check(eq("ABC-42"), eq("audit"), eq("digest"), any());
    verify(outcomes).apply(eq("ABC-42"), any(), any());
  }
}

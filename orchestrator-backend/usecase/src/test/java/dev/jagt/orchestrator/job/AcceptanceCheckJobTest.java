package dev.jagt.orchestrator.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceCheckJobTest {

  private static final ConfigFile ON =
      ConfigFile.defaults()
          .withRoles(
              Map.of(
                  "acceptance",
                  new ConfigFile.RoleConfig(
                      true,
                      null,
                      List.of(
                          new ConfigFile.AcceptanceEnvironment("https://stage.example.com", null)),
                      null)));

  private static TaskState deployed(String askedDigest) {
    return TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
        .build()
        .withDeployment(
            new Deployment(
                1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                1,
                askedDigest));
  }

  private static AcceptanceVerdict met() {
    return new AcceptanceVerdict(
        "proj@0123456789abcdef",
        "",
        List.of(
            new AcceptanceVerdict.Row("login works", AcceptanceVerdict.Status.MET, "GET /me 200")),
        "",
        "",
        AcceptanceVerdict.Flows.none());
  }

  private static AcceptanceVerdict notMet() {
    return new AcceptanceVerdict(
        "proj@0123456789abcdef",
        "",
        List.of(
            new AcceptanceVerdict.Row(
                "list is sorted", AcceptanceVerdict.Status.NOT_MET, "GET /list unsorted")),
        "",
        "",
        AcceptanceVerdict.Flows.none());
  }

  @Test
  void checksNothingWhereAcceptanceIsOff(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run("ABC-42", deployed("digest"));

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void waitsForTheDeploysPipelineToBeGreenBeforeItChecks(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load()).thenReturn(ON);
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run("ABC-42", deployed(null));

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void checksADeployOnceThenLeavesItAloneAcrossARestart(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load()).thenReturn(ON);
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
    when(outcomes.apply(any(), any(), any()))
        .thenReturn(dev.jagt.orchestrator.task.AcceptanceEnding.PASSED);
    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            outcomes)
        .run("ABC-42", deployed("digest"));

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            outcomes)
        .run("ABC-42", deployed("digest"));

    verify(reader, times(1)).check(any(), any(), any(), any());
  }

  @Test
  void sendsARowNotMetBackToTheSession(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
    when(config.load()).thenReturn(ON);
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(notMet()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    TaskState task = deployed("digest");

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            outcomes)
        .run("ABC-42", task);

    verify(outcomes).apply(eq("ABC-42"), eq(task), eq(notMet()));
  }

  @Test
  void handsAMetVerdictToTheOutcomesToMarkPassed(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
    when(config.load()).thenReturn(ON);
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            outcomes)
        .run("ABC-42", deployed("digest"));

    verify(outcomes).apply(eq("ABC-42"), any(), eq(met()));
  }

  @Test
  void runsACheckAgainOnTheSameShaWhereItsTurnWasLeftOpenUnanswered(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/brief.md.dist"),
        root.resolve("roles/acceptance/brief.md.dist"));
    ConfigService config = mock(ConfigService.class);
    CheckerReads reads = mock(CheckerReads.class);
    when(config.load()).thenReturn(ON);
    when(reads.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    TaskState restarted = TaskReducer.relayed(deployed("digest"), "accept", "digest", "", 1);

    new CheckerCheckJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reads,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run("ABC-42", restarted);

    verify(reads)
        .check(
            eq("ABC-42"),
            eq("again"),
            eq("digest"),
            argThat(
                run ->
                    run.prompt().contains("The same check on the same sha")
                        && run.sha().equals("proj@0123456789abcdef")));
  }

  @Test
  void opensWithNoEnvironmentNamedWhereTheRolesTriggerOmitsEnvironmentNamed(@TempDir Path root)
      throws IOException {
    Files.writeString(
        root.resolve("role.yml"),
        "id: acceptance\n"
            + "name: acceptance\n"
            + "model: sonnet\n"
            + "trigger: [deployed, pipelines-green]\n"
            + "oncePer: deploy\n"
            + "outcomes: [round]\n"
            + "tools:\n"
            + "  shell: [\"git log*\"]\n"
            + "answer:\n"
            + "  - required: failure\n"
            + "decline:\n"
            + "  - required: failure\n");
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withRoles(
                    Map.of("acceptance", new ConfigFile.RoleConfig(true, null, List.of(), null))));
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleDefinition role = RoleDefinitions.fromFile(root.resolve("role.yml"));

    new CheckerCheckJob(
            new RoleSettings(role, config),
            new CheckerPrompts(role, new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run("ABC-42", deployed("digest"));

    verify(reader).check(any(), any(), any(), any());
  }

  @Test
  void opensNothingWithNoEnvironmentNamedWhereTheRolesTriggerNamesIt(@TempDir Path root)
      throws IOException {
    Files.writeString(
        root.resolve("role.yml"),
        "id: acceptance\n"
            + "name: acceptance\n"
            + "model: sonnet\n"
            + "trigger: [deployed, pipelines-green, environment-named]\n"
            + "oncePer: deploy\n"
            + "outcomes: [round]\n"
            + "tools:\n"
            + "  shell: [\"git log*\"]\n"
            + "answer:\n"
            + "  - required: failure\n"
            + "decline:\n"
            + "  - required: failure\n");
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withRoles(
                    Map.of("acceptance", new ConfigFile.RoleConfig(true, null, List.of(), null))));
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleDefinition role = RoleDefinitions.fromFile(root.resolve("role.yml"));

    new CheckerCheckJob(
            new RoleSettings(role, config),
            new CheckerPrompts(role, new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run("ABC-42", deployed("digest"));

    verify(reader, never()).check(any(), any(), any(), any());
  }

  @Test
  void opensOnATaskNoLongerOpenWhereTheRolesTriggerOmitsTrackerOpen(@TempDir Path root)
      throws IOException {
    Files.writeString(
        root.resolve("role.yml"),
        "id: acceptance\n"
            + "name: acceptance\n"
            + "model: sonnet\n"
            + "trigger: [deployed, pipelines-green, environment-named]\n"
            + "oncePer: deploy\n"
            + "outcomes: [round]\n"
            + "tools:\n"
            + "  shell: [\"git log*\"]\n"
            + "answer:\n"
            + "  - required: failure\n"
            + "decline:\n"
            + "  - required: failure\n");
    ConfigService config = mock(ConfigService.class);
    CheckerReads reader = mock(CheckerReads.class);
    when(config.load()).thenReturn(ON);
    when(reader.check(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleDefinition role = RoleDefinitions.fromFile(root.resolve("role.yml"));

    new CheckerCheckJob(
            new RoleSettings(role, config),
            new CheckerPrompts(role, new RoleFiles(paths, "acceptance")),
            reader,
            new CheckerRecords(paths, Clock.systemUTC()),
            mock(CheckerOutcomes.class))
        .run(
            "ABC-42",
            TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .build()
                .withDeployment(
                    new Deployment(
                        1,
                        List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                        1,
                        "digest")));

    verify(reader).check(any(), any(), any(), any());
  }
}

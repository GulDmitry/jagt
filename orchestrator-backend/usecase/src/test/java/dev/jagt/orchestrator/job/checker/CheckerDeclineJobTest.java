package dev.jagt.orchestrator.job.checker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.role.RoleSettings;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CheckerDeclineJobTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private static final ConfigFile ON =
      ConfigFile.defaults().withRoles(Map.of("acceptance", new ConfigFile.RoleConfig(true, null)));

  private static final TaskState DECLINED =
      TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
          .report(AgentReport.NO_CHANGES)
          .message("the list is sorted by name, as the ticket says")
          .build()
          .withDeployment(
              new Deployment(
                  1,
                  List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                  1,
                  "digest"))
          .withRole(
              Checkers.ACCEPTANCE, RoleRecord.defaults().withRound("digest", "- list is sorted"));

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final CheckerReads reader = mock(CheckerReads.class);
  private final CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
  private CheckerRecords records;
  private CheckerDeclineJob job;

  @BeforeEach
  void anAcceptanceRoleWatchingOneEnvironment() throws IOException {
    when(config.load()).thenReturn(ON);
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "environments: [{url: \"https://stage.example.com\"}]\n");
    records = new CheckerRecords(paths, Clock.systemUTC(), "acceptance");
    job =
        new CheckerDeclineJob(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                paths),
            new CheckerPrompts(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new RoleFiles(paths, "acceptance")),
            reader,
            records,
            outcomes);
  }

  @Test
  void readsTheSessionsReasonBackToAcceptanceOnceItRepliedToTheRound() {
    AcceptanceDecline accepted = new AcceptanceDecline("proj@0123456789abcdef", "", "");
    when(reader.decline(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.NOT_MET);

    job.run(
        "ABC-42",
        DECLINED.withSession(
            Session.startedOver(Session.defaults())
                .withReportedAt(System.currentTimeMillis() + 1000)));

    verify(outcomes).declined(eq("ABC-42"), eq(accepted), eq(false));
  }

  @Test
  void readsNothingBeforeTheSessionRepliedToTheRound() {
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.NOT_MET);

    job.run(
        "ABC-42", DECLINED.withSession(Session.startedOver(Session.defaults()).withReportedAt(1)));

    verify(reader, never()).decline(any(), any(), any(), any());
  }

  @Test
  void readsNothingOnceTheDisputeWentToTheHuman() {
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.ESCALATED);

    job.run(
        "ABC-42",
        DECLINED.withSession(
            Session.startedOver(Session.defaults())
                .withReportedAt(System.currentTimeMillis() + 1000)));

    verify(reader, never()).decline(any(), any(), any(), any());
  }

  @Test
  void marksTheSecondReadAsAnotherDisputeWhereOneStandsAlready() {
    AcceptanceDecline disputed =
        new AcceptanceDecline("proj@0123456789abcdef", "", "still unsorted");
    when(reader.decline(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(disputed), TokenUsage.NONE));
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.DISPUTED);

    job.run(
        "ABC-42",
        DECLINED.withSession(
            Session.startedOver(Session.defaults())
                .withReportedAt(System.currentTimeMillis() + 1000)));

    verify(outcomes).declined(eq("ABC-42"), eq(disputed), eq(true));
  }

  @Test
  void readsTheDeclineAgainAfterRestartDuringItsRead() {
    AcceptanceDecline accepted = new AcceptanceDecline("proj@0123456789abcdef", "", "");
    when(reader.decline(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.NOT_MET);
    TaskState reading =
        reducer.relayed(
            DECLINED.withSession(
                Session.startedOver(Session.defaults())
                    .withReportedAt(System.currentTimeMillis() + 1000)),
            "decline",
            "digest",
            "",
            1);

    job.run("ABC-42", reading);

    verify(reader).decline(eq("ABC-42"), eq(AcceptanceVerdict.AGAIN), any(), any());
  }

  @Test
  void readsTheDeclineAgainAfterRestartOnStateFileWithTheReadOpenAsTurn() {
    AcceptanceDecline accepted = new AcceptanceDecline("proj@0123456789abcdef", "", "");
    when(reader.decline(any(), any(), any(), any()))
        .thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
    records.put("ABC-42", "digest", "proj@0123456789abcdef", AcceptanceEnding.NOT_MET);
    String json =
        """
        {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,"report":"NO_CHANGES",
         "message":"the list is sorted by name, as the ticket says",
         "repos":[{"project":"proj","worktreePath":"/wt"}],
         "turn":{"step":"decline","id":3,"digest":"digest","since":4,
                 "marks":{"qa:digest":"digest","qa:rows":"- list is sorted"}}}
        """;
    TaskState legacy =
        new JsonMapper()
            .readValue(json, TaskState.class)
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"))
            .withSession(
                Session.startedOver(Session.defaults())
                    .withReportedAt(System.currentTimeMillis() + 1000));

    job.run("ABC-42", legacy);

    verify(reader).decline(eq("ABC-42"), eq(AcceptanceVerdict.AGAIN), any(), any());
  }
}

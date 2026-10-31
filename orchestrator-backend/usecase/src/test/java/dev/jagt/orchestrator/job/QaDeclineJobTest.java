package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.RoleSettings;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.RoleRecord;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaDeclineJobTest {

    private static final ConfigFile ON = ConfigFile.defaults().withQa(new ConfigFile.QaConfig(true, null,
            List.of(new ConfigFile.QaEnvironment("https://stage.example.com", null))));

    private static TaskState declined(long reportedAt) {
        return TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).report(AgentReport.NO_CHANGES)
                .message("the list is sorted by name, as the ticket says").build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev")
                        .withMerge("0123456789abcdef")), 1, "digest"))
                .withRole(Checkers.QA, RoleRecord.defaults().withRound("digest", "- list is sorted"))
                .withSession(Session.startedOver(Session.defaults()).withReportedAt(reportedAt));
    }

    @Test
    void readsTheSessionsReasonBackToQaOnceItRepliedToTheRound(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline accepted = new QaDecline("proj@0123456789abcdef", "", "");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, outcomes)
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(outcomes).declined(eq("ABC-42"), eq(accepted), eq(false));
    }

    @Test
    void readsNothingBeforeTheSessionRepliedToTheRound(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        when(config.load()).thenReturn(ON);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, mock(CheckerOutcomes.class))
                .run("ABC-42", declined(1));

        verify(reader, never()).decline(any(), any(), any(), any());
    }

    @Test
    void readsNothingOnceTheDisputeWentToTheHuman(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        when(config.load()).thenReturn(ON);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.ESCALATED);

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, mock(CheckerOutcomes.class))
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(reader, never()).decline(any(), any(), any(), any());
    }

    @Test
    void marksTheSecondReadAsAnotherDisputeWhereOneStandsAlready(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline disputed = new QaDecline("proj@0123456789abcdef", "", "still unsorted");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(disputed), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.DISPUTED);

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, outcomes)
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(outcomes).declined(eq("ABC-42"), eq(disputed), eq(true));
    }

    @Test
    void readsTheDeclineAgainAfterARestartDuringItsRead(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline accepted = new QaDecline("proj@0123456789abcdef", "", "");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);
        TaskState reading = TaskReducer.relayed(declined(System.currentTimeMillis() + 1000), "decline", "digest", "", 1);

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, outcomes).run("ABC-42", reading);

        verify(reader).decline(eq("ABC-42"), eq(QaVerdict.AGAIN), any(), any());
    }

    @Test
    void readsTheDeclineAgainAfterARestartOnAStateFileWithTheReadOpenAsATurn(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        CheckerReads reader = mock(CheckerReads.class);
        CheckerOutcomes outcomes = mock(CheckerOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline accepted = new QaDecline("proj@0123456789abcdef", "", "");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        CheckerRecords records = new CheckerRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);
        String json = """
                {"status":"DEPLOYED","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,"report":"NO_CHANGES",
                 "message":"the list is sorted by name, as the ticket says",
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"decline","id":3,"digest":"digest","since":4,
                         "marks":{"qa:digest":"digest","qa:rows":"- list is sorted"}}}
                """;
        TaskState legacy = new JsonMapper().readValue(json, TaskState.class)
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev")
                        .withMerge("0123456789abcdef")), 1, "digest"))
                .withSession(Session.startedOver(Session.defaults()).withReportedAt(System.currentTimeMillis() + 1000));

        new CheckerDeclineJob(new RoleSettings(RoleDefinition.QA, config), new CheckerPrompts(new QaBrief(paths)), reader, records, outcomes).run("ABC-42", legacy);

        verify(reader).decline(eq("ABC-42"), eq(QaVerdict.AGAIN), any(), any());
    }
}

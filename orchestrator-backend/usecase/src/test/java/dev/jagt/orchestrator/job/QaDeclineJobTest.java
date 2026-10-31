package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.Qa;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                .withQa(Qa.defaults().withRound("digest", "- list is sorted"))
                .withSession(Session.startedOver().withReportedAt(reportedAt));
    }

    @Test
    void readsTheSessionsReasonBackToQaOnceItRepliedToTheRound(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        QaOutcomes outcomes = mock(QaOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline accepted = new QaDecline("proj@0123456789abcdef", "", "");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(accepted), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        QaRecords records = new QaRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);

        new QaDeclineJob(config, new QaPrompts(new QaBrief(paths)), reader, records, outcomes)
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(outcomes).declined(eq("ABC-42"), eq(accepted), eq(false));
    }

    @Test
    void readsNothingBeforeTheSessionRepliedToTheRound(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        when(config.load()).thenReturn(ON);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        QaRecords records = new QaRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.NOT_MET);

        new QaDeclineJob(config, new QaPrompts(new QaBrief(paths)), reader, records, mock(QaOutcomes.class))
                .run("ABC-42", declined(1));

        verify(reader, never()).decline(any(), any(), any(), any());
    }

    @Test
    void readsNothingOnceTheDisputeWentToTheHuman(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        when(config.load()).thenReturn(ON);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        QaRecords records = new QaRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.ESCALATED);

        new QaDeclineJob(config, new QaPrompts(new QaBrief(paths)), reader, records, mock(QaOutcomes.class))
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(reader, never()).decline(any(), any(), any(), any());
    }

    @Test
    void marksTheSecondReadAsAnotherDisputeWhereOneStandsAlready(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        QaOutcomes outcomes = mock(QaOutcomes.class);
        when(config.load()).thenReturn(ON);
        QaDecline disputed = new QaDecline("proj@0123456789abcdef", "", "still unsorted");
        when(reader.decline(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(disputed), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        QaRecords records = new QaRecords(paths, Clock.systemUTC());
        records.put("ABC-42", "digest", "proj@0123456789abcdef", QaEnding.DISPUTED);

        new QaDeclineJob(config, new QaPrompts(new QaBrief(paths)), reader, records, outcomes)
                .run("ABC-42", declined(System.currentTimeMillis() + 1000));

        verify(outcomes).declined(eq("ABC-42"), eq(disputed), eq(true));
    }
}

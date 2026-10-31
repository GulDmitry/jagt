package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaCheckJobTest {

    private static final ConfigFile ON = ConfigFile.defaults().withQa(new ConfigFile.QaConfig(true, null,
            List.of(new ConfigFile.QaEnvironment("https://stage.example.com", null))));

    private static TaskState deployed(String askedDigest) {
        return TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build().withDeployment(new Deployment(1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, askedDigest));
    }

    private static QaVerdict met() {
        return new QaVerdict("proj@0123456789abcdef", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.MET, "GET /me 200")), "", "");
    }

    private static QaVerdict notMet() {
        return new QaVerdict("proj@0123456789abcdef", "", List.of(
                new QaVerdict.Row("list is sorted", QaVerdict.Status.NOT_MET, "GET /list unsorted")), "", "");
    }

    @Test
    void checksNothingWhereQaIsOff(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        when(config.load()).thenReturn(ConfigFile.defaults());
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                mock(QaOutcomes.class)).run("ABC-42", deployed("digest"));

        verify(reader, never()).check(any(), any(), any(), any());
    }

    @Test
    void waitsForTheDeploysPipelineToBeGreenBeforeItChecks(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        when(config.load()).thenReturn(ON);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                mock(QaOutcomes.class)).run("ABC-42", deployed(null));

        verify(reader, never()).check(any(), any(), any(), any());
    }

    @Test
    void checksADeployOnceThenLeavesItAloneAcrossARestart(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        when(config.load()).thenReturn(ON);
        when(reader.check(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        QaOutcomes outcomes = mock(QaOutcomes.class);
        when(outcomes.apply(any(), any(), any())).thenReturn(dev.jagt.orchestrator.task.QaEnding.PASSED);
        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                outcomes).run("ABC-42", deployed("digest"));

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                outcomes).run("ABC-42", deployed("digest"));

        verify(reader, times(1)).check(any(), any(), any(), any());
    }

    @Test
    void sendsARowNotMetBackToTheSession(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        QaOutcomes outcomes = mock(QaOutcomes.class);
        when(config.load()).thenReturn(ON);
        when(reader.check(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(notMet()), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        TaskState task = deployed("digest");

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                outcomes).run("ABC-42", task);

        verify(outcomes).apply(eq("ABC-42"), eq(task), eq(notMet()));
    }

    @Test
    void handsAMetVerdictToTheOutcomesToMarkPassed(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        QaReads reader = mock(QaReads.class);
        QaOutcomes outcomes = mock(QaOutcomes.class);
        when(config.load()).thenReturn(ON);
        when(reader.check(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reader, new QaRecords(paths, Clock.systemUTC()),
                outcomes).run("ABC-42", deployed("digest"));

        verify(outcomes).apply(eq("ABC-42"), any(), eq(met()));
    }

    @Test
    void runsACheckAgainOnTheSameShaWhereItsTurnWasLeftOpenUnanswered(@TempDir Path root) throws IOException {
        Files.copy(Path.of("../../qa-brief.md.dist"), root.resolve("qa-brief.md.dist"));
        ConfigService config = mock(ConfigService.class);
        QaReads reads = mock(QaReads.class);
        when(config.load()).thenReturn(ON);
        when(reads.check(any(), any(), any(), any())).thenReturn(new Answer<>(Optional.of(met()), TokenUsage.NONE));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
        TaskState restarted = TaskReducer.relayed(deployed("digest"), "accept", "digest", "", 1);

        new QaCheckJob(config, new QaPrompts(new QaBrief(paths)), reads, new QaRecords(paths, Clock.systemUTC()),
                mock(QaOutcomes.class)).run("ABC-42", restarted);

        verify(reads).check(eq("ABC-42"), eq("again"), eq("digest"), argThat(run ->
                run.prompt().contains("The same check on the same sha") && run.sha().equals("proj@0123456789abcdef")));
    }
}

package dev.jagt.orchestrator.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.HumanWait;
import dev.jagt.orchestrator.task.HumanWait.Why;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HumanWaitRecorderTest {

    @ResourceLock(Resources.GLOBAL)
    @Test
    void recordsTheIntervalAQuestionHoldsTheHumanAndLogsItOnceTheSessionIsAnswered(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        state.putTask("ABC-77", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(HumanWaitRecorder.class)).addAppender(log);
        new HumanWaitRecorder(state, new Rounds(config, new MasterReview(state)),
                Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)).follow();

        state.updateTask("ABC-77", task -> task.withReport(AgentReport.QUESTION));
        state.updateTask("ABC-77", task -> task.withReport(AgentReport.PLAIN));

        assertThat(state.task("ABC-77").orElseThrow().waits()).containsExactly(new HumanWait(5_000, 5_000, Why.QUESTION));
        assertThat(List.copyOf(log.list)).filteredOn(event -> "human wait closed".equals(event.getMessage())
                        && event.getKeyValuePairs().contains(new KeyValuePair("task", "ABC-77")))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("task", "ABC-77"), new KeyValuePair("why", Why.QUESTION),
                        new KeyValuePair("millis", 0L));
    }

    @Test
    void keepsAnIntervalOpenAcrossARestartWhileTheHumanStillHoldsTheTurn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION)
                .waits(List.of(new HumanWait(1_000, 0, Why.QUESTION))).build());

        new HumanWaitRecorder(state, new Rounds(config, new MasterReview(state)),
                Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)).follow();

        assertThat(state.task("ABC-42").orElseThrow().waits()).containsExactly(new HumanWait(1_000, 0, Why.QUESTION));
    }

    @Test
    void closesAnIntervalAfterARestartOnceTheTurnHasLeftTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .waits(List.of(new HumanWait(1_000, 0, Why.QUESTION))).build());

        new HumanWaitRecorder(state, new Rounds(config, new MasterReview(state)),
                Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)).follow();

        assertThat(state.task("ABC-42").orElseThrow().waits()).containsExactly(new HumanWait(1_000, 9_000, Why.QUESTION));
    }
}

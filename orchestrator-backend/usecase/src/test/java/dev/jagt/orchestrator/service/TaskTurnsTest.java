package dev.jagt.orchestrator.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTurnsTest {

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsTheStepAndTheTurnEachRelayOpens(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(TaskTurns.class)).addAppender(log);

        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2");

        assertThat(List.copyOf(log.list)).filteredOn(event -> "turn opened".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("task", "ABC-42"), new KeyValuePair("step", "deploy"),
                        new KeyValuePair("turn", 1L));
    }
}

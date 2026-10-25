package dev.jagt.orchestrator.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
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

        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.DEPLOYED);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "turn opened".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("task", "ABC-42"), new KeyValuePair("step", "deploy"),
                        new KeyValuePair("turn", 1L));
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsTheStatusTheDoorAdmittedTheStepAtNotTheOneTheTaskHoldsByNow(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(TaskTurns.class)).addAppender(log);

        new TaskTurns(state, Clock.systemUTC()).opened("ABC-42", "deploy", "a1b2", TaskStatus.REVIEWED);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "turn opened".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("status", TaskStatus.REVIEWED));
    }

    @Test
    void leavesTheStepToTheHumanWhereTheMasterOfferedItInsteadOfPressingIt() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
                .turn(Turn.none().withMark(TaskTurns.LEFT, "deploy src/Login.java")).build();

        assertThat(TaskTurns.leftToTheHuman(task)).isTrue();
    }

    @Test
    void leavesAQuestionToTheHumanOnceTheMasterGaveItNoUsableAnswer() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION)
                .message("which cache").build();
        TaskState unanswered = asking.withTurn(Turn.none().withMark(TaskTurns.UNANSWERED, TaskTurns.asked(asking)));

        assertThat(TaskTurns.leftToTheHuman(unanswered)).isTrue();
    }

    @Test
    void leavesAQuestionToTheMasterBeforeItHasFailedToAnswerIt() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION)
                .message("which cache").build();

        assertThat(TaskTurns.leftToTheHuman(asking)).isFalse();
    }

    @Test
    void leavesAnotherQuestionToTheMasterThanTheOneItFailedToAnswer() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.QUESTION)
                .message("which cache")
                .turn(Turn.none().withMark(TaskTurns.UNANSWERED, Turn.digestOf("which queue"))).build();

        assertThat(TaskTurns.leftToTheHuman(asking)).isFalse();
    }

    @Test
    void leavesAMarkedAnswerToTheMasterWhereTheTaskIsNotAsking() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).message("which cache")
                .turn(Turn.none().withMark(TaskTurns.UNANSWERED, Turn.digestOf("which cache"))).build();

        assertThat(TaskTurns.leftToTheHuman(working)).isFalse();
    }

    @Test
    void leavesADeployTheMasterPressedToTheHumanWhileTheTaskStillWaitsForIt() {
        TaskState deployed = TaskState.builder("proj", "/wt", TaskStatus.APPROVED).lastActiveTimestamp(7).build();
        TaskState pressed = deployed.withTurn(Turn.none()
                .withMark(TaskTurns.PRESSED_DEPLOY, String.valueOf(deployed.statusSince())));

        assertThat(TaskTurns.leftToTheHuman(pressed)).isTrue();
        assertThat(TaskTurns.leftToTheHuman(deployed)).isFalse();
    }

    @Test
    void setsAndClearsAMarkOnTheTasksTurn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        TaskTurns turns = new TaskTurns(state, Clock.systemUTC());

        turns.mark("ABC-42", TaskTurns.LEFT, "deploy src/Login.java");
        turns.mark("ABC-42", TaskTurns.UNANSWERED, "d1");
        String marked = turns.of("ABC-42").mark(TaskTurns.LEFT);
        turns.unmark("ABC-42", TaskTurns.LEFT);

        assertThat(marked).isEqualTo("deploy src/Login.java");
        assertThat(turns.of("ABC-42").mark(TaskTurns.LEFT)).isNull();
        assertThat(turns.of("ABC-42").mark(TaskTurns.UNANSWERED)).isEqualTo("d1");
    }
}

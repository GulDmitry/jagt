package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.CheckerPhase;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaReadsTest {

    private static final QaReader.Run RUN = new QaReader.Run("", "", Paths.get("/wt"), "proj@0123", "", List.of());

    private static TaskState deployed() {
        return TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build().withDeployment(new Deployment(1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, "digest"));
    }

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void qaHoldsTheTurnWhileItReadsAndNobodyHoldsItOnceItAnswered(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-42", deployed());
        List<Turn.Holder> whileReading = new ArrayList<>();
        QaReader reader = mock(QaReader.class);
        when(reader.read(any())).thenAnswer(call -> {
            TaskState task = state.task("ABC-42").orElseThrow();
            whileReading.add(Turns.next(task.status(), task.report(), task.stopped(), Checkers.of(task),
                    Turns.Profile.OFF));
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        });

        new QaReads(reader, new TaskTurns(state, Clock.systemUTC())).check("ABC-42", QaVerdict.ACCEPT, "digest", RUN);

        TaskState after = state.task("ABC-42").orElseThrow();
        assertThat(whileReading).containsExactly(new Turn.Holder.Role("qa"));
        assertThat(Turns.next(after.status(), after.report(), after.stopped(), Checkers.of(after), Turns.Profile.OFF))
                .isEqualTo(Turn.Holder.NOBODY);
    }
}

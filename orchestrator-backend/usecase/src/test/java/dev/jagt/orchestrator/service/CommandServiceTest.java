package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandServiceTest {

    private final FlowEngine flow = mock(FlowEngine.class);
    private final CommandService commands = new CommandService(flow, mock(CaseDoor.class));

    @Test
    void handsBackWhateverTheFlowSaidWithoutRewordingIt() {
        when(flow.run("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(commands.execute("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: approval relayed");
    }

    @Test
    void passesAnAliasDownUntouchedSoOnlyOnePlaceResolvesIt() {
        commands.execute("a1", TaskAction.FOCUS);

        verify(flow).run("a1", TaskAction.FOCUS);
    }

    @Test
    void letsARefusalReachTheCallerWithTheCodeItMustActOn() {
        when(flow.run("ABC-1", TaskAction.DEPLOY)).thenThrow(new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE,
                "Deploy is not available for ABC-1 (it is IN_PROGRESS — agent working)"));

        assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.DEPLOY))
                .asInstanceOf(type(Refusal.class))
                .extracting(Refusal::code).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE);
    }

    @Test
    void refusesTheMasterAVerbOnlyAHumanPresses() {
        assertThatThrownBy(() -> OriginContext.as(ActionOrigin.MASTER,
                () -> commands.execute("ABC-1", TaskAction.DONE)))
                .isInstanceOf(Refusal.class)
                .hasMessage("Done is a human's to press, never the Master's.");
    }

    @Test
    void refusesRecordsAndStopsAMasterPressNoMasterCaseAllows(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.writeString(root.resolve("master-cases.md"),
                "## deploy\n\nmove: deploy\nat: APPROVED\nby: master\nGreen and approved, the Master deploys it.\n");
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
        CommandService guarded = new CommandService(flow, new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC)),
                new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC())));

        OriginContext.as(ActionOrigin.MASTER, () -> guarded.execute("ABC-1", TaskAction.DEPLOY));

        verify(flow, never()).run("ABC-1", TaskAction.DEPLOY);
        assertThat(Files.readString(root.resolve("misses.jsonl")))
                .isEqualTo("{\"at\":1000,\"task\":\"ABC-1\",\"step\":\"deploy\",\"status\":\"REVIEWED\",\"by\":\"master\"}\n");
        assertThat(state.task("ABC-1").orElseThrow().loopOrNone().stop())
                .extracting(LoopWatch.Stop::trigger, LoopWatch.Stop::evidence)
                .containsExactly(LoopWatch.Trigger.MISS, "no master case allows deploy at REVIEWED");
    }
}

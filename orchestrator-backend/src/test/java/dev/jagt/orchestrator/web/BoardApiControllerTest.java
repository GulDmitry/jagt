package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private final TaskViews taskViews = mock(TaskViews.class);
    private final CommandService commands = mock(CommandService.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final UsageTracker usageTracker = mock(UsageTracker.class);
    private final BoardApiController api = new BoardApiController(taskViews, commands, launcher, configService,
            usageTracker, mock(TaskEventStream.class));

    @Test
    void executesAnActionByTheSameNameTheConsoleTakes() {
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("ship ABC-1: approval relayed");

        assertThat(api.act("ABC-1", "ship").message()).isEqualTo("ship ABC-1: approval relayed");
    }

    @Test
    void refusesAnUnknownActionIdRatherThanMappingItToSomethingNear() {
        assertThatThrownBy(() -> api.act("ABC-1", "shipit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown action 'shipit'");
        verifyNoInteractions(commands);
    }

    @Test
    void startsATaskThroughTheSameLauncherTheTypedCommandUses() {
        when(launcher.launch(eq("ABC-42"), eq("demo"), eq("plan"), any(), eq("with tests")))
                .thenReturn("Task ABC-42 initialized");

        var result = api.launch(new BoardApiController.LaunchRequest("ABC-42", "demo", "plan", null,
                "with tests"));

        assertThat(result.message()).isEqualTo("Task ABC-42 initialized");
    }

    @Test
    void treatsBlankModifiersAsAbsentSoAnEmptyFormFieldIsNotAProjectNamedEmptyString() {
        when(launcher.launch(eq("ABC-42"), eq(null), eq(null), eq(null), any())).thenReturn("ok");

        api.launch(new BoardApiController.LaunchRequest("  ABC-42 ", "", "", "", ""));

        verify(launcher).launch("ABC-42", null, null, null, "");
    }

    @Test
    void refusesALaunchWithNothingToLookUp() {
        assertThatThrownBy(() -> api.launch(new BoardApiController.LaunchRequest(" ", null, null, null, null)))
                .hasMessageContaining("ticket key or a URL is required");
        verifyNoInteractions(launcher);
    }

    @Test
    void turnsARefusalIntoA400WithTheSentenceTheHumanShouldRead() {
        var response = api.refused(new IllegalStateException("ship: ABC-1 is DONE — nothing to ship onto"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "ship: ABC-1 is DONE — nothing to ship onto");
    }

    @Test
    void reportsTheSessionSpendAndTheProjectsAlongsideTheTasks() {
        when(taskViews.all()).thenReturn(List.of());
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.model.TokenUsage.ofCall(1000, 0, 50, 0.1));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                java.util.Map.of("demo", new dev.jagt.orchestrator.model.ProjectConfig("/p", "origin/main",
                        "dev", List.of()))));

        var board = api.tasks();

        assertThat(board.spend().calls()).isEqualTo(1);
        assertThat(board.spend().tokens()).isEqualTo(1050);
        assertThat(board.projects()).containsExactly("demo");
    }
}

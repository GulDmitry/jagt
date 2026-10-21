package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractKittyTerminalDriverTest {

    @Test
    void launchesADetachedRemoteControllableInstanceAttachedToTheSession() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).startsWith("kitty", "--detach")
                .containsSequence("-o", "allow_remote_control=yes")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }

    @Test
    void isolatesTheViewerFromTheUsersOwnKittyWindows() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).containsSequence("--single-instance", "--instance-group", "jagt-agents")
                .containsSequence("--listen-on", "unix:/tmp/jagt-kitty-agents");
    }

    @Test
    void appliesTheConfiguredFontSizeToTheViewerInstance() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "13",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).containsSequence("-o", "font_size=13");
    }

    @Test
    void leavesTheFontSizeToKittysOwnConfigWhenUnset() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents", List.of());

        assertThat(cmd).noneMatch(argument -> argument.startsWith("font_size="));
    }

    @Test
    void passesAPlatformsOwnLaunchOptionsThrough() {
        List<String> cmd = AbstractKittyTerminalDriver.firstOpenCommand("kitty", "",
                "unix:/tmp/jagt-kitty-agents", "agents", "/work/tree", "tmux", "agents",
                List.of("-o", "map=cmd+м paste_from_clipboard"));

        assertThat(cmd).containsSequence("-o", "map=cmd+м paste_from_clipboard");
    }

    @Test
    void opensATabInTheViewerInstanceListeningOnTheSessionsOwnSocket() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(isNull(), any(Duration.class), any())).thenReturn(new Processes.Result(0, "", ""));
        var driver = new AbstractKittyTerminalDriver(runner, OrchestratorProperties.defaults().withOpenTerminalWindow(true).withTmuxCommand("tmux"), "kitty", "") {
            @Override
            protected List<String> platformOptions() {
                return List.of();
            }

            @Override
            public void bringToFront() {
            }
        };

        driver.openViewer("agents", "agents", Path.of("/work/tree"));

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(runner, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        String socket = "unix:" + Path.of(System.getProperty("java.io.tmpdir"), "jagt-kitty-agents");
        assertThat(commands.getAllValues()).anySatisfy(command -> assertThat(command)
                .containsSequence("@", "--to", socket, "launch", "--type=tab"));
    }
}

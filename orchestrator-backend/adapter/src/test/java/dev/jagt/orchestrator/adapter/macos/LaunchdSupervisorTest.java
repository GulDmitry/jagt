package dev.jagt.orchestrator.adapter.macos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Supervisor.Supervision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class LaunchdSupervisorTest {

  private final Processes processes = mock(Processes.class);
  private final Host host = mock(Host.class);

  @Test
  void writesTheAgentForTheStagedJarAndLoadsItWhenInstalled(@TempDir Path dir) throws Exception {
    Path jar = Files.createFile(dir.resolve("jagt-run.jar"));
    when(host.home()).thenReturn(dir);
    when(host.workingDir()).thenReturn(Path.of("/work"));
    when(processes.run(isNull(), any(), any())).thenReturn(new Processes.Result(0, "501\n", ""));

    Supervision result = new LaunchdSupervisor(processes, host).install(jar);

    assertThat(result).isInstanceOf(Supervision.Handover.class);
    assertThat(dir.resolve("Library/LaunchAgents/jagt.orchestrator.plist"))
        .content()
        .contains("<string>" + jar + "</string>")
        .contains("<key>KeepAlive</key><true/>")
        .contains("<string>/work</string>");
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes, org.mockito.Mockito.atLeastOnce()).run(isNull(), any(), command.capture());
    assertThat(command.getAllValues())
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("bootstrap", "gui/501")
                    .anyMatch(word -> word.endsWith("launchctl")));
  }

  @Test
  void doesNotHandOverWhenLaunchdStartedThisProcess(@TempDir Path dir) throws Exception {
    Path jar = Files.createFile(dir.resolve("jagt-run.jar"));
    when(host.home()).thenReturn(dir);
    when(host.workingDir()).thenReturn(dir);
    when(host.env("XPC_SERVICE_NAME")).thenReturn(Optional.of("jagt.orchestrator"));
    when(processes.run(isNull(), any(), any())).thenReturn(new Processes.Result(0, "501\n", ""));

    Supervision result = new LaunchdSupervisor(processes, host).install(jar);

    assertThat(result).isInstanceOf(Supervision.Done.class);
  }

  @Test
  void pointsTheAgentAtTheStableLinkWhenTheStampedJarRuns(@TempDir Path dir) throws Exception {
    Files.createFile(dir.resolve("jagt-run.jar"));
    Path stamped = Files.createFile(dir.resolve("jagt-run-20261110-101010.jar"));
    when(host.home()).thenReturn(dir);
    when(host.workingDir()).thenReturn(dir);
    when(processes.run(isNull(), any(), any())).thenReturn(new Processes.Result(0, "501", ""));

    new LaunchdSupervisor(processes, host).install(stamped);

    assertThat(dir.resolve("Library/LaunchAgents/jagt.orchestrator.plist"))
        .content()
        .contains("<string>" + dir.resolve("jagt-run.jar") + "</string>");
  }

  @Test
  void refusesAJarThatIsNotStaged(@TempDir Path dir) throws Exception {
    Path built = Files.createFile(dir.resolve("jagt.jar"));
    when(host.home()).thenReturn(dir);

    Supervision result = new LaunchdSupervisor(processes, host).install(built);

    assertThat(result).isInstanceOf(Supervision.Refused.class);
    verify(processes, never()).run(any(), any(), any());
  }

  @Test
  void refusesToRestartWhatWasNeverInstalled(@TempDir Path dir) {
    when(host.home()).thenReturn(dir);

    Supervision result = new LaunchdSupervisor(processes, host).restart();

    assertThat(result).isInstanceOf(Supervision.Refused.class);
    verify(processes, never()).runDetached(any(), any());
  }

  @Test
  void kicksTheInstalledAgentWhenRestarted(@TempDir Path dir) throws Exception {
    Path agents = Files.createDirectories(dir.resolve("Library/LaunchAgents"));
    Files.createFile(agents.resolve("jagt.orchestrator.plist"));
    when(host.home()).thenReturn(dir);
    when(processes.run(isNull(), any(), any())).thenReturn(new Processes.Result(0, "501\n", ""));

    Supervision result = new LaunchdSupervisor(processes, host).restart();

    assertThat(result).isInstanceOf(Supervision.Done.class);
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).runDetached(isNull(), command.capture());
    assertThat(command.getValue()).contains("kickstart", "-k", "gui/501/jagt.orchestrator");
  }
}

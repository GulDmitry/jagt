package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Supervisor;
import dev.jagt.orchestrator.port.Supervisor.Supervision;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupervisorCommandsTest {

  private final Supervisor supervisor = mock(Supervisor.class);
  private final Host host = mock(Host.class);

  @Test
  void restartSaysWhatTheSupervisorDid() {
    when(supervisor.restart()).thenReturn(new Supervision.Done("restarting"));

    assertThat(new RestartCommand(supervisor).run("")).isEqualTo("restarting");
  }

  @Test
  void restartRefusesWithTheSupervisorsWords() {
    when(supervisor.restart()).thenReturn(new Supervision.Refused("set `supervisor`"));

    assertThatThrownBy(() -> new RestartCommand(supervisor).run(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("set `supervisor`");
  }

  @Test
  void superviseInstallsTheJarThisProcessRuns() {
    Path jar = Path.of("/stage/jagt-run.jar");
    when(host.jar()).thenReturn(Optional.of(jar));
    when(supervisor.install(jar)).thenReturn(new Supervision.Done("installed"));

    assertThat(new SuperviseCommand(supervisor, host).run("")).isEqualTo("installed");
  }

  @Test
  void superviseRefusesWhereNoSingleJarRuns() {
    when(host.jar()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new SuperviseCommand(supervisor, host).run(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

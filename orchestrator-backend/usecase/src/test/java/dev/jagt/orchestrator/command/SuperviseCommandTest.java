package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Exit;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Supervisor;
import dev.jagt.orchestrator.port.host.Supervisor.Supervision;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SuperviseCommandTest {

  private final Supervisor supervisor = mock(Supervisor.class);
  private final Host host = mock(Host.class);
  private final Exit exit = mock(Exit.class);

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

    assertThat(new SuperviseCommand(supervisor, host, exit).run("")).isEqualTo("installed");
    verify(exit, never()).afterAnswer();
  }

  @Test
  void superviseEndsThisJagtWhenTheSupervisorTakesOver() {
    Path jar = Path.of("/stage/jagt-run.jar");
    when(host.jar()).thenReturn(Optional.of(jar));
    when(supervisor.install(jar)).thenReturn(new Supervision.Handover("back under launchd"));

    String answer = new SuperviseCommand(supervisor, host, exit).run("");

    assertThat(answer).isEqualTo("back under launchd");
    verify(exit).afterAnswer();
  }

  @Test
  void superviseKeepsThisJagtWhenTheSupervisorRefuses() {
    Path jar = Path.of("/stage/jagt-run.jar");
    when(host.jar()).thenReturn(Optional.of(jar));
    when(supervisor.install(jar)).thenReturn(new Supervision.Refused("no"));

    assertThatThrownBy(() -> new SuperviseCommand(supervisor, host, exit).run(""))
        .isInstanceOf(IllegalArgumentException.class);
    verify(exit, never()).afterAnswer();
  }

  @Test
  void superviseRefusesWhereNoSingleJarRuns() {
    when(host.jar()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new SuperviseCommand(supervisor, host, exit).run(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

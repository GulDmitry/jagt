package dev.jagt.orchestrator.adapter.macos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.KittyOptions;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Processes;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.mockito.ArgumentCaptor;

final class KittyFixtures {

  private KittyFixtures() {}

  static ProcessRunner answeringEveryCommand(int exit) {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(exit, "", ""));
    return runner;
  }

  static KittyTerminalDriver plainDriver() {
    return new KittyTerminalDriver(
        mock(ProcessRunner.class),
        OrchestratorProperties.defaults(),
        mock(OsaScript.class),
        new KittyOptions("kitty", ""),
        Clock.systemUTC());
  }

  static KittyTerminalDriver viewerDriver(ProcessRunner runner) {
    return new KittyTerminalDriver(
        runner,
        OrchestratorProperties.defaults().withOpenTerminalWindow(true),
        mock(OsaScript.class),
        new KittyOptions("/k/kitty", ""),
        Clock.systemUTC());
  }

  static KittyTerminalDriver driver(ProcessRunner runner, OsaScript osaScript) {
    return new KittyTerminalDriver(
        runner,
        OrchestratorProperties.defaults(),
        osaScript,
        new KittyOptions("/k/kitty", ""),
        Clock.systemUTC());
  }

  static List<String> secondCommandOf(ProcessRunner runner) {
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner, times(2)).run(isNull(), any(Duration.class), command.capture());
    return command.getAllValues().get(1);
  }
}

package dev.jagt.orchestrator.adapter.macos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OsaScriptTest {

  @Test
  void answersWhatTheScriptPrintedWithoutItsTrailingNewline() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(isNull(), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "found\n", ""));

    String answer = new OsaScript(runner).answer("return \"found\"");

    assertThat(answer).isEqualTo("found");
  }
}

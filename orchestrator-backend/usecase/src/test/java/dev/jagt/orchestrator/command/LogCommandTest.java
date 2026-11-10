package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class LogCommandTest {

  private final LogTail tail = mock(LogTail.class);

  @Test
  void opensTheLogsTailAsReport() {
    when(tail.render()).thenReturn("the last 2 lines");
    LogCommand command = new LogCommand(tail);

    assertThat(command.id()).isEqualTo("tail");
    assertThat(command.report()).isTrue();
    assertThat(command.run("")).isEqualTo("the last 2 lines");
  }
}

package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.FakeSink;
import dev.jagt.orchestrator.port.host.Processes;
import org.junit.jupiter.api.Test;

class TmuxWindowStreamTest {

  private final FakeControlProcess client = new FakeControlProcess();
  private final FakeProcesses processes =
      new FakeProcesses()
          .answering(new Processes.Result(0, "", ""))
          .answering(new Processes.Result(0, "100 30", ""), "display-message")
          .answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows")
          .starting(client);
  private final TmuxReads reads = new TmuxReads(processes, new TmuxProperties("", ""));
  private final TmuxWindowStream stream =
      new TmuxWindowStream(processes, reads, new ResizedWindows(processes, reads));

  @Test
  void opensNoViewOfWindowTheSessionDoesNotHold() throws Exception {
    var view = stream.open("jagt", "ABC-2", 120, 40, new FakeSink());

    assertThat(view).isEmpty();
    assertThat(processes.issuedContaining("-C", "attach")).isEmpty();
  }

  @Test
  void sizesTheWindowThenAttachesControlClientToItsSession() throws Exception {
    client.print(
        """
        %begin 1 1 0
        %end 1 1 0
        %begin 1 2 1
        jagt-window %1 0 0
        %end 1 2 1
        %begin 1 3 1
        row
        %end 1 3 1
        """);

    var view = stream.open("jagt", "ABC-1", 120, 40, new FakeSink());

    assertThat(view).isPresent();
    assertThat(processes.issuedContaining("resize-window", "@1", "120", "40")).hasSize(1);
    assertThat(processes.issuedContaining("-C", "attach", "-t", "=jagt")).hasSize(1);
  }

  @Test
  void putsTheSizeBackWhenTheControlClientCannotBeOpened() {
    client.print("%begin 1 1 0\n%end 1 1 0\n%begin 1 2 1\nno window\n%error 1 2 1\n");

    assertThatThrownBy(() -> stream.open("jagt", "ABC-1", 120, 40, new FakeSink()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(processes.issuedContaining("resize-window", "@1", "100", "30")).hasSize(1);
  }
}

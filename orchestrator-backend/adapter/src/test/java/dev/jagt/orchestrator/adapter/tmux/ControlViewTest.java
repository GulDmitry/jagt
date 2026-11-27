package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.FakeSink;
import dev.jagt.orchestrator.adapter.ProcessRunnerTest;
import dev.jagt.orchestrator.port.host.Processes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControlViewTest {

  private static final String SLASH = "\\";
  private static final String ESC = Character.toString(27);
  private static final String ATTACHED = "%begin 1 1 0\n%end 1 1 0\n%session-changed $0 s\n";
  private static final String PANE_AT_ROW_TWO_COLUMN_FIVE =
      "%begin 1 2 1\njagt-window %1 4 1\n%end 1 2 1\n";
  private static final String TWO_ROWS = "%begin 1 3 1\nfirst row\nsecond row\n%end 1 3 1\n";

  private final FakeProcesses processes =
      new FakeProcesses().answering(new Processes.Result(0, "100 30", ""));
  private final ResizedWindows sizes =
      new ResizedWindows(processes, new TmuxReads(processes, new TmuxProperties("", "")));
  private final FakeControlProcess client = new FakeControlProcess();
  private final FakeSink sink = new FakeSink();
  private final ControlView view = new ControlView(client, "@1", sizes, sink);

  @BeforeEach
  void tmuxHasAttachedAndNamedThePane() {
    client.print(ATTACHED + PANE_AT_ROW_TWO_COLUMN_FIVE);
  }

  @Test
  void opensWithTheScreenAsItReadsAndTheCursorWhereTheWindowHasIt() throws Exception {
    client.print(TWO_ROWS);
    view.begin();

    assertThat(sink.next())
        .isEqualTo(ESC + "[2J" + ESC + "[Hfirst row\r\nsecond row" + ESC + "[0m" + ESC + "[2;5H");
  }

  @Test
  void streamsWhatTheWindowsPaneThenPrintsAndNothingOfAnotherPane() throws Exception {
    client.print(TWO_ROWS);
    view.begin();
    sink.next();

    client.print("%output %2 other\n%output %1 hello" + SLASH + "015" + SLASH + "012\n");

    assertThat(sink.next()).isEqualTo("hello\r\n");
  }

  @Test
  void dropsOutputPrintedBeforeTheScreenWasTakenBecauseTheScreenHoldsIt() throws Exception {
    client.print("%output %1 early\n" + TWO_ROWS);
    view.begin();

    assertThat(sink.next()).contains("first row");
    client.print("%output %1 late\n");
    assertThat(sink.next()).isEqualTo("late");
  }

  @Test
  void typesAsciiAsHexKeysAndOtherTextLiterallyInOrder() throws Exception {
    client.print(TWO_ROWS);
    view.begin();

    view.type("hi\rä");

    assertThat(client.commandsSent())
        .endsWith("send-keys -t %1 -H 68 69 0d\nsend-keys -t %1 -l 'ä'\n");
  }

  @Test
  void resizesTheWindowToTheViewsScreen() throws Exception {
    client.print(TWO_ROWS);
    view.begin();

    view.resize(132, 43);

    assertThat(processes.issuedContaining("resize-window", "@1", "132", "43")).hasSize(1);
  }

  @Test
  void endsTheViewWhenTmuxEndsTheClient() throws Exception {
    client.print(TWO_ROWS);
    view.begin();

    client.exit();

    await().atMost(ProcessRunnerTest.WAIT_CEILING).until(() -> sink.endedWith() != null);
    assertThat(sink.endedWith()).contains("ended");
  }

  @Test
  void detachesTheClientOnCloseAndTellsNoOneItEnded() throws Exception {
    client.print(TWO_ROWS);
    view.begin();

    view.close();
    view.close();

    assertThat(client.destroyed()).isTrue();
    assertThat(sink.endedWith()).isNull();
  }

  @Test
  void refusesToOpenWhenTmuxRefusesTheCommand() {
    client.print("%begin 1 3 1\ncan't find window\n%error 1 3 1\n");

    assertThatThrownBy(view::begin)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("can't find window");
  }
}

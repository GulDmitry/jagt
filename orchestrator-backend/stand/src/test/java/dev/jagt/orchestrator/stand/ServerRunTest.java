package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerRunTest {

  @TempDir Path dir;

  private final JagtEngine jagt = mock(JagtEngine.class);
  private final StandBoard board = mock(StandBoard.class);

  @Test
  void theRunsBoardAndEngineAreTheEnginesOwn() throws Exception {
    when(jagt.board()).thenReturn(board);
    Path root = dir.resolve("base/jagt-stand-41-1");
    when(jagt.screens()).thenReturn("");

    try (ServerRun run = TestServerRunBuilder.serverRun(root).build(jagt, new StandSlots(1))) {
      assertThat(run.board()).isSameAs(board);
      assertThat(run.engine()).isSameAs(jagt);
      assertThat(run.root()).isEqualTo(root);
    }
  }

  @Test
  void restartedEngineReplacesTheRunsEngineAndItsMarkerNamesTheNewPid() throws Exception {
    JagtEngine restarted = mock(JagtEngine.class);
    when(jagt.restart(Restart.UNIT)).thenReturn(restarted);
    when(restarted.pid()).thenReturn(7L);
    when(restarted.port()).thenReturn(9);
    when(restarted.screens()).thenReturn("");
    Path root = dir.resolve("base/jagt-stand-41-1");

    try (ServerRun run = TestServerRunBuilder.serverRun(root).build(jagt, new StandSlots(1))) {
      run.restart(Restart.UNIT);

      assertThat(run.engine()).isSameAs(restarted);
      assertThat(StandMarker.read(root, 0).jagtPid()).isEqualTo(7);
    }
  }

  @Test
  void closeKeepsTheScreensTakesTheEngineDownRemovesTheRootAndFreesTheSlot() throws Exception {
    when(jagt.screens()).thenReturn("=== work");
    Path root = dir.resolve("base/jagt-stand-41-1");
    StandSlots slots = new StandSlots(1);
    slots.take();

    ServerRun run = TestServerRunBuilder.serverRun(root).build(jagt, slots);
    run.passed();
    run.close();

    verify(jagt).close();
    assertThat(Files.exists(root)).isFalse();
    assertTimeoutPreemptively(Duration.ofSeconds(5), slots::take);
  }

  @Test
  void closeFreesTheSlotEvenWhenTheEngineCannotBeClosed() throws Exception {
    when(jagt.screens()).thenReturn("");
    org.mockito.Mockito.doThrow(new StandFailure("stuck")).when(jagt).close();
    StandSlots slots = new StandSlots(1);
    slots.take();
    ServerRun run =
        TestServerRunBuilder.serverRun(dir.resolve("base/jagt-stand-41-1")).build(jagt, slots);

    org.assertj.core.api.Assertions.assertThatThrownBy(run::close).isInstanceOf(StandFailure.class);

    assertTimeoutPreemptively(Duration.ofSeconds(5), slots::take);
  }
}

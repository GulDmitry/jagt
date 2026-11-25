package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartRetryTest {

  @Test
  void rethrowsTheFailureAndKeepsTheInterrupt_whenStartIsRefusedAndTheWaitIsInterrupted() {
    IllegalStateException refused = new IllegalStateException("no projects");
    FakeApplicationRun run = new FakeApplicationRun().failing(refused);
    StartRetry retry =
        new StartRetry(run, (port, file, problems) -> Optional.of(new FakeServedFailure(false)));

    assertThatThrownBy(() -> retry.run(new String[] {"--server.port=8291"})).isSameAs(refused);

    assertThat(Thread.interrupted()).isTrue();
    assertThat(run.runs()).isEqualTo(1);
  }

  @Test
  void startsAgain_whenTheConfigIsSavedAfterRefusedStart() {
    FakeApplicationRun run =
        new FakeApplicationRun().failing(new IllegalStateException("no projects"));
    StartRetry retry =
        new StartRetry(run, (port, file, problems) -> Optional.of(new FakeServedFailure(true)));

    retry.run(new String[] {"--server.port=8291"});

    assertThat(run.runs()).isEqualTo(2);
  }

  @Test
  void rethrowsTheFailure_whenNoFailurePageCanOpen() {
    IllegalStateException refused = new IllegalStateException("port taken");
    FakeApplicationRun run = new FakeApplicationRun().failing(refused);
    StartRetry retry = new StartRetry(run, (port, file, problems) -> Optional.empty());

    assertThatThrownBy(() -> retry.run(new String[] {"--server.port=8291"})).isSameAs(refused);

    assertThat(run.runs()).isEqualTo(1);
  }

  @Test
  void rethrowsTheError_whenTheRunThrowsAnError() {
    NoClassDefFoundError broken = new NoClassDefFoundError("rewritten jar");
    FakeApplicationRun run = new FakeApplicationRun().failing(broken);
    StartRetry retry = new StartRetry(run, (port, file, problems) -> Optional.empty());

    assertThatThrownBy(() -> retry.run(new String[] {"--server.port=8291"})).isSameAs(broken);

    assertThat(run.runs()).isEqualTo(1);
  }
}

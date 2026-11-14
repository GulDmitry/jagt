package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxTest {

  @Test
  void offersSandboxOnlyWhereWritableSetWasGiven() {
    Sandbox on = Sandbox.on(List.of(Path.of("/tmp/wt")), List.of("git"));

    assertThat(on.enabled()).isTrue();
    assertThat(on.writable()).containsExactly(Path.of("/tmp/wt"));
    assertThat(on.excludedCommands()).containsExactly("git");
    assertThat(Sandbox.off().enabled()).isFalse();
  }
}

package dev.jagt.orchestrator.port.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentWorktreeTest {

  @Test
  void provisionsWorktreeWithNoHintsUntilItIsGivenSome() {
    AgentWorktree bare = AgentWorktree.of(Path.of("/wt"), Path.of("/root"));

    AgentWorktree hinted =
        bare.withAllowedTools(List.of("Read")).withSandbox(Sandbox.on(List.of(), List.of()));
    assertThat(bare.allowedTools()).isEmpty();
    assertThat(bare.sandbox().enabled()).isFalse();
    assertThat(hinted.path()).isEqualTo(Path.of("/wt"));
    assertThat(hinted.orchestratorRoot()).isEqualTo(Path.of("/root"));
    assertThat(hinted.allowedTools()).containsExactly("Read");
    assertThat(hinted.sandbox().enabled()).isTrue();
  }
}

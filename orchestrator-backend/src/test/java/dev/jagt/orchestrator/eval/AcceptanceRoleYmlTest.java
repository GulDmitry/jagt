package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.role.RoleOptions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptanceRoleYmlTest {

  @Test
  void fillsEveryPlaceholderOfTheRoleFileItInstalls() {
    var flows = new RoleOptions.Flows("script", "flows", List.of("run", "--all"), Duration.ZERO);

    String yml = AcceptanceRoleYml.of("http://127.0.0.1:8080", "host-job", flows);

    assertThat(yml)
        .contains("url: \"http://127.0.0.1:8080\"", "landed: \"host-job\"")
        .contains("driver: \"script\"", "command: [\"run\", \"--all\"]")
        .doesNotContain("%s");
  }
}

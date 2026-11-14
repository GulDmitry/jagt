package dev.jagt.orchestrator.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void sendsBackEveryRuleBrokenAsOneCorrection() {
    String refusal =
        Message.refusal(
            List.of(new Violation("taskId", "required"), new Violation("mode", "one of [plan]")));

    assertThat(refusal)
        .isEqualTo(
            "This message was not accepted. Fix every line and send it again:\n"
                + "taskId: required\nmode: one of [plan]");
  }
}

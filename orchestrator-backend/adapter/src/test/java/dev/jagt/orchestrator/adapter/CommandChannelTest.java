package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandChannelTest {

  private static final ChannelMessage DEPLOYED =
      new ChannelMessage("deployed", "ABC-42", null, "deployed", "landed");

  @Test
  void isTheDriverOfKindCommand() {
    assertThat(new CommandChannel(new ProcessRunner()).kind()).isEqualTo("command");
  }

  @Test
  void givesTheMessageAsOneJsonObjectOnTheCommandsInput(@TempDir Path dir) throws Exception {
    Path out = dir.resolve("out.json");
    ChannelAddress address =
        new ChannelAddress("ops", "command", "", List.of("sh", "-c", "cat > " + out));

    new CommandChannel(new ProcessRunner()).deliver(address, DEPLOYED);

    assertThat(Files.readString(out))
        .isEqualTo(
            "{\"event\":\"deployed\",\"task\":\"ABC-42\",\"title\":\"deployed\",\"body\":\"land"
                + "ed\"}");
  }

  @Test
  void throwsWhenTheCommandFails() {
    ChannelAddress address =
        new ChannelAddress("ops", "command", "", List.of("sh", "-c", "exit 3"));

    assertThatThrownBy(() -> new CommandChannel(new ProcessRunner()).deliver(address, DEPLOYED))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void throwsWhenTheCommandCannotStart() {
    ChannelAddress address =
        new ChannelAddress("ops", "command", "", List.of("no-such-binary-abc"));

    assertThatThrownBy(() -> new CommandChannel(new ProcessRunner()).deliver(address, DEPLOYED))
        .isInstanceOf(RuntimeException.class);
  }
}

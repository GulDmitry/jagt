package dev.jagt.orchestrator.adapter.herdr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "orchestrator.session-host", havingValue = "herdr")
public class UnixHerdrWire implements HerdrWire {

  private static final Duration ANSWER_WITHIN = Duration.ofSeconds(20);

  @Override
  public Optional<String> exchange(Path socket, String request) throws IOException {
    if (!Files.exists(socket)) {
      return Optional.empty();
    }
    try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
      channel.write(ByteBuffer.wrap((request + "\n").getBytes(StandardCharsets.UTF_8)));
      return Optional.of(answer(channel));
    } catch (ConnectException refused) {
      log.atDebug().setMessage("herdr socket refused").addKeyValue("path", socket).log();
      return Optional.empty();
    }
  }

  private static String answer(SocketChannel channel) throws IOException {
    channel.configureBlocking(false);
    try (Selector selector = Selector.open()) {
      channel.register(selector, SelectionKey.OP_READ);
      ByteArrayOutputStream reply = new ByteArrayOutputStream();
      ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
      boolean complete = false;
      while (!complete) {
        if (selector.select(ANSWER_WITHIN.toMillis()) == 0) {
          throw new IOException("no answer within " + ANSWER_WITHIN);
        }
        selector.selectedKeys().clear();
        buffer.clear();
        int read = channel.read(buffer);
        if (read < 0) {
          throw new IOException("the connection closed before an answer");
        }
        reply.write(buffer.array(), 0, read);
        complete = read > 0 && buffer.get(read - 1) == '\n';
      }
      return reply.toString(StandardCharsets.UTF_8).strip();
    }
  }
}

package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class FakePrintedSink implements WindowStream.Sink {

  private final List<String> text = new CopyOnWriteArrayList<>();

  @Override
  public void output(byte[] bytes) {
    text.add(new String(bytes, StandardCharsets.UTF_8));
  }

  @Override
  public void ended(String cause) {
    text.add("[ended " + cause + "]");
  }

  String printed() {
    return String.join("", text);
  }
}

package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FakeSink implements WindowStream.Sink {

  private final BlockingQueue<byte[]> outputs = new LinkedBlockingQueue<>();
  private final AtomicReference<String> ended = new AtomicReference<>();

  @Override
  public void output(byte[] bytes) {
    outputs.add(bytes);
  }

  @Override
  public void ended(String cause) {
    ended.set(cause);
  }

  public String next() throws InterruptedException {
    byte[] bytes = outputs.poll(5, TimeUnit.SECONDS);
    return bytes == null ? "nothing arrived" : new String(bytes, StandardCharsets.UTF_8);
  }

  public String endedWith() {
    return ended.get();
  }
}

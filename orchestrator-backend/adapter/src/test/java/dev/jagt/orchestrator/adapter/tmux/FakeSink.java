package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class FakeSink implements WindowStream.Sink {

  private final BlockingQueue<byte[]> outputs = new LinkedBlockingQueue<>();
  private final CompletableFuture<String> ended = new CompletableFuture<>();

  @Override
  public void output(byte[] bytes) {
    outputs.add(bytes);
  }

  @Override
  public void ended(String cause) {
    ended.complete(cause);
  }

  String next() throws InterruptedException {
    byte[] bytes = outputs.poll(5, TimeUnit.SECONDS);
    return bytes == null ? "nothing arrived" : new String(bytes, StandardCharsets.UTF_8);
  }

  CompletableFuture<String> endedWith() {
    return ended;
  }
}

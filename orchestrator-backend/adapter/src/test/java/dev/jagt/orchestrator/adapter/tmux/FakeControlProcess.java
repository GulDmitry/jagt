package dev.jagt.orchestrator.adapter.tmux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class FakeControlProcess extends Process {

  private final PipedOutputStream printing = new PipedOutputStream();
  private final PipedInputStream printed;
  private final ByteArrayOutputStream commands = new ByteArrayOutputStream();
  private volatile boolean destroyed;

  FakeControlProcess() {
    try {
      printed = new PipedInputStream(printing, 1 << 16);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void print(String text) {
    try {
      printing.write(text.getBytes(StandardCharsets.UTF_8));
      printing.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void exit() {
    try {
      printing.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String commandsSent() {
    return commands.toString(StandardCharsets.UTF_8);
  }

  boolean destroyed() {
    return destroyed;
  }

  @Override
  public OutputStream getOutputStream() {
    return commands;
  }

  @Override
  public InputStream getInputStream() {
    return printed;
  }

  @Override
  public InputStream getErrorStream() {
    return InputStream.nullInputStream();
  }

  @Override
  public int waitFor() {
    return 0;
  }

  @Override
  public int exitValue() {
    return 0;
  }

  @Override
  public void destroy() {
    destroyed = true;
    exit();
  }
}

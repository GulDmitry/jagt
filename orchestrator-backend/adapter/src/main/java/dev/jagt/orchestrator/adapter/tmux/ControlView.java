package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * One tmux control-mode client attached to a session, following one window. Its commands are
 * answered in the order they were sent, and so is the output between them: what the screen showed
 * when it was captured is the screen, and everything printed after the answer is the stream.
 */
@Slf4j
final class ControlView implements WindowStream.View {

  private static final String MARK = "jagt-window ";
  private static final String CLEAR = "\u001b[2J\u001b[H";
  private static final long OPENING_SECONDS = 5;

  private final Process client;
  private final String windowId;
  private final ResizedWindows sizes;
  private final WindowStream.Sink sink;
  private final CountDownLatch opened = new CountDownLatch(1);
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();
  private final List<String> block = new ArrayList<>();
  private volatile String pane = "";
  private volatile String failure = "";
  private volatile boolean live;
  private String cursor = "";
  private boolean inBlock;

  ControlView(Process client, String windowId, ResizedWindows sizes, WindowStream.Sink sink) {
    this.client = client;
    this.windowId = windowId;
    this.sizes = sizes;
    this.sink = sink;
  }

  /** Asks for the pane and its screen, then waits until the screen has been handed to the sink. */
  void begin() {
    Thread.startVirtualThread(this::read);
    command(
        "display-message -p -t "
            + windowId
            + " \""
            + MARK
            + "#{pane_id} #{cursor_x} #{cursor_y}\"");
    command("capture-pane -p -e -t " + windowId);
    try {
      if (!opened.await(OPENING_SECONDS, TimeUnit.SECONDS) || !live) {
        throw new IllegalStateException(
            failure.isEmpty() ? "tmux control client did not answer for " + windowId : failure);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while opening a view of " + windowId, e);
    }
  }

  @Override
  public void type(String text) {
    StringBuilder ascii = new StringBuilder();
    StringBuilder other = new StringBuilder();
    for (int at = 0; at < text.length(); ) {
      int point = text.codePointAt(at);
      at += Character.charCount(point);
      if (point < 0x80) {
        flush(other, false);
        ascii.append(' ').append(HexFormat.of().toHexDigits((byte) point));
      } else {
        flush(ascii, true);
        other.appendCodePoint(point);
      }
    }
    flush(ascii, true);
    flush(other, false);
  }

  private void flush(StringBuilder run, boolean hex) {
    if (run.isEmpty()) {
      return;
    }
    command(
        hex ? "send-keys -t " + pane + " -H" + run : "send-keys -t " + pane + " -l '" + run + "'");
    run.setLength(0);
  }

  @Override
  public void resize(int columns, int rows) {
    sizes.resize(windowId, columns, rows);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    client.destroy();
    sizes.release(windowId);
  }

  private synchronized void command(String line) {
    try {
      OutputStream in = client.getOutputStream();
      in.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      in.flush();
    } catch (IOException e) {
      end("tmux control client is gone");
    }
  }

  private void read() {
    try (InputStream out = client.getInputStream()) {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      for (int next = out.read(); next >= 0; next = out.read()) {
        if (next == '\n') {
          handle(line.toByteArray());
          line.reset();
        } else {
          line.write(next);
        }
      }
    } catch (IOException e) {
      log.atDebug().setMessage("tmux control client unreadable").addKeyValue("cause", e).log();
    }
    end("tmux control client ended");
  }

  private void handle(byte[] raw) {
    String text = new String(raw, StandardCharsets.UTF_8);
    if (text.startsWith("%begin ")) {
      inBlock = true;
      block.clear();
    } else if (text.startsWith("%end ") || text.startsWith("%error ")) {
      inBlock = false;
      answered(text.startsWith("%error "));
    } else if (inBlock) {
      block.add(text);
    } else if (text.startsWith("%exit")) {
      end("tmux detached the control client");
    } else if (live) {
      ControlOutput.parse(raw)
          .filter(output -> output.pane().equals(pane))
          .ifPresent(output -> sink.output(output.data()));
    }
  }

  private void answered(boolean refused) {
    if (refused && opened.getCount() > 0) {
      end("tmux refused: " + String.join(" ", block));
    } else if (!block.isEmpty() && block.getFirst().startsWith(MARK) && pane.isEmpty()) {
      String[] facts = block.getFirst().substring(MARK.length()).split(" ", 3);
      pane = facts[0];
      cursor =
          "\u001b[0m\u001b["
              + (Integer.parseInt(facts[2]) + 1)
              + ";"
              + (Integer.parseInt(facts[1]) + 1)
              + "H";
    } else if (!pane.isEmpty() && opened.getCount() > 0) {
      sink.output(snapshot());
      live = true;
      opened.countDown();
    }
  }

  private byte[] snapshot() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.writeBytes(CLEAR.getBytes(StandardCharsets.US_ASCII));
    bytes.writeBytes(String.join("\r\n", block).getBytes(StandardCharsets.UTF_8));
    bytes.writeBytes(cursor.getBytes(StandardCharsets.US_ASCII));
    return bytes.toByteArray();
  }

  private void end(String cause) {
    if (ended.compareAndSet(false, true)) {
      failure = cause;
      opened.countDown();
      if (live && !closed.get()) {
        sink.ended(cause);
      }
    }
  }
}

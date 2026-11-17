package dev.jagt.orchestrator.adapter.tmux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** One {@code %output} line of tmux's control mode: a pane and the bytes it printed. */
final class ControlOutput {

  private static final String PREFIX = "%output ";
  private static final int PANE_ID_WIDTH = 16;

  private final String pane;
  private final byte[] data;

  private ControlOutput(String pane, byte[] data) {
    this.pane = pane;
    this.data = data;
  }

  String pane() {
    return pane;
  }

  byte[] data() {
    return data;
  }

  static Optional<ControlOutput> parse(byte[] line) {
    String head =
        new String(
            line,
            0,
            Math.min(line.length, PREFIX.length() + PANE_ID_WIDTH),
            StandardCharsets.ISO_8859_1);
    int space = head.indexOf(' ', PREFIX.length());
    if (!head.startsWith(PREFIX) || space < 0) {
      return Optional.empty();
    }
    return Optional.of(
        new ControlOutput(head.substring(PREFIX.length(), space), unescape(line, space + 1)));
  }

  /** tmux writes a byte under a space, and the backslash, as a backslash and three octal digits. */
  private static byte[] unescape(byte[] line, int from) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(line.length - from);
    int at = from;
    while (at < line.length) {
      if (line[at] == '\\' && octal(line, at + 1)) {
        bytes.write((line[at + 1] - '0') * 64 + (line[at + 2] - '0') * 8 + (line[at + 3] - '0'));
        at += 4;
      } else {
        bytes.write(line[at]);
        at++;
      }
    }
    return bytes.toByteArray();
  }

  private static boolean octal(byte[] line, int at) {
    return at + 2 < line.length
        && line[at] >= '0'
        && line[at] <= '3'
        && line[at + 1] >= '0'
        && line[at + 1] <= '7'
        && line[at + 2] >= '0'
        && line[at + 2] <= '7';
  }
}

package dev.jagt.orchestrator.service.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The {@code sha256sum} format the release publishes: {@code <hex> <file name>} per line. */
public final class Sha256 {

  private static final int CHUNK = 64 * 1024;

  private Sha256() {}

  public static String of(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] chunk = new byte[CHUNK];
      for (int read = in.read(chunk); read >= 0; read = in.read(chunk)) {
        digest.update(chunk, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("every JVM carries SHA-256", e);
    }
  }

  /** The hash a sums file lists for {@code name}; fails when it lists none. */
  public static String listed(Path sums, String name) throws IOException {
    return Files.readAllLines(sums).stream()
        .map(String::strip)
        .filter(line -> line.endsWith(" " + name) || line.endsWith(" *" + name))
        .map(line -> line.substring(0, line.indexOf(' ')))
        .findFirst()
        .orElseThrow(() -> new IOException("no checksum published for " + name));
  }
}

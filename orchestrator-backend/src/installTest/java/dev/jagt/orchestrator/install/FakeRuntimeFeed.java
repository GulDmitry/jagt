package dev.jagt.orchestrator.install;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class FakeRuntimeFeed {

  private final HttpServer feed;
  private final Path work;
  private final boolean windows;

  FakeRuntimeFeed(HttpServer feed, Path work, boolean windows) {
    this.feed = feed;
    this.work = work;
    this.windows = windows;
  }

  void serve() throws IOException, InterruptedException, NoSuchAlgorithmException {
    String archiveName = windows ? "jre.zip" : "jre.tar.gz";
    Path archive = work.resolve(archiveName);
    Path javaHome = Path.of(System.getProperty("java.home"));
    if (windows) {
      zip(javaHome, archive);
    } else {
      tar(javaHome, archive);
    }
    byte[] runtime = Files.readAllBytes(archive);
    String sum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(runtime));
    String url = "http://127.0.0.1:" + feed.getAddress().getPort() + "/jre-archive/" + archiveName;
    feed.createContext(
        "/jre/",
        exchange -> {
          exchange.getResponseHeaders().add("Location", url);
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    feed.createContext(
        "/jre-archive/",
        exchange -> {
          byte[] body =
              exchange.getRequestURI().getPath().endsWith(".sha256.txt")
                  ? (sum + "  " + archiveName + "\n").getBytes(StandardCharsets.UTF_8)
                  : runtime;
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
  }

  private static void zip(Path javaHome, Path archive) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive));
        Stream<Path> files = Files.walk(javaHome)) {
      zip.setLevel(Deflater.NO_COMPRESSION);
      for (Path each : files.filter(Files::isRegularFile).toList()) {
        String entry = javaHome.relativize(each).toString().replace('\\', '/');
        zip.putNextEntry(new ZipEntry("jre/" + entry));
        Files.copy(each, zip);
        zip.closeEntry();
      }
    }
  }

  private void tar(Path javaHome, Path archive) throws IOException, InterruptedException {
    Path staged = Files.createDirectories(work.resolve("runtime-image/jre/bin"));
    Path java = staged.resolve("java");
    Files.writeString(java, "#!/bin/sh\nexec '" + javaHome.resolve("bin/java") + "' \"$@\"\n");
    java.toFile().setExecutable(true);
    Process tar =
        new ProcessBuilder(
                "tar",
                "-czf",
                archive.toString(),
                "-C",
                work.resolve("runtime-image").toString(),
                "jre")
            .inheritIO()
            .start();
    assertThat(tar.waitFor(2, TimeUnit.MINUTES)).isTrue();
    assertThat(tar.exitValue()).isZero();
  }
}

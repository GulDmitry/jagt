package dev.jagt.orchestrator.stand;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class FakeInstallFeed implements AutoCloseable {

  private final Path root;
  private final HttpServer server;

  FakeInstallFeed(Path root, String jarSha256) throws IOException, NoSuchAlgorithmException {
    this.root = root;
    Path release = Files.createDirectories(root.resolve("feed/download/v1.2.3"));
    Files.writeString(release.resolve("jagt-1.2.3.jar"), "fake jar");
    Files.writeString(release.resolve("jagt-1.2.3.sha256"), jarSha256 + "  jagt-1.2.3.jar\n");
    Path jre = Files.createDirectories(root.resolve("jre/linux/x64/jre/hotspot/normal"));
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jre.resolve("eclipse")))) {
      zip.putNextEntry(new ZipEntry("jdk-25-jre/bin/java"));
      zip.write("#!/bin/sh\necho fake-java-25\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    String jreSha =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(jre.resolve("eclipse"))));
    Files.writeString(jre.resolve("eclipse.sha256.txt"), jreSha + "  OpenJDK25U-jre.zip\n");
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          Path file = root.resolve(exchange.getRequestURI().getPath().substring(1));
          boolean found = Files.isRegularFile(file);
          byte[] body = found ? Files.readAllBytes(file) : new byte[0];
          exchange.sendResponseHeaders(found ? 200 : 404, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
  }

  Process runInstaller() throws IOException {
    String host = "http://host.docker.internal:" + server.getAddress().getPort();
    ProcessBuilder install =
        new ProcessBuilder(
            "docker",
            "run",
            "--rm",
            "--add-host",
            "host.docker.internal:host-gateway",
            "--user",
            Files.getAttribute(root, "unix:uid").toString(),
            "-v",
            root + ":/work",
            "-v",
            Path.of(System.getProperty("stand.repo"), "install.ps1") + ":/install.ps1:ro",
            "-e",
            "HOME=/tmp",
            "-e",
            "JAGT_VERSION=1.2.3",
            "-e",
            "JAGT_HOME=/work/home",
            "-e",
            "JAGT_BIN=/work/bin",
            "-e",
            "JAGT_INSTALL_FEED=" + host + "/feed",
            "-e",
            "JAGT_INSTALL_JRE_FEED=" + host + "/jre",
            System.getProperty("stand.powershell"),
            "pwsh",
            "-NoProfile",
            "-File",
            "/install.ps1");
    install.redirectErrorStream(true);
    return install.start();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}

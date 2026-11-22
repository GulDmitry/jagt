package dev.jagt.orchestrator.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstallTest {

  @TempDir static Path work;

  private final HttpClient http = HttpClient.newHttpClient();
  private final AtomicReference<String> latest = new AtomicReference<>("0.0.1");
  private HttpServer feed;
  private Path log;
  private String feedUrl;
  private Path bin;
  private String path;
  private Path home;
  private String api;
  private ProcessBuilder install;
  private ProcessBuilder launch;
  private final AtomicReference<Process> launcher = new AtomicReference<>();
  private HttpRequest upgrade;
  private HttpRequest releaseCheck;
  private Callable<Boolean> answersState;
  private Callable<Boolean> offersUpgrade;
  private Callable<Boolean> startupCheckDone;
  private Callable<String> currentVersion;
  private String expectedCurrent;

  @BeforeAll
  void layOutTheFeedTheInstallerAndTheLauncher() throws Exception {
    Path jar = Path.of(System.getProperty("install.jar"));
    String sum =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
    feed = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    feed.createContext(
        "/latest",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/tag/v" + latest.get());
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    feed.createContext(
        "/download/",
        exchange -> {
          String name = exchange.getRequestURI().getPath().replaceAll(".*/", "");
          byte[] body =
              name.endsWith(".sha256")
                  ? (sum + "  " + name.replace(".sha256", ".jar") + "\n")
                      .getBytes(StandardCharsets.UTF_8)
                  : Files.readAllBytes(jar);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    feed.start();
    feedUrl = "http://127.0.0.1:" + feed.getAddress().getPort();

    log = work.resolve("run.log");
    home = work.resolve("install");
    bin = work.resolve("bin");
    Path stubs = Files.createDirectories(work.resolve("stubs"));
    Files.writeString(stubs.resolve("gh"), "#!/bin/sh\nexit 0\n");
    Files.writeString(stubs.resolve("gh.cmd"), "@exit /b 0\r\n");
    stubs.resolve("gh").toFile().setExecutable(true);
    path = stubs + File.pathSeparator + System.getenv("PATH");
    Path repo = Path.of(System.getProperty("install.repo"));
    boolean windows = System.getProperty("os.name").startsWith("Windows");
    List<String> installer =
        windows
            ? List.of("pwsh", "-NoProfile", "-File", repo.resolve("install.ps1").toString())
            : List.of("sh", repo.resolve("install.sh").toString());

    int port;
    try (ServerSocket free = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      port = free.getLocalPort();
    }
    api = "http://127.0.0.1:" + port;

    install = new ProcessBuilder(installer);
    install.environment().put("JAGT_VERSION", "0.0.1");
    install.environment().put("JAGT_INSTALL_FEED", feedUrl);
    install.environment().put("JAGT_HOME", home.toString());
    install.environment().put("JAGT_BIN", bin.toString());
    install.environment().put("PATH", path);
    install
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));

    Path launcherFile = bin.resolve(windows ? "jagt.cmd" : "jagt");
    List<String> start =
        windows
            ? List.of("cmd.exe", "/c", launcherFile.toString())
            : List.of(launcherFile.toString());

    launch =
        new ProcessBuilder(
            Stream.concat(
                    start.stream(),
                    Stream.of(
                        "--server.port=" + port,
                        "--orchestrator.terminal=none",
                        "--orchestrator.startup-checks=false"))
                .toList());
    launch.environment().remove("ORCHESTRATOR_ROOT");
    launch.environment().remove("ORCHESTRATOR_CONFIG_FILE");
    launch.environment().put("JAGT_INSTALL_FEED", feedUrl);
    launch.environment().put("JAGT_HOME", home.toString());
    launch.environment().put("JAGT_BIN", bin.toString());
    launch.environment().put("PATH", path);
    launch.environment().put("JAGT__orchestrator__releaseFeed__url", feedUrl);
    launch.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));

    HttpRequest state = HttpRequest.newBuilder(URI.create(api + "/state")).build();
    releaseCheck =
        HttpRequest.newBuilder(URI.create(api + "/api/commands/run?about=releasecheck"))
            .header("Origin", api)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    upgrade =
        HttpRequest.newBuilder(URI.create(api + "/api/commands/upgrade"))
            .header("Origin", api)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    answersState =
        () -> http.send(state, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    HttpRequest commands = HttpRequest.newBuilder(URI.create(api + "/api/commands")).build();
    offersUpgrade =
        () ->
            http.send(commands, HttpResponse.BodyHandlers.ofString())
                .body()
                .contains("\"upgrade\"");
    HttpRequest jobs = HttpRequest.newBuilder(URI.create(api + "/api/commands/jobs")).build();
    startupCheckDone =
        () ->
            http.send(jobs, HttpResponse.BodyHandlers.ofString())
                .body()
                .lines()
                .anyMatch(line -> line.matches("^ +releasecheck .* ran .*"));
    currentVersion =
        windows
            ? () -> Files.readString(home.resolve("current")).strip()
            : () -> Files.readSymbolicLink(home.resolve("current")).toString();
    expectedCurrent = windows ? "0.0.2" : "versions/0.0.2";
  }

  @AfterAll
  void endEverythingTheRunStarted() throws Exception {
    try {
      ProcessHandle.allProcesses()
          .filter(each -> each.info().commandLine().orElse("").contains(work.toString()))
          .forEach(ProcessHandle::destroyForcibly);
      if (log != null && Files.isRegularFile(log)) {
        List<String> lines = Files.readAllLines(log);
        System.out.println(
            String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size())));
      }
    } finally {
      if (feed != null) {
        feed.stop(0);
      }
    }
  }

  @Test
  @Order(1)
  void installsTheFirstReleaseAndStartsItUnderTheLauncher() throws Exception {
    Process installed = install.start();
    assertThat(installed.waitFor(10, TimeUnit.MINUTES)).isTrue();
    assertThat(installed.exitValue()).isZero();

    launcher.set(launch.start());

    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(1))
        .ignoreExceptions()
        .until(answersState);
    assertThat(home.resolve("versions/0.0.1/jagt.jar")).isRegularFile();
    assertThat(work.resolve("bin")).isDirectoryContaining("glob:**/jagt*");
    assertThat(home.resolve("jagt.desktop.yml.dist")).isRegularFile();
    assertThat(home.resolve("roles/session/brief.md.dist")).isRegularFile();
    assertThat(home.resolve("jagt.yml")).doesNotExist();
  }

  @Test
  @Order(2)
  void keepsTheNewerReleaseBesideTheFirstAndUpgradesThroughTheBoardsCommand() throws Exception {
    await().atMost(Duration.ofMinutes(2)).ignoreExceptions().until(startupCheckDone);
    latest.set("0.0.2");
    http.send(releaseCheck, HttpResponse.BodyHandlers.discarding());
    await()
        .atMost(Duration.ofMinutes(2))
        .until(() -> Files.isRegularFile(home.resolve("versions/0.0.2/jagt.jar")));
    await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(offersUpgrade);

    http.send(upgrade, HttpResponse.BodyHandlers.discarding());

    await().atMost(Duration.ofMinutes(3)).ignoreExceptions().until(() -> !offersUpgrade.call());
    await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(answersState);
    assertThat(home.resolve("versions/0.0.1/jagt.jar")).isRegularFile();
    assertThat(currentVersion.call()).isEqualTo(expectedCurrent);
  }

  @Test
  @Order(3)
  @DisabledOnOs(OS.WINDOWS)
  void endsTheJvmAndFreesThePortWhenTheLauncherGetsTerm() throws Exception {
    List<ProcessHandle> jvms =
        ProcessHandle.allProcesses()
            .filter(each -> each.info().commandLine().orElse("").contains(home + "/runtime"))
            .toList();
    assertThat(jvms).isNotEmpty();

    launcher.get().destroy();
    assertThat(launcher.get().waitFor(1, TimeUnit.MINUTES)).isTrue();

    await()
        .atMost(Duration.ofMinutes(1))
        .until(() -> jvms.stream().noneMatch(ProcessHandle::isAlive));
    assertThatThrownBy(answersState::call).isInstanceOf(IOException.class);
  }
}

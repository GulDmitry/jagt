package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.host.Supervisor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "supervisor", havingValue = "launchd")
@RequiredArgsConstructor
@Slf4j
public class LaunchdSupervisor implements Supervisor {

  static final String LABEL = "jagt.orchestrator";

  private static final String STARTED_BY = "XPC_SERVICE_NAME";
  private static final String STAGED = "jagt-run.jar";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final Processes processes;
  private final Host host;

  @Override
  public boolean supervised() {
    return true;
  }

  @Override
  public Supervision install(Path stagedJar) {
    Path jar = stagedJar.resolveSibling(STAGED);
    Optional<String> java = ProcessHandle.current().info().command();
    if (!stagedJar.getFileName().toString().startsWith("jagt-run") || !Files.exists(jar)) {
      return new Supervision.Refused(
          "this process runs " + stagedJar + ", not a staged jar: run `./gradlew stageJar` first");
    }
    if (java.isEmpty()) {
      return new Supervision.Refused("the java this process runs cannot be read");
    }
    try {
      String domain = domain();
      Path plist = plist();
      Files.createDirectories(plist.getParent());
      Files.writeString(plist, definition(java.get(), jar));
      processes.run(null, TIMEOUT, List.of(launchctl(), "bootout", domain + "/" + LABEL));
      processes
          .run(null, TIMEOUT, List.of(launchctl(), "bootstrap", domain, plist.toString()))
          .expectSuccess("launchctl bootstrap");
      return host.env(STARTED_BY).filter(LABEL::equals).isPresent()
          ? new Supervision.Done(
              "launchd keeps " + jar + " running, and this jagt already runs under it")
          : new Supervision.Handover(
              "launchd keeps " + jar + " running: this jagt ends and comes back under launchd");
    } catch (IOException | RuntimeException e) {
      return failed("install", e);
    }
  }

  @Override
  public Supervision restart() {
    if (!Files.exists(plist())) {
      return new Supervision.Refused("nothing is installed: press `supervise` first");
    }
    try {
      processes.runDetached(null, List.of(launchctl(), "kickstart", "-k", domain() + "/" + LABEL));
      return new Supervision.Done("restarting: the board reloads once it answers");
    } catch (RuntimeException e) {
      return failed("restart", e);
    }
  }

  private Supervision failed(String action, Exception cause) {
    log.atError()
        .setMessage("supervisor call failed")
        .addKeyValue("action", action)
        .addKeyValue("cause", cause.toString())
        .log();
    return new Supervision.Refused(action + " failed: " + cause.getMessage());
  }

  private Path plist() {
    return host.home().resolve("Library/LaunchAgents").resolve(LABEL + ".plist");
  }

  private String domain() {
    String uid = processes.run(null, TIMEOUT, List.of("id", "-u")).expectSuccess("id -u").stdout();
    return "gui/" + uid.strip();
  }

  private static String launchctl() {
    return Executables.resolve("launchctl");
  }

  private String definition(String java, Path jar) {
    return String.join(
        "\n",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
        "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\""
            + " \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">",
        "<plist version=\"1.0\">",
        "<dict>",
        "  <key>Label</key><string>" + LABEL + "</string>",
        "  <key>ProgramArguments</key>",
        "  <array><string>"
            + escaped(java)
            + "</string><string>-jar</string><string>"
            + escaped(jar.toString())
            + "</string></array>",
        "  <key>WorkingDirectory</key><string>"
            + escaped(host.workingDir().toString())
            + "</string>",
        "  <key>RunAtLoad</key><true/>",
        "  <key>KeepAlive</key><true/>",
        "  <key>ThrottleInterval</key><integer>10</integer>",
        "</dict>",
        "</plist>",
        "");
  }

  private static String escaped(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}

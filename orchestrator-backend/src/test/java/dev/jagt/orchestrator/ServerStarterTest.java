package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ServerStarterTest {

  private static final Path SERVER = Path.of("../jagt.server.yml.dist");
  private static final Path DESKTOP = Path.of("../jagt.desktop.yml.dist");

  @Test
  void everyKeyOfTheServerStarterIsDescribedInTheDesktopReferenceAlone() {
    String server = SourceRoots.read(SERVER);
    String desktop = SourceRoots.read(DESKTOP);

    assertThat(
            Pattern.compile("(?m)^\\s*([a-zA-Z]+):")
                .matcher(server)
                .results()
                .map(match -> match.group(1))
                .distinct()
                .filter(
                    key -> !Pattern.compile("(?m)^\\s*#?\\s*" + key + ":").matcher(desktop).find()))
        .isEmpty();
  }

  @Test
  void theServerStarterDescribesNoKeyInProse() {
    String server = SourceRoots.read(SERVER);

    assertThat(Pattern.compile("(?m)^\\s*#.{100,}$").matcher(server).find()).isFalse();
  }

  @Test
  void theServerStarterEndsSessionsOnStopAndLogsToTheConsole() {
    String server = SourceRoots.read(SERVER);

    assertThat(server).containsPattern("(?m)^  onStop: end\\b");
    assertThat(server).containsPattern("(?m)^  logging: console\\b");
  }

  @Test
  void theServerStarterTakesNoIdentityFromLiterals() {
    String server = SourceRoots.read(SERVER);

    assertThat(server).containsPattern("(?m)^\\s+mcpConfig: \\$\\{");
    assertThat(server).containsPattern("(?m)^\\s+assignee: \\$\\{");
    assertThat(server).containsPattern("(?m)webhookEnv: [A-Z][A-Z0-9_]*\\s*}");
    assertThat(server)
        .doesNotContainPattern("(?m)^\\s+(mcpConfig|assignee|passwordHash): [^$\\s#]");
  }

  @Test
  void theServerStarterSitsBehindTransportSecurityIngressWithSecureCookieAndSessionTimeout() {
    String server = SourceRoots.read(SERVER);

    assertThat(server).containsPattern("(?m)^  forward-headers-strategy: native\\b");
    assertThat(server).containsPattern("(?m)^  servlet\\.session\\.cookie\\.secure: true\\b");
    assertThat(server).containsPattern("(?m)^  servlet\\.session\\.timeout: 30m\\b");
  }

  @Test
  void theServerStarterDeploysAnApprovedRequestAndLetsTheMasterActWhereTheDesktopDoesNot() {
    String server = SourceRoots.read(SERVER);
    String desktop = SourceRoots.read(DESKTOP);

    assertThat(server).containsPattern("(?m)^  deploy:\\n    onApproval: true\\b");
    assertThat(server).containsPattern("(?m)^    master:\\n      mode: act\\b");
    assertThat(server).containsPattern("(?m)^  autoReview:\\n    enabled: true\\b");
    assertThat(server).containsPattern("(?m)^    waitingMaxIntervalMinutes: 60\\b");
    assertThat(desktop).containsPattern("(?m)^  # deploy:\\n  #   onApproval: false\\b");
    assertThat(desktop).containsPattern("(?m)^    waitingMaxIntervalMinutes: 60\\b");
  }

  @Test
  void theServerStarterTurnsTheAcceptanceRoleOn() {
    String server = SourceRoots.read(SERVER);

    assertThat(server).containsPattern("(?m)^    acceptance:\\n      enabled: true\\b");
  }
}

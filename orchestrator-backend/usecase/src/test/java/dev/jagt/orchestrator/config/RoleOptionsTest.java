package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleOptionsTest {

  @Test
  void theFoldersRunnerIsItsDriverDirectoryArgvAndTimeout(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml"),
        "flows: {driver: playwright, path: /flows, command: [npx, test], timeout: 2m}\n");

    RoleOptions.Flows flows = RoleOptions.read(folder).flows();

    assertThat(flows)
        .isEqualTo(
            new RoleOptions.Flows(
                "playwright", "/flows", List.of("npx", "test"), Duration.ofMinutes(2)));
  }

  @Test
  void aTimeoutThatIsNoDurationLeavesTheDefaultAndIsNamedUnread(@TempDir Path folder)
      throws Exception {
    Files.writeString(folder.resolve("role.yml"), "flows: {timeout: soon}\n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.flows().timeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(options.unread()).singleElement().asString().contains("flows.timeout soon");
  }

  @Test
  void anEnvironmentNamingNoUrlIsLeftOut(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml"),
        "environments: [{landed: https://a.test/up}, {url: https://b.test}]\n");

    List<RoleOptions.Environment> environments = RoleOptions.read(folder).environments();

    assertThat(environments).containsExactly(new RoleOptions.Environment("https://b.test", ""));
  }

  @Test
  void aCopyThatDoesNotParseLeavesTheShippedEnvironmentsStanding(@TempDir Path folder)
      throws Exception {
    Files.writeString(folder.resolve("role.yml.dist"), "environments: [{url: https://a.test}]\n");
    Files.writeString(folder.resolve("role.yml"), "environments: [{url: \n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.environments())
        .containsExactly(new RoleOptions.Environment("https://a.test", ""));
  }
}

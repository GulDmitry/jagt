package dev.jagt.orchestrator.config.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
  void timeoutThatIsNoDurationLeavesTheDefaultAndIsNamedUnread(@TempDir Path folder)
      throws Exception {
    Files.writeString(folder.resolve("role.yml"), "flows: {timeout: soon}\n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.flows().timeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(options.unread()).singleElement().asString().contains("flows.timeout soon");
  }

  @Test
  void environmentNamingNoUrlIsLeftOut(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml"),
        "environments: [{landed: https://a.test/up}, {url: https://b.test}]\n");

    List<RoleOptions.Environment> environments = RoleOptions.read(folder).environments();

    assertThat(environments).containsExactly(new RoleOptions.Environment("https://b.test", ""));
  }

  @Test
  void copyThatDoesNotParseLeavesTheShippedEnvironmentsStanding(@TempDir Path folder)
      throws Exception {
    Files.writeString(folder.resolve("role.yml.dist"), "environments: [{url: https://a.test}]\n");
    Files.writeString(folder.resolve("role.yml"), "environments: [{url: \n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.environments())
        .containsExactly(new RoleOptions.Environment("https://a.test", ""));
  }

  @Test
  void relativeWorkspaceIsTheRoleFoldersAndNoneOrAbsentIsNoWorkspace(
      @TempDir Path folder, @TempDir Path other) throws Exception {
    Files.writeString(folder.resolve("role.yml"), "workspace: ws\n");
    final Path relative = RoleOptions.read(folder).workspacePath().orElseThrow();
    Files.writeString(folder.resolve("role.yml"), "workspace: none\n");
    final Optional<Path> none = RoleOptions.read(folder).workspacePath();
    Files.writeString(folder.resolve("role.yml"), "model: sonnet\n");
    final Optional<Path> absent = RoleOptions.read(folder).workspacePath();
    Files.writeString(folder.resolve("role.yml"), "workspace: " + other + "\n");
    Path absolute = RoleOptions.read(folder).workspacePath().orElseThrow();

    assertThat(relative).isEqualTo(folder.toAbsolutePath().resolve("ws"));
    assertThat(none).isEmpty();
    assertThat(absent).isEmpty();
    assertThat(absolute).isEqualTo(other.toAbsolutePath());
  }

  @Test
  void workspaceMergeRequestIsOffUntilTheRoleFolderTurnsItOn(@TempDir Path folder)
      throws Exception {
    final boolean absent = RoleOptions.read(folder).workspaceMergeRequest();
    Files.writeString(folder.resolve("role.yml"), "workspace: ws\n");
    final boolean scalar = RoleOptions.read(folder).workspaceMergeRequest();
    Files.writeString(folder.resolve("role.yml"), "workspace:\n  path: ws\n  mergeRequest: true\n");
    RoleOptions on = RoleOptions.read(folder);

    assertThat(absent).isFalse();
    assertThat(scalar).isFalse();
    assertThat(on.workspaceMergeRequest()).isTrue();
    assertThat(on.workspacePath()).contains(folder.toAbsolutePath().resolve("ws"));
  }

  @Test
  void mergeRequestSwitchThatIsNoBooleanIsNamedAndStaysOff(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml"), "workspace:\n  path: ws\n  mergeRequest: maybe\n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.workspaceMergeRequest()).isFalse();
    assertThat(options.unread())
        .containsExactly("workspace.mergeRequest maybe is neither true nor false");
  }

  @Test
  void workspaceMergeRequestAloneKeepsTheDistPath(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml.dist"), "workspace:\n  path: ws\n  mergeRequest: false\n");
    Files.writeString(folder.resolve("role.yml"), "workspace:\n  mergeRequest: true\n");

    RoleOptions options = RoleOptions.read(folder);

    assertThat(options.workspaceMergeRequest()).isTrue();
    assertThat(options.workspacePath()).contains(folder.toAbsolutePath().resolve("ws"));
  }

  @Test
  void workspaceLayoutTheFolderMapsStandsOverTheShippedOne(@TempDir Path folder) throws Exception {
    Files.writeString(
        folder.resolve("role.yml.dist"),
        "workspace:\n  path: ws\n  index: index.md\n  steps: steps\n  flows: flows\n");
    Files.writeString(
        folder.resolve("role.yml"), "workspace:\n  index: wiki/index.md\n  steps: lib\n");

    RoleOptions.Layout layout = RoleOptions.read(folder).layout();

    assertThat(layout)
        .isEqualTo(new RoleOptions.Layout(false, "wiki/index.md", "lib", "flows", "scenarios"));
  }

  @Test
  void layoutNamesEachWorkspacePlaceholderOfTheBrief() {
    RoleOptions.Layout layout =
        new RoleOptions.Layout(false, "wiki/index.md", "lib", "e2e", "drafts");

    String brief =
        layout.fill(
            "{workspace.index} {workspace.steps}/ {workspace.flows}/ {workspace.scenarios}/");

    assertThat(brief).isEqualTo("wiki/index.md lib/ e2e/ drafts/");
  }
}

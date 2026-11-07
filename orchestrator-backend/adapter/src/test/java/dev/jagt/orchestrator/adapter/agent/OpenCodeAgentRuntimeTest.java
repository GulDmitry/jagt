package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AgentConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.SandboxConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenCodeAgentRuntimeTest {

  @Test
  void reachesJagtOverItsMcpUrlCarryingTheWorktreeAsTheCaller(@TempDir Path root) throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());

    new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:9111/mcp"),
            config)
        .provisionWorktree(AgentWorktree.of(worktree, root));

    JsonNode server =
        new JsonMapper()
            .readTree(worktree.resolve(".jagt/opencode/opencode.json").toFile())
            .path("mcp")
            .path("jagt-orchestrator");
    assertThat(server.path("type").asString()).isEqualTo("remote");
    assertThat(server.path("url").asString()).isEqualTo("http://127.0.0.1:9111/mcp");
    assertThat(server.path("headers").path("X-Working-Directory").asString())
        .isEqualTo(worktree.toString());
    assertThat(server.path("oauth").asBoolean(true)).isFalse();
    assertThat(server.path("enabled").asBoolean(false)).isTrue();
  }

  @Test
  void runsUnattendedBecauseNobodyApprovesInTheWindowAndNeverUpdatesOrShares(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());

    new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            config)
        .provisionWorktree(AgentWorktree.of(worktree, root));

    JsonNode written =
        new JsonMapper().readTree(worktree.resolve(".jagt/opencode/opencode.json").toFile());
    assertThat(written.path("permission").path("edit").asString()).isEqualTo("allow");
    assertThat(written.path("permission").path("bash").asString()).isEqualTo("allow");
    assertThat(written.path("permission").path("webfetch").asString()).isEqualTo("allow");
    assertThat(written.path("autoupdate").asBoolean(true)).isFalse();
    assertThat(written.path("share").asString()).isEqualTo("disabled");
    assertThat(written.path("$schema").asString()).isEqualTo("https://opencode.ai/config.json");
  }

  @Test
  void usesTheConfiguredLocalEndpointAndModelAsTheSessionsModel(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());

    new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            new OpenCodeProperties("", "http://127.0.0.1:8080/v1", "qwen-4b", null),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            config)
        .provisionWorktree(AgentWorktree.of(worktree, root));

    JsonNode written =
        new JsonMapper().readTree(worktree.resolve(".jagt/opencode/opencode.json").toFile());
    JsonNode local = written.path("provider").path("local");
    assertThat(local.path("npm").asString()).isEqualTo("@ai-sdk/openai-compatible");
    assertThat(local.path("options").path("baseURL").asString())
        .isEqualTo("http://127.0.0.1:8080/v1");
    assertThat(local.path("models").has("qwen-4b")).isTrue();
    assertThat(written.path("model").asString()).isEqualTo("local/qwen-4b");
  }

  @Test
  void leavesTheModelToTheUsersOwnOpenCodeConfigWhereNoEndpointIsSet(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());

    new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            new OpenCodeProperties("", "", "qwen-4b", null),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            config)
        .provisionWorktree(AgentWorktree.of(worktree, root));

    JsonNode written =
        new JsonMapper().readTree(worktree.resolve(".jagt/opencode/opencode.json").toFile());
    assertThat(written.has("provider")).isFalse();
    assertThat(written.has("model")).isFalse();
  }

  @Test
  void startsTheCliOnTheWorktreesOwnConfigWithTheProjectsConfigDisabled(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## start\n\nRead AGENTS.md and work\n");

    var runtime =
        new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            mock(ConfigService.class));

    assertThat(runtime.launchCommand(Path.of("/wt/ABC-1-proj"), false))
        .isEqualTo(
            "OPENCODE_CONFIG='/wt/ABC-1-proj/.jagt/opencode/opencode.json'"
                + " OPENCODE_DISABLE_PROJECT_CONFIG=1 opencode --standalone --prompt 'Read"
                + " AGENTS.md and work'");
  }

  @Test
  void startsOnThePlanAgentInPlanModeSoNothingIsEditedBeforeTheHumanApproves() {
    var runtime =
        new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            new OpenCodeProperties("/opt/bin/opencode", "", "", null),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            mock(ConfigService.class));

    assertThat(runtime.launchCommand(Path.of("/wt/ABC-1-proj"), true, "", "go"))
        .isEqualTo(
            "OPENCODE_CONFIG='/wt/ABC-1-proj/.jagt/opencode/opencode.json'"
                + " OPENCODE_DISABLE_PROJECT_CONFIG=1"
                + " OPENCODE_CONFIG_CONTENT='{\"default_agent\":\"plan\"}' /opt/bin/opencode"
                + " --standalone --prompt 'go'");
  }

  @Test
  void refusesToStartWhereTheSandboxIsOnBecauseOpenCodeHasNone() {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withSandbox(SandboxConfig.defaults().withEnabled(true))));

    var runtime =
        new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            config);

    assertThat(runtime.problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.agent.cli");
  }

  @Test
  void startsWhereTheSandboxIsOff() {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    AgentConfig.defaults()
                        .withSandbox(SandboxConfig.defaults().withEnabled(false))));

    var runtime =
        new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            config);

    assertThat(runtime.problems()).isEmpty();
  }

  @Test
  void fallsBackToTheCliNameWhereTheConfiguredCommandIsBlank() {
    assertThat(new OpenCodeProperties("  ", "", "", null).command()).isEqualTo("opencode");
  }

  @Test
  void namesItselfOpenCodeWhereTheBoardSaysWhichAgentRuns() {
    var runtime =
        new OpenCodeAgentRuntime(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
            OpenCodeProperties.defaults(),
            new McpEndpoint("http://127.0.0.1:8290/mcp"),
            mock(ConfigService.class));

    assertThat(runtime.displayName()).isEqualTo("OpenCode");
  }
}

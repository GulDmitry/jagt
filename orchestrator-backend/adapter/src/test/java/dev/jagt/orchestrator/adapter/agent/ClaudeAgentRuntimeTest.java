package dev.jagt.orchestrator.adapter.agent;


import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.Sandbox;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeAgentRuntimeTest {

    @Test
    void launchesTheClaudeCliWithTheBootstrapPromptQuoted() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults()
                .withAgentPrompt("Read AGENTS.md and work"), new ClaudeProperties("claude"),
                new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.launchCommand(Path.of("/wt"), false))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'Read AGENTS.md and work'");
    }

    @Test
    void startsNoCliPlanModeWhenThePlanIsAskedFirstBecauseTheBoardApprovesIt() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.launchCommand(Path.of("/wt"), true))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'go'");
    }

    @Test
    void continuesTheWorktreesNewestConversationWhenJagtReEntersASessionItDidNotEnd() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults()
                .withAgentPrompt("go").withAgentRevivePrompt("pick up where you left off"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.reviveCommand(Path.of("/wt")))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --continue 'pick up where you left off'");
    }

    @Test
    void keepsAPromptWithAnApostropheOneShellArgumentInsteadOfBreakingTheLaunch() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("it's fine"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.launchCommand(Path.of("/wt"), false))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'it'\\''s fine'");
    }

    @Test
    void trustsAProvisionedWorktreeSoNoDialogStopsTheSessionAndForgetsItOnceRetired(@TempDir Path root)
            throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-7-proj"));
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"),
                Clock.systemUTC());
        String key = worktree.toRealPath().toString();

        runtime.provisionWorktree(AgentWorktree.of(worktree, root));
        boolean trusted = new JsonMapper().readTree(Files.readString(ClaudeTrust.configFile()))
                .path("projects").path(key).path("hasTrustDialogAccepted").asBoolean(false);
        runtime.retireWorktree(worktree);
        boolean remembered = new JsonMapper().readTree(Files.readString(ClaudeTrust.configFile()))
                .path("projects").has(key);

        assertThat(trusted).isTrue();
        assertThat(remembered).isFalse();
    }

    @Test
    void declaresTheJagtMcpServerOverHttpWithThisWorktreeAsTheCaller(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        var config = new JsonMapper().readTree(Files.readString(worktree.resolve(".mcp.json")))
                .path("mcpServers").path("jagt-orchestrator");
        assertThat(config.path("type").asString("")).isEqualTo("http");
        assertThat(config.path("url").asString("")).isEqualTo("http://localhost:8290/mcp");
        assertThat(config.path("headers").path("X-Working-Directory").asString(""))
                .isEqualTo(worktree.toAbsolutePath().normalize().toString());
    }

    @Test
    void letsTheSessionEditWhereverItsShellMayWrite(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root)
                        .withSandbox(Sandbox.on(List.of(Path.of("/w/ABC-1"), Path.of("/notes")), List.of())));

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
                .path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));
        assertThat(allow).contains("Edit(//w/ABC-1/**)", "Edit(//notes/**)");
    }

    @Test
    void leavesNoStdioProxyInTheWorktree(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
    }

    @Test
    void aliasesClaudeMdToTheSharedSystemKnowledgeFileSoOnlyOneCopyExists(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "system knowledge");

        assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("system knowledge");
    }

    @Test
    void writesTheBriefingBesideTheRepositorysOwnClaudeMdInsteadOfReplacingIt(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());
        Path briefing = runtime.systemKnowledgeFile(worktree);
        runtime.provisionWorktree(AgentWorktree.of(worktree, root));

        assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
        assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("project rules");
    }

    @Test
    void writesTheBriefingBesideTheRepositorysOwnAgentsMdInsteadOfReplacingIt(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "project rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());
        Path briefing = runtime.systemKnowledgeFile(worktree);
        runtime.provisionWorktree(AgentWorktree.of(worktree, root));

        assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
        assertThat(Files.readString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)))
                .isEqualTo("project rules");
    }

    @Test
    void refusesToStartAnAgentWhenTheRepositoryOwnsEveryNameTheBriefingCouldTake(@TempDir Path root)
            throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");
        Files.writeString(worktree.resolve("CLAUDE.local.md"), "personal rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThatThrownBy(() -> runtime.systemKnowledgeFile(worktree))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLAUDE.local.md");
    }

    @Test
    void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson("sob-ai:Engineer", null, null, null);

        String style = new JsonMapper().readTree(json).path("outputStyle").asString(null);

        assertThat(style).isEqualTo("sob-ai:Engineer");
    }

    @Test
    void compactsATaskSessionLongBeforeItsContextWindowIsFull() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null, null);

        String window = new JsonMapper().readTree(json).path("env").path("CLAUDE_CODE_AUTO_COMPACT_WINDOW").asString("");

        assertThat(window).isEqualTo("300000");
    }

    @Test
    void keepsATaskSessionFromWritingTheRepositorysAutoMemory() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null, null);

        boolean enabled = new JsonMapper().readTree(json).path("autoMemoryEnabled").asBoolean(true);

        assertThat(enabled).isFalse();
    }

    @Test
    void preApprovesTheJagtToolsAndTheAgentsGitInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null, null);

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(json).path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));

        assertThat(allow).contains("mcp__jagt-orchestrator", "Bash(git:*)");
    }

    @Test
    void preApprovesTheConfiguredToolsBesideJagtsOwnInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, List.of("mcp__host__open_request"), null);

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(json).path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));

        assertThat(allow).containsExactly("mcp__jagt-orchestrator", "Bash(git:*)", "mcp__host__open_request");
    }

    @Test
    void confinesTheShellsWritesToTheTaskWithEveryHostReachable() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null, ClaudeAgentRuntime.sandboxJson(
                Sandbox.on(List.of(Path.of("/w/ABC-42"), Path.of("/repo/.git")), List.of("ssh *"))));

        JsonNode sandbox = new JsonMapper().readTree(json).path("sandbox");

        assertThat(sandbox).isEqualTo(new JsonMapper().readTree("""
                {"enabled":true,"failIfUnavailable":true,"autoAllowBashIfSandboxed":true,\
                "allowUnsandboxedCommands":true,"enableWeakerNetworkIsolation":true,"excludedCommands":["ssh *"],\
                "filesystem":{"allowWrite":["/w/ABC-42","/repo/.git"]},\
                "network":{"allowedDomains":["*"],"allowLocalBinding":true,"allowAllUnixSockets":true}}"""));
    }

    @Test
    void leavesTheShellUnsandboxedWhenTheSandboxIsOff() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null, ClaudeAgentRuntime.sandboxJson(Sandbox.off()));

        assertThat(new JsonMapper().readTree(json).has("sandbox")).isFalse();
    }

    @Test
    void disablesConfiguredPluginsInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, List.of("jdtls-lsp@claude-plugins-official"), null, null);

        boolean enabled = new JsonMapper().readTree(json)
                .path("enabledPlugins").path("jdtls-lsp@claude-plugins-official").asBoolean(true);

        assertThat(enabled).isFalse();
    }

    @Test
    void writesADeclaredEventAsAHookThatReportsTheStateItMeans(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        String command = new JsonMapper()
                .readTree(Files.readString(worktree.resolve(".claude").resolve("settings.local.json")))
                .path("hooks").path("SessionEnd").path(0).path("hooks").path(0).path("command").asString("");
        assertThat(command).contains("/api/agent/session/gone", worktree.toString());
    }

    @Test
    void asksJagtAtTheEndOfEveryTurnWhetherTheTurnMayEnd(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        String command = new JsonMapper()
                .readTree(Files.readString(worktree.resolve(".claude").resolve("settings.local.json")))
                .path("hooks").path("Stop").path(0).path("hooks").path(0).path("command").asString("");
        assertThat(command).contains("/api/agent/session/turn-end", worktree.toString());
    }

    @Test
    void reportsATurnEndedOnAnApiErrorThroughItsOwnUnmatchedEvent(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        var hook = new JsonMapper()
                .readTree(Files.readString(worktree.resolve(".claude").resolve("settings.local.json")))
                .path("hooks").path("StopFailure").path(0);
        assertThat(hook.has("matcher")).isFalse();
        assertThat(hook.path("hooks").path(0).path("command").asString(""))
                .contains("/api/agent/session/turn-failure", worktree.toString());
    }

    @Test
    void refusesATurnEndInTheShapeClaudeReadsAsKeepGoing() {
        String refusal = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .refusedTurnEnd("report \"first\"");

        var answer = new JsonMapper().readTree(refusal);
        assertThat(answer.path("decision").asString("")).isEqualTo("block");
        assertThat(answer.path("reason").asString("")).isEqualTo("report \"first\"");
    }

    @Test
    void showsTheHumanALineInTheFieldClaudeKeepsFromTheModel() {
        String line = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .toldTheHuman("with the \"Master\"");

        var answer = new JsonMapper().readTree(line);
        assertThat(answer.path("systemMessage").asString("")).isEqualTo("with the \"Master\"");
    }

    @Test
    void writesTheGateAsAHookOnTheShellAndTheFileWritingTools(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC())
                .provisionWorktree(AgentWorktree.of(worktree, root));

        var gate = new JsonMapper().readTree(Files.readString(
                        worktree.resolve(".claude/settings.local.json")))
                .path("hooks").path("PreToolUse").path(0);
        assertThat(gate.path("matcher").asString("")).isEqualTo("Bash|Edit|Write|MultiEdit|NotebookEdit");
        assertThat(gate.path("hooks").path(0).path("command").asString(""))
                .contains("/api/agent/tool", worktree.toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\u276f\u00a0where is the merge re|true",
            "\u276f\u00a0\u001b[2mclose the task\u001b[0m|false",
            "\u276f an earlier message\\n\u276f\u00a0|false"})
    void tellsADraftTheHumanIsComposingFromTheSuggestionAndFromWhatTheyAlreadySent(String screen, boolean draft) {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults(), new ClaudeProperties("claude"),
                new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.holdsDraft(screen.translateEscapes())).isEqualTo(draft);
    }

    @Test
    void pinsTheModelAskedForAsOneShellArgument() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.launchCommand(Path.of("/wt"), true, " opus "))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --model 'opus' 'go'");
    }

    @Test
    void leavesTheModelToTheCliWhenNoneIsAskedFor() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"), Clock.systemUTC());

        assertThat(runtime.launchCommand(Path.of("/wt"), false, "  "))
                .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'go'");
    }

    @Test
    void skipsBlankPluginsInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, List.of("  ", "jdtls-lsp@claude-plugins-official"), null, null);

        var plugins = new JsonMapper().readTree(json).path("enabledPlugins");

        assertThat(plugins.size()).isEqualTo(1);
        assertThat(plugins.has("jdtls-lsp@claude-plugins-official")).isTrue();
    }

    @Test
    void skipsBlankToolsInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, List.of("  ", " mcp__host__open_request "), null);

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(json).path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));

        assertThat(allow).containsExactly("mcp__jagt-orchestrator", "Bash(git:*)", "mcp__host__open_request");
    }

    @Test
    void fallsBackToTheCliNameWhereTheConfiguredCommandIsBlank() {
        assertThat(new ClaudeProperties("  ").command()).isEqualTo("claude");
    }

    @Test
    void keepsAConfiguredCommand() {
        assertThat(new ClaudeProperties("/opt/bin/claude").command()).isEqualTo("/opt/bin/claude");
    }
}

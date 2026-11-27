package dev.jagt.orchestrator.adapter.agent;

import static dev.jagt.orchestrator.adapter.agent.TestClaudeRuntimeBuilder.claudeRuntime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.adapter.agent.trust.ClaudeTrust;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.AgentWorktree;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.agent.Sandbox;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ClaudeAgentRuntimeTest {

  @Test
  void launchesTheClaudeCliWithTheBootstrapPromptQuoted(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## start\n\nRead AGENTS.md and work\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.WORK))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'Read AGENTS.md and work'");
  }

  @Test
  void startsNoCliPlanModeWhenThePlanIsAskedFirstBecauseTheBoardApprovesIt(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\ngo\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.PLAN))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'go'");
  }

  @Test
  void continuesTheWorktreesNewestConversationWhenJagtReEntersSessionItDidNotEnd(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"),
        "## start\n\ngo\n\n## revive\n\npick up where you left off\n");
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(
        Files.createDirectories(
                ClaudeTranscripts.projectsDir(FakeHost.machine())
                    .resolve(ClaudeTranscripts.slug(worktree)))
            .resolve("s.jsonl"),
        "{}\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.reviveCommand(worktree))
        .isEqualTo(
            "CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --continue 'pick up where you left"
                + " off'");
  }

  @Test
  void startsAfreshWhenJagtRevivesWorktreeHoldingNoConversation(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"),
        "## start\n\ngo\n\n## revive\n\npick up where you left off\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.reviveCommand(root.resolve("wt")))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'go'");
  }

  @Test
  void keepsPromptWithAnApostropheOneShellArgumentInsteadOfBreakingTheLaunch(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\nit's fine\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.WORK))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'it'\\''s fine'");
  }

  @Test
  void trustsProvisionedWorktreeSoNoDialogStopsTheSessionAndForgetsItOnceRetired(@TempDir Path root)
      throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-7-proj"));
    var runtime = claudeRuntime().build(new FakeClock());
    String key = worktree.toRealPath().toString();

    runtime.provisionWorktree(AgentWorktree.of(worktree, root));
    boolean trusted =
        new JsonMapper()
            .readTree(Files.readString(ClaudeTrust.configFile(FakeHost.machine())))
            .path("projects")
            .path(key)
            .path("hasTrustDialogAccepted")
            .asBoolean(false);
    runtime.retireWorktree(worktree);
    boolean remembered =
        new JsonMapper()
            .readTree(Files.readString(ClaudeTrust.configFile(FakeHost.machine())))
            .path("projects")
            .has(key);

    assertThat(trusted).isTrue();
    assertThat(remembered).isFalse();
  }

  @Test
  void trustsTheWorktreeAgainAtLaunchWhenAnotherWriterDroppedTheFlagSinceProvisioning(
      @TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-8-proj"));
    var runtime = claudeRuntime().build(new FakeClock());
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));
    ClaudeTrust.forget(ClaudeTrust.configFile(FakeHost.machine()), worktree);

    runtime.beforeLaunch(worktree);

    boolean trusted =
        new JsonMapper()
            .readTree(Files.readString(ClaudeTrust.configFile(FakeHost.machine())))
            .path("projects")
            .path(worktree.toRealPath().toString())
            .path("hasTrustDialogAccepted")
            .asBoolean(false);
    assertThat(trusted).isTrue();
  }

  @Test
  void letsTheSessionEditWhereverItsShellMayWrite(@TempDir Path root) throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();

    claudeRuntime()
        .build(new FakeClock())
        .provisionWorktree(
            AgentWorktree.of(worktree, root)
                .withSandbox(
                    Sandbox.on(List.of(Path.of("/w/ABC-1"), Path.of("/notes")), List.of())));

    List<String> allow = new ArrayList<>();
    new JsonMapper()
        .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
        .path("permissions")
        .path("allow")
        .forEach(n -> allow.add(n.asString("")));
    assertThat(allow).contains("Edit(//w/ABC-1/**)", "Edit(//notes/**)");
  }

  @Test
  void letsRunningSessionReachDirectoryThatAppearedSinceItStarted(@TempDir Path root)
      throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-proj"));
    Path deploy = Files.createDirectories(root.resolve("ABC-1-deploy"));
    var runtime = claudeRuntime().build(new FakeClock());
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));

    AgentRuntime.Admission admission = runtime.admit(worktree, deploy);

    var permissions =
        new JsonMapper()
            .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
            .path("permissions");
    List<String> allow = new ArrayList<>();
    permissions.path("allow").forEach(n -> allow.add(n.asString("")));
    List<String> directories = new ArrayList<>();
    permissions.path("additionalDirectories").forEach(n -> directories.add(n.asString("")));
    assertThat(admission).isEqualTo(AgentRuntime.Admission.GRANTED);
    assertThat(allow).contains("mcp__jagt-orchestrator", "Edit(/" + deploy.toRealPath() + "/**)");
    assertThat(directories).containsExactly(deploy.toRealPath().toString());
  }

  @Test
  void letsSandboxedSessionWriteInDirectoryItWasAdmittedTo(@TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-proj"));
    Path deploy = Files.createDirectories(root.resolve("ABC-1-deploy"));
    var runtime = claudeRuntime().build(new FakeClock());
    runtime.provisionWorktree(
        AgentWorktree.of(worktree, root)
            .withSandbox(Sandbox.on(List.of(Path.of("/w/ABC-1")), List.of())));

    runtime.admit(worktree, deploy);

    List<String> writable = new ArrayList<>();
    new JsonMapper()
        .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
        .path("sandbox")
        .path("filesystem")
        .path("allowWrite")
        .forEach(n -> writable.add(n.asString("")));
    assertThat(writable).containsExactly("/w/ABC-1", deploy.toRealPath().toString());
  }

  @Test
  void grantsDirectoryNotThereYetAsPathRuleAlone(@TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-proj"));
    var runtime = claudeRuntime().build(new FakeClock());
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));

    runtime.admit(worktree, Path.of("/src/ABC-1-deploy"));

    var permissions =
        new JsonMapper()
            .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
            .path("permissions");
    List<String> allow = new ArrayList<>();
    permissions.path("allow").forEach(n -> allow.add(n.asString("")));
    assertThat(allow).contains("Read(//src/ABC-1-deploy/**)", "Edit(//src/ABC-1-deploy/**)");
    assertThat(permissions.has("additionalDirectories")).isFalse();
  }

  @Test
  void admitsNothingWhereItProvisionedNothing(@TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-proj"));
    var runtime = claudeRuntime().build(new FakeClock());

    AgentRuntime.Admission admission = runtime.admit(worktree, Path.of("/src/ABC-1-deploy"));

    assertThat(admission).isEqualTo(AgentRuntime.Admission.UNPROVISIONED);
  }

  @Test
  void writesTheBriefingBesideTheRepositorysOwnAgentsMdInsteadOfReplacingIt(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "project rules");

    var runtime = claudeRuntime().build(new FakeClock());
    Path briefing = runtime.systemKnowledgeFile(worktree);
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));

    assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
    assertThat(Files.readString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)))
        .isEqualTo("project rules");
  }

  @Test
  void findsTheBriefingAtTheRootNameOnceTheWorktreeIsProvisionedWhereTheRootWasFree(
      @TempDir Path root) throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    var runtime = claudeRuntime().build(new FakeClock());
    Path briefing = runtime.systemKnowledgeFile(worktree);
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));
    Files.writeString(briefing, "the briefing");

    Path again = runtime.briefedFile(worktree);

    assertThat(again).isEqualTo(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE));
  }

  @Test
  void refusesToStartAnAgentWhenTheRepositoryOwnsEveryNameTheBriefingCouldTake(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");
    Files.writeString(worktree.resolve("CLAUDE.local.md"), "personal rules");

    var runtime = claudeRuntime().build(new FakeClock());

    assertThatThrownBy(() -> runtime.systemKnowledgeFile(worktree))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLAUDE.local.md");
  }

  @Test
  void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
    String json = ClaudeAgentRuntime.settingsJson("team:Engineer", List.of(), List.of());

    String style = new JsonMapper().readTree(json).path("outputStyle").asString(null);

    assertThat(style).isEqualTo("team:Engineer");
  }

  @Test
  void compactsTaskSessionLongBeforeItsContextWindowIsFull() {
    String json = ClaudeAgentRuntime.settingsJson(null, List.of(), List.of());

    String window =
        new JsonMapper()
            .readTree(json)
            .path("env")
            .path("CLAUDE_CODE_AUTO_COMPACT_WINDOW")
            .asString("");

    assertThat(window).isEqualTo("300000");
  }

  @Test
  void keepsTaskSessionFromWritingTheRepositorysAutoMemory() {
    String json = ClaudeAgentRuntime.settingsJson(null, List.of(), List.of());

    boolean enabled = new JsonMapper().readTree(json).path("autoMemoryEnabled").asBoolean(true);

    assertThat(enabled).isFalse();
  }

  @Test
  void keepsAnIdleTaskSessionUncompactedSoColdPromptStillFindsItCold() {
    String json = ClaudeAgentRuntime.settingsJson(null, List.of(), List.of());

    boolean idleCompaction = new JsonMapper().readTree(json).path("idleCompaction").asBoolean(true);

    assertThat(idleCompaction).isFalse();
  }

  @Test
  void deniesTheQuestionDialogSoAnAnswerRelayedToTheSessionIsNotHeldBehindIt() {
    String json = ClaudeAgentRuntime.settingsJson(null, List.of(), List.of());

    List<String> deny = new ArrayList<>();
    new JsonMapper()
        .readTree(json)
        .path("permissions")
        .path("deny")
        .forEach(n -> deny.add(n.asString("")));

    assertThat(deny).containsExactly("AskUserQuestion");
  }

  @Test
  void preApprovesTheJagtToolsAndTheAgentsGitInGeneratedAgentSettings() {
    String json = ClaudeAgentRuntime.settingsJson(null, List.of(), List.of());

    List<String> allow = new ArrayList<>();
    new JsonMapper()
        .readTree(json)
        .path("permissions")
        .path("allow")
        .forEach(n -> allow.add(n.asString("")));

    assertThat(allow).contains("mcp__jagt-orchestrator", "Bash(git:*)");
  }

  @Test
  void preApprovesTheConfiguredToolsBesideJagtsOwnInGeneratedAgentSettings() {
    String json =
        ClaudeAgentRuntime.settingsJson(null, List.of(), List.of("mcp__host__open_request"));

    List<String> allow = new ArrayList<>();
    new JsonMapper()
        .readTree(json)
        .path("permissions")
        .path("allow")
        .forEach(n -> allow.add(n.asString("")));

    assertThat(allow)
        .containsExactly("mcp__jagt-orchestrator", "Bash(git:*)", "mcp__host__open_request");
  }

  @Test
  void confinesTheShellsWritesToTheTaskWithEveryHostReachable() {
    String json =
        ClaudeAgentRuntime.settingsJson(
            null,
            List.of(),
            List.of(),
            ClaudeAgentRuntime.sandboxJson(
                Sandbox.on(
                    List.of(Path.of("/w/ABC-42"), Path.of("/repo/.git")), List.of("ssh *"))));

    JsonNode sandbox = new JsonMapper().readTree(json).path("sandbox");

    assertThat(sandbox)
        .isEqualTo(
            new JsonMapper()
                .readTree(
                    """
                    {"enabled":true,"failIfUnavailable":true,"autoAllowBashIfSandboxed":true,\
                    "allowUnsandboxedCommands":true,"enableWeakerNetworkIsolation":true,"excludedCommands":["ssh *"],\
                    "filesystem":{"allowWrite":["/w/ABC-42","/repo/.git"]},\
                    "network":{"allowedDomains":["*"],"allowLocalBinding":true,"allowAllUnixSockets":true}}\
                    """));
  }

  @Test
  void leavesTheShellUnsandboxedWhenTheSandboxIsOff() {
    String json =
        ClaudeAgentRuntime.settingsJson(
            null, List.of(), List.of(), ClaudeAgentRuntime.sandboxJson(Sandbox.off()));

    assertThat(new JsonMapper().readTree(json).has("sandbox")).isFalse();
  }

  @Test
  void disablesConfiguredPluginsInGeneratedAgentSettings() {
    String json =
        ClaudeAgentRuntime.settingsJson(
            null, List.of("jdtls-lsp@claude-plugins-official"), List.of());

    boolean enabled =
        new JsonMapper()
            .readTree(json)
            .path("enabledPlugins")
            .path("jdtls-lsp@claude-plugins-official")
            .asBoolean(true);

    assertThat(enabled).isFalse();
  }

  @Test
  void writesTheConfiguredOutputStyleAndDisabledPluginsIntoTheWorktreeSettings(@TempDir Path root)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();

    claudeRuntime()
        .withProperties(new ClaudeProperties("claude", "", "team:Engineer", List.of("lsp@market")))
        .build(new FakeClock())
        .provisionWorktree(AgentWorktree.of(worktree, root));

    var settings =
        new JsonMapper()
            .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")));
    assertThat(settings.path("outputStyle").asString("")).isEqualTo("team:Engineer");
    assertThat(settings.path("enabledPlugins").path("lsp@market").asBoolean(true)).isFalse();
  }

  @Test
  void readsTheOutputStyleAndDisabledPluginsFromTheClaudeSectionOfTheConfig() {
    var env =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("orchestrator.claude.outputStyle", "terse")
            .withProperty("orchestrator.claude.disabledPlugins[0]", "lsp@market");

    ClaudeProperties bound =
        org.springframework.boot.context.properties.bind.Binder.get(env)
            .bind("orchestrator.claude", ClaudeProperties.class)
            .get();

    assertThat(bound.outputStyle()).isEqualTo("terse");
    assertThat(bound.disabledPlugins()).containsExactly("lsp@market");
  }

  @Test
  void pinsTheModelAskedForAsOneShellArgument(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\ngo\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.PLAN, " opus "))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --model 'opus' 'go'");
  }

  @Test
  void leavesTheModelToTheCliWhenNoneIsAskedFor(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\ngo\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.WORK, "  "))
        .isEqualTo("CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'go'");
  }

  @Test
  void allowsWorkspaceSessionToWriteItsWorkspaceAndRunItsCommandsAfterThePrompt(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\ngo\n");
    var runtime = claudeRuntime().withBriefRoot(root.toString()).build(new FakeClock());

    assertThat(
            runtime.workspaceCommand(
                Path.of("/ws"),
                "",
                "p",
                new CheckerReader.Workspace(Path.of("/ws"), List.of("npx test"))))
        .isEqualTo(
            "CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude 'p' --allowedTools 'Read' 'Grep' 'Glob'"
                + " 'Write(//ws/**)' 'Edit(//ws/**)' 'Bash(npx test:*)'");
  }

  @Test
  void startsWithJagtsServerAndThePinnedOnesAloneWhenServersArePinned(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## start\n\ngo\n");
    var runtime =
        claudeRuntime()
            .withBriefRoot(root.toString())
            .withProperties(new ClaudeProperties("claude", "/stand/servers.json", "", List.of()))
            .build(new FakeClock());

    assertThat(runtime.launchCommand(Path.of("/wt"), LaunchMode.WORK))
        .isEqualTo(
            "CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --mcp-config '/wt/.mcp.json'"
                + " '/stand/servers.json' --strict-mcp-config 'go'");
  }

  @Test
  void revivesWithThePinnedServersAloneWhenServersArePinned(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## revive\n\ngo on\n");
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(
        Files.createDirectories(
                ClaudeTranscripts.projectsDir(FakeHost.machine())
                    .resolve(ClaudeTranscripts.slug(worktree)))
            .resolve("s.jsonl"),
        "{}\n");
    var runtime =
        claudeRuntime()
            .withBriefRoot(root.toString())
            .withProperties(new ClaudeProperties("claude", "/stand/servers.json", "", List.of()))
            .build(new FakeClock());

    assertThat(runtime.reviveCommand(worktree))
        .isEqualTo(
            "CLAUDE_CODE_AUTO_COMPACT_WINDOW=300000 claude --mcp-config '"
                + worktree.resolve(".mcp.json")
                + "' '/stand/servers.json' --strict-mcp-config --continue 'go on'");
  }

  @Test
  void skipsBlankPluginsInGeneratedAgentSettings() {
    String json =
        ClaudeAgentRuntime.settingsJson(
            null, List.of("  ", "jdtls-lsp@claude-plugins-official"), List.of());

    var plugins = new JsonMapper().readTree(json).path("enabledPlugins");

    assertThat(plugins.size()).isEqualTo(1);
    assertThat(plugins.has("jdtls-lsp@claude-plugins-official")).isTrue();
  }

  @Test
  void skipsBlankToolsInGeneratedAgentSettings() {
    String json =
        ClaudeAgentRuntime.settingsJson(
            null, List.of(), List.of("  ", " mcp__host__open_request "));

    List<String> allow = new ArrayList<>();
    new JsonMapper()
        .readTree(json)
        .path("permissions")
        .path("allow")
        .forEach(n -> allow.add(n.asString("")));

    assertThat(allow)
        .containsExactly("mcp__jagt-orchestrator", "Bash(git:*)", "mcp__host__open_request");
  }

  @Test
  void fallsBackToTheCliNameWhereTheConfiguredCommandIsBlank() {
    assertThat(new ClaudeProperties("  ", "", "", List.of()).command()).isEqualTo("claude");
  }

  @Test
  void keepsConfiguredCommand() {
    assertThat(new ClaudeProperties("/opt/bin/claude", "", "", List.of()).command())
        .isEqualTo("/opt/bin/claude");
  }

  @Test
  void namesItselfAndTheModelsItOffersAndReadsWhatTheHumanTyped() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.displayName()).isEqualTo("Claude");
    assertThat(runtime.models()).containsExactly("fable", "opus", "sonnet", "haiku");
    assertThat(runtime.tiers()).containsExactly("sonnet", "opus");
    assertThat(runtime.readsWhatTheHumanTyped()).isTrue();
  }

  @Test
  void declaresTheFilesItGeneratesAndTheOnesTheStatusLeavesOut() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.generatedFiles())
        .containsExactly(".mcp.json", ".claude/settings.local.json");
    assertThat(runtime.projectAgentFiles()).containsExactly("AGENTS.md", "CLAUDE.md");
    assertThat(runtime.statusExclusions())
        .containsExactly(".mcp.json", ".claude/", "CLAUDE.md", "CLAUDE.local.md");
  }

  @Test
  void declaresWhatTheCliSaysAtCompactionUsageLimitAndAnEndThatIsFollowedByStart() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.compactedStart()).isEqualTo("compact");
    assertThat(runtime.rateLimitError()).isEqualTo("rate_limit");
    assertThat(runtime.continuingEnds()).containsExactlyInAnyOrder("clear", "resume");
  }

  @Test
  void isNotPastTheCacheLifetimeAtTheMomentItsLastActivityExactlyFillsIt() {
    var runtime = claudeRuntime().build(Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));

    assertThat(runtime.pastCacheLifetime(0L, Duration.ofSeconds(1))).isFalse();
    assertThat(runtime.pastCacheLifetime(0L, Duration.ofMillis(999L))).isTrue();
  }

  @Test
  void grantsTheSameDirectoryOnlyOnceHoweverOftenItIsAdmitted(@TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.resolve("ABC-1-proj"));
    Path deploy = Files.createDirectories(root.resolve("ABC-1-deploy"));
    var runtime = claudeRuntime().build(new FakeClock());
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));

    runtime.admit(worktree, deploy);
    runtime.admit(worktree, deploy);

    var allow =
        new JsonMapper()
            .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")))
            .path("permissions")
            .path("allow");
    List<String> reads = new ArrayList<>();
    allow.forEach(n -> reads.add(n.asString("")));
    assertThat(reads).filteredOn(rule -> rule.startsWith("Read(/")).hasSize(1);
  }

  @Test
  void readsTheConversationSessionLogAndActivityOfWorktreeFromWhereTheCliKeepsThem(
      @TempDir Path worktree) throws Exception {
    Path logs =
        Files.createDirectories(
            ClaudeTranscripts.projectsDir(FakeHost.machine())
                .resolve(ClaudeTranscripts.slug(worktree)));
    Path log =
        Files.writeString(
            logs.resolve("s.jsonl"),
            """
            {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"fix the login"}}
            """);
    Files.setLastModifiedTime(log, java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L));
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.conversationTail(worktree, 16_000, Set.of()))
        .contains("human: fix the login");
    assertThat(runtime.sessionLogOf(worktree)).contains(log);
    assertThat(runtime.lastSessionActivity(worktree))
        .isEqualTo(OptionalLong.of(1_700_000_000_000L));
    assertThat(runtime.humanSaid(worktree, Set.of())).contains(List.of("fix the login"));
  }

  @Test
  void readsTheHumansQuestionAsTheNewestPromptOfWorktree(@TempDir Path worktree) throws Exception {
    Path logs =
        Files.createDirectories(
            ClaudeTranscripts.projectsDir(FakeHost.machine())
                .resolve(ClaudeTranscripts.slug(worktree)));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        """);
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.humanExchangeOpen(worktree, Set.of("Re-read the file."))).isTrue();
  }

  @Test
  void readsLineJagtTypedAsNoHumanPromptOfWorktree(@TempDir Path worktree) throws Exception {
    Path logs =
        Files.createDirectories(
            ClaudeTranscripts.projectsDir(FakeHost.machine())
                .resolve(ClaudeTranscripts.slug(worktree)));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
        """);
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.humanExchangeOpen(worktree, Set.of("Re-read the file."))).isFalse();
  }

  @Nested
  class ProvisionedWorktree {

    @TempDir Path root;

    private Path worktree;
    private JsonNode settings;

    @BeforeEach
    void provision() throws Exception {
      worktree = root.resolve("ABC-1-proj");
      worktree.toFile().mkdirs();
      claudeRuntime().build(new FakeClock()).provisionWorktree(AgentWorktree.of(worktree, root));
      settings =
          new JsonMapper()
              .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")));
    }

    @Test
    void declaresTheJagtMcpServerOverHttpWithThisWorktreeAsTheCaller() throws Exception {
      var config =
          new JsonMapper()
              .readTree(Files.readString(worktree.resolve(".mcp.json")))
              .path("mcpServers")
              .path("jagt-orchestrator");
      assertThat(config.path("type").asString("")).isEqualTo("http");
      assertThat(config.path("url").asString("")).isEqualTo("http://localhost:8290/mcp");
      assertThat(config.path("headers").path("X-Working-Directory").asString(""))
          .isEqualTo(worktree.toAbsolutePath().normalize().toString());
    }

    @Test
    void writesTheSettingsFileAsOneJsonObjectWithEveryKeyTheSessionReads() {
      assertThat(settings.propertyNames())
          .containsExactlyInAnyOrder(
              "hooks",
              "env",
              "enableAllProjectMcpServers",
              "autoMemoryEnabled",
              "idleCompaction",
              "permissions");
      assertThat(settings.path("enableAllProjectMcpServers").asBoolean(false)).isTrue();
      assertThat(settings.path("hooks").propertyNames())
          .containsExactlyInAnyOrder(
              "Notification",
              "SessionEnd",
              "SessionStart",
              "Stop",
              "UserPromptSubmit",
              "StopFailure",
              "PermissionRequest",
              "PreToolUse");
    }

    @Test
    void leavesNoStdioProxyInTheWorktree() throws Exception {
      assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
    }

    @Test
    void aliasesClaudeMdToTheSharedSystemKnowledgeFileSoOnlyOneCopyExists() throws Exception {
      Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "system knowledge");

      assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("system knowledge");
    }

    @Test
    void writesDeclaredEventAsHookThatReportsTheStateItMeans() throws Exception {
      String command =
          settings
              .path("hooks")
              .path("SessionEnd")
              .path(0)
              .path("hooks")
              .path(0)
              .path("command")
              .asString("");
      assertThat(command).contains("/api/agent/session/gone", worktree.toString());
    }

    @Test
    void asksJagtAtTheEndOfEveryTurnWhetherTheTurnMayEnd() throws Exception {
      String command =
          settings
              .path("hooks")
              .path("Stop")
              .path(0)
              .path("hooks")
              .path(0)
              .path("command")
              .asString("");
      assertThat(command).contains("/api/agent/session/turn-end", worktree.toString());
    }

    @Test
    void reportsTurnEndedOnAnApiErrorThroughItsOwnUnmatchedEvent() throws Exception {
      var hook = settings.path("hooks").path("StopFailure").path(0);
      assertThat(hook.has("matcher")).isFalse();
      assertThat(hook.path("hooks").path(0).path("command").asString(""))
          .contains("/api/agent/session/turn-failure", worktree.toString());
    }

    @Test
    void reportsPermissionPromptThroughItsOwnEventToTheEndpointAnsweringNothing() throws Exception {
      var hook = settings.path("hooks").path("PermissionRequest").path(0);
      assertThat(hook.has("matcher")).isFalse();
      assertThat(hook.path("hooks").path(0).path("command").asString(""))
          .contains("/api/agent/session/permission", worktree.toString());
    }

    @Test
    void writesTheGateAsHookOnTheShellAndTheFileWritingTools() throws Exception {
      var gate = settings.path("hooks").path("PreToolUse").path(0);
      assertThat(gate.path("matcher").asString(""))
          .isEqualTo("Bash|Edit|Write|MultiEdit|NotebookEdit");
      assertThat(gate.path("hooks").path(0).path("command").asString(""))
          .contains("/api/agent/tool", worktree.toString());
    }

    @Test
    void asksJagtBeforeTypedPromptReachesTheModelAndWaitsLongerForTheAnswer() throws Exception {
      var hook = settings.path("hooks").path("UserPromptSubmit");
      assertThat(hook).hasSize(1);
      assertThat(hook.path(0).path("hooks").path(0).path("command").asString(""))
          .contains("/api/agent/session/prompt", "-m 10", worktree.toString());
      assertThat(hook.path(0).path("hooks").path(0).path("timeout").asInt()).isEqualTo(15);
    }
  }

  @Nested
  class WorktreeHoldingProjectRules {

    @TempDir Path root;

    private Path worktree;
    private ClaudeAgentRuntime runtime;
    private Path briefing;

    @BeforeEach
    void provision() throws Exception {
      worktree = root.resolve("ABC-1-proj");
      worktree.toFile().mkdirs();
      Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");
      runtime = claudeRuntime().build(new FakeClock());
      briefing = runtime.systemKnowledgeFile(worktree);
      runtime.provisionWorktree(AgentWorktree.of(worktree, root));
    }

    @Test
    void writesTheBriefingBesideTheRepositorysOwnClaudeMdInsteadOfReplacingIt() throws Exception {
      assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
      assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("project rules");
    }

    @Test
    void findsTheBriefingBesideTheRepositorysOwnClaudeMdOnceTheWorktreeIsProvisioned()
        throws Exception {
      Files.writeString(briefing, "the briefing");

      Path again = runtime.briefedFile(worktree);

      assertThat(again).isEqualTo(worktree.resolve("CLAUDE.local.md"));
    }
  }
}

package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.agent.trust.ClaudeTrust;
import dev.jagt.orchestrator.adapter.assistant.ClaudeToolScope;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.port.agent.AgentWorktree;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.agent.Sandbox;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.task.text.Ansi;
import dev.jagt.orchestrator.task.turn.Step;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code CLAUDE.md} symlinks the shared system-knowledge file, read by Claude under its own name;
 * where the repository ships either name, the briefing goes to the never-versioned {@code
 * CLAUDE.local.md}.
 */
@Component
@ConditionalOnProperty(
    name = "orchestrator.agent.cli",
    havingValue = "claude",
    matchIfMissing = true)
@RequiredArgsConstructor
public class ClaudeAgentRuntime extends AbstractAgentRuntime {

  private static final JsonMapper JSON = new JsonMapper();
  private static final String PROMPT_MARK = "❯";
  private static final String INTERRUPT_HINT = "esc to interrupt";
  private static final String PERMISSION_QUESTION = "Do you want to ";
  private static final String PERMISSION_CANCEL = "Esc to cancel";
  private static final Pattern SPINNER_LINE =
      Pattern.compile("(?m)^\\s*[^\\p{L}\\p{N}\\s❯⏺>]\\s\\p{L}+…");
  private static final Pattern DIMMED = Pattern.compile("\u001b\\[2m.*?(?=\u001b\\[(?:0|22)?m|$)");
  private static final String CLAUDE_MEMORY_FILE = "CLAUDE.md";
  private static final String LOCAL_MEMORY_FILE = "CLAUDE.local.md";
  private static final List<String> MODELS = List.of("fable", "opus", "sonnet", "haiku");

  private final SessionBrief brief;
  private final ClaudeProperties claude;
  private final ClaudeEndpoints endpoints;
  private final Clock clock;
  private final Host host;

  @Override
  public String displayName() {
    return "Claude";
  }

  @Override
  public String launchCommand(Path worktree, LaunchMode mode) {
    return launchCommand(worktree, mode, "");
  }

  @Override
  public String launchCommand(Path worktree, LaunchMode mode, String model) {
    return launchCommand(worktree, mode, model, brief.line(Step.START));
  }

  @Override
  public String launchCommand(
      Path worktree, LaunchMode mode, String model, @Nullable String prompt) {
    return COMPACT_ENV
        + claude.command()
        + pinnedServers(worktree)
        + (model == null || model.isBlank() ? "" : " --model " + shellQuote(model.strip()))
        + " "
        + shellQuote(prompt);
  }

  /** The allow rules follow the prompt: the flag takes every word up to the next option. */
  @Override
  public String workspaceCommand(
      Path root, String model, String prompt, CheckerReader.Workspace reach) {
    return launchCommand(root, LaunchMode.WORK, model, prompt)
        + " --allowedTools "
        + ClaudeToolScope.of(List.of(), List.of(), List.of(), false, Optional.of(reach))
            .allow()
            .stream()
            .map(AbstractAgentRuntime::shellQuote)
            .collect(Collectors.joining(" "));
  }

  /**
   * `--continue` exits where the worktree holds no conversation, so there the session starts
   * afresh.
   */
  @Override
  public String reviveCommand(Path worktree) {
    if (sessionLogOf(worktree).isEmpty()) {
      return launchCommand(worktree, LaunchMode.WORK);
    }
    return COMPACT_ENV
        + claude.command()
        + pinnedServers(worktree)
        + " --continue "
        + shellQuote(brief.line(Step.REVIVE));
  }

  /**
   * Strict drops the worktree's own `.mcp.json` too, so jagt's server is named beside the pinned
   * ones. The flag comes after the files: `--mcp-config` takes every word up to the next option,
   * the prompt included.
   */
  private String pinnedServers(Path worktree) {
    if (claude.mcpConfig().isEmpty()) {
      return "";
    }
    return " --mcp-config "
        + shellQuote(worktree.resolve(".mcp.json").toString())
        + " "
        + shellQuote(claude.mcpConfig())
        + " --strict-mcp-config";
  }

  /** https://code.claude.com/docs/en/prompt-caching#cache-lifetime */
  @Override
  public boolean pastCacheLifetime(long lastActiveMillis, Duration cacheLifetime) {
    return !cacheLifetime.isZero() && clock.millis() - lastActiveMillis > cacheLifetime.toMillis();
  }

  @Override
  public Optional<String> conversationTail(Path worktree, int maxChars, Set<String> typedByJagt) {
    return ClaudeTranscripts.tail(
        ClaudeTranscripts.projectsDir(host), worktree, maxChars, withBriefLines(typedByJagt));
  }

  @Override
  public List<String> models() {
    return MODELS;
  }

  @Override
  public List<String> tiers() {
    return List.of("sonnet", "opus");
  }

  @Override
  public java.util.Optional<Path> sessionLogOf(Path worktree) {
    return ClaudeTranscripts.newestLog(ClaudeTranscripts.projectsDir(host), worktree);
  }

  /**
   * The input line is the last one opening with the prompt mark; a dimmed remainder is a
   * suggestion, not a draft.
   */
  @Override
  public Optional<String> draft(String screen) {
    String draft = "";
    for (String line : screen.split("\n", 0)) {
      if (Ansi.strip(line).startsWith(PROMPT_MARK)) {
        String typed = line.substring(line.indexOf(PROMPT_MARK) + PROMPT_MARK.length());
        draft = Ansi.strip(DIMMED.matcher(typed).replaceAll(""));
      }
    }
    String typed = draft.replace('\u00a0', ' ').strip();
    return typed.isEmpty() ? Optional.empty() : Optional.of(typed);
  }

  @Override
  public boolean working(String screen) {
    String plain = Ansi.strip(screen);
    return plain.contains(INTERRUPT_HINT) || SPINNER_LINE.matcher(plain).find();
  }

  @Override
  public Optional<String> unansweredPrompt(String screen) {
    String shown = Ansi.strip(screen);
    if (SessionHooks.trustDialog("claude").stream().anyMatch(shown::contains)) {
      return Optional.of("waiting on the trust dialog");
    }
    if (SessionHooks.firstRunPrompt("claude").stream().anyMatch(shown::contains)) {
      return Optional.of("waiting on the first-run setup");
    }
    return shown.contains(PERMISSION_QUESTION) && shown.contains(PERMISSION_CANCEL)
        ? Optional.of("waiting on a permission dialog")
        : Optional.empty();
  }

  @Override
  public Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
    return ClaudeTranscripts.humanSaid(
        ClaudeTranscripts.projectsDir(host), worktree, withBriefLines(typedByJagt));
  }

  @Override
  public boolean humanExchangeOpen(Path worktree, Set<String> typedByJagt) {
    return ClaudeTranscripts.humanExchangeOpen(
        ClaudeTranscripts.projectsDir(host), worktree, withBriefLines(typedByJagt));
  }

  private Set<String> withBriefLines(Set<String> typedByJagt) {
    Set<String> cut = new HashSet<>(typedByJagt);
    cut.add(brief.line(Step.START));
    cut.add(brief.line(Step.REVIVE));
    return cut;
  }

  @Override
  public boolean readsWhatTheHumanTyped() {
    return true;
  }

  @Override
  public OptionalLong lastSessionActivity(Path worktree) {
    return OptionalLong.of(
        ClaudeTranscripts.lastEntryMillis(ClaudeTranscripts.projectsDir(host), worktree));
  }

  @Override
  public Path systemKnowledgeFile(Path worktree) {
    if (rootNamesFree(worktree)) {
      return worktree.resolve(SYSTEM_KNOWLEDGE_FILE);
    }
    Path local = worktree.resolve(LOCAL_MEMORY_FILE);
    if (broughtByCheckout(local)) {
      throw new IllegalStateException(
          "Cannot brief the agent: the repository ships "
              + LOCAL_MEMORY_FILE
              + " as well as its own memory file, so jagt has no name left to write.");
    }
    return local;
  }

  /**
   * The root memory file is jagt's own alias only where the root names were free when it briefed.
   */
  @Override
  public Path briefedFile(Path worktree) {
    return Files.isSymbolicLink(worktree.resolve(CLAUDE_MEMORY_FILE))
        ? worktree.resolve(SYSTEM_KNOWLEDGE_FILE)
        : worktree.resolve(LOCAL_MEMORY_FILE);
  }

  /**
   * The memory-file names are absent on purpose: jagt only ever takes one the checkout left free.
   */
  @Override
  public List<String> generatedFiles() {
    return List.of(".mcp.json", ".claude/settings.local.json");
  }

  @Override
  public List<String> projectAgentFiles() {
    return List.of(SYSTEM_KNOWLEDGE_FILE, CLAUDE_MEMORY_FILE);
  }

  @Override
  public List<String> statusExclusions() {
    return List.of(".mcp.json", ".claude/", CLAUDE_MEMORY_FILE, LOCAL_MEMORY_FILE);
  }

  @Override
  protected void wireAgent(AgentWorktree worktree) {
    write(
        worktree.path().resolve(".mcp.json"),
        mcpJson(endpoints.mcp().url(), McpEndpoint.callerHeaderValue(worktree.path())));
    if (rootNamesFree(worktree.path())) {
      symlink(
          worktree.path().resolve(CLAUDE_MEMORY_FILE),
          worktree.path().resolve(SYSTEM_KNOWLEDGE_FILE));
    }
    write(
        worktree.path().resolve(".claude").resolve("settings.local.json"),
        settingsJson(
            claude.outputStyle(),
            claude.disabledPlugins(),
            Stream.concat(worktree.allowedTools().stream(), editRules(worktree.sandbox())).toList(),
            hooksJson(worktree.path()),
            sandboxJson(worktree.sandbox())));
    ClaudeTrust.accept(ClaudeTrust.configFile(host), worktree.path());
  }

  /**
   * The pair is one layout: neither the alias nor the file it points at is any use without the
   * other.
   */
  private static boolean rootNamesFree(Path worktree) {
    return !broughtByCheckout(worktree.resolve(SYSTEM_KNOWLEDGE_FILE))
        && !broughtByCheckout(worktree.resolve(CLAUDE_MEMORY_FILE));
  }

  /**
   * Written per worktree rather than symlinked from the root, because the header value IS the
   * worktree path.
   */
  static String mcpJson(String url, String worktreePath) {
    ObjectNode root = JSON.createObjectNode();
    ObjectNode jagt = root.withObjectProperty("mcpServers").withObjectProperty("jagt-orchestrator");
    jagt.put("type", "http");
    jagt.put("url", url);
    jagt.withObjectProperty("headers").put(McpEndpoint.CALLER_HEADER, worktreePath);
    return pretty(root);
  }

  /**
   * Every turn rereads the whole context, so a task session left to grow to its 1M window pays for
   * it each time.
   */
  static final int COMPACT_AT_TOKENS = 300_000;

  /**
   * On the command as well as in the settings, which a worktree provisioned before them does not
   * carry.
   */
  private static final String COMPACT_ENV =
      "CLAUDE_CODE_AUTO_COMPACT_WINDOW=" + COMPACT_AT_TOKENS + " ";

  /**
   * Without {@code enableAllProjectMcpServers} a spawned session stops at a "New MCP server found"
   * prompt, and without the allow-list the permission classifier gates individual calls — prompts
   * nobody answers. A worktree is an untrusted project, where the human's global output style may
   * not apply; disabling plugins keeps a ~1-2GB language server from spawning per worktree.
   */
  static String settingsJson(
      @Nullable String outputStyle,
      List<String> disabledPlugins,
      List<String> allowedTools,
      ObjectNode... sections) {
    ObjectNode root = JSON.createObjectNode();
    if (outputStyle != null && !outputStyle.isBlank()) {
      root.put("outputStyle", outputStyle);
    }
    if (disabledPlugins != null) {
      ObjectNode plugins = JSON.createObjectNode();
      disabledPlugins.stream()
          .filter(p -> p != null && !p.isBlank())
          .forEach(p -> plugins.put(p.strip(), false));
      if (!plugins.isEmpty()) {
        root.set("enabledPlugins", plugins);
      }
    }
    for (ObjectNode section : sections) {
      root.setAll(section);
    }
    root.withObjectProperty("env")
        .put("CLAUDE_CODE_AUTO_COMPACT_WINDOW", String.valueOf(COMPACT_AT_TOKENS));
    root.put("enableAllProjectMcpServers", true);
    root.put("autoMemoryEnabled", false);
    root.put("idleCompaction", false);
    ObjectNode permissions = root.withObjectProperty("permissions");
    ArrayNode allow = permissions.withArrayProperty("allow");
    Stream.concat(
            Stream.of("mcp__jagt-orchestrator", "Bash(git:*)"),
            allowedTools == null ? Stream.empty() : allowedTools.stream())
        .filter(tool -> tool != null && !tool.isBlank())
        .forEach(tool -> allow.add(tool.strip()));
    permissions.withArrayProperty("deny").add("AskUserQuestion");
    return pretty(root);
  }

  /**
   * Claude rereads its settings files during a session. A path rule matches before the directory
   * exists; an additional directory and a sandbox write path are only granted once it does.
   */
  @Override
  public Admission admit(Path worktree, Path directory) {
    Path file = worktree.resolve(".claude").resolve("settings.local.json");
    if (!Files.isRegularFile(file)) {
      return Admission.UNPROVISIONED;
    }
    ObjectNode settings = (ObjectNode) JSON.readTree(read(file));
    boolean present = Files.isDirectory(directory);
    String path = (present ? real(directory) : directory.toAbsolutePath().normalize()).toString();
    ObjectNode permissions = settings.withObjectProperty("permissions");
    addOnce(permissions.withArrayProperty("allow"), "Read(/" + path + "/**)");
    addOnce(permissions.withArrayProperty("allow"), "Edit(/" + path + "/**)");
    if (present) {
      addOnce(permissions.withArrayProperty("additionalDirectories"), path);
      if (settings.path("sandbox").path("enabled").asBoolean(false)) {
        addOnce(
            settings
                .withObjectProperty("sandbox")
                .withObjectProperty("filesystem")
                .withArrayProperty("allowWrite"),
            path);
      }
    }
    write(file, pretty(settings));
    return Admission.GRANTED;
  }

  private static void addOnce(ArrayNode values, String value) {
    if (values.valueStream().noneMatch(node -> value.equals(node.asString()))) {
      values.add(value);
    }
  }

  private static Path real(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot resolve " + path, e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }

  @Override
  public void beforeLaunch(Path worktree) {
    ClaudeTrust.reaccept(ClaudeTrust.configFile(host), worktree);
  }

  @Override
  public void retireWorktree(Path worktree) {
    ClaudeTrust.forget(ClaudeTrust.configFile(host), worktree);
  }

  @Override
  public String compactedStart() {
    return SessionHooks.compactedStart("claude");
  }

  @Override
  public String rateLimitError() {
    return SessionHooks.rateLimitError("claude");
  }

  @Override
  public Set<String> continuingEnds() {
    return SessionHooks.continuingEnds("claude");
  }

  @Override
  public String refusedTurnEnd(String reason) {
    return JSON.createObjectNode().put("decision", "block").put("reason", reason).toString();
  }

  @Override
  public String blockedPrompt(String reason) {
    return refusedTurnEnd(reason);
  }

  @Override
  public String toldTheHuman(String line) {
    return JSON.createObjectNode().put("systemMessage", line).toString();
  }

  @Override
  public Optional<String> shellEscape() {
    return Optional.of("!");
  }

  /**
   * The sandbox binds the shell alone; Claude's edit tools ask outside the working directory
   * without a rule.
   */
  private static Stream<String> editRules(Sandbox sandbox) {
    return sandbox.writable().stream().map(path -> "Edit(/" + path.toAbsolutePath() + "/**)");
  }

  /**
   * Claude's sandbox reaches the network through a web proxy alone, so every host is allowed there;
   * a socket and a local port stay open for a container client and a tunnel, the OS trust store for
   * a TLS client.
   */
  static ObjectNode sandboxJson(Sandbox sandbox) {
    ObjectNode root = JSON.createObjectNode();
    if (!sandbox.enabled()) {
      return root;
    }
    ObjectNode block = root.withObjectProperty("sandbox");
    block.put("enabled", true);
    block.put("failIfUnavailable", true);
    block.put("autoAllowBashIfSandboxed", true);
    block.put("allowUnsandboxedCommands", true);
    block.put("enableWeakerNetworkIsolation", true);
    block.set("excludedCommands", JSON.valueToTree(sandbox.excludedCommands()));
    block
        .withObjectProperty("filesystem")
        .set(
            "allowWrite",
            JSON.valueToTree(sandbox.writable().stream().map(Path::toString).toList()));
    ObjectNode network = block.withObjectProperty("network");
    network.putArray("allowedDomains").add("*");
    network.put("allowLocalBinding", true);
    network.put("allowAllUnixSockets", true);
    return root;
  }

  /**
   * Which of Claude's events mean what is declared in {@code hooks/claude.properties}, not here.
   */
  private ObjectNode hooksJson(Path worktree) {
    ObjectNode events = JSON.createObjectNode();
    SessionHooks.of("claude")
        .forEach(
            (event, state) -> hook(events, event, endpoints.hooks().command(worktree, state), 5));
    SessionHooks.turnEnd("claude")
        .ifPresent(
            event -> hook(events, event, endpoints.hooks().command(worktree, "turn-end"), 5));
    SessionHooks.promptGate("claude")
        .ifPresent(event ->
            hook(
                events,
                event,
                endpoints.hooks().promptCommand(worktree),
                HookEndpoint.PROMPT_GATE_SECONDS + 5));
    SessionHooks.turnFailure("claude")
        .ifPresent(
            event -> hook(events, event, endpoints.hooks().command(worktree, "turn-failure"), 5));
    SessionHooks.permissionRequest("claude")
        .ifPresent(
            event -> hook(events, event, endpoints.hooks().command(worktree, "permission"), 5));
    SessionHooks.gate("claude")
        .ifPresent(event ->
            hook(events, event, endpoints.hooks().gateCommand(worktree), 5)
                .put("matcher", "Bash|Edit|Write|MultiEdit|NotebookEdit"));
    ObjectNode root = JSON.createObjectNode();
    if (!events.isEmpty()) {
      root.set("hooks", events);
    }
    return root;
  }

  private static ObjectNode hook(ObjectNode events, String event, String command, int timeout) {
    ObjectNode entry = events.putArray(event).addObject();
    entry
        .putArray("hooks")
        .addObject()
        .put("type", "command")
        .put("command", command)
        .put("timeout", timeout);
    return entry;
  }

  private static String pretty(ObjectNode root) {
    return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
  }
}

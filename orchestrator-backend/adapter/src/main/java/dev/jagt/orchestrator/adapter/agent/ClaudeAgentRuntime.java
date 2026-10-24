package dev.jagt.orchestrator.adapter.agent;


import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.Sandbox;
import dev.jagt.orchestrator.config.SessionBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code CLAUDE.md} symlinks the shared system-knowledge file, read by Claude under its own name; where the
 * repository ships either name, the briefing goes to the never-versioned {@code CLAUDE.local.md}.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
@RequiredArgsConstructor
public class ClaudeAgentRuntime extends AbstractAgentRuntime {

    private static final JsonMapper JSON = new JsonMapper();
    private static final String PROMPT_MARK = "\u276f";
    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;?]*[A-Za-z]");
    private static final Pattern DIMMED = Pattern.compile("\u001b\\[2m.*?(?=\u001b\\[(?:0|22)?m|$)");
    private static final String CLAUDE_MEMORY_FILE = "CLAUDE.md";
    private static final String LOCAL_MEMORY_FILE = "CLAUDE.local.md";
    private static final List<String> MODELS = List.of("fable", "opus", "sonnet", "haiku");

    private final SessionBrief brief;
    private final ClaudeProperties claude;
    private final McpEndpoint mcp;
    private final HookEndpoint hooks;
    private final Clock clock;

    @Override
    public String displayName() {
        return "Claude";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        return launchCommand(worktree, planMode, "");
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode, String model) {
        return launchCommand(worktree, planMode, model, brief.line("start"));
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode, String model, @Nullable String prompt) {
        return COMPACT_ENV + claude.command() + pinnedServers(worktree)
                + (model == null || model.isBlank() ? "" : " --model " + shellQuote(model.strip()))
                + " " + shellQuote(prompt);
    }

    /** `--continue` takes the newest conversation this worktree holds, and simply starts one where it holds none. */
    @Override
    public String reviveCommand(Path worktree) {
        return COMPACT_ENV + claude.command() + pinnedServers(worktree) + " --continue " + shellQuote(brief.line("revive"));
    }

    /**
     * Strict drops the worktree's own `.mcp.json` too, so jagt's server is named beside the pinned ones. The flag
     * comes after the files: `--mcp-config` takes every word up to the next option, the prompt included.
     */
    private String pinnedServers(Path worktree) {
        if (claude.mcpConfig().isEmpty()) {
            return "";
        }
        return " --mcp-config " + shellQuote(worktree.resolve(".mcp.json").toString())
                + " " + shellQuote(claude.mcpConfig()) + " --strict-mcp-config";
    }

    /** The prompt cache's lifetime: https://code.claude.com/docs/en/prompt-caching#cache-lifetime */
    @Override
    public boolean pastContinuing(Path worktree) {
        return clock.millis() - lastSessionActivity(worktree).orElse(0) > CACHE_LIFETIME.toMillis();
    }

    @Override
    public List<String> models() {
        return MODELS;
    }

    @Override
    public java.util.Optional<Path> sessionLogOf(Path worktree) {
        return ClaudeTranscripts.newestLog(ClaudeTranscripts.projectsDir(), worktree);
    }

    /** The input line is the last one opening with the prompt mark; a dimmed remainder is a suggestion, not a draft. */
    @Override
    public Optional<String> draft(String screen) {
        String draft = "";
        for (String line : screen.split("\n")) {
            if (ANSI.matcher(line).replaceAll("").startsWith(PROMPT_MARK)) {
                String typed = line.substring(line.indexOf(PROMPT_MARK) + PROMPT_MARK.length());
                draft = ANSI.matcher(DIMMED.matcher(typed).replaceAll("")).replaceAll("");
            }
        }
        String typed = draft.replace('\u00a0', ' ').strip();
        return typed.isEmpty() ? Optional.empty() : Optional.of(typed);
    }

    @Override
    public Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
        Set<String> cut = new HashSet<>(typedByJagt);
        cut.add(brief.line("start"));
        cut.add(brief.line("revive"));
        return ClaudeTranscripts.humanSaid(ClaudeTranscripts.projectsDir(), worktree, cut);
    }

    @Override
    public boolean readsWhatTheHumanTyped() {
        return true;
    }

    @Override
    public OptionalLong lastSessionActivity(Path worktree) {
        return OptionalLong.of(ClaudeTranscripts.lastEntryMillis(ClaudeTranscripts.projectsDir(), worktree));
    }

    @Override
    public Path systemKnowledgeFile(Path worktree) {
        if (rootNamesFree(worktree)) {
            return worktree.resolve(SYSTEM_KNOWLEDGE_FILE);
        }
        Path local = worktree.resolve(LOCAL_MEMORY_FILE);
        if (broughtByCheckout(local)) {
            throw new IllegalStateException("Cannot brief the agent: the repository ships " + LOCAL_MEMORY_FILE
                    + " as well as its own memory file, so jagt has no name left to write.");
        }
        return local;
    }

    /** The root memory file is jagt's own alias only where the root names were free when it briefed. */
    @Override
    public Path briefedFile(Path worktree) {
        return Files.isSymbolicLink(worktree.resolve(CLAUDE_MEMORY_FILE))
                ? worktree.resolve(SYSTEM_KNOWLEDGE_FILE) : worktree.resolve(LOCAL_MEMORY_FILE);
    }

    /** The memory-file names are absent on purpose: jagt only ever takes one the checkout left free. */
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
        write(worktree.path().resolve(".mcp.json"), mcpJson(mcp.url(),
                McpEndpoint.callerHeaderValue(worktree.path())));
        if (rootNamesFree(worktree.path())) {
            symlink(worktree.path().resolve(CLAUDE_MEMORY_FILE),
                    worktree.path().resolve(SYSTEM_KNOWLEDGE_FILE));
        }
        write(worktree.path().resolve(".claude").resolve("settings.local.json"),
                settingsJson(worktree.outputStyle(), worktree.disabledPlugins(),
                        Stream.concat(worktree.allowedTools().stream(), editRules(worktree.sandbox())).toList(),
                        hooksJson(worktree.path()) + sandboxJson(worktree.sandbox())));
        ClaudeTrust.accept(ClaudeTrust.configFile(), worktree.path());
    }

    /** The pair is one layout: neither the alias nor the file it points at is any use without the other. */
    private static boolean rootNamesFree(Path worktree) {
        return !broughtByCheckout(worktree.resolve(SYSTEM_KNOWLEDGE_FILE))
                && !broughtByCheckout(worktree.resolve(CLAUDE_MEMORY_FILE));
    }

    /** Written per worktree rather than symlinked from the root, because the header value IS the worktree path. */
    static String mcpJson(String url, String worktreePath) {
        return """
                {
                  "mcpServers": {
                    "jagt-orchestrator": {
                      "type": "http",
                      "url": %s,
                      "headers": { %s: %s }
                    }
                  }
                }
                """.formatted(quoted(url), quoted(McpEndpoint.CALLER_HEADER), quoted(worktreePath));
    }

    /** Every turn rereads the whole context, so a task session left to grow to its 1M window pays for it each time. */
    static final int COMPACT_AT_TOKENS = 300_000;

    /** On the command as well as in the settings, which a worktree provisioned before them does not carry. */
    private static final String COMPACT_ENV = "CLAUDE_CODE_AUTO_COMPACT_WINDOW=" + COMPACT_AT_TOKENS + " ";

    private static final Duration CACHE_LIFETIME = Duration.ofHours(1);

    /**
     * Without {@code enableAllProjectMcpServers} a spawned session stops at a "New MCP server found" prompt, and
     * without the allow-list the permission classifier gates individual calls — prompts nobody answers. A
     * worktree is an untrusted project, where the human's global output style may not apply; disabling plugins
     * keeps a ~1-2GB language server from spawning per worktree.
     */
    static String settingsJson(@Nullable String outputStyle, List<String> disabledPlugins, List<String> allowedTools,
                               String sections) {
        String styleLine = outputStyle == null || outputStyle.isBlank() ? ""
                : "\n  \"outputStyle\": " + quoted(outputStyle) + ",";
        String pluginsLine = "";
        if (disabledPlugins != null) {
            String entries = disabledPlugins.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(p -> quoted(p.strip()) + ": false")
                    .collect(Collectors.joining(", "));
            if (!entries.isBlank()) {
                pluginsLine = "\n  \"enabledPlugins\": {" + entries + "},";
            }
        }
        return """
                {%s%s%s
                  "env": {"CLAUDE_CODE_AUTO_COMPACT_WINDOW": "%d"},
                  "enableAllProjectMcpServers": true,
                  "autoMemoryEnabled": false,
                  "permissions": {
                    "allow": [%s]
                  }
                }
                """.formatted(styleLine, pluginsLine, sections == null ? "" : sections, COMPACT_AT_TOKENS,
                Stream.concat(Stream.of("mcp__jagt-orchestrator", "Bash(git:*)"),
                                allowedTools == null ? Stream.empty() : allowedTools.stream())
                        .filter(tool -> tool != null && !tool.isBlank())
                        .map(tool -> quoted(tool.strip())).collect(Collectors.joining(", ")));
    }

    /**
     * Claude rereads its settings files during a session. A path rule matches before the directory exists; an
     * additional directory and a sandbox write path are only granted once it does.
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
                addOnce(settings.withObjectProperty("sandbox").withObjectProperty("filesystem")
                        .withArrayProperty("allowWrite"), path);
            }
        }
        write(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
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
    public void retireWorktree(Path worktree) {
        ClaudeTrust.forget(ClaudeTrust.configFile(), worktree);
    }

    @Override
    public String compactedStart() {
        return SessionHooks.compactedStart("claude");
    }

    @Override
    public String blockingNotification() {
        return SessionHooks.blockingNotification("claude");
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
        return "{\"decision\": \"block\", \"reason\": " + quoted(reason) + "}";
    }

    @Override
    public String toldTheHuman(String line) {
        return "{\"systemMessage\": " + quoted(line) + "}";
    }

    /** The sandbox binds the shell alone; Claude's edit tools ask outside the working directory without a rule. */
    private static Stream<String> editRules(Sandbox sandbox) {
        return sandbox.writable().stream().map(path -> "Edit(/" + path.toAbsolutePath() + "/**)");
    }

    /**
     * Claude's sandbox reaches the network through a web proxy alone, so every host is allowed there; a socket and a
     * local port stay open for a container client and a tunnel, the OS trust store for a TLS client.
     */
    static String sandboxJson(Sandbox sandbox) {
        if (!sandbox.enabled()) {
            return "";
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("enabled", true);
        block.put("failIfUnavailable", true);
        block.put("autoAllowBashIfSandboxed", true);
        block.put("allowUnsandboxedCommands", true);
        block.put("enableWeakerNetworkIsolation", true);
        block.put("excludedCommands", sandbox.excludedCommands());
        block.put("filesystem", Map.of("allowWrite", sandbox.writable().stream().map(Path::toString).toList()));
        block.put("network", Map.of("allowedDomains", List.of("*"), "allowLocalBinding", true,
                "allowAllUnixSockets", true));
        return "\n  \"sandbox\": " + JSON.writeValueAsString(block) + ",";
    }

    /** Which of Claude's events mean what is declared in {@code hooks/claude.properties}, not here. */
    private String hooksJson(Path worktree) {
        String events = Stream.of(
                        SessionHooks.of("claude").entrySet().stream()
                                .map(event -> hook(event.getKey(), hooks.command(worktree, event.getValue()))),
                        SessionHooks.turnEnd("claude").stream()
                                .map(event -> hook(event, hooks.command(worktree, "turn-end"))),
                        SessionHooks.turnFailure("claude").stream()
                                .map(event -> hook(event, hooks.command(worktree, "turn-failure"))),
                        // Scoped to the tools that can push or write a file: a hook on all of them would sit in
                        // front of every step the agent takes.
                        SessionHooks.gate("claude").stream()
                                .map(event -> """
                                        %s: [{"matcher": "Bash|Edit|Write|MultiEdit|NotebookEdit", "hooks": [{"type": "command", "command": %s,\
                                         "timeout": 5}]}]"""
                                        .formatted(quoted(event), quoted(hooks.gateCommand(worktree)))))
                .flatMap(stream -> stream)
                .collect(Collectors.joining(",\n    "));
        return events.isBlank() ? "" : "\n  \"hooks\": {\n    " + events + "\n  },";
    }

    private static String hook(String event, String command) {
        return """
                %s: [{"hooks": [{"type": "command", "command": %s, "timeout": 5}]}]"""
                .formatted(quoted(event), quoted(command));
    }

    /** Serialized rather than hand-quoted: a control character in a path would make the whole file unreadable,
     *  and it is then discarded whole. */
    private static String quoted(String value) {
        return JSON.writeValueAsString(value);
    }
}

package dev.jagt.orchestrator.adapter.agent;


import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.List;
import java.util.Set;
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
    private static final String CLAUDE_MEMORY_FILE = "CLAUDE.md";
    private static final String LOCAL_MEMORY_FILE = "CLAUDE.local.md";

    private final OrchestratorProperties properties;
    private final ClaudeProperties claude;
    private final McpEndpoint mcp;
    private final HookEndpoint hooks;

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
        return launchCommand(worktree, planMode, model, properties.agentPrompt());
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode, String model, String prompt) {
        return COMPACT_ENV + claude.command()
                + (planMode ? " --permission-mode plan" : "")
                + (model == null || model.isBlank() ? "" : " --model " + shellQuote(model.strip()))
                + " " + shellQuote(prompt);
    }

    /** `--continue` takes the newest conversation this worktree holds, and simply starts one where it holds none. */
    @Override
    public String reviveCommand(Path worktree) {
        return COMPACT_ENV + claude.command() + " --continue " + shellQuote(properties.agentRevivePrompt());
    }

    /** The prompt cache's lifetime: https://code.claude.com/docs/en/prompt-caching#cache-lifetime */
    @Override
    public Optional<Duration> continuesWithin() {
        return Optional.of(CACHE_LIFETIME);
    }

    @Override
    public boolean choosesModel() {
        return true;
    }

    @Override
    public java.util.Optional<Path> sessionLogOf(Path worktree) {
        return ClaudeTranscripts.newestLog(ClaudeTranscripts.projectsDir(), worktree);
    }

    @Override
    public Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
        Set<String> cut = new HashSet<>(typedByJagt);
        cut.add(properties.agentPrompt());
        cut.add(properties.agentRevivePrompt());
        return ClaudeTranscripts.humanSaid(ClaudeTranscripts.projectsDir(), worktree, cut);
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

    /** The memory-file names are absent on purpose: jagt only ever takes one the checkout left free. */
    @Override
    public List<String> generatedFiles() {
        return List.of(".mcp.json", ".claude/settings.local.json");
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
                settingsJson(worktree.outputStyle(), worktree.disabledPlugins(), hooksJson(worktree.path())));
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

    /**
     * Without {@code enableAllProjectMcpServers} a spawned session stops at a "New MCP server found" prompt, and
     * without the allow-list the permission classifier gates individual calls — prompts nobody answers. A
     * worktree is an untrusted project, where the human's global output style may not apply; disabling plugins
     * keeps a ~1-2GB language server from spawning per worktree.
     */
    /** Every turn rereads the whole context, so a task session left to grow to its 1M window pays for it each time. */
    static final int COMPACT_AT_TOKENS = 300_000;

    /** On the command as well as in the settings, which a worktree provisioned before them does not carry. */
    private static final String COMPACT_ENV = "CLAUDE_CODE_AUTO_COMPACT_WINDOW=" + COMPACT_AT_TOKENS + " ";

    private static final Duration CACHE_LIFETIME = Duration.ofHours(1);

    static String settingsJson(String outputStyle, List<String> disabledPlugins, String hooksLine) {
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
                  "permissions": {
                    "allow": ["mcp__jagt-orchestrator", "Bash(git:*)"]
                  }
                }
                """.formatted(styleLine, pluginsLine, hooksLine == null ? "" : hooksLine, COMPACT_AT_TOKENS);
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
    public String refusedTurnEnd(String reason) {
        return "{\"decision\": \"block\", \"reason\": " + quoted(reason) + "}";
    }

    @Override
    public String toldTheHuman(String line) {
        return "{\"systemMessage\": " + quoted(line) + "}";
    }

    /** Which of Claude's events mean what is declared in {@code hooks/claude.properties}, not here. */
    private String hooksJson(Path worktree) {
        String events = Stream.of(
                        SessionHooks.of("claude").entrySet().stream()
                                .map(event -> hook(event.getKey(), hooks.command(worktree, event.getValue()))),
                        SessionHooks.turnEnd("claude").stream()
                                .map(event -> hook(event, hooks.command(worktree, "turn-end"))),
                        // Scoped to the one tool that can push: a hook on all of them would sit in front of
                        // every step the agent takes.
                        SessionHooks.gate("claude").stream()
                                .map(event -> """
                                        %s: [{"matcher": "Bash", "hooks": [{"type": "command", "command": %s,\
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

package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.protocol.RoundRead;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class HeadlessClaudeRoundReviewer implements RoundReviewer {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private static final String TOOLS = "Read,Grep,Glob,Bash";
    private final HeadlessClaude headless;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;

    @Override
    public Answer<Judgement> review(Round round) {
        List<String> cmd = new ArrayList<>(List.of(claude.command(), round.prompt(), "-p",
                "--json-schema", RoundRead.SCHEMA.json(), "--output-format", "json",
                // The worktree's settings are the diff's to write, and its hooks would report as the task's session.
                "--setting-sources", "user", "--no-session-persistence",
                // Off the system prompt, cwd and git status stop making every worktree write its own cache.
                "--exclude-dynamic-system-prompt-sections"));
        if (!round.shared().isBlank()) {
            cmd.addAll(List.of("--append-system-prompt", round.shared()));
        }
        // Any server's search takes free text, so a diff could send it anywhere: none loads unless named.
        List<String> mcpReads = ReadOnlyTools.mcpNamed(assistant.allowedTools());
        String pinned = assistant.mcpConfigFor(AssistantCallKind.MASTER_REVIEW);
        if (mcpReads.isEmpty()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}"));
        } else if (!pinned.isBlank()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", pinned));
        }
        round.worktrees().stream().skip(1).forEach(dir -> cmd.addAll(List.of("--add-dir", dir.toString())));
        if (!round.model().isBlank()) {
            cmd.addAll(List.of("--model", round.model()));
        }
        cmd.addAll(List.of("--tools", TOOLS, "--permission-mode", "dontAsk", "--disallowedTools"));
        cmd.addAll(RoundReadTools.REFUSED);
        cmd.addAll(RoundReadTools.BUILDS);
        cmd.addAll(ReadOnlyTools.MCP_WRITES);
        cmd.add("--allowedTools");
        cmd.addAll(RoundReadTools.READS);
        cmd.addAll(mcpReads);
        List<String> tools = new ArrayList<>(mcpReads);
        tools.add(HeadlessClaude.STRUCTURED_OUTPUT);
        return RoundAnswers.judged(headless.run(round.worktrees().getFirst(), TIMEOUT, cmd,
                new ReadScope(round.worktrees(), tools, true), AssistantCallKind.MASTER_REVIEW,
                round.worktrees().getFirst().toString()));
    }

    @Override
    public boolean loadsMcpServer() {
        return !ReadOnlyTools.mcpNamed(assistant.allowedTools()).isEmpty();
    }
}

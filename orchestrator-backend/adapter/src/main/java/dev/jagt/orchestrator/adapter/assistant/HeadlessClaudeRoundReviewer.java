package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.protocol.RoundRead;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeRoundReviewer implements RoundReviewer {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private static final String TOOLS = "Read,Grep,Glob,Bash";
    /** The CLI's own allow-list; the fence on the run is what the human's allow rules cannot widen. */
    private static final List<String> READS = List.of("Read", "Grep", "Glob", "Bash(git diff:*)",
            "Bash(git log:*)", "Bash(git show:*)", "Bash(git status:*)", "Bash(git blame:*)",
            "Bash(git merge-base:*)", "Bash(git rev-parse:*)", "Bash(git ls-files:*)", "Bash(git -C:*)");
    /**
     * The human's own allow rules still load with their MCP servers, so what must never run is denied as well.
     * Git takes {@code --ou} for {@code --output}, which writes a file, and {@code -c} for any config, an external
     * diff included.
     */
    private static final List<String> REFUSED = List.of("Edit", "Write", "NotebookEdit",
            "Bash(git push:*)", "Bash(git commit:*)", "Bash(git reset:*)", "Bash(git checkout:*)",
            "Bash(git stash:*)", "Bash(git restore:*)", "Bash(git clean:*)", "Bash(git * --ou*)",
            "Bash(git -c:*)", "Bash(git --config-env:*)", "Read(**/review_replies.md)");
    /** The tests ran before the round reached review; every role building at once in one worktree is the cost. */
    private static final List<String> BUILDS = List.of("Bash(./gradlew:*)", "Bash(gradle:*)", "Bash(./mvnw:*)",
            "Bash(mvn:*)", "Bash(npm:*)", "Bash(npx:*)", "Bash(yarn:*)", "Bash(pnpm:*)", "Bash(composer:*)",
            "Bash(vendor/bin/phpunit:*)", "Bash(pytest:*)", "Bash(make:*)", "Bash(docker:*)", "Bash(timeout:*)");

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
        cmd.addAll(REFUSED);
        cmd.addAll(BUILDS);
        cmd.addAll(ReadOnlyTools.MCP_WRITES);
        cmd.add("--allowedTools");
        cmd.addAll(READS);
        cmd.addAll(mcpReads);
        List<String> tools = new ArrayList<>(mcpReads);
        tools.add(HeadlessClaude.STRUCTURED_OUTPUT);
        return judged(headless.run(round.worktrees().getFirst(), TIMEOUT, cmd,
                new ReadScope(round.worktrees(), tools, true), AssistantCallKind.MASTER_REVIEW,
                round.worktrees().getFirst().toString()));
    }

    @Override
    public boolean loadsMcpServer() {
        return !ReadOnlyTools.mcpNamed(assistant.allowedTools()).isEmpty();
    }

    private static Answer<Judgement> judged(HeadlessClaude.Reply reply) {
        if (!reply.answered()) {
            return new Answer<>(Optional.of(Judgement.failed("the review failed: " + reply.cause())), reply.usage());
        }
        JsonNode answer = reply.envelope().path("structured_output");
        return new Answer<>(Optional.of(answer.isObject() ? judgement(answer)
                : Judgement.failed("the review answered nothing readable")), reply.usage());
    }

    private static Judgement judgement(JsonNode answer) {
        List<Finding> findings = new ArrayList<>();
        answer.path("findings").forEach(f -> findings.add(new Finding(f.path("file").asString(""),
                f.path("issue").asString(""), f.path("pattern").asString(""),
                f.path("severity").asString("").strip().toLowerCase(java.util.Locale.ROOT))));
        List<Premise> premises = new ArrayList<>();
        answer.path("premises").forEach(p -> premises.add(new Premise(p.path("claim").asString(""),
                p.path("provenBy").asString(""))));
        return new Judgement(answer.path("failure").asString(""), answer.path("verdict").asString(""),
                findings, answer.path("question").asString(""), premises);
    }
}

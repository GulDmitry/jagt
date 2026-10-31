package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.protocol.QaRead;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.QaVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class HeadlessClaudeQaReader implements QaReader {

    static final String REPORT = "qa-report.md";

    private static final Duration TIMEOUT = Duration.ofMinutes(20);
    private static final String TOOLS = "Read,Grep,Glob,Bash,Write";
    private static final List<String> ALLOWED = List.of("Read", "Grep", "Glob", "Write(" + REPORT + ")",
            "Bash(curl -s:*)", "Bash(curl -sS:*)", "Bash(git log:*)", "Bash(git show:*)", "Bash(git diff:*)");
    /** A request that sends a body or names a method writes to the environment. */
    private static final List<String> REFUSED = List.of("Edit", "NotebookEdit", "Bash(curl * -X *)",
            "Bash(curl * --request*)", "Bash(curl * -d*)", "Bash(curl * --data*)", "Bash(curl * -F*)",
            "Bash(curl * --form*)", "Bash(curl * -T*)", "Bash(curl * --upload*)", "Bash(curl * --json*)",
            "Bash(git push:*)", "Bash(git commit:*)", "Bash(git reset:*)", "Bash(git checkout:*)");

    private final HeadlessClaude headless;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;

    @Override
    public Answer<QaVerdict> read(Run run) {
        List<String> mcpReads = ReadOnlyTools.mcpNamed(assistant.allowedTools());
        List<String> cmd = new ArrayList<>(List.of(claude.command(), run.prompt(), "-p",
                "--json-schema", QaRead.SCHEMA.json(), "--output-format", "json",
                "--setting-sources", "user", "--no-session-persistence"));
        if (!run.shared().isBlank()) {
            cmd.addAll(List.of("--append-system-prompt", run.shared()));
        }
        if (mcpReads.isEmpty()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}"));
        }
        if (!run.model().isBlank()) {
            cmd.addAll(List.of("--model", run.model()));
        }
        cmd.addAll(List.of("--tools", TOOLS, "--permission-mode", "dontAsk", "--disallowedTools"));
        cmd.addAll(REFUSED);
        cmd.addAll(ReadOnlyTools.MCP_WRITES);
        cmd.add("--allowedTools");
        cmd.addAll(ALLOWED);
        cmd.addAll(mcpReads);
        List<String> tools = new ArrayList<>(mcpReads);
        tools.add(HeadlessClaude.STRUCTURED_OUTPUT);
        HeadlessClaude.Reply reply = headless.run(run.worktree(), TIMEOUT, cmd,
                new ReadScope(List.of(run.worktree()), tools, true), AssistantCallKind.QA_CHECK,
                run.worktree().toString());
        return new Answer<>(Optional.of(verdict(run.sha(), reply)), reply.usage());
    }

    private static QaVerdict verdict(String sha, HeadlessClaude.Reply reply) {
        JsonNode envelope = reply.envelope();
        if (envelope == null) {
            return QaVerdict.failed(sha, "the check failed: " + reply.cause());
        }
        JsonNode answer = envelope.path("structured_output");
        if (!answer.isObject()) {
            return QaVerdict.failed(sha, "the check answered nothing readable");
        }
        List<QaVerdict.Row> rows = new ArrayList<>();
        for (JsonNode row : answer.path("rows")) {
            Optional<QaVerdict.Status> status = QaVerdict.Status.of(row.path("status").asString(""));
            rows.add(new QaVerdict.Row(row.path("criterion").asString(""),
                    status.orElse(QaVerdict.Status.UNPROVABLE), row.path("evidence").asString("")));
        }
        return new QaVerdict(sha, answer.path("failure").asString(""), rows, answer.path("question").asString(""),
                answer.path("waitingOn").asString(""));
    }
}

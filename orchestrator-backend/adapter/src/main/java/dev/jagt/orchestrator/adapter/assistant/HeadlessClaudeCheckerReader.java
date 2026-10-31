package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
@Slf4j
public class HeadlessClaudeCheckerReader implements CheckerReader {

    private static final Duration TIMEOUT = Duration.ofMinutes(20);

    private final HeadlessClaude headless;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;
    private final List<RoleDefinition> roles;

    @Override
    public Answer<QaVerdict> read(Run run) {
        HeadlessClaude.Reply reply = ask(run);
        QaVerdict verdict = verdict(run.sha(), reply);
        write(run.worktree().resolve(run.role() + "-report.md"), run.role(), verdict);
        return new Answer<>(Optional.of(verdict), reply.usage());
    }

    @Override
    public Answer<QaDecline> reconsider(Run run) {
        HeadlessClaude.Reply reply = ask(run);
        JsonNode answer = reply.envelope() == null ? null : reply.envelope().path("structured_output");
        QaDecline decline = answer == null || !answer.isObject()
                ? QaDecline.failed(run.sha(), reply.envelope() == null ? "the check failed: " + reply.cause()
                        : "the check answered nothing readable")
                : new QaDecline(run.sha(), answer.path("failure").asString(""), answer.path("dispute").asString(""));
        return new Answer<>(Optional.of(decline), reply.usage());
    }

    private HeadlessClaude.Reply ask(Run run) {
        RoleDefinition.Tools scope = roles.stream().filter(role -> role.id().equals(run.role())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no installed role " + run.role())).tools();
        List<String> mcpReads = ReadOnlyTools.mcpNamed(assistant.allowedTools());
        List<String> cmd = new ArrayList<>(List.of(claude.command(), run.prompt(), "-p",
                "--json-schema", run.schema(), "--output-format", "json",
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
        cmd.addAll(List.of("--tools", String.join(",", scope.cli()), "--permission-mode", "dontAsk", "--disallowedTools"));
        cmd.addAll(scope.refuse());
        cmd.addAll(ReadOnlyTools.MCP_WRITES);
        cmd.add("--allowedTools");
        cmd.addAll(scope.allow());
        cmd.addAll(mcpReads);
        List<String> tools = new ArrayList<>(mcpReads);
        tools.add(HeadlessClaude.STRUCTURED_OUTPUT);
        return headless.run(run.worktree(), TIMEOUT, cmd, new ReadScope(List.of(run.worktree()), tools, scope.git(), List.of(),
                        scope.fetch() ? run.environments() : List.of()),
                AssistantCallKind.QA_CHECK, run.worktree().toString());
    }

    private static void write(Path file, String role, QaVerdict verdict) {
        if (verdict.rows().isEmpty()) {
            return;
        }
        String rows = verdict.rows().stream().map(row -> "| " + row.criterion() + " | " + row.status() + " | "
                + row.evidence() + " |").collect(Collectors.joining("\n"));
        try {
            Files.writeString(file, "# " + role.toUpperCase(Locale.ROOT) + " report, " + verdict.sha() + "\n\n| criterion | status | evidence |\n|---|---|---|\n"
                    + rows + "\n");
        } catch (IOException unwritten) {
            log.atWarn().setMessage("qa report not written").addKeyValue("file", file)
                    .addKeyValue("cause", unwritten.toString()).log();
        }
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

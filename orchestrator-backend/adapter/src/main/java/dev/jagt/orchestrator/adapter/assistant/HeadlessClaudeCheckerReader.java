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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class HeadlessClaudeCheckerReader implements CheckerReader {

    private static final Duration TIMEOUT = Duration.ofMinutes(20);

    private final HeadlessClaude headless;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;
    private final List<RoleDefinition> roles;

    @Override
    public Answer<QaVerdict> read(Run run) {
        HeadlessReply reply = ask(run);
        QaVerdict verdict = CheckerAnswers.verdict(run.sha(), reply);
        CheckerAnswers.write(run.worktree().resolve(run.role() + "-report.md"), run.role(), verdict);
        return new Answer<>(Optional.of(verdict), reply.usage());
    }

    @Override
    public Answer<QaDecline> reconsider(Run run) {
        HeadlessReply reply = ask(run);
        return new Answer<>(Optional.of(CheckerAnswers.decline(run.sha(), reply)), reply.usage());
    }

    private HeadlessReply ask(Run run) {
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
}

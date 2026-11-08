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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class HeadlessClaudeCheckerReader {

  private static final Duration TIMEOUT = Duration.ofMinutes(20);

  private final HeadlessClaude headless;
  private final ClaudeProperties claude;
  private final AssistantProperties assistant;
  private final List<RoleDefinition> roles;

  public Answer<QaVerdict> read(CheckerReader.Run run) {
    HeadlessReply reply = ask(run);
    if (reply.envelope() != null && reply.envelope().path("structured_output").isObject()) {
      HeadlessClaude.answered(
          run.sha(), AssistantCallKind.QA_CHECK, reply.envelope().path("structured_output"));
    }
    return CheckerAnswers.verdictOf(run, reply);
  }

  public Answer<QaDecline> reconsider(CheckerReader.Run run) {
    return CheckerAnswers.declineOf(run, ask(run));
  }

  private HeadlessReply ask(CheckerReader.Run run) {
    List<String> mcpReads = ReadOnlyTools.mcpNamed(assistant.allowedTools());
    List<String> cmd =
        new ArrayList<>(
            List.of(
                claude.command(),
                run.prompt(),
                "-p",
                "--json-schema",
                run.schema(),
                "--output-format",
                "json",
                "--setting-sources",
                "user",
                "--no-session-persistence"));
    if (!run.shared().isBlank()) {
      cmd.addAll(List.of("--append-system-prompt", run.shared()));
    }
    if (mcpReads.isEmpty()) {
      cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}"));
    }
    if (!run.model().isBlank()) {
      cmd.addAll(List.of("--model", run.model()));
    }
    RoleDefinition.Tools scope =
        roles.stream()
            .filter(role -> role.id().equals(run.role()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no installed role " + run.role()))
            .tools();
    ClaudeToolScope claude =
        ClaudeToolScope.of(scope.shell(), scope.refuseShell(), scope.refuseRead(), scope.fetch());
    cmd.addAll(
        List.of("--tools", claude.tools(), "--permission-mode", "dontAsk", "--disallowedTools"));
    cmd.addAll(claude.refuse());
    cmd.addAll(ReadOnlyTools.MCP_WRITES);
    cmd.add("--allowedTools");
    cmd.addAll(claude.allow());
    cmd.addAll(mcpReads);
    List<String> tools = new ArrayList<>(mcpReads);
    tools.add(HeadlessClaude.STRUCTURED_OUTPUT);
    return headless.run(
        run.worktree(),
        TIMEOUT,
        cmd,
        new ReadScope(
            Stream.concat(Stream.of(run.worktree()), run.reach().paths().stream()).toList(),
            tools,
            scope.git(),
            List.of(),
            scope.fetch() ? run.reach().urls() : List.of()),
        AssistantCallKind.QA_CHECK,
        run.worktree().toString());
  }
}

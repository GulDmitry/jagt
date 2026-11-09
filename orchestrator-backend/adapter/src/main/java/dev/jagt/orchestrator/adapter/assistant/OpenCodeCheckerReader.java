package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.AcceptanceDecline;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AssistantCallKind;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
class OpenCodeCheckerReader {

  private static final Duration TIMEOUT = Duration.ofMinutes(20);

  private final HeadlessOpenCode headless;
  private final OpenCodeProperties opencode;
  private final List<RoleDefinition> roles;

  Answer<AcceptanceVerdict> read(CheckerReader.Run run) {
    return CheckerAnswers.verdictOf(run, ask(run));
  }

  Answer<AcceptanceDecline> reconsider(CheckerReader.Run run) {
    return CheckerAnswers.declineOf(run, ask(run));
  }

  private HeadlessReply ask(CheckerReader.Run run) {
    RoleDefinition.Tools tools =
        roles.stream()
            .filter(role -> role.id().equals(run.role()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no installed role " + run.role()))
            .tools();
    String model =
        opencode
            .readModel(run.role())
            .orElseThrow(() -> new IllegalStateException("no OpenCode model for " + run.role()));
    String prompt = run.shared().isBlank() ? run.prompt() : run.shared() + "\n\n" + run.prompt();
    OpenCodeScope scope =
        OpenCodeScope.of(
            tools.shell(),
            tools.refuseShell(),
            tools.refuseRead(),
            tools.fetch(),
            tools.git(),
            Stream.concat(Stream.of(run.worktree()), run.reach().paths().stream()).toList(),
            run.reach().urls());
    return headless.run(
        new OpenCodeRead(
            run.worktree(),
            prompt,
            run.schema(),
            model,
            scope,
            AssistantCallKind.ACCEPTANCE_CHECK,
            TIMEOUT));
  }
}

package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.task.acceptance.AcceptanceDecline;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * A role named under {@code orchestrator.opencode.reads} reads on OpenCode, every other on {@code
 * claude -p}.
 */
@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class ChosenCheckerReader implements CheckerReader {

  private final HeadlessClaudeCheckerReader claude;
  private final OpenCodeCheckerReader opencode;
  private final OpenCodeProperties properties;

  @Override
  public Answer<AcceptanceVerdict> read(Run run) {
    return properties.readModel(run.role()).isPresent() ? opencode.read(run) : claude.read(run);
  }

  @Override
  public Answer<AcceptanceDecline> reconsider(Run run) {
    return properties.readModel(run.role()).isPresent()
        ? opencode.reconsider(run)
        : claude.reconsider(run);
  }
}

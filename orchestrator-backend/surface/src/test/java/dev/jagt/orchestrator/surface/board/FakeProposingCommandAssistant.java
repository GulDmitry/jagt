package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.Optional;

record FakeProposingCommandAssistant(CommandProposal proposal) implements CommandAssistant {

  @Override
  public Answer<CommandProposal> mapCommand(String text, Menu menu) {
    return new Answer<>(Optional.of(proposal), TokenUsage.NONE);
  }
}

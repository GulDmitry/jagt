package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.Optional;

record FakeProposingCommandAssistant(CommandProposal proposal) implements CommandAssistant {

  @Override
  public Answer<CommandProposal> mapCommand(String text, Menu menu) {
    return new Answer<>(Optional.of(proposal), TokenUsage.NONE);
  }
}

package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestRoundsBuilder.rounds;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import lombok.With;

@With
public record TestHandBackBuilder() {

  public static TestHandBackBuilder handBack() {
    return new TestHandBackBuilder();
  }

  public HandBack build(WorktreeChanges worktreeChanges, ConfigService configService) {
    return new HandBack(worktreeChanges, rounds().build(configService), configService);
  }
}

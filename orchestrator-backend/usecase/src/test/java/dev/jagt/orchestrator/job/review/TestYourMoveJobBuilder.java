package dev.jagt.orchestrator.job.review;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReachOffers;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import java.time.Clock;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestYourMoveJobBuilder(String mode, @Nullable List<String> mine) {

  public static TestYourMoveJobBuilder yourMoveJob() {
    return new TestYourMoveJobBuilder("off", null);
  }

  public ConfigFile config() {
    return ConfigFile.defaults()
        .withArbiter(new ConfigFile.ArbiterConfig(mode, null, null, mine, null));
  }

  public YourMoveJob build(
      StateService state,
      Notifications notifications,
      WorktreeChanges worktreeChanges,
      ConfigService configService,
      Clock clock) {
    return new YourMoveJob(
        state,
        notifications,
        new HandBack(
            worktreeChanges,
            new Rounds(
                configService,
                ignored -> false,
                new ReachOffers(() -> false, () -> false),
                unheld -> null,
                new Checkers(new TaskChart(Composition.builtIn()))),
            configService),
        clock,
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}

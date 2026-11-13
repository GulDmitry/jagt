package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.SubAgentBriefing;
import dev.jagt.orchestrator.service.TicketScenarios;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestSessionBriefBuilder(Path root, String task, String project) {

  public static TestSessionBriefBuilder sessionBrief() {
    return new TestSessionBriefBuilder(Path.of("/state"), "ABC-42", "web");
  }

  public String build(TicketScenarios scenarios, NewRepo primary, List<NewRepo> repos) {
    return new SubAgentBriefing(
            new SessionBrief(orchestratorPaths().withRoot(root).build()), scenarios)
        .of(
            NewTask.builder(task, project).build(),
            primary,
            repos,
            ConfigService.ConfigFile.AutoplanConfig.defaults(),
            ConfigService.ConfigFile.VisualizeConfig.defaults(),
            ConfigService.ConfigFile.DiagnoseConfig.defaults());
  }
}

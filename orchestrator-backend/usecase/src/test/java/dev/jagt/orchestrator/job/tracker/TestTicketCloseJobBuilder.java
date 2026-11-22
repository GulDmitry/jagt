package dev.jagt.orchestrator.job.tracker;

import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import java.time.Clock;
import lombok.With;

@With
public record TestTicketCloseJobBuilder(TrackerConfig tracker) {

  public static TestTicketCloseJobBuilder ticketCloseJob() {
    return new TestTicketCloseJobBuilder(
        new TrackerConfig("both", "jira", "jdoe", "In Progress", "Ready for Stage", null));
  }

  public ConfigFile config() {
    return ConfigFile.defaults().withTracker(tracker);
  }

  public TicketCloseJob build(
      ConfigService configService,
      StateService state,
      TrackerClose close,
      CommandService commands,
      Clock clock) {
    return new TicketCloseJob(configService, state, close, commands, clock);
  }
}

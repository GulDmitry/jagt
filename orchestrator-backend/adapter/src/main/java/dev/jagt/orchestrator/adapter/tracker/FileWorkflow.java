package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** A Markdown file's items: unchecked reads as the start stage, checked as the done one. */
@Component
@ConditionalOnProperty(name = "orchestrator.tracker.workflow", havingValue = FileWorkflow.ID)
@RequiredArgsConstructor
public class FileWorkflow implements TrackerWorkflow {

  static final String ID = "file";

  private final ConfigService configService;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isItemKey(@Nullable String name) {
    return name != null && MarkdownItems.KEY.matcher(name).matches();
  }

  /** The file driver lists the file whole, so no query is phrased. */
  @Override
  public String candidateQuery() {
    return "";
  }

  @Override
  public boolean startsWork(TicketFacts facts) {
    return StageMatch.same(facts.trackerStatus(), configService.load().tracker().startStatus());
  }

  @Override
  public boolean closesWork(TicketFacts facts) {
    return StageMatch.same(facts.trackerStatus(), configService.load().tracker().doneStatus());
  }
}

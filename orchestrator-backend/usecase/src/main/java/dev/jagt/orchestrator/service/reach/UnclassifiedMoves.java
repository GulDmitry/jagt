package dev.jagt.orchestrator.service.reach;

import dev.jagt.orchestrator.flow.Unclassified;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A move the chart has no class for is jagt's own fault: logged once per kind, and the board's head
 * says so until a restart reads the chart again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnclassifiedMoves implements Consumer<Unclassified> {

  private final Reach reach;

  @Override
  public void accept(Unclassified found) {
    String where = String.join(" ", found.where());
    log.atWarn()
        .setMessage("move unclassified")
        .addKeyValue("kind", found.kind())
        .addKeyValue("where", where)
        .addKeyValue("refused", found.refused())
        .addKeyValue("effect", "the board's head says so, a claim or a press is refused")
        .log();
    reach.marked(
        new ReachLost(
            "flow", found.kind(), List.of(), "the chart declares no class for it at " + where));
  }
}

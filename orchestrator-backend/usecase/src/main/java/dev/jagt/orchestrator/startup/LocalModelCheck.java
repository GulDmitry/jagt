package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.LocalModelProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.task.AssistantCallKind;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A local model that is set must be an http(s) address, name its model and serve only reads that
 * need no tool.
 */
@Component
@RequiredArgsConstructor
public class LocalModelCheck implements StartupCheck {

  private static final String ENDPOINT = "orchestrator.assistant.local.endpoint";
  private static final String MODEL = "orchestrator.assistant.local.model";
  private static final String KINDS = "orchestrator.assistant.local.kinds";

  private final LocalModelProperties local;

  @Override
  public List<Problem> problems() {
    if (!local.on()) {
      return List.of();
    }
    List<Problem> found = new ArrayList<>();
    if (!HttpAddress.is(local.endpoint())) {
      found.add(
          new Problem(
              ENDPOINT, ENDPOINT + ": '" + local.endpoint() + "' is not an http(s) address"));
    }
    if (local.model().isEmpty()) {
      found.add(new Problem(MODEL, MODEL + ": a local endpoint needs the model name to send"));
    }
    local.kinds().stream()
        .filter(AssistantCallKind::readsTools)
        .forEach(
            kind ->
                found.add(
                    new Problem(
                        KINDS,
                        KINDS
                            + ": "
                            + kind
                            + " reads tools, which a local endpoint does not offer")));
    return found;
  }

  @Override
  public boolean machine() {
    return false;
  }
}

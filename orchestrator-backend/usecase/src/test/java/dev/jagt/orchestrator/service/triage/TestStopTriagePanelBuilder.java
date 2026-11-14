package dev.jagt.orchestrator.service.triage;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestStopTriagePanelBuilder(Path root, String brief) {

  public static TestStopTriagePanelBuilder stopTriagePanel() {
    return new TestStopTriagePanelBuilder(Path.of("/state"), "brief");
  }

  public StopTriagePanel build(ChargedReviews reviews) throws IOException {
    Files.createDirectories(root.resolve("roles/triage"));
    Files.writeString(root.resolve("roles/triage/brief.md.dist"), brief);
    return new StopTriagePanel(
        reviews,
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));
  }
}

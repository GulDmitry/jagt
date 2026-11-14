package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StepCheckTest {

  @TempDir Path root;

  private StepCheck check;

  @BeforeEach
  void checkOverTheTempRoot() throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    check = new StepCheck(new SessionCases(paths), new SessionBrief(paths));
  }

  @Test
  void refusesStepNoCaseSends() throws Exception {
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(check.problems())
        .contains(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: no case sends the step 'deploy' — add one, or it"
                    + " never reaches a session"));
  }

  @Test
  void refusesStepTheBriefHoldsNoSectionFor() throws Exception {
    Files.writeString(root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n");
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));

    assertThat(check.problems())
        .contains(
            new Problem(
                "roles/session/brief.md",
                "roles/session/brief.md: no section for the step 'deploy' — add one, or"
                    + " the session is told nothing of it"));
  }

  @Test
  void passesTheShippedBriefAndCases() throws Exception {
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));

    assertThat(check.problems()).isEmpty();
  }
}

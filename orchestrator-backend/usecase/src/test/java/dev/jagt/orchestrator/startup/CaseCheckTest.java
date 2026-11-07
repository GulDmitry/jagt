package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseCheckTest {

  @Test
  void refusesACaseNamingAStepTheBriefLacks(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n\n"
            + "## woke\n\nstep: woke\nat: any\nby: jagt\nreplies: any; plain\nA typo.\n");

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .containsExactly(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: case woke names step 'woke', which no brief section"
                    + " holds"));
  }

  @Test
  void refusesABriefStepNoCaseSends(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n\n## deploy\n\ncheck it\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .containsExactly(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: no case sends the brief step 'deploy' — add one, or it"
                    + " never reaches a session"));
  }

  @Test
  void refusesACaseNamingNoStatus(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: WAITING\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .extracting(Problem::text)
        .anySatisfy(
            text ->
                assertThat(text).startsWith("roles/session/cases.md: case wake has at: 'WAITING'"));
  }

  @Test
  void passesTheShippedCasesAgainstTheShippedBrief(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .isEmpty();
  }

  @Test
  void refusesToStartWhenTheHumansCasesCannotBeRead(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session/cases.md"));

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .extracting(Problem::text)
        .containsExactly(
            "roles/session/cases.md: cannot be read — java.io.IOException: Is a directory");
  }

  @Test
  void refusesToStartWhenTheHumansBriefCannotBeRead(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/session/brief.md"));

    assertThat(new CaseCheck(new SessionCases(paths), new SessionBrief(paths)).problems())
        .extracting(Problem::text)
        .containsExactly(
            "roles/session/brief.md: cannot be read — java.io.IOException: Is a directory");
  }
}

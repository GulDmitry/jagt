package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.host.Problem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseCheckTest {

  @TempDir Path root;

  private OrchestratorPaths paths;
  private CaseCheck check;

  @BeforeEach
  void checkOverTheTempRoot() throws Exception {
    paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    Files.createDirectories(root.resolve("roles/session"));
    check =
        new CaseCheck(
            new SessionCases(paths),
            new SessionBrief(paths),
            new Checkers(new TaskChart(Composition.builtIn())));
  }

  @Test
  void refusesCaseNamingStepTheBriefLacks() throws Exception {
    Files.writeString(root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        """
        ## wake

        step: wake
        at: any
        by: jagt
        replies: any; plain
        A reset.

        ## woke

        step: woke
        at: any
        by: jagt
        replies: any; plain
        A typo.
        """);

    assertThat(check.problems())
        .containsExactly(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: case woke names step 'woke', which no brief section"
                    + " holds"));
  }

  @Test
  void refusesBriefStepNoCaseSends() throws Exception {
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n\n## deploy\n\ncheck it\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(check.problems())
        .containsExactly(
            new Problem(
                "roles/session/cases.md",
                "roles/session/cases.md: no case sends the brief step 'deploy' — add one, or it"
                    + " never reaches a session"));
  }

  @Test
  void refusesCaseNamingNoStatus() throws Exception {
    Files.writeString(root.resolve("roles/session/brief.md"), "## wake\n\ngo on\n");
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## wake\n\nstep: wake\nat: WAITING\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(check.problems())
        .extracting(Problem::text)
        .anySatisfy(text ->
            assertThat(text).startsWith("roles/session/cases.md: case wake has at: 'WAITING'"));
  }

  @Nested
  class WithTheShippedCases {

    @BeforeEach
    void copyTheShippedCases() throws Exception {
      Files.copy(
          Path.of("../../roles/session/brief.md.dist"),
          root.resolve("roles/session/brief.md.dist"));
      Files.copy(
          Path.of("../../roles/session/cases.md.dist"),
          root.resolve("roles/session/cases.md.dist"));
    }

    @Test
    void passesTheShippedCasesAgainstTheShippedBrief() throws Exception {
      assertThat(check.problems()).isEmpty();
    }

    @Test
    void passesTheShippedCasesWhereNoCheckerIsInstalled() throws Exception {
      assertThat(
              new CaseCheck(
                      new SessionCases(paths),
                      new SessionBrief(paths),
                      new Checkers(new TaskChart(Composition.of(List.of()))))
                  .problems())
          .isEmpty();
    }

    @Test
    void refusesToStartWhenTheHumansCasesCannotBeRead() throws Exception {
      Files.createDirectories(root.resolve("roles/session/cases.md"));

      assertThat(check.problems())
          .extracting(Problem::text)
          .containsExactly(
              "roles/session/cases.md: cannot be read — java.io.IOException: Is a directory");
    }

    @Test
    void refusesToStartWhenTheHumansBriefCannotBeRead() throws Exception {
      Files.createDirectories(root.resolve("roles/session/brief.md"));

      assertThat(check.problems())
          .extracting(Problem::text)
          .containsExactly(
              "roles/session/brief.md: cannot be read — java.io.IOException: Is a directory");
    }
  }
}

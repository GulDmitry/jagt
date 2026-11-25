package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.config.role.SessionCases;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.testing.FakeHost;
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
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), FakeHost.machine());
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
  class WithAuditInstalled {

    private CaseCheck withAudit;

    @BeforeEach
    void auditBesideAcceptance() {
      withAudit =
          new CaseCheck(
              new SessionCases(paths),
              new SessionBrief(paths),
              new Checkers(
                  new TaskChart(
                      Composition.of(
                          List.of(
                              new CheckerRole("acceptance", false, List.of("accept"), List.of()),
                              new CheckerRole(
                                  "audit", false, List.of("audit-read"), List.of()))))));
    }

    @Test
    void refusesInstalledCheckerWithNoCaseSendingItsRound() throws Exception {
      Files.writeString(
          root.resolve("roles/session/brief.md"), "## acceptance-round\n\nfix rows\n");
      Files.writeString(
          root.resolve("roles/session/cases.md"),
          """
          ## acceptance-round-by-acceptance

          step: acceptance-round
          at: DEPLOYED
          by: acceptance
          replies: IN_PROGRESS; plain
          Acceptance sends its rows.
          """);

      assertThat(withAudit.problems())
          .containsExactly(
              new Problem(
                  "roles/session/cases.md",
                  "roles/session/cases.md: installed checker audit has no case sending the step"
                      + " 'acceptance-round' by: audit — add acceptance-round-by-audit, or its rows"
                      + " never reach a session"));
    }

    @Test
    void startsWhereInstalledSecondCheckerHasItsRoundCase() throws Exception {
      Files.writeString(
          root.resolve("roles/session/brief.md"), "## acceptance-round\n\nfix rows\n");
      Files.writeString(
          root.resolve("roles/session/cases.md"),
          """
          ## acceptance-round-by-acceptance

          step: acceptance-round
          at: DEPLOYED
          by: acceptance
          replies: IN_PROGRESS; plain
          Acceptance sends its rows.

          ## acceptance-round-by-audit

          step: acceptance-round
          at: DEPLOYED
          by: audit
          replies: IN_PROGRESS; plain
          Audit sends its rows.
          """);

      assertThat(withAudit.problems()).isEmpty();
    }
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

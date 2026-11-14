package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectSetupTest {

  @TempDir Path root;

  private final Processes git = mock(Processes.class);
  private final Processes bareGit = mock(Processes.class, call -> new Processes.Result(1, "", ""));

  private Path configFile;
  private OrchestratorPaths paths;
  private ConfigService config;
  private ProjectSetup setup;
  private ProjectSetup bareSetup;
  private Path confFolder;
  private Path repo;
  private ProjectSetup.Entry billingEntry;

  @BeforeEach
  void wireSetup() throws Exception {
    confFolder = Files.createDirectories(root.resolve("conf"));
    configFile =
        Files.writeString(confFolder.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    repo = Files.createDirectories(root.resolve("billing"));
    Files.createDirectories(repo.resolve(".git"));
    billingEntry =
        new ProjectSetup.Entry(
            "billing", repo.toString(), "origin/main", null, null, null, null, null);
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(configFile.toString()));
    config =
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())));
    when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
    when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
        .thenReturn(new Processes.Result(0, repo + "\n", ""));
    doReturn(new Processes.Result(0, repo + "\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
    setup = new ProjectSetup(config, paths, git, checkout -> List.of());
    bareSetup = new ProjectSetup(config, paths, bareGit, checkout -> List.of());
  }

  @AfterEach
  void unlock() {
    confFolder.toFile().setWritable(true);
  }

  @Test
  void addsTheEntryUnderProjectsAndKeepsEveryOtherLineAsItWas() throws Exception {
    Files.writeString(
        configFile,
        """
        # mine
        orchestrator:
          projects:
            web:
              path: /srv/web
              baseBranch: origin/main
          keepAwake: false   # stays
        """);
    String answer =
        setup.add(
            new ProjectSetup.Entry(
                "billing",
                repo.toString(),
                "origin/main",
                "dev",
                List.of("./gradlew", "test"),
                "the invoicing backend",
                null,
                null));

    assertThat(answer).isEqualTo("Added billing to jagt.yml");

    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            # mine
            orchestrator:
              projects:
                billing:
                  path: "%s"
                  baseBranch: "origin/main"
                  deployBranch: "dev"
                  verifyCommand: ["./gradlew","test"]
                  about: "the invoicing backend"
                web:
                  path: /srv/web
                  baseBranch: origin/main
              keepAwake: false   # stays
            """
                .formatted(repo));
  }

  @Test
  void refusesPathThatIsNotGitRepositoryAndWritesNothing() throws Exception {
    Path folder = Files.createDirectories(root.resolve("notes"));
    doReturn(new Processes.Result(128, "", "not a git repository"))
        .when(git)
        .run(any(), any(), anyList());
    assertThatThrownBy(() ->
            setup.add(
                new ProjectSetup.Entry(
                    "notes", folder.toString(), "origin/main", null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("not a git repository");
    assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
  }

  @Test
  void refusesKeyAnotherProjectAlreadyHolds() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            billing: {path: /srv/other, baseBranch: origin/main}
        """);
    assertThatThrownBy(() -> setup.add(billingEntry)).isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:
              projects:
                billing: {path: /srv/other, baseBranch: origin/main}
            """);
  }

  @Test
  void addsUnderTheOrchestratorsProjectsAtTheIndentItsEntriesUseAndNotUnderTheTrackers()
      throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:   # mine
            tracker:
                projects:
                    - ABC
            projects:
                web:
                    path: /srv/web
                    baseBranch: origin/main
        """);

    setup.add(billingEntry);

    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:   # mine
                tracker:
                    projects:
                        - ABC
                projects:
                    billing:
                        path: "%s"
                        baseBranch: "origin/main"
                    web:
                        path: /srv/web
                        baseBranch: origin/main
            """
                .formatted(repo));
  }

  @Test
  void addsTheFirstProjectToAnEmptyProjectsMap() throws Exception {
    Files.writeString(configFile, "orchestrator:\n  projects: {}\n  keepAwake: false\n");

    setup.add(billingEntry);

    assertThat(Files.readString(configFile))
        .isEqualTo(
            "orchestrator:\n  projects:\n    billing:\n      path: \"%s\"\n".formatted(repo)
                + "      baseBranch: \"origin/main\"\n  keepAwake: false\n");
  }

  @Test
  void refusesFolderInsideRepositoryAndNamesTheRepositoryToAddInstead() throws Exception {
    Path docs = Files.createDirectories(repo.resolve("docs"));
    doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), anyList());

    ProjectSetup.Reading reading = setup.read(docs.toString());

    assertThat(reading.ready()).isFalse();
    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .anyMatch(text -> text.contains(repo.toString()));
  }

  @Test
  void refusesDeployBranchEqualToTheBaseBranchAndLeavesTheFileUnchanged() throws Exception {
    assertThatThrownBy(() ->
            setup.add(
                new ProjectSetup.Entry(
                    "billing", repo.toString(), "origin/main", "main", null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jagt never writes to the base branch");
    assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
  }

  @Test
  void readsAnOriginWithoutTheCredentialsItCarries() throws Exception {
    doReturn(new Processes.Result(0, "https://user:secret@host.test/g/p.git\n", ""))
        .when(git)
        .run(any(), any(), anyList());
    doReturn(new Processes.Result(0, repo + "\n", ""))
        .when(git)
        .run(any(), any(), argThat(command -> command.contains("--show-toplevel")));

    ProjectSetup.Reading reading = setup.read(repo.toString());

    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("a git repository, origin https://host.test/g/p.git");
  }

  @Test
  void saysGitCouldNotReadRepositoryItRefusesForItsOwnership() throws Exception {
    when(git.run(any(), any(), anyList()))
        .thenReturn(
            new Processes.Result(
                128, "", "fatal: detected dubious ownership in repository at '/x'\nmore"));

    ProjectSetup.Reading reading = setup.read(repo.toString());

    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .containsExactly(
            "git could not read "
                + repo
                + ": fatal: detected dubious ownership in repository at '/x'");
  }

  @Test
  void putsTheFileBackWhenTheInsertedEntryDoesNotRead() throws Exception {
    ConfigService spied = spy(config);
    doThrow(new IllegalStateException("bad yaml")).when(spied).project("billing");
    ProjectSetup spying = new ProjectSetup(spied, paths, git, checkout -> List.of());

    assertThatThrownBy(() -> spying.add(billingEntry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("jagt.yml did not read after the change, so it was put back: bad yaml");
    assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
  }

  @Test
  void namesTheFileWhenItDidNotReadAndCouldNotBePutBack() throws Exception {
    ConfigService spied = spy(config);
    doAnswer(
            call -> {
              Files.delete(configFile);
              Files.createDirectories(configFile.resolve("child"));
              throw new IllegalStateException("bad yaml");
            })
        .when(spied)
        .project("billing");
    ProjectSetup spying = new ProjectSetup(spied, paths, git, checkout -> List.of());

    assertThatThrownBy(() -> spying.add(billingEntry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "jagt.yml did not read after the change and could not be put back; fix it by hand:");
  }

  @Test
  void keepsConfigFileThatIsSymlinkAndWritesTheEntryIntoItsTarget() throws Exception {
    Path target = Files.writeString(root.resolve("real.yml"), "orchestrator:\n  projects: {}\n");
    Files.delete(configFile);
    Files.createSymbolicLink(configFile, target);

    setup.add(billingEntry);

    assertThat(Files.isSymbolicLink(configFile)).isTrue();
    assertThat(Files.readString(target)).contains("billing:");
  }

  @Test
  void readsNoDirectoryWhereThePathLeadsNowhereAndKeysItByItsLowerCasedName() {
    Path missing = root.resolve("Ledger");

    ProjectSetup.Reading reading = setup.read(missing.toString());

    assertThat(reading.ready()).isFalse();
    assertThat(reading.key()).isEqualTo("ledger");
    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .containsExactly("no directory at " + missing);
  }

  @Test
  void readsFolderThatIsNoRepositoryAsSuch() throws Exception {
    Path folder = Files.createDirectories(root.resolve("notes"));
    doReturn(new Processes.Result(128, "", "fatal: not a git repository"))
        .when(git)
        .run(any(), any(), anyList());

    ProjectSetup.Reading reading = setup.read(folder.toString());

    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .containsExactly("not a git repository");
  }

  @ParameterizedTest
  @CsvSource(value = {"0,'',''", "2,'','error: No such remote origin'"})
  void readsRepositoryWithoutAnOriginAsNotReady(int exit, String stdout, String stderr)
      throws Exception {
    doReturn(new Processes.Result(exit, stdout, stderr))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("no remote named origin");
  }

  @ParameterizedTest
  @CsvSource({"main", "master"})
  void readsRepositoryWithNoRemoteFromItsLocalBranches(String branch) throws Exception {
    doReturn(new Processes.Result(0, "", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("remote") && command.size() == 4));
    doReturn(new Processes.Result(0, branch + "\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains(branch)));
    doReturn(new Processes.Result(0, branch + "\ndev\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("for-each-ref")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.ready()).isTrue();
    assertThat(reading.baseBranch()).isEqualTo(branch);
    assertThat(reading.deployBranches()).containsExactly("dev");
  }

  @Test
  void addsRepositoryWithNoRemoteAndWritesRemoteNone() throws Exception {
    doReturn(new Processes.Result(0, "", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("remote") && command.size() == 4));
    doReturn(new Processes.Result(0, "main\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("main")));

    bareSetup.add(
        new ProjectSetup.Entry("billing", repo.toString(), "main", null, null, null, null, null));

    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:
              projects:
                billing:
                  path: "%s"
                  baseBranch: "main"
                  remote: "none"
            """
                .formatted(repo));
  }

  @Test
  void saysGitCouldNotReadTheRemoteWhereItFailsForAnotherReason() throws Exception {
    doReturn(new Processes.Result(1, "", "fatal: broken config\nmore"))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("git could not read the remote of " + repo + ": fatal: broken config");
  }

  @Test
  void readsWhatTheEntryWouldSayFromTheOriginsHeadTheDeployBranchAndTheBuildScript()
      throws Exception {
    doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));
    doReturn(new Processes.Result(0, "origin/trunk\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("symbolic-ref")));
    doReturn(new Processes.Result(0, "origin/HEAD\norigin/trunk\norigin/dev\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("for-each-ref")));
    ProjectSetup scripted =
        new ProjectSetup(config, paths, bareGit, checkout -> List.of("./gradlew", "test"));

    ProjectSetup.Reading reading = scripted.read(repo.toString());

    assertThat(reading.ready()).isTrue();
    assertThat(reading.baseBranch()).isEqualTo("origin/trunk");
    assertThat(reading.deployBranch()).isEqualTo("dev");
    assertThat(reading.verifyCommand()).containsExactly("./gradlew", "test");
    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("tasks are cut from origin/trunk", "no project points here yet");
  }

  @Test
  void offersTheRemotesBranchesExceptItsHeadAndTheBaseAndPrefillsTheFirstConventionalOne()
      throws Exception {
    doReturn(new Processes.Result(0, "origin/main\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("symbolic-ref")));
    doReturn(
            new Processes.Result(
                0, "origin\norigin/main\norigin/feature-x\norigin/staging\norigin/develop\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("for-each-ref")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.deployBranch()).isEqualTo("develop");
    assertThat(reading.deployBranches()).containsExactly("feature-x", "staging", "develop");
  }

  @Test
  void prefillsNoDeployBranchWhereNoRemoteBranchIsConventionalOne() throws Exception {
    doReturn(new Processes.Result(0, "origin/main\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("symbolic-ref")));
    doReturn(new Processes.Result(0, "origin/main\norigin/feature-x\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("for-each-ref")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.deployBranch()).isEmpty();
    assertThat(reading.deployBranches()).containsExactly("feature-x");
  }

  @ParameterizedTest
  @CsvSource({"origin/main", "origin/master"})
  void fallsBackToTheConventionalOriginBranchWhereTheRemoteNamesNoHead(String branch)
      throws Exception {
    doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));
    doReturn(new Processes.Result(0, "abc\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains(branch)));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.baseBranch()).isEqualTo(branch);
    assertThat(reading.deployBranch()).isEmpty();
    assertThat(reading.verifyCommand()).isEmpty();
  }

  @Test
  void readsRepositoryWithNoBranchToCutTasksFromAsNotReady() throws Exception {
    doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));

    ProjectSetup.Reading reading = bareSetup.read(repo.toString());

    assertThat(reading.ready()).isFalse();
    assertThat(reading.baseBranch()).isEmpty();
    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("no origin/main or origin/master to cut tasks from");
  }

  @Test
  void readsRepositoryAnotherProjectAlreadyPointsAtAsNotReady() throws Exception {
    Files.writeString(
        configFile,
        "orchestrator:\n  projects:\n    web: {path: \"" + repo + "\", baseBranch: origin/main}\n");

    ProjectSetup.Reading reading = setup.read(repo.toString());

    assertThat(reading.ready()).isFalse();
    assertThat(reading.lines())
        .extracting(ProjectSetup.Line::text)
        .contains("a project already points here");
  }

  @Test
  void refusesToAddWhereTheFileHoldsNoProjectsUnderOrchestrator() throws Exception {
    Files.writeString(configFile, "orchestrator:\n  keepAwake: false\n");
    assertThatThrownBy(() -> setup.add(billingEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jagt.yml holds no projects: under orchestrator: to add to");
  }

  @Test
  void addsAtTheIndentTheExistingEntriesUseWhenItIsNotTwiceTheMapsOwn() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
             web:
                path: /srv/web
                baseBranch: origin/main
        """);

    setup.add(billingEntry);

    assertThat(Files.readString(configFile))
        .startsWith(
            """
            orchestrator:
              projects:
                 billing:
                    path: "%s"
            """
                .formatted(repo));
  }

  @Test
  void refusesRepositoryByTheFirstThingThatIsWrongWithItNotByWhatIsFine() throws Exception {
    doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", ""))
        .when(bareGit)
        .run(any(), any(), argThat(command -> command.contains("get-url")));
    assertThatThrownBy(() -> bareSetup.add(billingEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("no origin/main or origin/master to cut tasks from");
  }

  @Test
  void editRewritesTheManagedKeysOfOneBlockAndKeepsItsOtherKeysAndEveryOtherLine()
      throws Exception {
    Files.writeString(
        configFile,
        """
        # mine
        orchestrator:
          projects:
            billing:
              # why
              path: /old/path
              baseBranch: origin/main
              codeHost: github
              verifyCommand:
                - ./gradlew
                - test
            web:
              path: /srv/web
              baseBranch: origin/main
          keepAwake: false   # stays
        """);
    String answer =
        setup.edit(
            new ProjectSetup.Entry(
                "billing",
                repo.toString(),
                "origin/main",
                "dev",
                List.of("make", "test"),
                "the invoicing backend",
                null,
                null));

    assertThat(answer).isEqualTo("Changed billing in jagt.yml");
    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            # mine
            orchestrator:
              projects:
                billing:
                  path: "%s"
                  baseBranch: "origin/main"
                  deployBranch: "dev"
                  verifyCommand: ["make","test"]
                  about: "the invoicing backend"
                  # why
                  codeHost: github
                web:
                  path: /srv/web
                  baseBranch: origin/main
              keepAwake: false   # stays
            """
                .formatted(repo));
  }

  @Test
  void editKeepsTheProjectsOwnPathWhereItIsUnchanged() throws Exception {
    Files.writeString(
        configFile,
        "orchestrator:\n  projects:\n    billing:\n      path: \""
            + repo
            + "\"\n      baseBranch: origin/main\n");
    String answer =
        setup.edit(
            new ProjectSetup.Entry(
                "billing",
                repo.toString(),
                "origin/main",
                null,
                null,
                "now with a line",
                null,
                null));

    assertThat(answer).isEqualTo("Changed billing in jagt.yml");
  }

  @Test
  void editRefusesPathAnotherProjectAlreadyHoldsAndLeavesTheFileUnchanged() throws Exception {
    String before =
        "orchestrator:\n"
            + "  projects:\n"
            + "    billing:\n"
            + "      path: /srv/billing\n"
            + "      baseBranch: origin/main\n"
            + "    web:\n"
            + "      path: \""
            + repo
            + "\"\n      baseBranch: origin/main\n";
    Files.writeString(configFile, before);
    assertThatThrownBy(() -> setup.edit(billingEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("a project already points here");
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void editRefusesProjectTheFileDoesNotHold() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            web:
              path: /srv/web
              baseBranch: origin/main
        """);
    assertThatThrownBy(() -> setup.edit(billingEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No project billing");
  }

  @Test
  void editRefusesAnEntryWrittenInlineAndLeavesTheFileUnchanged() throws Exception {
    String before =
        "orchestrator:\n  projects:\n    billing: {path: /srv/billing, baseBranch: origin/main}\n";
    Files.writeString(configFile, before);
    assertThatThrownBy(() -> setup.edit(billingEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("written inline");
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void removeDeletesOneBlockAndLeavesTheCommentOfTheNextOne() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            billing:
              path: /srv/billing
              baseBranch: origin/main
              labels: [BILL]

            # the web one
            web:
              path: /srv/web
              baseBranch: origin/main
          keepAwake: false   # stays
        """);
    String answer = setup.remove("billing");

    assertThat(answer).isEqualTo("Removed billing from jagt.yml");
    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:
              projects:

                # the web one
                web:
                  path: /srv/web
                  baseBranch: origin/main
              keepAwake: false   # stays
            """);
  }

  @Test
  void removeDeletesTheLastBlockOfTheFileWhereTheSectionEndsTheFile() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            web:
              path: /srv/web
              baseBranch: origin/main
            billing:
              path: /srv/billing
              baseBranch: origin/main
        """);
    setup.remove("billing");

    assertThat(Files.readString(configFile))
        .isEqualTo(
            """
            orchestrator:
              projects:
                web:
                  path: /srv/web
                  baseBranch: origin/main
            """);
  }

  @Test
  void removeRefusesTheOnlyProjectBecauseJagtDoesNotStartWithoutOne() throws Exception {
    String before =
        """
        orchestrator:
          projects:
            billing:
              path: /srv/billing
              baseBranch: origin/main
        """;
    Files.writeString(configFile, before);
    assertThatThrownBy(() -> setup.remove("billing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only project");
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void addRefusesAndLeavesTheFileUnchangedWhereItsFolderCannotBeWritten() throws Exception {
    String before =
        """
        orchestrator:
          projects:
            web:
              path: /srv/web
              baseBranch: origin/main
        """;
    Files.writeString(configFile, before);
    confFolder.toFile().setWritable(false);

    assertThatThrownBy(() -> setup.add(billingEntry))
        .isInstanceOf(java.io.UncheckedIOException.class)
        .hasMessageStartingWith("Cannot change ");
    assertThat(Files.readString(configFile)).isEqualTo(before);
  }

  @Test
  void entryServesOneProjectsManagedKeysAndRefusesKeyJagtYmlDoesNotHold() throws Exception {
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            billing:
              path: /srv/billing
              baseBranch: origin/main
              deployBranch: dev
              about: the invoicing backend
              labels: [bill, pay]
              humanPaths: ["**/auth/**"]
        """);
    assertThat(setup.entry("billing"))
        .isEqualTo(
            new ProjectSetup.Entry(
                "billing",
                "/srv/billing",
                "origin/main",
                "dev",
                null,
                "the invoicing backend",
                List.of("bill", "pay"),
                List.of("**/auth/**")));
    assertThatThrownBy(() -> setup.entry("web")).isInstanceOf(IllegalArgumentException.class);
  }
}

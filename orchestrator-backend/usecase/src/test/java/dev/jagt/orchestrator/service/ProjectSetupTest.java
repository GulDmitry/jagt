package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

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

class ProjectSetupTest {

    @TempDir
    Path root;

    @Test
    void addsTheEntryUnderProjectsAndKeepsEveryOtherLineAsItWas() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                # mine
                orchestrator:
                  projects:
                    web:
                      path: /srv/web
                      baseBranch: origin/main
                  keepAwake: false   # stays
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        String answer = setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", "dev",
                List.of("./gradlew", "test"), "the invoicing backend"));

        assertThat(answer).isEqualTo("Added billing to jagt.yml");

        assertThat(Files.readString(configFile)).isEqualTo("""
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
                """.formatted(repo));
    }

    @Test
    void refusesAPathThatIsNotAGitRepositoryAndWritesNothing() throws Exception {
        Path folder = Files.createDirectories(root.resolve("notes"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(128, "", "not a git repository"));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("notes", folder.toString(), "origin/main", null, null,
                null))).isInstanceOf(IllegalArgumentException.class).hasMessage("not a git repository");
        assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
    }

    @Test
    void refusesAKeyAnotherProjectAlreadyHolds() throws Exception {
        Path repo = Files.createDirectories(root.resolve("payments"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"),
                "orchestrator:\n  projects:\n    billing: {path: /srv/other, baseBranch: origin/main}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null,
                null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(configFile))
                .isEqualTo("orchestrator:\n  projects:\n    billing: {path: /srv/other, baseBranch: origin/main}\n");
    }

    @Test
    void addsUnderTheOrchestratorsProjectsAtTheIndentItsEntriesUseAndNotUnderTheTrackers() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:   # mine
                    tracker:
                        projects:
                            - ABC
                    projects:
                        web:
                            path: /srv/web
                            baseBranch: origin/main
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null, null));

        assertThat(Files.readString(configFile)).isEqualTo("""
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
                """.formatted(repo));
    }

    @Test
    void addsTheFirstProjectToAnEmptyProjectsMap() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  keepAwake: false\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null, null));

        assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects:\n    billing:\n      path: \"%s\"\n"
                .formatted(repo) + "      baseBranch: \"origin/main\"\n  keepAwake: false\n");
    }

    @Test
    void refusesAFolderInsideARepositoryAndNamesTheRepositoryToAddInstead() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path docs = Files.createDirectories(repo.resolve("docs"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(docs.toString());

        assertThat(reading.ready()).isFalse();
        assertThat(reading.lines()).extracting(ProjectSetup.Line::text).anyMatch(text -> text.contains(repo.toString()));
    }

    @Test
    void refusesADeployBranchEqualToTheBaseBranchAndLeavesTheFileUnchanged() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", "main",
                null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jagt never writes to the base branch");
        assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
    }

    @Test
    void readsAnOriginWithoutTheCredentialsItCarries() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "https://user:secret@host.test/g/p.git\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.lines()).extracting(ProjectSetup.Line::text)
                .contains("a git repository, origin https://host.test/g/p.git");
    }

    @Test
    void saysGitCouldNotReadARepositoryItRefusesForItsOwnership() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(128, "",
                "fatal: detected dubious ownership in repository at '/x'\nmore"));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.lines()).extracting(ProjectSetup.Line::text)
                .containsExactly("git could not read " + repo + ": fatal: detected dubious ownership in repository at '/x'");
    }

    @Test
    void putsTheFileBackWhenTheInsertedEntryDoesNotRead() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ConfigService config = spy(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)));
        doThrow(new IllegalStateException("bad yaml")).when(config).project("billing");
        ProjectSetup setup = new ProjectSetup(config, paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null,
                null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("jagt.yml did not read after the change, so it was put back: bad yaml");
        assertThat(Files.readString(configFile)).isEqualTo("orchestrator:\n  projects: {}\n");
    }

    @Test
    void namesTheFileWhenItDidNotReadAndCouldNotBePutBack() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ConfigService config = spy(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)));
        doAnswer(call -> {
            Files.delete(configFile);
            Files.createDirectories(configFile.resolve("child"));
            throw new IllegalStateException("bad yaml");
        }).when(config).project("billing");
        ProjectSetup setup = new ProjectSetup(config, paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null,
                null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("jagt.yml did not read after the change and could not be put back; fix it by hand:");
    }

    @Test
    void keepsAConfigFileThatIsASymlinkAndWritesTheEntryIntoItsTarget() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path target = Files.writeString(root.resolve("real.yml"), "orchestrator:\n  projects: {}\n");
        Path configFile = Files.createSymbolicLink(root.resolve("jagt.yml"), target);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        when(git.run(any(), any(), argThat(command -> command.contains("--show-toplevel"))))
                .thenReturn(new Processes.Result(0, repo + "\n", ""));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null, null));

        assertThat(Files.isSymbolicLink(configFile)).isTrue();
        assertThat(Files.readString(target)).contains("billing:");
    }

    @Test
    void readsNoDirectoryWhereThePathLeadsNowhereAndKeysItByItsLowerCasedName() {
        Path missing = root.resolve("Billing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths, mock(Processes.class));

        ProjectSetup.Reading reading = setup.read(missing.toString());

        assertThat(reading.ready()).isFalse();
        assertThat(reading.key()).isEqualTo("billing");
        assertThat(reading.lines()).extracting(ProjectSetup.Line::text).containsExactly("no directory at " + missing);
    }

    @Test
    void readsAFolderThatIsNoRepositoryAsSuch() throws Exception {
        Path folder = Files.createDirectories(root.resolve("notes"));
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(128, "", "fatal: not a git repository"));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(folder.toString());

        assertThat(reading.lines()).extracting(ProjectSetup.Line::text).containsExactly("not a git repository");
    }

    @ParameterizedTest
    @CsvSource(value = {"0,'',''", "2,'','error: No such remote origin'"})
    void readsARepositoryWithoutAnOriginAsNotReady(int exit, String stdout, String stderr) throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(exit, stdout, stderr)).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.lines()).extracting(ProjectSetup.Line::text).contains("no remote named origin");
    }

    @Test
    void saysGitCouldNotReadTheRemoteWhereItFailsForAnotherReason() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(1, "", "fatal: broken config\nmore")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.lines()).extracting(ProjectSetup.Line::text)
                .contains("git could not read the remote of " + repo + ": fatal: broken config");
    }

    @Test
    void readsWhatTheEntryWouldSayFromTheOriginsHeadTheDeployBranchAndTheBuildScript() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.writeString(repo.resolve("gradlew"), "");
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        doReturn(new Processes.Result(0, "origin/trunk\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("symbolic-ref")));
        doReturn(new Processes.Result(0, "abc\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("origin/dev")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.ready()).isTrue();
        assertThat(reading.baseBranch()).isEqualTo("origin/trunk");
        assertThat(reading.deployBranch()).isEqualTo("dev");
        assertThat(reading.verifyCommand()).containsExactly("./gradlew", "test");
        assertThat(reading.lines()).extracting(ProjectSetup.Line::text)
                .contains("tasks are cut from origin/trunk", "no project points here yet");
    }

    @Test
    void fallsBackToOriginMainWhereTheRemoteNamesNoHead() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.writeString(repo.resolve("package.json"), "{}");
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        doReturn(new Processes.Result(0, "abc\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("origin/main")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.baseBranch()).isEqualTo("origin/main");
        assertThat(reading.deployBranch()).isEmpty();
        assertThat(reading.verifyCommand()).containsExactly("npm", "test");
    }

    @Test
    void fallsBackToOriginMasterWhereThereIsNoOriginMain() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        doReturn(new Processes.Result(0, "abc\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("origin/master")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.baseBranch()).isEqualTo("origin/master");
        assertThat(reading.verifyCommand()).isEmpty();
    }

    @Test
    void readsARepositoryWithNoBranchToCutTasksFromAsNotReady() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.ready()).isFalse();
        assertThat(reading.baseBranch()).isEmpty();
        assertThat(reading.lines()).extracting(ProjectSetup.Line::text)
                .contains("no origin/main or origin/master to cut tasks from");
    }

    @Test
    void readsARepositoryAnotherProjectAlreadyPointsAtAsNotReady() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"),
                "orchestrator:\n  projects:\n    web: {path: \"" + repo + "\", baseBranch: origin/main}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        ProjectSetup.Reading reading = setup.read(repo.toString());

        assertThat(reading.ready()).isFalse();
        assertThat(reading.lines()).extracting(ProjectSetup.Line::text).contains("a project already points here");
    }

    @Test
    void refusesToAddWhereTheFileHoldsNoProjectsUnderOrchestrator() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  keepAwake: false\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null,
                null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jagt.yml holds no projects: under orchestrator: to add to");
    }

    @Test
    void addsAtTheIndentTheExistingEntriesUseWhenItIsNotTwiceTheMapsOwn() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(repo.resolve(".git"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects:
                     web:
                        path: /srv/web
                        baseBranch: origin/main
                """);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "origin/main\n", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null, null));

        assertThat(Files.readString(configFile)).startsWith("""
                orchestrator:
                  projects:
                     billing:
                        path: "%s"
                """.formatted(repo));
    }

    @Test
    void refusesARepositoryByTheFirstThingThatIsWrongWithItNotByWhatIsFine() throws Exception {
        Path repo = Files.createDirectories(root.resolve("billing"));
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withConfigFile(configFile.toString()));
        Processes git = mock(Processes.class);
        when(git.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
        doReturn(new Processes.Result(0, repo + "\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("--show-toplevel")));
        doReturn(new Processes.Result(0, "git@host.test:g/p.git\n", "")).when(git).run(any(), any(), argThat(command -> command.contains("get-url")));
        ProjectSetup setup = new ProjectSetup(new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)), paths, git);

        assertThatThrownBy(() -> setup.add(new ProjectSetup.Entry("billing", repo.toString(), "origin/main", null, null,
                null))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no origin/main or origin/master to cut tasks from");
    }
}

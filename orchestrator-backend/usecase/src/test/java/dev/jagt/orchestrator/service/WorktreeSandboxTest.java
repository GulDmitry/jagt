package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Sandbox;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.SandboxConfig;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorktreeSandboxTest {

    @Test
    void writesInEveryWorktreeOfTheTaskAndTheGitDirectoryEachCommitsTo() {
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/main", "dev", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/main",
                "git@host:g/api.git", false);

        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(true), List.of(web, api), Path.of("/home/me"), Path.of("/tmp"));

        assertThat(sandbox.writable()).contains(Path.of("/w/ABC-42"), Path.of("/repo/web/.git"),
                Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"));
    }

    @Test
    void writesInTheUsersTempDirectoryWhereAJvmOpensItsAttachSocket() {
        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(true), List.of(), Path.of("/home/me"),
                Path.of("/users-temp/T"));

        assertThat(sandbox.writable()).contains(Path.of("/users-temp/T"));
    }

    @Test
    void addsTheConfiguredPathsToTheBuiltInOnesWithTheHomeExpanded() {
        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(true).withAllowWrite(List.of("~/www/specs")),
                List.of(), Path.of("/home/me"), Path.of("/tmp"));

        assertThat(sandbox.writable()).contains(Path.of("/home/me/.gradle"), Path.of("/home/me/www/specs"));
    }

    @Test
    void allowsTheDirectoryASymlinkedPathResolvesTo(@TempDir Path root) throws Exception {
        Path target = Files.createDirectory(root.resolve("target"));
        Files.createSymbolicLink(root.resolve("link"), target);

        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(true).withAllowWrite(List.of(root + "/link")),
                List.of(), Path.of("/home/me"), Path.of("/tmp"));

        assertThat(sandbox.writable()).contains(target.toRealPath()).doesNotContain(root.resolve("link"));
    }

    @Test
    void runsTheConfiguredCommandsOutsideTheSandboxBesideTheSshRemotes() {
        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(true).withExcludedCommands(List.of("~/bin/db *")),
                List.of(), Path.of("/home/me"), Path.of("/tmp"));

        assertThat(sandbox.excludedCommands()).contains("git push *", "ssh *", "~/bin/db *");
    }

    @Test
    void confinesNothingWhenTheSandboxIsTurnedOff() {
        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults().withEnabled(false), List.of(),
                Path.of("/home/me"), Path.of("/tmp"));

        assertThat(sandbox).isEqualTo(Sandbox.off());
    }

    @Test
    void confinesNothingUnlessTheSandboxIsTurnedOn() {
        Sandbox sandbox = WorktreeSandbox.of(SandboxConfig.defaults(), List.of(), Path.of("/home/me"),
                Path.of("/tmp"));

        assertThat(sandbox).isEqualTo(Sandbox.off());
    }
}

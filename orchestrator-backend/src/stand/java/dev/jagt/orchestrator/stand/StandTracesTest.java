package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StandTracesTest {

    @TempDir
    Path directory;

    @Test
    void forgetsTheTrustOfStandWorktreesAndKeepsEveryOtherEntry() throws Exception {
        Path base = Files.createDirectories(directory.resolve("tmp")).toRealPath();
        Path config = directory.resolve(".claude.json");
        Files.writeString(config, """
                {"projects": {
                  "%1$s/jagt-stand-41-1/work/ABC-1": {"hasTrustDialogAccepted": true},
                  "%1$s/jagt-stand-41-2/work/ABC-1": {"hasTrustDialogAccepted": true},
                  "%1$s/jagt-stand-notes/work": {"hasTrustDialogAccepted": true},
                  "%1$s/other/jagt-stand-41-1/work": {"hasTrustDialogAccepted": true},
                  "/home/me/jagt-stand-41-1/work": {"hasTrustDialogAccepted": true},
                  "/home/me/project": {"hasTrustDialogAccepted": true}
                }, "theme": "dark"}
                """.formatted(base));
        StandTraces traces = new StandTraces(base, config, directory.resolve("projects"));

        StandTraces.Removed removed = traces.remove("jagt-stand-41-1"::equals);

        var kept = JsonMapper.builder().build().readTree(Files.readString(config));
        assertThat(removed.trust()).isEqualTo(1);
        assertThat(kept.path("projects").propertyNames()).containsExactlyInAnyOrder(
                base + "/jagt-stand-41-2/work/ABC-1", base + "/jagt-stand-notes/work", base + "/other/jagt-stand-41-1/work",
                "/home/me/jagt-stand-41-1/work", "/home/me/project");
        assertThat(kept.path("theme").asString()).isEqualTo("dark");
    }

    @Test
    void deletesTheTranscriptDirectoriesOfStandWorktreesAndKeepsEveryOtherDirectory() throws Exception {
        Path base = Files.createDirectories(directory.resolve("tmp")).toRealPath();
        String encoded = base.toString().replaceAll("[^A-Za-z0-9]", "-");
        Path projects = Files.createDirectories(directory.resolve("projects"));
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-41-1-work-ABC-1"));
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-41-1"));
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-41-10-work"));
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-notes-work"));
        Files.createDirectories(projects.resolve("-home-me-jagt-stand-41-1-work"));
        Files.createDirectories(projects.resolve("-home-me-project"));
        StandTraces traces = new StandTraces(base, directory.resolve(".claude.json"), projects);

        StandTraces.Removed removed = traces.remove("jagt-stand-41-1"::equals);

        assertThat(removed.transcripts()).isEqualTo(2);
        assertThat(projects.toFile().list()).containsExactlyInAnyOrder(encoded + "-jagt-stand-41-10-work",
                encoded + "-jagt-stand-notes-work", "-home-me-jagt-stand-41-1-work", "-home-me-project");
    }
}

package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandCleanupTest {

    @TempDir
    Path directory;

    @Test
    void copiesTheEvidenceOfADeadFailedRunUnderTheFailedDirectoryBeforeDeletingItsRoot() throws Exception {
        Path base = Files.createDirectories(directory.resolve("tmp")).toRealPath();
        Path root = Files.createDirectory(base.resolve("jagt-stand-41-1"));
        new StandMarker("happy-path/plain", 41, StandMarker.FAILED, 0, 0).writeTo(root);
        Files.writeString(root.resolve("timeline.log"), "t0");
        Files.writeString(root.resolve("scratch.txt"), "x");
        Path failed = directory.resolve("failed");
        StandTraces traces = new StandTraces(base, directory.resolve(".claude.json"), directory.resolve("projects"));

        StandCleanup.sweep(StandRoots.plan(base, pid -> false), failed, traces, pid -> false);

        Path kept = failed.resolve("happy-path-plain-jagt-stand-41-1");
        assertThat(root).doesNotExist();
        assertThat(kept.resolve("timeline.log")).hasContent("t0");
        assertThat(kept.resolve(StandMarker.FILE)).exists();
        assertThat(kept.resolve("scratch.txt")).doesNotExist();
    }

    @Test
    void forgetsTheTracesOfARootAlreadyGoneButNotOfOneWhoseProcessLives() throws Exception {
        Path base = Files.createDirectories(directory.resolve("tmp")).toRealPath();
        Path config = Files.writeString(directory.resolve(".claude.json"), """
                {"projects": {"%1$s/jagt-stand-41-1/work": {}, "%1$s/jagt-stand-42-1/work": {}}}
                """.formatted(base));
        Path projects = Files.createDirectories(directory.resolve("projects"));
        String encoded = base.toString().replaceAll("[^A-Za-z0-9]", "-");
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-41-1-work"));
        Files.createDirectories(projects.resolve(encoded + "-jagt-stand-42-1-work"));

        StandTraces.Removed removed = StandCleanup.sweep(StandRoots.plan(base, pid -> pid == 42), directory.resolve("failed"),
                new StandTraces(base, config, projects), pid -> pid == 42);

        assertThat(removed).isEqualTo(new StandTraces.Removed(1, 1));
        assertThat(config).content().doesNotContain("jagt-stand-41-1").contains("jagt-stand-42-1");
        assertThat(projects.resolve(encoded + "-jagt-stand-42-1-work")).exists();
    }
}

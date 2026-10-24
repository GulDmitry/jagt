package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandRootsTest {

    @TempDir
    Path base;

    @Test
    void removesAPassedRunWhoseProcessIsGone() throws Exception {
        Path root = Files.createDirectory(base.resolve("jagt-stand-41-1"));
        new StandMarker("happy-path", 41, StandMarker.PASSED, 0, 0).writeTo(root);

        StandRoots.Plan plan = StandRoots.plan(base, pid -> false);

        assertThat(plan.dead()).extracting(StandRoots.Found::root).containsExactly(root);
    }

    @Test
    void removesAFailedRunToo() throws Exception {
        Path root = Files.createDirectory(base.resolve("jagt-stand-41-1"));
        new StandMarker("happy-path", 41, StandMarker.FAILED, 77, 9000).writeTo(root);

        StandRoots.Plan plan = StandRoots.plan(base, pid -> false);

        assertThat(plan.dead()).extracting(StandRoots.Found::root).containsExactly(root);
    }

    @Test
    void leavesARunWhoseProcessIsStillAliveUntouched() throws Exception {
        Path root = Files.createDirectory(base.resolve("jagt-stand-41-1"));
        new StandMarker("happy-path", 41, StandMarker.RUNNING, 0, 0).writeTo(root);

        StandRoots.Plan plan = StandRoots.plan(base, pid -> pid == 41);

        assertThat(plan.dead()).isEmpty();
        assertThat(plan.dead()).isEmpty();
    }

    @Test
    void removesARootThatNeverWroteItsMarkerOnceItsNamedProcessIsGone() throws Exception {
        Path root = Files.createDirectory(base.resolve("jagt-stand-41-2"));

        StandRoots.Plan plan = StandRoots.plan(base, pid -> pid != 41);

        assertThat(plan.dead()).extracting(StandRoots.Found::root).containsExactly(root);
    }

    @Test
    void neverSelectsADirectoryThatIsNotAStandRoot() throws Exception {
        Files.createDirectory(base.resolve("jagt-stand-notes"));
        Files.createDirectory(base.resolve("jagt-tmux-1a2b"));

        StandRoots.Plan plan = StandRoots.plan(base, pid -> false);

        assertThat(plan.dead()).isEmpty();
    }
}

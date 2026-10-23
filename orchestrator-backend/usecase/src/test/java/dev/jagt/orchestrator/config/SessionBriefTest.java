package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBriefTest {

    @Test
    void readsTheHumansCopyOfAStepUpToTheNextStep(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "# Brief\n\n## deploy\n\ncheck it in a browser\n\n## verify\n\nrun it\n");
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section("deploy")).contains("check it in a browser");
    }

    @Test
    void readsTheShippedStepTheHumansCopyLacks(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## verify\n\nrun it\n");
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section("deploy")).contains("check the pipeline");
    }

    @Test
    void readsTheShippedBriefWhereNoneWasCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section("deploy")).contains("check the pipeline");
    }

    @Test
    void keepsAStepTheHumanEmptiedEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\n## verify\n\nrun it\n");
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section("deploy")).contains("");
    }

    @Test
    void keepsAHeadingThatNamesNoStepInsideItsStep(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## replies\n\nshape:\n## <thread link>\nFIXED\n\n## verify\n\nrun it\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section("replies")).contains("shape:\n## <thread link>\nFIXED");
    }

    @Test
    void readsAStepAsOneLineForACommandLine(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## start\n\nRead the notes,\n  then work.\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.line("start")).isEqualTo("Read the notes, then work.");
    }
}

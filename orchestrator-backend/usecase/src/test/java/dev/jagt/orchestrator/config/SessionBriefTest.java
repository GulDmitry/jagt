package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.Step;
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

        assertThat(brief.section(Step.DEPLOY)).contains("check it in a browser");
    }

    @Test
    void theShipStepRebasesOntoTheMovedTargetBeforeItPushes(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        String ship = brief.section(Step.SHIP).orElseThrow();

        assertThat(ship).contains("`git fetch`", "`git rebase`", "`--force-with-lease`");
        assertThat(ship.indexOf("`git rebase`")).isLessThan(ship.indexOf("Push the branch named above"));
        assertThat(ship.indexOf("Push the branch named above")).isLessThan(ship.indexOf("Open the request"));
    }

    @Test
    void readsTheShippedStepTheHumansCopyLacks(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## verify\n\nrun it\n");
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section(Step.DEPLOY)).contains("check the pipeline");
    }

    @Test
    void readsTheShippedBriefWhereNoneWasCopied(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section(Step.DEPLOY)).contains("check the pipeline");
    }

    @Test
    void keepsAStepTheHumanEmptiedEmpty(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## deploy\n\n## verify\n\nrun it\n");
        Files.writeString(root.resolve("session-brief.md.dist"), "## deploy\n\ncheck the pipeline\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section(Step.DEPLOY)).contains("");
    }

    @Test
    void keepsAHeadingThatNamesNoStepInsideItsStep(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## ship\n\nshape:\n## <thread link>\nFIXED\n\n## verify\n\nrun it\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.section(Step.SHIP)).contains("shape:\n## <thread link>\nFIXED");
    }

    @Test
    void readsAStepAsOneLineForACommandLine(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## start\n\nRead the notes,\n  then work.\n");
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.line(Step.START)).isEqualTo("Read the notes, then work.");
    }

    @Test
    void aCarriedTaskKeepsItsBoardStatusUntilAChangeBegins(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        SessionBrief brief = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(brief.line(Step.START))
                .contains("One carried from a previous session keeps the status the board shows")
                .contains("answer a question and end your turn without a report")
                .contains("report IN_PROGRESS when a change begins");
    }
}

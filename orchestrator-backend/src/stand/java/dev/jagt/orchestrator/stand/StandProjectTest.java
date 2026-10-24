package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.invariant.DeployBranchByDeploy;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.stand.invariant.TimelineFile;
import dev.jagt.orchestrator.stand.invariant.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandProjectTest {

    @TempDir
    Path root;

    @Test
    void aCommitTheStandWroteOnTheDeployBranchIsNotCountedAsAnotherWriter() throws Exception {
        StandProject project = StandProject.create(root);
        String sha = project.commitOnDeployBranch(root.resolve("outsider"), "greet.sh", "echo hi\n", "Greet");
        Files.writeString(root.resolve("timeline.log"), "2026-10-24T00:00:00Z stand " + TimelineFile.wroteLine(sha) + "\n");

        List<Violation> violations = new DeployBranchByDeploy().check(RunRecord.read(root));

        assertThat(violations).isEmpty();
    }

    @Test
    void aCommitNoDeployLandedOnTheDeployBranchIsNamedByItsReflogLine() {
        StandProject project = StandProject.create(root);
        String sha = project.commitOnDeployBranch(root.resolve("outsider"), "greet.sh", "echo hi\n", "Greet");

        List<Violation> violations = new DeployBranchByDeploy().check(RunRecord.read(root));

        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation.evidence()).contains(sha));
    }
}

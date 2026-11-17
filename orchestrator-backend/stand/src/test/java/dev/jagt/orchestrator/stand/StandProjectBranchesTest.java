package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.invariant.GitAncestry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandProjectBranchesTest {

  @TempDir Path root;

  @Test
  void theDeployBranchStartsAtTheCommitMainHolds() {
    StandProject project = StandProject.create(root);

    assertThat(project.head("dev")).isEqualTo(project.head("main")).isNotEmpty();
    assertThat(project.onDeployBranch("main")).isTrue();
    assertThat(project.deployBranchFile("greet.sh")).contains("Hello, $1");
  }

  @Test
  void branchThatDoesNotExistHasNoHead() {
    StandProject project = StandProject.create(root);

    assertThat(project.head("none")).isEmpty();
  }

  @Test
  void anOutsiderCommitMovesTheDeployBranchAheadAndIsFoundInItsHistory() {
    StandProject project = StandProject.create(root);
    String before = project.head("dev");

    String sha =
        project.commitOnDeployBranch(
            root.resolve("outsider"), "greet.sh", "echo outsider\n", "Out");
    String second =
        project.commitOnDeployBranch(root.resolve("outsider"), "greet.sh", "echo again\n", "Again");

    assertThat(project.head("dev")).isEqualTo(second).isNotEqualTo(before);
    assertThat(project.deployBranchEverHeld("outsider")).isTrue();
    assertThat(project.deployBranchEverHeld("never-written-text")).isFalse();
    assertThat(project.onDeployBranch("main")).isTrue();
    assertThat(sha).isNotEqualTo(second);
  }

  @Test
  void ancestryIsAskedOfTheGivenRepository() {
    StandProject project = StandProject.create(root);
    String head = project.head("main");
    GitAncestry ancestry = new GitAncestry(root.resolve("origin.git"));

    assertThat(ancestry.descends(head, head)).isTrue();
    assertThat(ancestry.descends(head, "0000000000000000000000000000000000000001")).isFalse();
  }
}

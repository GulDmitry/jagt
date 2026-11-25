package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskBranchesTest {

  @Test
  void namesTheDeployBranchAfterTheSourceBranch() {
    assertThat(TaskBranches.deploy("ABC-1")).isEqualTo("jagt-deploy-ABC-1");
  }

  @Test
  void namesTheRevertBranchAfterTheSourceBranch() {
    assertThat(TaskBranches.revert("ABC-1")).isEqualTo("jagt-revert-ABC-1");
  }
}

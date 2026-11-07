package dev.jagt.orchestrator.adapter.codehost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoCodeHostTest {

  private final NoCodeHost none = new NoCodeHost();
  private final HostSettings host =
      new HostSettings(
          "shop",
          "orchestrator.codeHost",
          Path.of("/repo"),
          new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
              null, null, List.of(), List.of(), List.of(), List.of(), List.of()));

  @Test
  void noPipelineIsReadAndTheDeploySettlesOnTheAddressNoHostHas() {
    Answer<CommitPipeline> answer =
        none.onCommit(host, "git@git.example.invalid:shop/web.git", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
  }

  @Test
  void noPipelineIsReadOnABranch() {
    Answer<CommitPipeline> answer =
        none.onBranchSince(host, "git@git.example.invalid:shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void nothingIsReRun() {
    assertThat(none.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/7").facts())
        .isEmpty();
  }

  @Test
  void noRequestIsClosed() {
    assertThat(
            none.closeRequest(host, "https://git.example.invalid/shop/web/-/merge_requests/7")
                .facts())
        .isEmpty();
  }

  @Test
  void noNoteIsAwaitingAnAnswer() {
    assertThat(
            none.unresolvedNotes(host, "https://git.example.invalid/shop/web/-/merge_requests/7")
                .facts())
        .contains(List.of());
  }
}

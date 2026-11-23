package dev.jagt.gradle.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProvenTreeTest {

  @Test
  void publishesWhenMergeTreeEqualsGreenHeadTree() {
    assertThat(ProvenTree.refusal("tree-a", "tree-a", List.of("success"))).isEmpty();
  }

  @Test
  void refusesWhenMergeTreeDiffersFromHeadTree() {
    assertThat(ProvenTree.refusal("tree-b", "tree-a", List.of("success")))
        .hasValueSatisfying(reason -> assertThat(reason).contains("tree-b", "tree-a"));
  }

  @Test
  void refusesWhenCommitHasNoSecondParent() {
    assertThat(ProvenTree.refusal("tree-a", "", List.of())).isPresent();
  }

  @Test
  void refusesWhenHeadHasNoRun() {
    assertThat(ProvenTree.refusal("tree-a", "tree-a", List.of())).isPresent();
  }

  @Test
  void refusesWhenNewestRunOfHeadIsRed() {
    assertThat(ProvenTree.refusal("tree-a", "tree-a", List.of("failure", "success"))).isPresent();
  }
}

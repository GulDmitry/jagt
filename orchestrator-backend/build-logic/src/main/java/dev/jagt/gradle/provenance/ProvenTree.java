package dev.jagt.gradle.provenance;

import java.util.List;
import java.util.Optional;

/** Whether a merge commit holds exactly the tree its request's green run proved. */
public final class ProvenTree {

  private ProvenTree() {}

  /**
   * @param mergeTree the tree of the merge commit
   * @param headTree the tree of the request's head, empty when the merge has no second parent
   * @param conclusions the conclusions of the head's request runs, newest first
   * @return why nothing may be published, empty when the tree is proven
   */
  public static Optional<String> refusal(
      String mergeTree, String headTree, List<String> conclusions) {
    if (headTree.isEmpty()) {
      return Optional.of("the commit is not a merge of a request head");
    }
    if (!mergeTree.equals(headTree)) {
      return Optional.of("the merge tree " + mergeTree + " differs from the proven " + headTree);
    }
    if (conclusions.isEmpty() || !"success".equals(conclusions.getFirst())) {
      return Optional.of("the request head has no green run");
    }
    return Optional.empty();
  }
}

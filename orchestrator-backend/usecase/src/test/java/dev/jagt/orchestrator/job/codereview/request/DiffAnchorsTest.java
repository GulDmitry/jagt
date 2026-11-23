package dev.jagt.orchestrator.job.codereview.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiffAnchorsTest {

  private static final String DIFF =
      """
      --- a/A.java
      +++ b/A.java
      @@ -1,3 +10,3 @@
       keep
      -old
      +int x = null;
      @@ -40,2 +50,2 @@
       tail
      +int z = 1;
      --- a/B.java
      +++ b/B.java
      @@ -1,1 +1,2 @@
       head
      +int x = null;
      """;

  @Test
  void lineIsTheNewSideNumberOfTheQuotedAddedLine() {
    assertThat(DiffAnchors.lineOf(DIFF, "A.java", "int x = null;")).isEqualTo(11);
  }

  @Test
  void lineInLaterHunkRestartsFromItsOwnStart() {
    assertThat(DiffAnchors.lineOf(DIFF, "A.java", "int z = 1;")).isEqualTo(51);
  }

  @Test
  void quoteIsLookedUpOnlyInTheFileTheFindingNames() {
    assertThat(DiffAnchors.lineOf(DIFF, "B.java", "int x = null;")).isEqualTo(2);
  }

  @Test
  void quoteTheDiffDoesNotHoldIsNoLine() {
    assertThat(DiffAnchors.lineOf(DIFF, "A.java", "never written")).isZero();
  }

  @Test
  void findingWithNoFileOrNoQuoteIsNoLine() {
    assertThat(DiffAnchors.lineOf(DIFF, "", "int x = null;")).isZero();
    assertThat(DiffAnchors.lineOf(DIFF, "A.java", " ")).isZero();
  }
}

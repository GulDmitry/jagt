package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecisionIndexTest {

  @Test
  void indexesEveryRecordedDecision() throws Exception {
    String index =
        Files.readString(Path.of("../docs/decisions/index-0001-0099.md"))
            + Files.readString(Path.of("../docs/decisions/index-0100-0199.md"))
            + Files.readString(Path.of("../docs/decisions/index-0200-0299.md"));
    try (Stream<Path> files = Files.list(Path.of("../docs/decisions"))) {
      assertThat(
              files
                  .map(file -> file.getFileName().toString())
                  .filter(name -> name.matches("\\d{4}-.*\\.md")))
          .isNotEmpty()
          .allSatisfy(record -> assertThat(index).contains("(" + record + ")"));
    }
  }

  @Test
  void anAmendedOrSupersededRecordNamesItsAmenderInItsStatus() throws Exception {
    List<Path> records = DecisionRecords.records();

    assertThat(records.stream().flatMap(record -> DecisionRecords.unnamedAmenders(record, records)))
        .isEmpty();
  }

  @Test
  void everyExceptionToNamedBadPracticeHoldsOwnerApprovalAndReviewerAgreement() throws Exception {
    List<Path> records = DecisionRecords.records();
    String fitnessYml = Files.readString(Path.of("../fitness.yml"));

    assertThat(DecisionRecords.exceptionsWithoutQuorum(records, fitnessYml)).isEmpty();
  }

  @Test
  void exceptionRowNamingDecisionWithoutBothAgreementsFails(@TempDir Path directory)
      throws Exception {
    Path owned = directory.resolve("0901-owner-only.md");
    Files.writeString(
        owned,
        """
        # 0901

        ## Status

        Exception to ABC.
        Owner approved 2026-11-24.

        ## Context
        """);
    Path reviewed = directory.resolve("0902-reviewer-only.md");
    Files.writeString(
        reviewed,
        """
        # 0902

        ## Status

        Exception to ABC.
        Reviewer agreed 2026-11-24, Someone.

        ## Context
        """);
    Path both = directory.resolve("0903-both.md");
    Files.writeString(
        both,
        """
        # 0903

        ## Status

        Exception to ABC.
        Owner approved 2026-11-24.
        Reviewer agreed 2026-11-24, Someone.

        ## Context
        """);
    String rows =
        "a: { exception: \"0901\" }\nb: { exception: \"0902\" }\n"
            + "c: { exception: \"0903\" }\nd: { exception: \"0904\" }";

    assertThat(DecisionRecords.exceptionsWithoutQuorum(List.of(owned, reviewed, both), rows))
        .containsExactlyInAnyOrder(
            "0904 is named by an exception row and is no decision",
            "0901 grants an exception without a reviewer's agreement",
            "0902 grants an exception without the owner's approval");
  }

  @Test
  void decisionStatingExceptionWithoutRowNamingItStillNeedsBothAgreements(@TempDir Path directory)
      throws Exception {
    Path record = directory.resolve("0905-unnamed.md");
    Files.writeString(record, "# 0905\n\n## Status\n\nException to ABC.\n\n## Context\n");

    assertThat(DecisionRecords.exceptionsWithoutQuorum(List.of(record), ""))
        .containsExactlyInAnyOrder(
            "0905 grants an exception without the owner's approval",
            "0905 grants an exception without a reviewer's agreement");
  }
}

package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.FixedBugCommits.Commit;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedBugCommitsTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

  @Test
  void commitWithoutDefectFamilyIsNotJudged() {
    Commit feature =
        new Commit(SHA, "A feature\n", List.of("orchestrator-backend/src/main/java/A.java"));

    assertThat(FixedBugCommits.defect(feature)).isEmpty();
  }

  @Test
  void defectFamilyCommitWithoutCaseTrailerFails() {
    Commit fix = new Commit(SHA, "A fix\n\nDefect-family: B\n", List.of("build.gradle"));

    assertThat(FixedBugCommits.defect(fix))
        .isEqualTo("012345678 carries Defect-family and no Case trailer");
  }

  @Test
  void caseTrailerWithoutScenarioNameFails() {
    Commit fix = new Commit(SHA, "A fix\n\nDefect-family: B\nCase: new\n", List.of("build.gradle"));

    assertThat(FixedBugCommits.defect(fix)).contains("no Case trailer");
  }

  @Test
  void mainCodeFixWithNewCaseAndNoOpenspecFileFails() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nCase: new a stopped task resumes\n",
            List.of("orchestrator-backend/src/main/java/A.java"));

    assertThat(FixedBugCommits.defect(fix))
        .isEqualTo("012345678 fixes main code with no openspec file and no Case: existing");
  }

  @Test
  void mainCodeFixWithOpenspecFilePasses() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nCase: new a stopped task resumes\n",
            List.of(
                "orchestrator-backend/src/main/java/A.java",
                "openspec/changes/abc/specs/board/spec.md"));

    assertThat(FixedBugCommits.defect(fix)).isEmpty();
  }

  @Test
  void mainCodeFixNamingAnExistingScenarioPasses() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nCase: existing a stopped task resumes\n",
            List.of("orchestrator-backend/src/main/java/A.java"));

    assertThat(FixedBugCommits.defect(fix)).isEmpty();
  }

  @Test
  void buildFixWithCaseNonePasses() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: L\nCase: none\n",
            List.of("orchestrator-backend/build.gradle"));

    assertThat(FixedBugCommits.defect(fix)).isEmpty();
  }

  @Test
  void logOutputIsSplitIntoCommitsWithTheirFiles() {
    String log =
        "\u001e"
            + SHA
            + "\u001fA fix\n\nCase: none\n\u001f\n\na.txt\nb/c.txt\n"
            + "\u001e"
            + SHA.replace('0', '9')
            + "\u001fSecond\n\u001f\n\nd.txt\n";

    List<Commit> commits = FixedBugCommits.parse(log);

    assertThat(commits)
        .extracting(Commit::files)
        .containsExactly(List.of("a.txt", "b/c.txt"), List.of("d.txt"));
  }

  @Test
  void defectFamilyCommitWithAllPostmortemTrailersPasses() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nWritten: abc1234\nUncaught: no rule test\nUnseen: no log\n",
            List.of("build.gradle"));

    assertThat(FixedBugCommits.postmortem(fix)).isEmpty();
  }

  @Test
  void defectFamilyCommitWithoutUncaughtTrailerNamesIt() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nWritten: abc1234\nUnseen: no log\n",
            List.of("build.gradle"));

    assertThat(FixedBugCommits.postmortem(fix))
        .isEqualTo("012345678 carries Defect-family and no Uncaught trailer");
  }

  @Test
  void defectFamilyCommitWithEmptyWrittenAndNoUnseenNamesBoth() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nWritten: \nUncaught: no rule test\n",
            List.of("build.gradle"));

    assertThat(FixedBugCommits.postmortem(fix))
        .contains("no Written trailer")
        .contains("no Unseen trailer");
  }

  @Test
  void commitWithoutDefectFamilyNeedsNoPostmortem() {
    Commit feature = new Commit(SHA, "A feature\n", List.of("build.gradle"));

    assertThat(FixedBugCommits.postmortem(feature)).isEmpty();
  }

  @Test
  void defectFamilyCommitWithKindHoldsAndPreventsPasses() {
    Commit fix =
        new Commit(
            SHA,
            "A fix\n\nDefect-family: B\nKind: core\nHolds: today\nPrevents: a rule test\n",
            List.of("build.gradle"));

    assertThat(FixedBugCommits.investigation(fix)).isEmpty();
  }

  @Test
  void defectFamilyCommitWithoutKindHoldsOrPreventsNamesEach() {
    Commit fix = new Commit(SHA, "A fix\n\nDefect-family: B\nHolds: \n", List.of("build.gradle"));

    assertThat(FixedBugCommits.investigation(fix))
        .contains("no Kind trailer")
        .contains("no Holds trailer")
        .contains("no Prevents trailer");
  }

  @Test
  void commitWithoutDefectFamilyNeedsNoInvestigation() {
    Commit feature = new Commit(SHA, "A feature\n", List.of("build.gradle"));

    assertThat(FixedBugCommits.investigation(feature)).isEmpty();
  }
}

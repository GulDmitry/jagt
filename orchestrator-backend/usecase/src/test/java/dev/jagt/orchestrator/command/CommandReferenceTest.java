package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandReferenceTest {

  private record Declared(
      String id, String hint, List<String> usage, boolean report, boolean aboutOneTask, int rank)
      implements GlobalCommand {
    @Override
    public String run(String tail) {
      return "ran";
    }
  }

  private static final GlobalCommand SPEND =
      new Declared(
          "spend",
          "what the calls cost",
          List.of("spend [since]", "  … today — only this session"),
          true,
          false,
          15);

  @Test
  void advertisesOnlyTheCurrentSpellingOfRenamedVerb() {
    assertThat(CommandReference.text(List.of()))
        .contains("sweep <ticket>")
        .doesNotContain("review <ticket>");
  }

  @Test
  void offersOnlyTheCurrentSpellingToThePalette() {
    assertThat(CommandReference.verbs(List.of()))
        .extracting(CommandReference.Verb::id)
        .contains("sweep")
        .doesNotContain("review");
  }

  @Test
  void leavesVerbOutWhileItHasNothingToDo() {
    GlobalCommand idle =
        new GlobalCommand() {
          @Override
          public String id() {
            return "idle";
          }

          @Override
          public String hint() {
            return "nothing to do";
          }

          @Override
          public int rank() {
            return 15;
          }

          @Override
          public boolean available() {
            return false;
          }

          @Override
          public String run(String tail) {
            return "ran";
          }
        };

    assertThat(CommandReference.verbs(List.of(idle)))
        .extracting(CommandReference.Verb::id)
        .doesNotContain("idle");
    assertThat(CommandReference.text(List.of(idle))).doesNotContain("nothing to do");
  }

  @Test
  void handsThePaletteTheRetiredSpellingSoMuscleMemoryStillRuns() {
    assertThat(CommandReference.verbs(List.of()))
        .filteredOn(verb -> verb.id().equals("sweep"))
        .singleElement()
        .extracting(verb -> verb.taskOnly().aliases())
        .isEqualTo(List.of("review"));
  }

  @Test
  void putsCommandDeclaredOnceIntoTheReferenceAndThePaletteBoth() {
    assertThat(CommandReference.text(List.of(SPEND)))
        .contains("spend [since]", "what the calls cost");
    assertThat(CommandReference.verbs(List.of(SPEND)))
        .filteredOn(verb -> verb.id().equals("spend"))
        .singleElement()
        .satisfies(verb -> {
          assertThat(verb.report()).isTrue();
          assertThat(verb.takesTask()).isFalse();
        });
  }

  @Test
  void sortsCommandAmongTheTaskVerbsByTheRankItDeclares() {
    assertThat(CommandReference.verbs(List.of(SPEND)))
        .extracting(CommandReference.Verb::id)
        .startsWith("sweep", "spend", "ship");
  }

  @Test
  void ordersTwoCommandsOfTheSameRankByTheirIdWhateverOrderTheyWereDeclaredIn() {
    GlobalCommand zeta = new Declared("zeta", "last by name", List.of("zeta"), false, false, 15);
    GlobalCommand alpha =
        new Declared("alpha", "first by name", List.of("alpha"), false, false, 15);

    assertThat(CommandReference.verbs(List.of(zeta, alpha)))
        .extracting(CommandReference.Verb::id)
        .containsSubsequence("alpha", "zeta");
  }

  @Test
  void handsNoPartToCommandThatBelongsToNone() {
    assertThat(CommandReference.verbs(List.of(SPEND)))
        .filteredOn(verb -> verb.id().equals("spend"))
        .singleElement()
        .extracting(CommandReference.Verb::part)
        .isNull();
  }

  @Test
  void printsModifierUnderTheVerbItBelongsTo() {
    List<String> lines = List.of(CommandReference.text(List.of(SPEND)).split("\n"));

    int verb =
        lines.indexOf(
            lines.stream().filter(l -> l.contains("spend [since]")).findFirst().orElseThrow());
    assertThat(lines.get(verb + 1)).contains("only this session");
  }

  @Test
  void marksReportAboutOneTaskSoNoSurfaceOffersItWithoutOne() {
    GlobalCommand drafts =
        new Declared("drafts", "what one task drafted", List.of("drafts <task>"), true, true, 200);

    assertThat(CommandReference.verbs(List.of(drafts)))
        .filteredOn(verb -> verb.id().equals("drafts"))
        .singleElement()
        .extracting(CommandReference.Verb::aboutOneTask)
        .isEqualTo(true);
  }

  @Test
  void alignsEveryHintInOneColumn() {
    List<String> lines = List.of(CommandReference.text(List.of(SPEND)).split("\n"));

    assertThat(lines).contains("  spend [since]                what the calls cost");
  }

  @Test
  void separatesUsageAsWideAsTheColumnFromItsHintByOneSpace() {
    GlobalCommand wide =
        new Declared(
            "wide", "the hint", List.of("abcdefghij abcdefghij abcdefg"), true, false, 200);

    List<String> lines = List.of(CommandReference.text(List.of(wide)).split("\n"));

    assertThat(lines).contains("  abcdefghij abcdefghij abcdefg the hint");
  }
}

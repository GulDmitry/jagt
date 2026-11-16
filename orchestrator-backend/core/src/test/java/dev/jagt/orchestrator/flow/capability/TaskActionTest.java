package dev.jagt.orchestrator.flow.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskActionTest {

  @Test
  void resolvesRetiredSpellingTypedInAnyCase() {
    assertThat(TaskAction.byRetiredVerb("REVIEW")).contains(TaskAction.SWEEP);
  }

  @Test
  void answersNothingForWireIdThatIsNoLongerVerb() {
    assertThat(TaskAction.byId("review")).isEmpty();
  }

  @Test
  void answersNothingForRetiredLookupOfCurrentVerb() {
    assertThat(TaskAction.byRetiredVerb("sweep")).isEmpty();
  }

  @Test
  void noVerbTakesKeyTheBoardKeepsForItself() {
    List<String> keys = Arrays.stream(TaskAction.values()).map(TaskAction::key).toList();

    assertThat(keys).doesNotContainAnyElementsOf(List.of("t", "j", "k", "n", "/"));
  }

  @ParameterizedTest
  @CsvSource({
    "FOCUS,true",
    "CODE,true",
    "DIFF,true",
    "SHIP,false",
    "DEPLOY,false",
    "RESPAWN,false"
  })
  void callsOnlyTheVerbsThatLookAtTaskReadOnly(TaskAction action, boolean readOnly) {
    assertThat(action.readOnly()).isEqualTo(readOnly);
  }

  @ParameterizedTest
  @CsvSource({
    "ADD_REPO,true,false",
    "REMOVE_REPO,true,true",
    "SHIP,false,false",
    "DONE,false,false"
  })
  void letsHumanTypeProjectForTheRepositoryVerbsAndGivesRemovalNoButton(
      TaskAction action, boolean takesProject, boolean typed) {
    assertThat(action.takesProject()).isEqualTo(takesProject);
    assertThat(action.typed()).isEqualTo(typed);
  }

  @ParameterizedTest
  @CsvSource({"RUN,true", "ADD_REPO,false", "SHIP,false"})
  void letsHumanTypeLineOnlyForRun(TaskAction action, boolean takesLine) {
    assertThat(action.takesLine()).isEqualTo(takesLine);
  }

  @Test
  void sortsTheMostUsedVerbsLowest() {
    assertThat(TaskAction.SWEEP.rank()).isLessThan(TaskAction.SHIP.rank());
    assertThat(TaskAction.SHIP.rank()).isLessThan(TaskAction.DEPLOY.rank());
    assertThat(TaskAction.DEPLOY.rank()).isLessThan(TaskAction.DONE.rank());
  }

  @Test
  void writesWhatHumanTypesAsTheVerbAndItsArgument() {
    assertThat(TaskAction.SHIP.usage()).isEqualTo("ship <ticket>");
    assertThat(TaskAction.CODE.usage()).isEqualTo("code <ticket> [diff]");
  }

  @Test
  void describesVerbInTheWordsEverySurfaceRenders() {
    assertThat(TaskAction.DEPLOY.hint())
        .isEqualTo("merge the task branch into the deploy branch and push");
  }

  @Test
  void resolvesWireIdToItsVerbAndNothingElse() {
    assertThat(TaskAction.byId("deploy")).contains(TaskAction.DEPLOY);
    assertThat(TaskAction.byId("Deploy")).isEmpty();
  }

  @Test
  void listsWhatVerbWasRenamedFromAndNothingForOneThatWasNot() {
    assertThat(TaskAction.SWEEP.retiredVerbs()).containsExactly("review");
    assertThat(TaskAction.SHIP.retiredVerbs()).isEmpty();
  }

  @Test
  void asksOnlyBeforePressThatCannotBeTakenBack() {
    assertThat(TaskAction.DEPLOY.confirmation("ABC-1", List.of("php → main")))
        .contains("Deploy ABC-1?\n\nThis merges and pushes:\nphp → main");
    assertThat(TaskAction.SHIP.confirmation("ABC-1", List.of("php → main"))).isEmpty();
  }
}

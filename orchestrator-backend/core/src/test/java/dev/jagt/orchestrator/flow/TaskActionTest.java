package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskActionTest {

  @Test
  void resolvesARetiredSpellingTypedInAnyCase() {
    assertThat(TaskAction.byRetiredVerb("REVIEW")).contains(TaskAction.SWEEP);
  }

  @Test
  void answersNothingForAWireIdThatIsNoLongerAVerb() {
    assertThat(TaskAction.byId("review")).isEmpty();
  }

  @Test
  void answersNothingForARetiredLookupOfACurrentVerb() {
    assertThat(TaskAction.byRetiredVerb("sweep")).isEmpty();
  }

  @Test
  void noVerbTakesAKeyTheBoardKeepsForItself() {
    List<String> keys = Arrays.stream(TaskAction.values()).map(TaskAction::key).toList();

    assertThat(keys).doesNotContainAnyElementsOf(List.of("t", "j", "k", "n", "/"));
  }

  @ParameterizedTest
  @CsvSource({"FOCUS,true", "IDE,true", "DIFF,true", "SHIP,false", "DEPLOY,false", "RESPAWN,false"})
  void callsOnlyTheVerbsThatLookAtATaskReadOnly(TaskAction action, boolean readOnly) {
    assertThat(action.readOnly()).isEqualTo(readOnly);
  }

  @ParameterizedTest
  @CsvSource({
    "ADD_REPO,true,false",
    "REMOVE_REPO,true,true",
    "SHIP,false,false",
    "DONE,false,false"
  })
  void letsAHumanTypeAProjectForTheRepositoryVerbsAndGivesRemovalNoButton(
      TaskAction action, boolean takesProject, boolean typed) {
    assertThat(action.takesProject()).isEqualTo(takesProject);
    assertThat(action.typed()).isEqualTo(typed);
  }

  @Test
  void sortsTheMostUsedVerbsLowest() {
    assertThat(TaskAction.SWEEP.rank()).isLessThan(TaskAction.SHIP.rank());
    assertThat(TaskAction.SHIP.rank()).isLessThan(TaskAction.DEPLOY.rank());
    assertThat(TaskAction.DEPLOY.rank()).isLessThan(TaskAction.DONE.rank());
  }

  @Test
  void writesWhatAHumanTypesAsTheVerbAndItsArgument() {
    assertThat(TaskAction.SHIP.usage()).isEqualTo("ship <ticket>");
    assertThat(TaskAction.IDE.usage()).isEqualTo("ide <ticket> [diff]");
  }

  @Test
  void describesAVerbInTheWordsEverySurfaceRenders() {
    assertThat(TaskAction.DEPLOY.hint())
        .isEqualTo("merge the task branch into the deploy branch and push");
  }

  @Test
  void resolvesAWireIdToItsVerbAndNothingElse() {
    assertThat(TaskAction.byId("deploy")).contains(TaskAction.DEPLOY);
    assertThat(TaskAction.byId("Deploy")).isEmpty();
  }

  @Test
  void listsWhatAVerbWasRenamedFromAndNothingForOneThatWasNot() {
    assertThat(TaskAction.SWEEP.retiredVerbs()).containsExactly("review");
    assertThat(TaskAction.SHIP.retiredVerbs()).isEmpty();
  }

  @Test
  void asksOnlyBeforeAPressThatCannotBeTakenBack() {
    assertThat(TaskAction.DEPLOY.confirmation("ABC-1", List.of("php → main")))
        .contains("Deploy ABC-1?\n\nThis merges and pushes:\nphp → main");
    assertThat(TaskAction.SHIP.confirmation("ABC-1", List.of("php → main"))).isEmpty();
  }
}

package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeReach;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupValidationTest {

  @Test
  void startsWhenEveryPartAnswersThatItIsThere() {
    assertThatCode(
            new StartupValidation(List.of(List::of, List::of), new FakeReach(), true)
                ::refuseToRunWithoutWhatItNeeds)
        .doesNotThrowAnyException();
  }

  @Test
  void reportsEveryMissingPartAtOnceSoTheHumanFixesTheListOnce() {
    StartupValidation validation =
        new StartupValidation(
            List.of(
                () -> List.of(new Problem("orchestrator.tmux.command", "tmux is not on PATH")),
                () ->
                    List.of(
                        new Problem("orchestrator.projects", "config.json defines no projects"),
                        new Problem("orchestrator.kitty-command", "kitty is not on PATH"))),
            new FakeReach(),
            true);

    assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds)
        .isInstanceOf(Misconfigured.class)
        .hasMessageContaining("3 problems:")
        .hasMessageContaining("1. tmux is not on PATH")
        .hasMessageContaining("2. config.json defines no projects")
        .hasMessageContaining("3. kitty is not on PATH");
  }

  @Test
  void treatsCheckThatCannotAnswerAsPartThatIsNotThere() {
    StartupValidation validation =
        new StartupValidation(
            List.of(() -> {
              throw new IllegalStateException("no such directory");
            }),
            new FakeReach(),
            true);

    assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds)
        .isInstanceOf(Misconfigured.class)
        .hasMessageContaining("could not run")
        .hasMessageContaining("no such directory");
  }

  @Test
  void skipsWhatItAsksOfTheMachineWhenTheChecksAreOff() {
    StartupValidation validation =
        new StartupValidation(
            List.of(() -> List.of(new Problem("orchestrator.tmux.command", "tmux is not on PATH"))),
            new FakeReach(),
            false);

    assertThatCode(validation::refuseToRunWithoutWhatItNeeds).doesNotThrowAnyException();
  }

  @Test
  void refusesAnEmptyCaseBookEvenWithTheChecksOff() {
    StartupValidation validation =
        new StartupValidation(
            List.of(
                new StartupCheck() {
                  @Override
                  public List<Problem> problems() {
                    return List.of(
                        new Problem("roles/session/cases.md", "no case sends step ship"));
                  }

                  @Override
                  public boolean machine() {
                    return false;
                  }
                }),
            new FakeReach(),
            false);

    assertThatThrownBy(validation::refuseToRunWithoutWhatItNeeds).isInstanceOf(Misconfigured.class);
  }

  @Test
  void marksTheBoardsHeadWithWhatTheStartGoesOnWithout() {
    ReachLost lost = new ReachLost("notification", "team", List.of(), "team tells nothing");
    StartupCheck check = mock(StartupCheck.class);
    when(check.marks()).thenReturn(List.of(lost));
    ReachLedger reach = new ReachLedger();

    new StartupValidation(List.of(check), reach, true).refuseToRunWithoutWhatItNeeds();

    assertThat(reach.standing()).containsExactly(lost);
  }

  @Test
  void endsTheMarkOnTheReadThatNoLongerListsIt() {
    ReachLost lost = new ReachLost("notification", "team", List.of(), "team tells nothing");
    StartupCheck check = mock(StartupCheck.class);
    when(check.marks()).thenReturn(List.of(lost)).thenReturn(List.of());
    ReachLedger reach = new ReachLedger();

    new StartupValidation(List.of(check), reach, true).refuseToRunWithoutWhatItNeeds();

    assertThat(reach.standing()).isEmpty();
  }
}

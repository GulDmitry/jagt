package dev.jagt.orchestrator.command.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.command.DoCommand;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.command.ResumeCommand;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.stop.ActStops;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MasterPressesTest {

  private static final class Spend implements GlobalCommand {
    private final AtomicReference<ActionOrigin> seen;

    Spend(AtomicReference<ActionOrigin> seen) {
      this.seen = seen;
    }

    @Override
    public String id() {
      return "spend";
    }

    @Override
    public String hint() {
      return "what the calls cost";
    }

    @Override
    public int rank() {
      return 15;
    }

    @Override
    public String run(String tail) {
      seen.set(OriginContext.current());
      return "spent " + tail;
    }
  }

  private final CommandService commands = mock(CommandService.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void pressesVerbOnTaskAsTheMasterAndAnswersWhatTheDoorSaid() {
    AtomicReference<ActionOrigin> seen = new AtomicReference<>();
    when(commands.execute("ABC-42", TaskAction.REMOVE_REPO, "api"))
        .thenAnswer(call -> {
          seen.set(OriginContext.current());
          return "removed api";
        });
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    String answered = presses.press("ABC-42", "remove-repo", "api", null);

    assertThat(answered).isEqualTo("removed api");
    assertThat(seen.get()).isEqualTo(ActionOrigin.MASTER);
  }

  @Test
  void pressesRunLineAsItsOwnArgumentNeverAsProject() {
    when(commands.executeLine("ABC-42", TaskAction.RUN, "ls -la")).thenReturn("ran");
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThat(presses.press("ABC-42", "run", null, "ls -la")).isEqualTo("ran");
  }

  @Test
  void refusesPressNamingBothProjectAndLine() {
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThatThrownBy(() -> presses.press("ABC-42", "run", "api", "ls -la"))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
  }

  @Test
  void refusesVerbNoTaskActionNames() {
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThatThrownBy(() -> presses.press("ABC-42", "explode", null, null))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
  }

  @Test
  void runsCommandAsTheMaster() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    AtomicReference<ActionOrigin> seen = new AtomicReference<>();
    MasterPresses presses =
        new MasterPresses(commands, new GlobalCommands(List.of(new Spend(seen))), config);

    String answered = presses.run("spend", "today");

    assertThat(answered).isEqualTo("spent today");
    assertThat(seen.get()).isEqualTo(ActionOrigin.MASTER);
  }

  @Test
  void refusesCommandNoneDeclares() {
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThatThrownBy(() -> presses.run("spend", ""))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
  }

  @Test
  void refusesCommandItsHumanKeepsInMine() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("spend"), null)));
    MasterPresses presses =
        new MasterPresses(
            commands, new GlobalCommands(List.of(new Spend(new AtomicReference<>()))), config);

    assertThatThrownBy(() -> presses.run("spend", ""))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void neverOpensTaskForTheMasterWithDo() {
    MasterPresses presses =
        new MasterPresses(
            commands, new GlobalCommands(List.of(new DoCommand(mock(TaskLauncher.class)))), config);

    assertThatThrownBy(() -> presses.run("do", "ABC-42"))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void neverOpensTaskForTheMasterWithResume() {
    MasterPresses presses =
        new MasterPresses(
            commands,
            new GlobalCommands(List.of(new ResumeCommand(mock(TaskLauncher.class)))),
            config);

    assertThatThrownBy(() -> presses.run("resume", "https://host/x/-/merge_requests/1"))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void neverLetsTheMasterLiftItsOwnActBrake() {
    ActStops actStops = mock(ActStops.class);
    MasterPresses presses =
        new MasterPresses(commands, new GlobalCommands(List.of(new ActCommand(actStops))), config);

    assertThatThrownBy(() -> presses.run("act", ""))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
    verifyNoInteractions(actStops);
  }
}

package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.ActionOrigin;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MasterPressesTest {

  private record Spend() implements GlobalCommand {
    static final AtomicReference<ActionOrigin> SEEN = new AtomicReference<>();

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
      SEEN.set(OriginContext.current());
      return "spent " + tail;
    }
  }

  private final CommandService commands = mock(CommandService.class);
  private final ConfigService config = mock(ConfigService.class);

  @Test
  void pressesAVerbOnATaskAsTheMasterAndAnswersWhatTheDoorSaid() {
    AtomicReference<ActionOrigin> seen = new AtomicReference<>();
    when(commands.execute("ABC-42", TaskAction.REMOVE_REPO, "api"))
        .thenAnswer(
            call -> {
              seen.set(OriginContext.current());
              return "removed api";
            });
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    String answered = presses.press("ABC-42", "remove-repo", "api");

    assertThat(answered).isEqualTo("removed api");
    assertThat(seen.get()).isEqualTo(ActionOrigin.MASTER);
  }

  @Test
  void refusesAVerbNoTaskActionNames() {
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThatThrownBy(() -> presses.press("ABC-42", "explode", null))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
  }

  @Test
  void runsACommandAsTheMaster() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    MasterPresses presses =
        new MasterPresses(commands, new GlobalCommands(List.of(new Spend())), config);

    String answered = presses.run("spend", "today");

    assertThat(answered).isEqualTo("spent today");
    assertThat(Spend.SEEN.get()).isEqualTo(ActionOrigin.MASTER);
  }

  @Test
  void refusesACommandNoneDeclares() {
    MasterPresses presses = new MasterPresses(commands, new GlobalCommands(List.of()), config);

    assertThatThrownBy(() -> presses.run("spend", ""))
        .isInstanceOfSatisfying(
            Refusal.class, refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
  }

  @Test
  void refusesACommandItsHumanKeepsInMine() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("spend"), null)));
    MasterPresses presses =
        new MasterPresses(commands, new GlobalCommands(List.of(new Spend())), config);

    assertThatThrownBy(() -> presses.run("spend", ""))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void neverOpensATaskForTheMasterWithDo() {
    MasterPresses presses =
        new MasterPresses(
            commands,
            new GlobalCommands(List.of(new DoCommand(mock(TaskLauncher.class)))),
            config);

    assertThatThrownBy(() -> presses.run("do", "ABC-42"))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void neverOpensATaskForTheMasterWithResume() {
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

package dev.jagt.orchestrator.job.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MasterShipTest {

  private final ShipDue due = mock(ShipDue.class);
  private final MasterPress press = mock(MasterPress.class);
  private final ShipRecord record = mock(ShipRecord.class);
  private final ShipContent content = mock(ShipContent.class);

  @Test
  void shipsTaskThatHoldsWork() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(due.due("ABC-1", task)).thenReturn(true);
    when(press.press("ABC-1", task, TaskAction.SHIP)).thenReturn(true);

    boolean shipped = new MasterShip(due, press, record, content).ship("ABC-1", task);

    assertThat(shipped).isTrue();
  }

  @Test
  void leavesTaskNoShipIsDueOnForTheHuman() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

    boolean shipped = new MasterShip(due, press, record, content).ship("ABC-1", task);

    assertThat(shipped).isFalse();
    verify(press, never()).press(anyString(), any(), any());
  }

  @Test
  void pressesDeployWithoutTheShipChecks() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(press.press("ABC-1", task, TaskAction.DEPLOY)).thenReturn(true);

    boolean pressed =
        new MasterShip(due, press, record, content).press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isTrue();
    verify(due, never()).due(anyString(), any());
  }

  @Test
  void leavesDeployTheMasterWasRefusedUnpressed() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(press.press("ABC-1", task, TaskAction.DEPLOY)).thenReturn(false);

    boolean pressed =
        new MasterShip(due, press, record, content).press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isFalse();
  }

  @Test
  void standsOnTheContentTheShipWouldDeliver() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(content.of(task)).thenReturn(Optional.of("tree"));

    Optional<String> standing = new MasterShip(due, press, record, content).standing(task);

    assertThat(standing).contains("tree");
  }

  @Test
  void pressRecordsTheTreeItShipped() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(due.due("ABC-1", task)).thenReturn(true);
    when(press.press("ABC-1", task, TaskAction.SHIP)).thenReturn(true);

    new MasterShip(due, press, record, content).ship("ABC-1", task);

    verify(record).pressedOn("ABC-1", task);
  }
}

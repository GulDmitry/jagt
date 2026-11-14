package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.view.Attention;
import dev.jagt.orchestrator.flow.view.Owner;
import dev.jagt.orchestrator.flow.view.Phase;
import dev.jagt.orchestrator.notify.Routes.Routed;
import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.service.BoardUrl;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.RoleRecord;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessagesTest {

  private static final ChannelAddress DESKTOP =
      new ChannelAddress("desktop", "desktop", "", List.of());

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{task}|ABC-42",
        "{alias}|p3",
        "{ticket}|https://tracker.example/ABC-42",
        "{title}|agent stopped",
        "{body}|silent for 6 min",
        "{status}|in_progress",
        "{said}|second line",
        "{next}|read the plan",
        "{project}|api",
        "{requests}|https://host.example/mr/1",
        "{request}|https://host.example/mr/1",
        "{pipeline}|passed",
        "{sha}|abc123",
        "{board}|http://localhost:8290/?task=ABC-42",
        "{role}|acceptance",
        "{stop}|rounds bring nothing new",
        "{origin}|master"
      })
  void fillsEveryPlaceholderFromTheTaskAndThePoint(String placeholder, String expected) {
    NextMove next = mock(NextMove.class);
    when(next.of(any()))
        .thenReturn(
            new Move(
                Phase.BUILD, Owner.YOU, Attention.REQUIRED, "read the plan", List.of(), null, "h"));
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of("api", "/wt/ABC-42-api")), TaskStatus.IN_PROGRESS)
            .alias("p3")
            .ticketUrl("https://tracker.example/ABC-42")
            .message("first line\nsecond line\n \n")
            .mrUrl("https://host.example/mr/1")
            .pipelineStatus("passed")
            .checksTip("abc123")
            .roles(Map.of("acceptance", RoleRecord.defaults()))
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 1L, "evidence"))
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD),
                    new StatusChange(TaskStatus.IN_PROGRESS, 2L, ActionOrigin.MASTER)))
            .build();
    Notification sent =
        Notification.watchdog("acceptance-passed", "ABC-42", "agent stopped", "silent for 6 min")
            .withAlias("p3");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), next)
            .of(new Routed(DESKTOP, "", placeholder), sent, Optional.of(task));

    assertThat(message.text()).isEqualTo(expected);
  }

  @Test
  void dropsTheTitleSegmentsLeftEmptyWhenThePointHasNoTask() {
    Notification install = Notification.install("install", "restart needed", "jar rebuilt");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(
                new Routed(DESKTOP, "jagt · {alias} · {task} · {title}", "{body}"),
                install,
                Optional.empty());

    assertThat(message.title()).isEqualTo("jagt · restart needed");
  }

  @Test
  void keepsTheTaskSegmentsWhenThePointHasOneTask() {
    Notification stopped =
        Notification.watchdog("stopped", "ABC-42", "agent stopped", "silent").withAlias("p3");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(
                new Routed(DESKTOP, "jagt · {alias} · {task} · {title}", "{body}"),
                stopped,
                Optional.empty());

    assertThat(message.title()).isEqualTo("jagt · p3 · ABC-42 · agent stopped");
  }

  @Test
  void carriesThePipelineLinkWhenTheReadListedOne() {
    TaskState task =
        TaskState.builder("api", "/wt/ABC-42-api", TaskStatus.DEPLOYED)
            .pipelineStatus("passed")
            .pipelineUrl("https://host/pipelines/9")
            .build();
    Notification sent = Notification.fromAgent("deployed", "ABC-42", "deployed", "");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "", "{pipeline}"), sent, Optional.of(task));

    assertThat(message.text()).isEqualTo("https://host/pipelines/9");
  }

  @Test
  void namesNoRoleWhenNoRoleOfTheTaskOwnsThePoint() {
    TaskState task =
        TaskState.builder("api", "/wt/ABC-42-api", TaskStatus.IN_PROGRESS)
            .roles(Map.of("reviewer", RoleRecord.defaults()))
            .build();
    Notification sent = Notification.watchdog("acceptance-passed", "ABC-42", "t", "b");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "", "[{role}]"), sent, Optional.of(task));

    assertThat(message.text()).isEqualTo("[]");
  }

  @Test
  void leavesTaskPlaceholdersEmptyWhenTheTaskHasNoMessageOrHistory() {
    TaskState task =
        TaskState.builder("api", "/wt/ABC-42-api", TaskStatus.IN_PROGRESS)
            .history(List.of())
            .build();
    Notification sent = Notification.watchdog("stopped", "ABC-42", "t", "b");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "", "[{said}][{origin}][{stop}]"), sent, Optional.of(task));

    assertThat(message.text()).isEqualTo("[][][]");
  }

  @Test
  void leavesAnUnknownPlaceholderAsWritten() {
    Notification install = Notification.install("install", "restart needed", "jar rebuilt");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "", "{nope} {body}"), install, Optional.empty());

    assertThat(message.text()).isEqualTo("{nope} jar rebuilt");
  }

  @Test
  void namesTheTopicWhenTheNotificationHasNoTitle() {
    Notification install = Notification.install("install", " ", "jar rebuilt");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "jagt · {title}", ""), install, Optional.empty());

    assertThat(message.title()).isEqualTo("jagt · install");
  }

  @Test
  void carriesThePointTheTaskAndTheAliasToTheChannel() {
    Notification stopped =
        Notification.watchdog("stopped", "ABC-42", "agent stopped", "silent").withAlias("p3");

    ChannelMessage message =
        new Messages(new BoardUrl("8290"), mock(NextMove.class))
            .of(new Routed(DESKTOP, "t", "x"), stopped, Optional.empty());

    assertThat(message).isEqualTo(new ChannelMessage("stopped", "ABC-42", "p3", "t", "x"));
  }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskLaunchesTest {

    private final TaskProvisioning provisioning = mock(TaskProvisioning.class);
    private final NewTaskWorktrees worktrees = mock(NewTaskWorktrees.class);
    private final TicketPlacement placement = mock(TicketPlacement.class);
    private final TaskLaunches launches = new TaskLaunches(provisioning, worktrees, placement);

    @Test
    void namesTheTaskByTheCanonicalKeyTheReadGaveBackWhenGivenAUrl() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-123").withTitle("Some title")
                .withUrl("https://tracker.example.com/browse/ABC-123");
        when(placement.read("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("https://tracker.example.com/browse/ABC-123"), any(), eq(List.of("group-a"))))
                .thenReturn(new TicketPlacement.Placed(item, List.of("group-a")));

        launches.ticket(LaunchRequest.of("https://tracker.example.com/browse/ABC-123").withProject("group-a"),
                List.of("group-a"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::projectKey, NewTask::title, NewTask::ticketUrl)
                .containsExactly("ABC-123", "group-a", "Some title",
                        "https://tracker.example.com/browse/ABC-123");
    }

    @Test
    void chargesTheTicketReadOnlyOnceTheTaskItNamedExists() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-123").withTitle("t")
                .withUrl("https://tracker/ABC-123");
        when(placement.read("https://tracker/ABC-123")).thenReturn(new Answer<>(Optional.of(item), spent));
        when(placement.place(eq("https://tracker/ABC-123"), any(), any()))
                .thenReturn(new TicketPlacement.Placed(item, List.of("group-a")));

        launches.ticket(LaunchRequest.of("https://tracker/ABC-123").withProject("group-a"), List.of("group-a"));

        var order = inOrder(provisioning, placement);
        order.verify(provisioning).initializeTask(any());
        order.verify(placement).created("ABC-123", List.of("group-a"), spent);
    }

    @Test
    void createsNoTaskForAnItemThePlacementRefused() {
        when(placement.read("ABC-42")).thenReturn(Answer.unavailable());
        when(placement.place(eq("ABC-42"), any(), any()))
                .thenReturn(new TicketPlacement.Refused("error: read failed: ABC-42"));

        String out = launches.ticket(LaunchRequest.of("ABC-42"), null).message();

        assertThat(out).isEqualTo("error: read failed: ABC-42");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void buysNoSecondReadOfFactsTheCallerAlreadyPaidFor() {
        when(worktrees.strategyForExisting("ABC-42", "group-a")).thenReturn(BranchStrategy.FRESH);
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42");
        when(placement.place(eq("ABC-42"), any(), any()))
                .thenReturn(new TicketPlacement.Placed(item, List.of("group-a")));

        launches.ticket(LaunchRequest.of("ABC-42").withProject("group-a"), List.of("group-a"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));

        verify(placement, never()).read(anyString());
    }

    @Test
    void warnsAboutALeftoverBranchWithoutSpendingATicketRead() {
        when(worktrees.existingBranchProject(eq("ABC-9"), any())).thenReturn("group-a");

        String out = launches.ticket(LaunchRequest.of("ABC-9"), null).message();

        assertThat(out).contains("already exists in group-a", "recreate", "resume");
        verifyNoInteractions(placement);
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void intakeContinuesALeftoverBranchHoldingWorkInsteadOfRefusingIt() {
        when(worktrees.strategyForExisting("ABC-9", "group-a")).thenReturn(BranchStrategy.RESUME);
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-9").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-9");
        when(placement.place(eq("ABC-9"), any(), any())).thenReturn(new TicketPlacement.Placed(item, List.of("group-a")));

        launches.ticket(LaunchRequest.of("ABC-9").withProject("group-a"), List.of("group-a"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("resume");
    }

    @Test
    void warnsAboutALeftoverBranchWhenTheHumanAskedForAFreshOne() {
        when(worktrees.existingBranchProject(eq("ABC-9"), any())).thenReturn("group-a");

        String out = launches.ticket(LaunchRequest.of("ABC-9").withStrategy("fresh"), null).message();

        assertThat(out).contains("already exists in group-a");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void relaysTheHumansNotesToTheAgentAlongsideTheTicket() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-1");
        when(placement.read("ABC-1")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("ABC-1"), any(), any())).thenReturn(new TicketPlacement.Placed(item, List.of("demo")));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withMode("plan")
                .withNotes("start with tests only"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().instructions()).contains("start with tests only");
    }

    @Test
    void carriesTheModeTheHumanAskedForThroughToTheAgent() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-1");
        when(placement.read("ABC-1")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("ABC-1"), any(), any())).thenReturn(new TicketPlacement.Placed(item, List.of("demo")));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withMode("plan"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().mode()).isEqualTo("plan");
    }

    @Test
    void carriesTheHumansBranchStrategyThroughToTheWorktreeCut() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-1");
        when(placement.read("ABC-1")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("ABC-1"), any(), any())).thenReturn(new TicketPlacement.Placed(item, List.of("demo")));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withStrategy("recreate"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("recreate");
    }

    @Test
    void carriesTheHumansBaseBranchThroughToTheWorktreeCut() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-1");
        when(placement.read("ABC-1")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("ABC-1"), any(), any())).thenReturn(new TicketPlacement.Placed(item, List.of("demo")));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withBaseBranch("feature/parent"),
                List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void createsOneTaskAcrossEveryProjectItIsHanded() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-1");
        when(placement.read("ABC-1")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(placement.place(eq("ABC-1"), any(), any()))
                .thenReturn(new TicketPlacement.Placed(item, List.of("web", "api")));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("web,api"), List.of("web", "api"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.captor();
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().projectKeys()).containsExactly("web", "api");
        assertThat(created.getValue().projectKey()).isEqualTo("web");
    }

    @Test
    void makesATaskOfWhatTheHumanWroteWithoutSpendingATrackerRead() {
        when(worktrees.freeTaskName("split-the-invoice-mailer", List.of("group-a")))
                .thenReturn("split-the-invoice-mailer");

        launches.written(LaunchRequest.defaults().withProject("group-a").withNotes("Split the invoice mailer"),
                List.of("group-a"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::title, NewTask::instructions, NewTask::ticketUrl)
                .containsExactly("split-the-invoice-mailer", "Split the invoice mailer",
                        "Split the invoice mailer", null);
        verifyNoInteractions(placement);
    }
}

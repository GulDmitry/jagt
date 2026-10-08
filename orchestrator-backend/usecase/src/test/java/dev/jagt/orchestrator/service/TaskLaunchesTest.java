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
    private final TicketReader tickets = mock(TicketReader.class);
    private final ProjectRouting routing = mock(ProjectRouting.class);
    private final TaskLaunches launches = new TaskLaunches(provisioning, tickets, routing);

    @Test
    void namesTheTaskByTheCanonicalKeyTheReadGaveBackWhenGivenAUrl() {
        when(tickets.read("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true).withKey("ABC-123")
                        .withTitle("Some title").withTrackerProject("ABC")
                        .withUrl("https://tracker.example.com/browse/ABC-123")), TokenUsage.NONE));

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
    void chargesTheTicketReadToTheTaskItJustNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        when(tickets.read("https://tracker/ABC-123")).thenReturn(new Answer<>(
                Optional.of(TicketFacts.defaults().withExists(true).withKey("ABC-123").withTitle("t")
                        .withTrackerProject("ABC").withUrl("https://tracker/ABC-123")), spent));

        launches.ticket(LaunchRequest.of("https://tracker/ABC-123").withProject("group-a"), List.of("group-a"));

        var order = inOrder(provisioning, tickets);
        order.verify(provisioning).initializeTask(any());
        order.verify(tickets).charge("ABC-123", spent);
    }

    @Test
    void refusesAnItemTheRouterCouldNotPlaceRatherThanPickingARepository() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults()
                .withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42")), TokenUsage.NONE));
        when(routing.projectFor(any())).thenReturn(new ProjectRouting.Undecided("placed in no configured project"));

        String out = launches.ticket(LaunchRequest.of("ABC-42"), null).message();

        assertThat(out).contains("ABC-42 not placed in a configured project: placed in no configured project");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void asksTheRouterWhereToPutAnItemNobodyNamedAProjectFor() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults()
                .withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42")), TokenUsage.NONE));
        when(routing.projectFor(any())).thenReturn(new ProjectRouting.Placed("group-a"));

        launches.ticket(LaunchRequest.of("ABC-42"), null);

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().projectKey()).isEqualTo("group-a");
    }

    @Test
    void buysNoSecondReadOfFactsTheCallerAlreadyPaidFor() {
        when(provisioning.strategyForExisting("ABC-42", "group-a")).thenReturn(BranchStrategy.FRESH);
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
                .withTitle("Widget layout is off").withUrl("https://tracker/ABC-42");

        launches.ticket(LaunchRequest.of("ABC-42").withProject("group-a"), List.of("group-a"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));

        verify(tickets, never()).read(anyString());
    }

    @Test
    void createsNoTaskWhenTheTrackerSaysThereIsNoSuchItem() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults()),
                TokenUsage.ofCall(38_000, 0, 60, 0.41)));

        String out = launches.ticket(LaunchRequest.of("ABC-42"), null).message();

        assertThat(out).contains("no such item: ABC-42", "no task created");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void saysTheReadFailedInsteadOfCallingTheTicketMissing() {
        when(tickets.read("ABC-42")).thenReturn(Answer.unavailable());

        assertThat(launches.ticket(LaunchRequest.of("ABC-42"), null).message()).contains("read failed");
    }

    @Test
    void createsNoTaskWhenTheReadAnsweredAboutADifferentItem() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-99").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-99")), TokenUsage.NONE));

        String out = launches.ticket(LaunchRequest.of("ABC-42"), null).message();

        assertThat(out).contains("asked for ABC-42 and got ABC-99 back", "no task created");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void warnsAboutALeftoverBranchWithoutSpendingATicketRead() {
        when(provisioning.existingBranchProject(eq("ABC-9"), any())).thenReturn("group-a");

        String out = launches.ticket(LaunchRequest.of("ABC-9"), null).message();

        assertThat(out).contains("already exists in group-a", "recreate", "resume");
        verifyNoInteractions(tickets);
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void intakeContinuesALeftoverBranchHoldingWorkInsteadOfRefusingIt() {
        when(provisioning.strategyForExisting("ABC-9", "group-a")).thenReturn(BranchStrategy.RESUME);
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-9").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-9");

        launches.ticket(LaunchRequest.of("ABC-9").withProject("group-a"), List.of("group-a"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("resume");
    }

    @Test
    void warnsAboutALeftoverBranchWhenTheHumanAskedForAFreshOne() {
        when(provisioning.existingBranchProject(eq("ABC-9"), any())).thenReturn("group-a");

        String out = launches.ticket(LaunchRequest.of("ABC-9").withStrategy("fresh"), null).message();

        assertThat(out).contains("already exists in group-a");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void relaysTheHumansNotesToTheAgentAlongsideTheTicket() {
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-1").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-1")), TokenUsage.NONE));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withMode("plan")
                .withNotes("start with tests only"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().instructions()).contains("start with tests only");
    }

    @Test
    void carriesTheModeTheHumanAskedForThroughToTheAgent() {
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-1").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-1")), TokenUsage.NONE));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withMode("plan"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().mode()).isEqualTo("plan");
    }

    @Test
    void carriesTheHumansBranchStrategyThroughToTheWorktreeCut() {
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-1").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-1")), TokenUsage.NONE));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withStrategy("recreate"), List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("recreate");
    }

    @Test
    void carriesTheHumansBaseBranchThroughToTheWorktreeCut() {
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-1").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-1")), TokenUsage.NONE));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("demo").withBaseBranch("feature/parent"),
                List.of("demo"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void createsOneTaskAcrossEveryProjectItIsHanded() {
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-1").withTitle("Widget layout is off").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-1")), TokenUsage.NONE));

        launches.ticket(LaunchRequest.of("ABC-1").withProject("web,api"), List.of("web", "api"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.captor();
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().projectKeys()).containsExactly("web", "api");
        assertThat(created.getValue().projectKey()).isEqualTo("web");
    }

    @Test
    void makesATaskOfWhatTheHumanWroteWithoutSpendingATrackerRead() {
        when(provisioning.freeTaskName("split-the-invoice-mailer", List.of("group-a")))
                .thenReturn("split-the-invoice-mailer");

        launches.written(LaunchRequest.defaults().withProject("group-a").withNotes("Split the invoice mailer"),
                List.of("group-a"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::title, NewTask::instructions, NewTask::ticketUrl)
                .containsExactly("split-the-invoice-mailer", "Split the invoice mailer",
                        "Split the invoice mailer", null);
        verifyNoInteractions(tickets);
    }
}

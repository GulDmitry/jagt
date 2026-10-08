package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResumeRegistrationTest {

    private final TaskProvisioning provisioning = mock(TaskProvisioning.class);
    private final TicketReader tickets = mock(TicketReader.class);
    private final ResumeRegistration registration = new ResumeRegistration(provisioning,
            mock(AgentStatusReports.class), tickets);

    @Test
    void storesTheRequestsTitleBareAndItsTargetAsTheTasksBase() {
        when(tickets.read("PROJ-1")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("PROJ-1").withTitle("Excel export").withTrackerProject("PROJ")
                .withUrl("https://tracker/PROJ-1")), TokenUsage.NONE));

        registration.register("PROJ-1", "proj", "https://host/mr/425", "PROJ-1 Excel export", "release/2");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::projectKey, NewTask::title, NewTask::baseBranch)
                .containsExactly("PROJ-1", "proj", "Excel export", "release/2");
    }

    @Test
    void titlesTheCardFromTheTicketWhenTheRequestIsNamedAfterNothingButItsKey() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-42").withTitle("Excel export drops the last row").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-42")), TokenUsage.NONE));

        registration.register("ABC-42", "proj", "https://host/mr/450", "ABC-42", "release/2");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue()).extracting(NewTask::title, NewTask::ticketUrl)
                .containsExactly("Excel export drops the last row", "https://tracker/ABC-42");
    }

    @Test
    void linksTheCardToTheTicketWhereTheRequestTitledItself() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-42").withTitle("Excel export is broken").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-42")), TokenUsage.NONE));

        registration.register("ABC-42", "proj", "https://host/mr/451", "ABC-42 Excel export drops the last row",
                "release/2");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue()).extracting(NewTask::title, NewTask::ticketUrl)
                .containsExactly("Excel export drops the last row", "https://tracker/ABC-42");
    }

    @Test
    void asksNoTrackerForASourceBranchThatIsNoTicketKey() {
        registration.register("feature/widget-layout", "proj", "https://host/mr/455", "Widget layout is off",
                "main");

        verifyNoInteractions(tickets);
    }

    @Test
    void refusesARequestWhoseTicketTheTrackerNeverAnswersAbout() {
        when(tickets.read("ABC-42")).thenReturn(Answer.unavailable());

        String result = registration.register("ABC-42", "proj", "https://host/mr/456",
                "ABC-42 Excel export drops the last row", "release/2").message();

        assertThat(result).contains("ticket read failed").contains("ABC-42");
        verifyNoInteractions(provisioning);
    }

    @Test
    void refusesToOpenACardWhenTheTrackerAnswersAboutAnotherItem() {
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-43").withTitle("Invoice totals are wrong").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-43")), TokenUsage.NONE));

        String result = registration.register("ABC-42", "proj", "https://host/mr/453", "ABC-42", "release/2")
                .message();

        assertThat(result).isEqualTo("error: asked for ABC-42 and got ABC-43 back — no task created");
        verifyNoInteractions(provisioning);
    }

    @Test
    void chargesTheTicketReadThatTitledTheCardToTheTaskItTitled() {
        TokenUsage spent = TokenUsage.ofCall(9_000, 0, 80, 0.02);
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(TicketFacts.defaults().withExists(true)
                .withKey("ABC-42").withTitle("Excel export drops the last row").withTrackerProject("ABC")
                .withUrl("https://tracker/ABC-42")), spent));

        registration.register("ABC-42", "proj", "https://host/mr/452", "ABC-42", "release/2");

        verify(tickets).charge("ABC-42", spent);
    }
}

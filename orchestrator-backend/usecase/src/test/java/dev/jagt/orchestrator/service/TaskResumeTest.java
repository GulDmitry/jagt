package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskResumeTest {

    private final ReviewReader reviewReader = mock(ReviewReader.class);
    private final RequestProject projects = mock(RequestProject.class);
    private final ResumeRegistration registration = mock(ResumeRegistration.class);
    private final TaskResume resume = new TaskResume(reviewReader, projects, registration);

    @Test
    void takesTheTaskItsTitleAndItsBaseFromTheRequestBeingResumed() {
        when(projects.of("https://host/group/proj/-/merge_requests/425")).thenReturn("proj");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "release/2",
                        "PROJ-1 Excel export")), TokenUsage.NONE));
        when(registration.register("PROJ-1", "proj", "https://host/group/proj/-/merge_requests/425",
                "PROJ-1 Excel export", "release/2")).thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

        resume.resume("https://host/group/proj/-/merge_requests/425");

        verify(registration).register("PROJ-1", "proj", "https://host/group/proj/-/merge_requests/425",
                "PROJ-1 Excel export", "release/2");
    }

    @Test
    void chargesTheRequestReadToTheTaskItNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 120, 0.05);
        when(projects.of("https://host/group/proj/-/merge_requests/425")).thenReturn("proj");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "main",
                        "PROJ-1 Excel export")), spent));
        when(registration.register("PROJ-1", "proj", "https://host/group/proj/-/merge_requests/425",
                "PROJ-1 Excel export", "main")).thenReturn(Launched.created("PROJ-1", "Resumed PROJ-1"));

        resume.resume("https://host/group/proj/-/merge_requests/425");

        verify(reviewReader).charge("PROJ-1", spent);
    }

    @Test
    void saysTheReadFailedInsteadOfCallingTheRequestMissing() {
        when(reviewReader.readRequest("https://host/mr/1")).thenReturn(Answer.unavailable());

        assertThat(resume.resume("https://host/mr/1").message()).contains("read failed");
        verifyNoInteractions(projects, registration);
    }

    @Test
    void refusesARequestTheHostItselfSaysDoesNotExist() {
        when(reviewReader.readRequest("https://host/mr/1")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(false, "", "", "")), TokenUsage.NONE));

        assertThat(resume.resume("https://host/mr/1").message()).contains("no such review request");
    }

    @Test
    void takesOverABranchNamedBySomeoneElsesConvention() {
        when(projects.of("https://host/group/proj/-/merge_requests/426")).thenReturn("proj");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/426"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "feature/widget-layout",
                        "main", "Widget layout is off")), TokenUsage.NONE));
        when(registration.register("feature/widget-layout", "proj", "https://host/group/proj/-/merge_requests/426",
                "Widget layout is off", "main")).thenReturn(Launched.created("feature/widget-layout", "Resumed"));

        resume.resume("https://host/group/proj/-/merge_requests/426");

        verify(registration).register("feature/widget-layout", "proj",
                "https://host/group/proj/-/merge_requests/426", "Widget layout is off", "main");
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "-widget-layout, \"it starts with '-'\"",
            "feature/.widget, \"a part of it starts with '.'\"",
    })
    void namesWhatInASourceBranchStopsItFromBecomingATask(String branch, String reason) {
        when(reviewReader.readRequest("https://host/mr/426")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, branch, "main", "Widget layout is off")),
                TokenUsage.NONE));

        String result = resume.resume("https://host/mr/426").message();

        assertThat(result).contains(branch).contains(reason).contains("do <ticket> from");
        verifyNoInteractions(projects, registration);
    }

    @Test
    void refusesARequestThatNamesNoSourceBranch() {
        when(reviewReader.readRequest("https://host/mr/427")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, " ", "main", "t")), TokenUsage.NONE));

        assertThat(resume.resume("https://host/mr/427").message()).contains("names no source branch");
        verifyNoInteractions(projects, registration);
    }

    @Test
    void refusesAnUnusableTicketIdBeforeResolvingTheProjectFromTheMrUrl() {
        assertThatThrownBy(() -> resume.link("a b", "https://host/mr/1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a branch name");
        verifyNoInteractions(projects, registration);
    }

    @Test
    void refusesToResumeWithoutTheRequestUrlItIsSupposedToLinkTo() {
        assertThatThrownBy(() -> resume.link("ABC-1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resume needs the request url");
        verifyNoInteractions(projects, registration);
    }
}

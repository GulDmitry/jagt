package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskResumeTest {

    private final GitService git = mock(GitService.class);
    private final TaskProvisioning provisioning = mock(TaskProvisioning.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final ReviewReader reviewReader = mock(ReviewReader.class);
    private final TaskResume resume = new TaskResume(provisioning, mock(AgentStatusReports.class),
            configService, git, reviewReader);

    @Test
    void takesTheTaskItsTitleAndItsBaseFromTheRequestBeingResumed() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("proj", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(git.remoteUrl(Path.of("/p"))).thenReturn("git@host:group/proj.git");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "release/2",
                        "PROJ-1 Excel export")), TokenUsage.NONE));

        resume.resume("https://host/group/proj/-/merge_requests/425");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::projectKey, NewTask::title, NewTask::baseBranch)
                .containsExactly("PROJ-1", "proj", "Excel export", "release/2");
    }

    @Test
    void chargesTheRequestReadToTheTaskItNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 120, 0.05);
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("proj", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(git.remoteUrl(Path.of("/p"))).thenReturn("git@host:group/proj.git");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "main",
                        "PROJ-1 Excel export")), spent));

        resume.resume("https://host/group/proj/-/merge_requests/425");

        verify(reviewReader).charge("PROJ-1", spent);
    }

    @Test
    void saysTheReadFailedInsteadOfCallingTheRequestMissing() {
        when(reviewReader.readRequest("https://host/mr/1")).thenReturn(Answer.unavailable());

        assertThat(resume.resume("https://host/mr/1").message()).contains("read failed");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesARequestTheHostItselfSaysDoesNotExist() {
        when(reviewReader.readRequest("https://host/mr/1")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(false, "", "", "")), TokenUsage.NONE));

        assertThat(resume.resume("https://host/mr/1").message()).contains("no such review request");
    }

    @Test
    void takesOverABranchNamedBySomeoneElsesConvention() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("proj", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(git.remoteUrl(Path.of("/p"))).thenReturn("git@host:group/proj.git");
        when(reviewReader.readRequest("https://host/group/proj/-/merge_requests/426"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "feature/widget-layout",
                        "main", "Widget layout is off")), TokenUsage.NONE));

        resume.resume("https://host/group/proj/-/merge_requests/426");

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().taskId()).isEqualTo("feature/widget-layout");
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
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesARequestThatNamesNoSourceBranch() {
        when(reviewReader.readRequest("https://host/mr/427")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, " ", "main", "t")), TokenUsage.NONE));

        assertThat(resume.resume("https://host/mr/427").message()).contains("names no source branch");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesAnUnusableTicketIdBeforeResolvingTheProjectFromTheMrUrl() {
        assertThatThrownBy(() -> resume.link("a b", "https://host/mr/1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a branch name");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesToResumeWithoutTheRequestUrlItIsSupposedToLinkTo() {
        assertThatThrownBy(() -> resume.link("ABC-1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resume needs the request url");
        verifyNoInteractions(git, provisioning);
    }
}

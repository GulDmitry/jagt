package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
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
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final TaskResume resume = new TaskResume(provisioning, mock(AgentStatusReports.class),
            configService, git, assistant);

    @Test
    void takesTheTaskItsTitleAndItsBaseFromTheRequestBeingResumed() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("proj", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(git.remoteUrl(Path.of("/p"))).thenReturn("git@host:group/proj.git");
        when(assistant.readMergeRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "release/2",
                        "group/proj", "PROJ-1 Excel export")), TokenUsage.NONE));

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
        when(assistant.readMergeRequest("https://host/group/proj/-/merge_requests/425"))
                .thenReturn(new Answer<>(Optional.of(new MergeRequestFacts(true, "PROJ-1", "main",
                        "group/proj", "PROJ-1 Excel export")), spent));

        resume.resume("https://host/group/proj/-/merge_requests/425");

        verify(assistant).chargeTask("PROJ-1", spent);
    }

    @Test
    void refusesARequestItCouldNotRead() {
        when(assistant.readMergeRequest("https://host/mr/1")).thenReturn(Answer.unavailable());

        assertThat(resume.resume("https://host/mr/1")).contains("could not read the review request");
        verifyNoInteractions(git, provisioning);
    }

    /**
     * Taking over someone else's request means taking over a branch named by someone else's convention, and a
     * jagt task IS its branch (also a directory and a tmux window). Naming the branch and the way out beats
     * the generic id check reporting a regex against a name the human never typed.
     */
    @Test
    void explainsWhyASlashedSourceBranchCannotBecomeATask() {
        when(assistant.readMergeRequest("https://host/mr/426")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "feature/widget-layout", "main", "group/proj",
                        "Widget layout is off")), TokenUsage.NONE));

        String result = resume.resume("https://host/mr/426");

        assertThat(result).contains("feature/widget-layout").contains("do <ticket> from");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesARequestThatNamesNoSourceBranch() {
        when(assistant.readMergeRequest("https://host/mr/427")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, " ", "main", "group/proj", "t")), TokenUsage.NONE));

        assertThat(resume.resume("https://host/mr/427")).contains("names no source branch");
        verifyNoInteractions(git, provisioning);
    }

    /**
     * The id is validated BEFORE the MR url is matched against every project's git remote — otherwise an
     * unusable id costs a remote lookup per configured project just to be rejected.
     */
    @Test
    void refusesAnUnusableTicketIdBeforeResolvingTheProjectFromTheMrUrl() {
        assertThatThrownBy(() -> resume.link("feature/X", "https://host/mr/1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesToResumeWithoutTheRequestUrlItIsSupposedToLinkTo() {
        assertThatThrownBy(() -> resume.link("ABC-1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resume needs the MR url");
        verifyNoInteractions(git, provisioning);
    }
}

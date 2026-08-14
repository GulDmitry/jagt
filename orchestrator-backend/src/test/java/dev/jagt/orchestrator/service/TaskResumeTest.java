package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskResumeTest {

    private final GitService git = mock(GitService.class);
    private final TaskProvisioning provisioning = mock(TaskProvisioning.class);
    private final TaskResume resume = new TaskResume(provisioning, mock(AgentStatusReports.class),
            mock(ConfigService.class), git);

    /**
     * The id is validated BEFORE the MR url is matched against every project's git remote — otherwise an
     * unusable id costs a remote lookup per configured project just to be rejected.
     */
    @Test
    void refusesAnUnusableTicketIdBeforeResolvingTheProjectFromTheMrUrl() {
        assertThatThrownBy(() -> resume.resume("feature/X", "https://host/mr/1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git, provisioning);
    }

    @Test
    void refusesToResumeWithoutTheRequestUrlItIsSupposedToLinkTo() {
        assertThatThrownBy(() -> resume.resume("ABC-1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resume needs the MR url");
        verifyNoInteractions(git, provisioning);
    }
}

package dev.jagt.orchestrator.capability.trust;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.TrustedGitState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitStateGuardTest {

    private final TrustedGitState trusted = mock(TrustedGitState.class);

    @Test
    void refusesTheVerbNamingWhatGitNowObeys() {
        when(trusted.distrust("ABC-42")).thenReturn(Optional.of("core.fsmonitor in /r/.git/config added"));
        List<String> ran = new ArrayList<>();

        assertThatThrownBy(() -> new GitStateGuard(TaskAction.DEPLOY, trusted).around("ABC-42", () -> {
            ran.add("deploy");
            return Outcome.ok("deployed");
        })).isInstanceOf(Refusal.class)
                .hasMessage("deploy ABC-42 refused: core.fsmonitor in /r/.git/config added");
        assertThat(ran).isEmpty();
    }

    @Test
    void runsTheVerbWhileGitObeysWhatWasTrusted() {
        when(trusted.distrust("ABC-42")).thenReturn(Optional.empty());

        Outcome outcome = new GitStateGuard(TaskAction.SHIP, trusted).around("ABC-42", () -> Outcome.ok("shipped"));

        assertThat(outcome.message()).isEqualTo("shipped");
    }

    @Test
    void refusesBeforeAnyOtherCheckRuns() {
        assertThat(new GitStateGuard(TaskAction.DEPLOY, trusted).order()).isNegative();
    }
}

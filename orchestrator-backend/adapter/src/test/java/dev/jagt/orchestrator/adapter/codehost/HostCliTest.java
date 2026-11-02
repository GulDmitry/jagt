package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostCliTest {

    private final Processes processes = mock(Processes.class);
    private final ReachLedger reach = new ReachLedger();
    private final HostCli cli = new HostCli(processes, reach, "sh", host -> List.of("auth", "login", "--hostname", host));

    @Test
    void startupRefusesASelectedCliMissingFromPathNamingTheKey() {
        HostCli missing = new HostCli(processes, reach, "no-such-host-cli", host -> List.of());

        assertThat(missing.missing("orchestrator.codeHost")).extracting(Problem::key)
                .containsExactly("orchestrator.codeHost");
    }

    @Test
    void aCliMissingFromPathIsLostReachWithNoCommandToFixIt() {
        HostCli missing = new HostCli(processes, reach, "no-such-host-cli", host -> List.of());
        when(processes.run(isNull(), any(Duration.class), anyList())).thenThrow(new IllegalStateException("Failed to start"));

        Answer<String> answer = missing.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach()).contains(new ReachLost("no-such-host-cli", "git.example.invalid", List.of(),
                "no-such-host-cli is not installed"));
    }

    @Test
    void aCliSignedOutNamesItsOwnLoginAsTheFix() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "HTTP 404"))
                .thenReturn(new Processes.Result(1, "", "not authenticated"));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach().orElseThrow().fix())
                .endsWith("auth", "login", "--hostname", "git.example.invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP 401: Bad credentials", "HTTP 403: Forbidden"})
    void aHostRefusingTheLoginIsLostReachWithTheClisLoginAsTheFix(String refusal) {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", refusal));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach().orElseThrow().fix()).isNotEmpty();
    }

    @Test
    void aHostRefusingARequestOnItsRateLimitIsAFailedReadNotLostReach() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "HTTP 403: API rate limit exceeded"));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach()).isEmpty();
        assertThat(reach.standing()).isEmpty();
    }

    @Test
    void aHostThatCannotBeReachedIsLostReachWithNoCommandToFixIt() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "dial tcp: lookup git.example.invalid: no such host"));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach().orElseThrow().fix()).isEmpty();
    }

    @Test
    void aHostAnsweringWithAServerErrorIsAFailedReadNotLostReach() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "HTTP 500"));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach()).isEmpty();
        assertThat(reach.standing()).isEmpty();
    }

    @Test
    void aCliTimingOutIsAFailedReadNotLostReach() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenThrow(new IllegalStateException("Command timed out after PT30S"));

        Answer<String> answer = cli.run("git.example.invalid", List.of("api"));

        assertThat(answer.reach()).isEmpty();
        assertThat(answer.failure()).startsWith("sh did not run");
    }

    @Test
    void theNextReadThatSucceedsEndsTheLostReachOfThatHost() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "HTTP 401"))
                .thenReturn(new Processes.Result(0, "ok", ""));
        cli.run("git.example.invalid", List.of("api"));

        cli.run("git.example.invalid", List.of("api"));

        assertThat(reach.standing()).isEmpty();
    }

    @Test
    void aHostAnsweringWithAServerErrorEndsTheLostReachOfThatHost() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "HTTP 401"))
                .thenReturn(new Processes.Result(1, "", "HTTP 500"));
        cli.run("git.example.invalid", List.of("api"));

        cli.run("git.example.invalid", List.of("api"));

        assertThat(reach.standing()).isEmpty();
    }

    @Test
    void aReadSucceedingOnAnotherHostLeavesTheLostReachStanding() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "HTTP 401"))
                .thenReturn(new Processes.Result(0, "ok", ""));
        cli.run("git.example.invalid", List.of("api"));

        cli.run("other.example.invalid", List.of("api"));

        assertThat(reach.standing()).extracting(ReachLost::host).containsExactly("git.example.invalid");
    }
}

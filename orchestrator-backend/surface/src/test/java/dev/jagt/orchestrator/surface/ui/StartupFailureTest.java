package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.startup.Misconfigured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.PortInUseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupFailureTest {

    /** The common case by far: the human started a second jagt while the first still held the port. */
    @Test
    void namesThePortAndTheOtherWayToStartWhenTheAddressIsTaken() {
        IllegalStateException wrapped = new IllegalStateException("context failed",
                new PortInUseException(8290));

        assertThat(StartupFailure.describe(wrapped))
                .contains("port 8290 is already in use")
                .contains("--server.port=<port>");
    }

    @Test
    void handsBackTheWholeListWhenTheInstallationIsIncompleteRatherThanPointingAtALog() {
        IllegalStateException wrapped = new IllegalStateException("startup failed",
                new Misconfigured(List.of("git is not on PATH", "jagt.yml defines no projects")));

        assertThat(StartupFailure.describe(wrapped))
                .contains("1. git is not on PATH")
                .contains("2. jagt.yml defines no projects")
                .doesNotContain("log file");
    }

    @Test
    void pointsAtTheLogFileForAnyOtherFailure() {
        assertThat(StartupFailure.describe(new IllegalStateException("jagt.yml is unreadable")))
                .contains("jagt.yml is unreadable")
                .contains("log file");
    }
}

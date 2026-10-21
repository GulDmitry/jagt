package dev.jagt.orchestrator.surface.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("loopback-ports")
class LogFileResetTest {

    @Test
    void clearsTheFileAndItsArchivesBeforeTheAppenderOpensIt(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "yesterday\n");
        Files.writeString(root.resolve("jagt.log.2026-08-17.0.gz"), "older still");
        Files.writeString(root.resolve("keep-me.log"), "another program's");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        new LogFileReset().apply(new MockEnvironment()
                .withProperty("logging.file.name", log.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(log).doesNotExist();
        assertThat(root.resolve("jagt.log.2026-08-17.0.gz")).doesNotExist();
        assertThat(root.resolve("keep-me.log")).exists();
    }

    @Test
    void clearsTheMasterFeedAsWellAsTheMainLog(@TempDir Path root) throws IOException {
        Path feed = root.resolve("master.log");
        Files.writeString(feed, "yesterday's steps\n");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        new LogFileReset().apply(new MockEnvironment()
                .withProperty("logging.file.name", root.resolve("jagt.log").toString())
                .withProperty("logging.master.name", feed.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(feed).doesNotExist();
    }

    @Test
    void leavesTheLogOfAJagtThatIsStillListeningAlone(@TempDir Path root) throws Exception {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "the running instance's own record\n");

        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            boolean cleared = new LogFileReset().apply(new MockEnvironment()
                    .withProperty("logging.file.name", log.toString())
                    .withProperty("server.port", String.valueOf(occupied.getLocalPort())));

            assertThat(cleared).isFalse();
        }

        assertThat(log).content().contains("the running instance's own record");
    }

    @Test
    void clearsTheLogWhenTheConfiguredPortIsNotANumber(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "yesterday\n");

        boolean cleared = new LogFileReset().apply(new MockEnvironment()
                .withProperty("logging.file.name", log.toString())
                .withProperty("server.port", "eighty"));

        assertThat(cleared).isTrue();
        assertThat(log).doesNotExist();
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void tellsTheHumanWhichLogItCouldNotClearAndStillStarts(@TempDir Path root, CapturedOutput output)
            throws IOException {
        Path stuck = Files.createDirectories(root.resolve("jagt.log"));
        Files.writeString(stuck.resolve("inside"), "keeps the directory from being deleted");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        boolean cleared = new LogFileReset().apply(new MockEnvironment()
                .withProperty("logging.file.name", stuck.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(cleared).isTrue();
        assertThat(output.getErr()).contains("Could not clear " + stuck);
    }

    @Test
    void clearsNothingWhenNoLogFileIsConfigured() {
        assertThat(new LogFileReset().apply(new MockEnvironment())).isFalse();
    }

    @Test
    void runsAfterTheConfigFilesAreReadAndBeforeLoggingIsInitialised() {
        assertThat(new LogFileReset().getOrder())
                .isGreaterThan(new EnvironmentPostProcessorApplicationListener().getOrder())
                .isLessThan(new LoggingApplicationListener().getOrder());
    }
}

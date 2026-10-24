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
class LogFileRollTest {

    @Test
    void keepsThePreviousRunsLogAsTheOneArchiveBeforeTheAppenderOpensIt(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "yesterday\n");
        Files.writeString(root.resolve("jagt.log.1"), "the day before");
        Files.writeString(root.resolve("jagt.log.2026-08-17.0.gz"), "older still");
        Files.writeString(root.resolve("keep-me.log"), "another program's");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        new LogFileRoll().apply(new MockEnvironment()
                .withProperty("logging.file.name", log.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(log).doesNotExist();
        assertThat(root.resolve("jagt.log.1")).content().contains("yesterday");
        assertThat(root.resolve("jagt.log.2026-08-17.0.gz")).doesNotExist();
        assertThat(root.resolve("keep-me.log")).exists();
    }

    @Test
    void leavesADirectoryNamedAsTheLogAndItsDottedSiblingsInPlace(@TempDir Path root) throws IOException {
        Path folder = Files.createDirectories(root.resolve("surface"));
        Files.writeString(folder.resolve("Kept.java"), "class Kept {}");
        Path sibling = Files.createDirectories(root.resolve("surface.gradle"));
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        new LogFileRoll().apply(new MockEnvironment()
                .withProperty("logging.file.name", folder.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(folder.resolve("Kept.java")).exists();
        assertThat(sibling).exists();
        assertThat(root.resolve("surface.1")).doesNotExist();
    }

    @Test
    void rollsTheMasterFeedAsWellAsTheMainLog(@TempDir Path root) throws IOException {
        Path feed = root.resolve("master.log");
        Files.writeString(feed, "yesterday's steps\n");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        new LogFileRoll().apply(new MockEnvironment()
                .withProperty("logging.file.name", root.resolve("jagt.log").toString())
                .withProperty("logging.master.name", feed.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(root.resolve("master.log.1")).content().contains("yesterday's steps");
    }

    @Test
    void leavesTheLogOfAJagtThatIsStillListeningAlone(@TempDir Path root) throws Exception {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "the running instance's own record\n");

        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            boolean rolled = new LogFileRoll().apply(new MockEnvironment()
                    .withProperty("logging.file.name", log.toString())
                    .withProperty("server.port", String.valueOf(occupied.getLocalPort())));

            assertThat(rolled).isFalse();
        }

        assertThat(log).content().contains("the running instance's own record");
    }

    @Test
    void rollsTheLogWhenTheConfiguredPortIsNotANumber(@TempDir Path root) throws IOException {
        Path log = root.resolve("jagt.log");
        Files.writeString(log, "yesterday\n");

        boolean rolled = new LogFileRoll().apply(new MockEnvironment()
                .withProperty("logging.file.name", log.toString())
                .withProperty("server.port", "eighty"));

        assertThat(rolled).isTrue();
        assertThat(log).doesNotExist();
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void tellsTheHumanWhichLogItCouldNotRollAndStillStarts(@TempDir Path root, CapturedOutput output)
            throws IOException {
        Path stuck = root.resolve("jagt.log");
        Files.writeString(stuck, "yesterday\n");
        Path archive = Files.createDirectories(root.resolve("jagt.log.1"));
        Files.writeString(archive.resolve("inside"), "keeps the archive from being replaced");
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            freePort = probe.getLocalPort();
        }

        boolean rolled = new LogFileRoll().apply(new MockEnvironment()
                .withProperty("logging.file.name", stuck.toString())
                .withProperty("server.port", String.valueOf(freePort)));

        assertThat(rolled).isTrue();
        assertThat(output.getErr()).contains("Could not roll " + stuck);
    }

    @Test
    void rollsNothingWhenNoLogFileIsConfigured() {
        assertThat(new LogFileRoll().apply(new MockEnvironment())).isFalse();
    }

    @Test
    void runsAfterTheConfigFilesAreReadAndBeforeLoggingIsInitialised() {
        assertThat(new LogFileRoll().getOrder())
                .isGreaterThan(new EnvironmentPostProcessorApplicationListener().getOrder())
                .isLessThan(new LoggingApplicationListener().getOrder());
    }
}

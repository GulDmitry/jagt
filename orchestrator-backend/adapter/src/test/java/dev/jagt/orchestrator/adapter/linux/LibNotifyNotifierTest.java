package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibNotifyNotifierTest {

    @Test
    void refusesToStartWhenNoAlertCouldEverBeDelivered() {
        var notifier = new LibNotifyNotifier(mock(ProcessRunner.class), "no-such-notify-send");

        assertThat(notifier.problems()).singleElement(STRING)
                .contains("orchestrator.notify-send-command", "no-such-notify-send");
    }

    @Test
    void passesTicketTextAfterTheOptionTerminatorSoALeadingDashIsNotReadAsAnOption() {
        List<String> command = LibNotifyNotifier.command("notify-send", "-jagt · ABC-1", "--your move");

        assertThat(command).containsExactly("notify-send", "--app-name", "jagt", "--urgency", "normal", "--",
                "-jagt · ABC-1", "--your move");
    }

    @Test
    void neverThrowsWhenThereIsNoNotificationDaemon() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), any(Duration.class), any()))
                .thenReturn(new Processes.Result(1, "", "No notification daemon"));

        assertThatCode(() -> new LibNotifyNotifier(runner, "notify-send").notify("jagt", "your move", null))
                .doesNotThrowAnyException();
    }

    @Test
    void neverThrowsWhenTheBinaryIsNotInstalled() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), any(Duration.class), any()))
                .thenThrow(new IllegalStateException("Cannot run program \"notify-send\""));

        assertThatCode(() -> new LibNotifyNotifier(runner, "notify-send").notify("jagt", "your move", null))
                .doesNotThrowAnyException();
    }

    @Test
    void runsTheConfiguredBinarySoADistroPathCanBeOverridden() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "", ""));

        new LibNotifyNotifier(runner, "/usr/bin/notify-send").notify("jagt", "your move", null);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(), any(Duration.class), command.capture());
        assertThat(command.getValue()).startsWith("/usr/bin/notify-send");
    }
}

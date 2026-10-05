package dev.jagt.orchestrator.adapter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LsofWorktreeProcessesTest {

    @Test
    void reapSparesTheTmuxViewerButTakesAgentDaemonsUnderTheWorktree() {
        String lsof = String.join("\n",
                "p100", "cjava", "fcwd", "n/Users/x/www/wt",
                "p200", "cnode", "fcwd", "n/Users/x/www/wt/app",
                "p300", "ctmux", "fcwd", "n/Users/x/www/wt",
                "p400", "cjava", "fcwd", "n/Users/x/www/other");

        assertThat(LsofWorktreeProcesses.reapable(lsof, "/Users/x/www/wt"))
                .extracting(LsofWorktreeProcesses.Reapable::pid)
                .containsExactly("100", "200");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsOneLineForAWorktreeHowEverManyProcessesItHeld() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger reaperLog = (Logger) LoggerFactory.getLogger(LsofWorktreeProcesses.class);
        reaperLog.addAppender(log);
        Processes runner = mock(Processes.class);
        when(runner.run(isNull(), any(Duration.class), any())).thenReturn(new Processes.Result(0, String.join("\n",
                "p100", "cclaude", "fcwd", "n/nonexistent/wt",
                "p200", "cnode", "fcwd", "n/nonexistent/wt/app"), ""));

        new LsofWorktreeProcesses(runner).reap(Path.of("/nonexistent/wt"));

        assertThat(List.copyOf(log.list)).extracting(ILoggingEvent::getMessage)
                .containsExactly("worktree processes reaped");
        reaperLog.detachAppender(log);
    }
}

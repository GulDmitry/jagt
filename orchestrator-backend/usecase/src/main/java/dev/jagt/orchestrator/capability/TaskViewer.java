package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The agents window as the human sees it: raised onto a task, closed once no task is left. */
@Component
@RequiredArgsConstructor
public class TaskViewer {

    private final AgentSessions sessions;
    private final TerminalDriver terminal;

    public String focus(String taskId) {
        AgentSessions.Focused focused = sessions.focusTask(taskId);
        return "Focused tab '" + focused.taskId() + "'" + viewer(terminal.reveal(focused.viewerTitle()))
                + (focused.revivedAs() != null ? "; the session was down, re-entered its " + focused.revivedAs()
                        + " session" : "");
    }

    public void killWindows(String taskId) {
        sessions.killWindows(taskId);
    }

    public boolean closeIfNoTasksLeft() {
        return sessions.viewerToClose().map(title -> {
            terminal.closeViewerWindow(title);
            return true;
        }).orElse(false);
    }

    /** What is left for the human to do about the viewer, which only the terminal can say. */
    private static String viewer(TerminalDriver.Revealed revealed) {
        return switch (revealed) {
            case WINDOW -> " and raised the agents window";
            case UNREACHABLE_TAB -> " \u2014 the agents viewer is a tab this terminal cannot select; click it"
                    + " yourself";
            case NOT_RUNNING -> " \u2014 no agents viewer is open; the session is there, nothing is showing it";
        };
    }
}

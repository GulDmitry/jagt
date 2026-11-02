package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginCommand implements GlobalCommand {

    static final String WINDOW = "login";

    private final Reach reach;
    private final SessionHost sessions;
    private final ConfigService configService;

    @Override
    public String id() {
        return "login";
    }

    @Override
    public int rank() {
        return 187;
    }

    @Override
    public String hint() {
        return "log in where jagt lost its reach, in a window of its own";
    }

    @Override
    public String run(String tail) {
        ReachLost lost = reach.standing().stream().filter(one -> !one.fix().isEmpty()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no login is waiting: jagt reaches what it reads"));
        List<String> fix = lost.fix();
        String session = sessions.sessionName(configService.load().viewer().session());
        sessions.openCommandWindow(session, session, WINDOW, fix);
        return "`" + String.join(" ", fix) + "` is open in the " + WINDOW + " window; the next read that succeeds clears the mark";
    }
}

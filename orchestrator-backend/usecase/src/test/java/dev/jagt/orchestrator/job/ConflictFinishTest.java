package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConflictFinishTest {

    private final CommandService commands = mock(CommandService.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void pressesTheDeployTheHumanStarted() {
        new ConflictFinish(commands, config).deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(commands).execute("ABC-42", TaskAction.DEPLOY);
    }

    @Test
    void countsTheDeployItFinishedAsAnUnattendedStart() {
        new ConflictFinish(commands, config).deploy("ABC-42", Path.of("/src/ABC-42-deploy"));

        verify(config).startTaken();
    }
}

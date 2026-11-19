package dev.jagt.orchestrator.job.verdict;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MasterFeedWindowTest {

  private final ConfigService config = mock(ConfigService.class);
  private final SessionHost sessions = mock(SessionHost.class);

  @Test
  void keepsTheMasterWindowFollowingItsFeedWhereTheMasterRuns() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withArbiter(
                    new ConfigService.ConfigFile.ArbiterConfig("judge", null, null, null, null)));
    when(sessions.sessionName(any())).thenReturn("jagt");

    new MasterFeedWindow(config, sessions, "jagt-master.log").run();

    verify(sessions).keepFeedWindow("jagt", "master", Path.of("jagt-master.log").toAbsolutePath());
  }

  @Test
  void opensNoMasterWindowWhereTheMasterIsOff() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    new MasterFeedWindow(config, sessions, "jagt-master.log").run();

    verify(sessions, never()).keepFeedWindow(anyString(), anyString(), any());
  }
}

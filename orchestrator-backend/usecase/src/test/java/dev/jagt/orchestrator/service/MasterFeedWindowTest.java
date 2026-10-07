package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionHost;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterFeedWindowTest {

    private final ConfigService config = mock(ConfigService.class);
    private final SessionHost sessions = mock(SessionHost.class);

    @Test
    void keepsTheMasterWindowFollowingItsFeedWhereTheMasterRuns() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(sessions.sessionName(any())).thenReturn("jagt");

        new MasterFeedWindow(config, sessions).run();

        verify(sessions).keepFeedWindow("jagt", "master", Path.of("jagt-master.log").toAbsolutePath());
    }

    @Test
    void opensNoMasterWindowWhereTheMasterIsOff() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        new MasterFeedWindow(config, sessions).run();

        verify(sessions, never()).keepFeedWindow(anyString(), anyString(), any());
    }
}

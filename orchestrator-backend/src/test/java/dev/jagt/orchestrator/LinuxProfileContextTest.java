package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.config.import=",
        "orchestrator.open-terminal-window=false", "orchestrator.startup-checks=false",
        "orchestrator.platform=linux"})
@ResourceLock("spring-logging")
class LinuxProfileContextTest {

    @TempDir
    static Path root;

    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;

    @Autowired
    private UserNotifier userNotifier;
    @Autowired
    private TerminalDriver terminalDriver;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void picksTheLinuxNotifierAndTerminalWithoutAnyMacOsBeanBeingRequired() {
        assertThat(userNotifier).isInstanceOf(LibNotifyNotifier.class);
        assertThat(terminalDriver).isInstanceOf(LinuxKittyTerminalDriver.class);
    }
}

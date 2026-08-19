package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.adapter.linux.LinuxKittyTerminalDriver;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.surface.console.MasterShell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Linux platform profile must WIRE, and only a booted context can say so: the driver beans are selected by
 * a condition over two properties, so a typo in one of them yields no `TerminalDriver` at all — a failure that
 * would otherwise surface the first time somebody starts jagt on Linux, not here.
 *
 * <p>Being able to run this from macOS is the point of the seam: which driver Spring picks is a config
 * decision, not an OS one. It says nothing about the drivers' behaviour on a real desktop.
 */
@SpringBootTest(properties = {"orchestrator.open-warp-window=false", "orchestrator.startup-checks=false",
        "orchestrator.platform=linux"})
class LinuxProfileContextTest {

    @TempDir
    static Path root;

    @MockitoBean
    private MasterShell masterShell;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;

    @Autowired
    private UserNotifier userNotifier;
    @Autowired
    private TerminalDriver terminalDriver;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("config.json").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void picksTheLinuxNotifierAndTerminalWithoutAnyMacOsBeanBeingRequired() {
        assertThat(userNotifier).isInstanceOf(LibNotifyNotifier.class);
        assertThat(terminalDriver).isInstanceOf(LinuxKittyTerminalDriver.class);
    }
}

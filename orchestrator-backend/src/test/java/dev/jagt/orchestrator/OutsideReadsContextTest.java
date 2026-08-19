package dev.jagt.orchestrator;

import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.adapter.codehost.GitHubCodeHost;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.surface.console.MasterShell;
import dev.jagt.orchestrator.adapter.tracker.JiraTracker;
import dev.jagt.orchestrator.port.Tracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A configured host and tracker must WIRE, and only a booted context can say so: both are selected by a
 * condition over a property value, so a typo yields no bean at all — and no bean means every read silently
 * falls back to the PAID one, which is the failure this seam exists to remove. It would otherwise surface as a
 * bill.
 */
@SpringBootTest(properties = {
        "orchestrator.open-warp-window=false",
        "orchestrator.startup-checks=false",
        "orchestrator.code-host.type=github",
        "orchestrator.code-host.base-url=https://github.com",
        "orchestrator.code-host.token=test-token",
        "orchestrator.tracker.type=jira",
        "orchestrator.tracker.base-url=https://tracker.example.com",
        "orchestrator.tracker.token=test-token"})
@ResourceLock("spring-logging")
class OutsideReadsContextTest {

    @TempDir
    static Path root;

    @MockitoBean
    private MasterShell masterShell;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;

    @Autowired
    private List<CodeHost> codeHosts;
    @Autowired
    private List<Tracker> trackers;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("config.json").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
    }

    @Test
    void activatesTheNamedHostAndTrackerAndNothingElse() {
        assertThat(codeHosts).singleElement().isInstanceOf(GitHubCodeHost.class);
        assertThat(trackers).singleElement().isInstanceOf(JiraTracker.class);
    }
}

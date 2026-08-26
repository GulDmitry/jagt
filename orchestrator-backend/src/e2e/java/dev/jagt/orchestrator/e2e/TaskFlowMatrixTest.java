package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.capability.done.TaskRetirement;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end task flow, once per {@link TaskFlowCase} — real git worktrees, real tmux windows, real state
 * transitions, with the model replaced by the scripted {@code stub} runtime so the expected result is exact
 * rather than plausible. Creation and teardown are what varies with these combinations; the flow between them
 * is {@link ReviewAndDeployFlowTest}, and what neither covers is named in {@link TaskFlowCase}.
 *
 * <p>Not part of {@code ./gradlew test}: it needs git + tmux and writes worktrees. Run it with
 * {@code ./gradlew e2eTest}. It leaves no trace — throwaway origin/clone under a temp dir, a throwaway tmux
 * session killed afterwards, the task's leftover branch deleted between cases, and every GUI-touching driver
 * replaced by a double.
 */
@Tag("e2e")
@SpringBootTest(properties = {"spring.config.import=",
        "orchestrator.agent.cli=stub", "orchestrator.open-terminal-window=false",
        "orchestrator.startup-checks=false"})
class TaskFlowMatrixTest {

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void orchestratorLivesInTheTempWorkspace(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> workspace.resolve("root").toString());
        registry.add("orchestrator.config-file", () -> workspace.resolve("root/jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> workspace.resolve("root/state.json").toString());
    }

    /** It would act on the DEVELOPER's machine: the cleaner rewrites the IDE's own state. */
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;
    /** The headless doubles the oracle needs: a window, an editor or a notification cannot be asserted. */
    @MockitoBean
    private TerminalDriver terminalDriver;
    @MockitoBean
    private EditorDriver editorDriver;
    @MockitoBean
    private UserNotifier userNotifier;

    @Autowired
    private TaskProvisioning provisioning;
    @Autowired
    private TaskRetirement retirement;
    @Autowired
    private StateService stateService;
    @Autowired
    private OrchestratorPaths paths;
    @Autowired
    private OrchestratorProperties properties;

    @BeforeAll
    static void createTheThrowawayOutsideWorld() throws Exception {
        E2eWorkspace.createRootMarker(workspace.resolve("root"));
        E2eWorkspace.createRepositoryWithOrigin(workspace.resolve("origin.git"), workspace.resolve("proj"));
    }

    /** A run killed mid-case leaves its sessions behind, and the next run would read them as its own. */
    @BeforeEach
    void startFromNoneOfThisRunsSessions() {
        E2eWorkspace.killTmuxSessions(properties.tmuxCommand());
    }

    /**
     * A combination that failed early holds none of this, so nothing here may stop on the piece it cannot find:
     * a cleanup that gives up hands its own leftovers to the next combination, which then fails for a reason the
     * run never had. The one line that can throw — a file write — is last for the same reason.
     */
    @AfterEach
    void leaveNothingBehindForTheNextCombination() {
        // `done` keeps the branch by design, so the next combination would hit "branch already exists".
        E2eWorkspace.forgetTask(workspace.resolve("proj"), workspace.resolve("ABC-1-proj"), "ABC-1");
        E2eWorkspace.killTmuxSessions(properties.tmuxCommand());
        stateService.removeTask("ABC-1");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dev.jagt.orchestrator.e2e.TaskFlowCase#matrix")
    void aTaskIsCreatedProvisionedAndThenTornDownLeavingOnlyItsBranch(TaskFlowCase flowCase) throws Exception {
        E2eWorkspace.writeConfig(paths.configFile(), workspace.resolve("proj"), flowCase);
        Path worktree = workspace.resolve("ABC-1-proj");

        String created = provisioning.initializeTask(NewTask.builder("ABC-1", "proj")
                .instructions("Fix the widget").title("Widget layout is off").build());

        assertThat(created).contains("ABC-1 is a1", "agent running on ABC-1");
        assertThat(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)).exists();
        assertThat(worktree.resolve("task_context.md")).exists();
        // Nothing agent-shaped: no MCP config, no Claude directory, and no stdio bridge either. The bridge is
        // asked for by the runtimes that cannot reach the endpoint themselves (Codex today), so a worktree that
        // has one means something outside the runtime put it there.
        assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
        assertThat(worktree.resolve(".mcp.json")).doesNotExist();
        assertThat(worktree.resolve(".claude")).doesNotExist();
        assertThat(E2eWorkspace.tmuxSessions(properties.tmuxCommand()))
                .containsExactly(flowCase.agentSession());
        assertThat(stateService.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.NEW);
        assertThat(stateService.task("ABC-1").orElseThrow().autoReview()).isEqualTo(flowCase.autoReview());
        assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1")).contains("ABC-1");

        retirement.retire("ABC-1");

        assertThat(worktree).doesNotExist();
        assertThat(stateService.task("ABC-1")).isEmpty();
        assertThat(E2eWorkspace.git(workspace.resolve("proj"), "branch", "--list", "ABC-1")).contains("ABC-1");
    }
}
